# Pustakam — How Data Syncs Between the App and the Server

**Written from the code as it stands on 23-Sep-2026.**
Every file path and method name below is real and can be opened. No logic is reproduced here — this
document tells you *which file and which method* does each job, and *why*, in plain English.

Two repos are involved:

| Repo | Folder |
|---|---|
| App (Kotlin Multiplatform — Android + iOS) | `Pustakm/` |
| Server (Node + Express + MongoDB) | `PustakmServer/` |

---

## 0. The example used throughout

One note, `n_ABC`, holding one of every content type the code supports today:

| # | Content | Kotlin class | `type` on wire | `contentType` on wire |
|---|---|---|---|---|
| 0 | "Team sync notes" | `NoteContentModel.TextContent` | `TEXT` | `TEXT` |
| 1 | voice recording `.m4a` | `NoteContentModel.MediaContent` | `MEDIA` | `AUDIO` |
| 2 | photo `.jpg` | `NoteContentModel.MediaContent` | `MEDIA` | `IMAGE` |
| 3 | a `.pdf` | `NoteContentModel.MediaContent` | `MEDIA` | `PDF` |
| 4 | a video `.mp4` | `NoteContentModel.MediaContent` | `MEDIA` | `VIDEO` |
| 5 | `https://…` | `NoteContentModel.Link` | `LINK` | `LINK` |
| 6 | lat/long | `NoteContentModel.Location` | `LOCATION` | `LOCATION` |

All seven live in the sealed class `NoteContentModel` in
`core/model/src/commonMain/kotlin/com/app/pustakam/core/model/models/response/notes/Note.kt`.

Two things about that file matter more than anything else in this document:

1. **`type` is the sealed-class discriminator** (`TEXT` / `MEDIA` / `LINK` / `LOCATION`) and the
   property is annotated `@SerialName("contentType")`. So the Kotlin property called `type` goes on
   the wire as **`contentType`**, and the word **`type`** on the wire is the *class* name. A media
   block is `type: "MEDIA"`, `contentType: "AUDIO"`.
2. **`@SerialName("_id")` is repeated on all four subclasses.** kotlinx does *not* honour
   `@SerialName` on an abstract property of a sealed base, so the annotation on the base is
   decorative. Every subclass repeats it, plus `@JsonNames("id")` so a note written by an older
   build still parses.

---

## 1. Where a piece of content is born

**File:** `core/model/.../response/notes/NoteContentObjectHelper.kt`

This object is the **only** place a content id is generated. Methods:

| Method | Creates |
|---|---|
| `createText(noteId, positionedAt, text)` | `TextContent` |
| `createMedia(contentType, noteId, positionedAt, localPath, url, duration, …)` | `MediaContent` |
| `createHyperLink(link, noteId, positionedAt)` | `Link` |
| `createLocation(…)` | `Location` |

Each one calls `UniqueIdGenerator.generateUniqueId()` (timestamp + UUID) and stamps `createdAt` /
`updatedAt`. Because the id is minted once and never regenerated, the *same* content id survives
every round trip through the server — that is what makes updates idempotent later.

`position` is a **Double**, not an Int. Inserting between two blocks is `(a + b) / 2`, so no other
row has to be rewritten when the user drags something.

For a media block, `localPath` is set and `assetId` is still null. Remember that pair — it is the
entire upload queue.

---

## 2. The editor collects edits, then saves

**File:** `androidApp/src/main/java/com/app/pustakam/android/screen/noteEditor/NoteEditorViewModel.kt`

The editor keeps a set called **`dirtyContentIds`**. Every touch of a block adds its id; deleting a
block removes it. On save, the ViewModel calls **`CreateORUpdateNoteUseCase(note, dirtyContentIds)`**
and passes that set along, so only the rows the user actually touched are rewritten.

Deleting a single block goes through **`DeleteNoteContentUseCase(contentId)`**.

---

## 3. The repository writes to SQLite and nudges sync

**File:** `feature/notes/src/commonMain/kotlin/com/app/pustakam/feature/notes/data/repositoryImpl/NoteRepository.kt`

### Save — `insertOrUpdateNote(note, dirtyContentIds)`

In order, it:

1. calls `insertUpdateFromDb(note, dirtyContentIds)`, which calls
   `note.stampedWithNextVersion()` and then `notesDao.insertOrUpdateNoteFromDb(...)`;
2. publishes the new contents on the in-app bus — `syncRepository.publishContents(noteId, contents)`;
3. updates the two StateFlows the screens watch, `_notes` and `_noteSummaries`;
4. calls **`remoteSync.requestSyncSoon()`**.

Step 4 is the trigger. Before 28-Aug-2026 the engine watched the summaries flow instead, and any
save that did not happen to move that flow never synced at all.

### Delete a note — `deleteNote(id)`

Calls `deleteNoteByIdFromDb(id)` → `notesDao.deleteNoteByIdFromDb(id)`, removes the note from both
StateFlows, then `remoteSync.requestSyncSoon()` — **a delete has to travel too.**

### Delete one content block — `deleteNoteContentFromDb(id)`

Calls `notesDao.deleteNoteContentById(id)`. There is no separate "content tombstone": the *note* is
what syncs, and a pushed note simply arrives with that block absent, so the server's replace drops it.

### The offline-mode guard

`private suspend fun offlineModeOn(): Boolean = userPrefs.offlineModeFlow.first()`

It guards `upsertNewNoteApi()`, `updateNoteApi()` and `deleteNoteApi()` — the three *write* routes —
each of which falls back to the local write and returns `NetworkError.OFFLINE_MODE`. It deliberately
does **not** guard `getNotesForUserApi()` or `getNoteApi()`: offline mode stops this device
publishing, it never stops it reading.

These three wrappers have no live call site today — the sync engine is the only thing talking to the
server — but the guarantee is written where the call would be, not left to whoever switches them on.

---

## 4. The local database

**Kotlin:** `core/database/src/commonMain/kotlin/com/app/pustakam/core/database/localdb/database/NotesDao.kt`
**SQL:** `core/database/src/commonMain/sqldelight/com/app/pustakam/core/database/NotesDatabase.sq`

### Tables that matter

- `Notes` — one row per note, carrying `version`, `syncStatus`, `deleted`, `deletedAt`, `serverUpdatedAt`
- `NoteContent` — one row per block, carrying `localPath`, `url`, `assetId`, `checksum`, `mimeType`, `sizeBytes`
- `SyncState` — one row per user: `lastPulledAt`, `lastPulledId`, `lastSyncedAt` (the pull watermark)

### Methods, grouped by job

**Writing a local edit**

| Method | What it does |
|---|---|
| `insertOrUpdateNoteFromDb(note, dirtyContentIds?)` | Note row **and** its content rows in **one transaction**. Written apart, sync could read the row between the two writes and push stale contents. `dirtyContentIds = null` means write every block. |
| `deleteNoteByIdFromDb(id)` | **A tombstone, not a DELETE.** In one transaction: `deleteNoteContentsForNote`, `deleteCanvasNodesForNote`, `softDeleteNoteById` → `deleted = 1, syncStatus = 'PENDING_DELETE'`. A hard delete could never reach the other device. |
| `deleteNoteContentById(id)` | Drops one block row. |

**Feeding the push**

| Method | SQL behind it |
|---|---|
| `selectDirtyNotes(limit)` | `selectDirtyNoteIds` → `WHERE syncStatus != 'SYNCED' AND ownerId = :userId ORDER BY updatedAt`. **Tombstones are in this queue on purpose.** |
| `markNoteSynced(id, pushedVersion, acceptedVersion, serverUpdatedAt)` | Cleans the row **only if `version` still equals `pushedVersion`**. A note edited while its push was in flight stays dirty, so the newer edit gets its own turn. |

**Feeding the pull**

| Method | What it does |
|---|---|
| `readSyncWatermark(userId)` | Returns `(lastPulledAt, lastPulledId)`; `(0, null)` on a fresh install. |
| `applyServerNote(note)` | Writes a note that came **from** the server: deletes its content rows, re-inserts the note with `syncStatus = SYNCED`, re-inserts contents. It deliberately does **not** bump `version` or set a dirty status — either would re-queue every pulled note forever. |
| `commitPulledPage(userId, lastPulledAt, lastPulledId, block)` | Runs the note writes **and** the watermark write in one transaction, so a crash re-pulls a page instead of skipping it. |
| `purgeAckedTombstones(before)` | Drops tombstones this device has provably pulled past. |
| `resetSyncWatermark(userId)` | Back to zero — a full re-pull. |
| `selectNoteByIdIncludingDeleted(id)` | Sync's reader. Plain `selectNoteById(id)` hides tombstones so the rest of the app still sees a deleted note as absent. |

**Feeding the media queues**

| Method | The rule |
|---|---|
| `selectNoteIdsNeedingUpload(limit)` | `assetId IS NULL/'' AND localPath IS NOT NULL/''` — this device has the bytes, the server does not. Scoped to this account, newest note first. |
| `selectNoteIdsNeedingMedia(limit)` | The mirror image: `assetId` present, `localPath` empty — the server has the bytes, this device does not. |
| `markNoteDirtyForMedia(id)` | `syncStatus = 'PENDING_UPDATE'` **only where it is currently `SYNCED`**. Puts an already-clean note back on the push queue so a freshly-earned `assetId` actually travels. |
| `stampMediaAsset(contentId, assetId, url, checksum, mimeType, sizeBytes)` | One row after an upload. |
| `stampMediaLocalPath(contentId, localPath)` | One row after a download. |

Both `stamp…` methods write **one row**, never the whole note, because an upload can outlive the
edits made while it was running.

---

## 5. The sync engine

**File:** `feature/notes/src/commonMain/kotlin/com/app/pustakam/feature/notes/data/repositoryImpl/SyncRepository.kt`
**Interface:** `feature/notes/.../domain/repository/ISyncRepository.kt`
**Tuning:** `core/model/.../models/sync/SyncRunState.kt` → `object SyncConfig`
**Use cases:** `feature/notes/.../domain/usecase/SyncUseCase.kt`

### What starts a cycle

| Trigger | Method | Where it comes from |
|---|---|---|
| App start | `start()` | `StartSyncUseCase` |
| Sign-in | inside `start()` | collects `session.state`, `distinctUntilChanged` |
| Offline mode turned **off** | inside `start()` | collects `userPrefs.offlineModeFlow` |
| A save | `requestSyncSoon()` | `NoteRepository.insertOrUpdateNote` / `deleteNote` |
| Timer | `restartTimer()` → `nextDelayMillis()` | 20 s on screen, 15 min in the background |
| Network came back | `onConnectivityChanged(true)` | `NotifyConnectivityUseCase` |
| App to foreground | `setForeground(true)` | `SetSyncForegroundUseCase` |
| Pull-to-refresh | `syncNow()` | `SyncNowUseCase` |
| Android background | — | `androidApp/.../sync/SyncWorker.kt` (`NetworkType.CONNECTED` constraint) |

`requestSyncSoon()` debounces by `SyncConfig.SAVE_DEBOUNCE_MILLIS` (1200 ms), so a burst of typing
is one push. The nudge owns only the *wait* — it calls `requestSync()` and never runs the cycle
itself, or the next keystroke would cancel the push it just started.

### Single-flight — `runGuarded(waitForRunningCycle)`

A `Mutex` called `runLock`. A fire-and-forget trigger that finds it taken sets `rerunRequested` and
returns; an awaited one (`syncNow()`) queues. When the lock is released and `rerunRequested` is set,
one more cycle runs — a save that landed mid-cycle was not in that cycle's payload.

### The cycle — `runCycle(userId)`

Order is deliberate: **media up → push → pull → media down.**

1. `healWatermarkIfStale(userId)` — if `userPrefs.getSyncGeneration() < SyncConfig.RESYNC_GENERATION`,
   reset the watermark and re-pull everything once. Bump `RESYNC_GENERATION` whenever a fix changes
   what a stored watermark *means*.
2. Read `publishing = !userPrefs.offlineModeFlow.first()` — **fresh every cycle**, so a cycle already
   in flight cannot push after the switch was flipped.
3. `pushDirtyNotes(userId)` — skipped entirely when `publishing` is false.
4. `pullChanges(userId)` — **runs regardless of offline mode.**
5. `backfillMedia()` — only if the pull succeeded.
6. `startMediaUploads()` — launched in the background, outside the lock.
7. `localNotes.refreshFromDb()` if anything changed.

Push failure and pull failure are held in **separate variables**. They are independent halves: before
28-Aug-2026 a push error returned early, so one note the server would not accept stopped every
incoming change from ever arriving — sync looked dead while the network was fine.

### Push — `pushDirtyNotes(userId)`

- `notesDao.selectDirtyNotes(SyncConfig.PUSH_BATCH + attempted.size)`, minus an `attempted` set, so
  **every note gets exactly one attempt per cycle**. Without that, a batch the server keeps
  conflicting on made the same rows come back forever and the loop hammered the rate limiter.
- `NoteWireMapper.toWire(note)` for each.
- `SyncPushRequest(notes, lastPulledAt)` → `apiClient.syncPush(userId, request)`.
- The response has three lists, handled separately:
  - **`accepted`** → `notesDao.markNoteSynced(...)` with the version that actually went up.
  - **`conflicts`** → `applyServerWins(conflict.server)`. A **forced** write: a conflicted note is
    dirty by definition, so the normal `canApplyOver` guard would refuse it and the note would be
    re-pushed and re-conflicted forever. The server already archived our losing edit.
  - **`rejected`** → counted and logged with `rejected.fields` (e.g. `contents.0._id`). The note
    **stays dirty on purpose** — marking it clean would lose the edit silently.

### Pull — `pullChanges(userId)`

- Reads `(since, sinceId)` from `notesDao.readSyncWatermark(userId)`.
- Loops up to `SyncConfig.MAX_PULL_PAGES`, calling
  `apiClient.syncPull(userId, since, sinceId, SyncConfig.PULL_LIMIT)`.
- A `NetworkError.BAD_REQUEST` with `since > 0` means the watermark predates the server's tombstone
  window → `resetSyncWatermark` and restart from zero.
- **`takeWhile { canApplyOver(it) }`** — the page stops at the first note this device may not
  overwrite, and the watermark stops with it. This is the data-loss fix of 28-Aug-2026:
  `applyIncoming` always refused to overwrite an unpushed local edit, but the watermark used to
  advance past it anyway, and the server never offers the same note twice.
- `notesDao.commitPulledPage(...)` writes the page and the watermark together.
- If it stalled, `noteStalledOn(id)`; after `SyncConfig.MAX_STALLED_CYCLES` (3) `canApplyOver`
  gives up and takes the server copy, so one unsendable note cannot hold the whole inbound stream.
- Finally `notesDao.purgeAckedTombstones(since)`.

### The two small deciders

| Method | Question it answers |
|---|---|
| `canApplyOver(incoming)` | May the server's copy replace ours yet? Delegates to `NoteWireMapper.canApplyOverLocal(local, SYNC_STATUS_SYNCED)`, with the stalled-note escape hatch. |
| `applyServerWins(incoming)` | Merge our file paths in via `NoteWireMapper.mergeLocalMedia`, then `notesDao.applyServerNote(merged)` and `publishToOpenEditor(merged)`. |

### Telling an open screen

`publishToOpenEditor(note)` → `noteBus.publishContents(note.id, note.contents)`.

**File:** `feature/notes/.../repositoryImpl/NoteSyncRepository.kt` — a `MutableSharedFlow` bus with
`publishContents()` / `observeContents(noteId)`. Writing the row is not enough: an editor already on
screen reads its own state, not the database. This is how a note open on both devices updates live.

---

## 6. What actually goes on the wire

**File:** `feature/notes/src/commonMain/kotlin/com/app/pustakam/feature/notes/data/sync/NoteWireMapper.kt`

Three methods, and that is the whole file:

| Method | Job |
|---|---|
| `toWire(note)` | Strips `deletedAt`, `serverUpdatedAt`, and every block's `localPath` / `thumbnailPath`. **Device paths never leave the device.** The server strips them anyway. |
| `mergeLocalMedia(incoming, local)` | A pulled note carries no local paths. Without this merge, pulling a note this device already has would orphan every file sitting on its disk. |
| `canApplyOverLocal(local, syncedMarker)` | `true` when there is no local row, or the local row is `SYNCED`. |

All three use `copy()` rather than the `withX()` helpers on purpose — `withX()` stamps `updatedAt`,
and neither preparing a note for the wire nor merging a pulled one is a user edit.

---

## 7. Media going up

**File:** `feature/notes/src/commonMain/kotlin/com/app/pustakam/feature/notes/data/sync/MediaSyncer.kt`

### Two lanes — `startMediaUploads()` in `SyncRepository`

`SyncRepository.LARGE_UPLOAD_BYTES` = 10 MB. Two jobs, `smallUploadJob` and `largeUploadJob`, each
running `flushMediaUploads(large)`. **A voice note never waits behind a video.** Which lane a note
takes is decided by `mediaSyncer.pendingBytes(note)`, which reads the real size off disk because
recordings are often saved with `sizeBytes = 0`.

Uploads run **outside** the run lock, so `startMediaUploads()` checks the cached `offlineMode` field
itself (it is not a suspend function), and `flushMediaUploads` re-reads `userPrefs.offlineModeFlow`
**per note** and stops mid-queue if the switch is flipped.

### `uploadPending(note, budget): Outcome`

For each `MediaContent` where `needsUpload()` is true (`assetId` blank **and** `localPath` set):

1. `resolveLocalFilePath(content.localPath)` — on iOS the app container UUID changes on every
   update, so stored absolute paths are re-anchored onto the current container at read time.
2. `directories.exists(relative)` — a deleted file is reported as missing, not as unreadable.
3. `fileReader.read(relative)` / `readAbsolute(absolute)`.
4. `MediaFileNaming.declaredMimeFor(content.type, content.mimeType, fileName)` — see §9.
5. `apiClient.uploadMedia(upload) { sent, total -> uploads.progress(...) }`.
6. On success, `content.withAsset(assetId, url, checksum)` plus the server's `mimeType` / `sizeBytes`
   when the local ones were blank or zero.

`withAsset()` does **not** touch `updatedAt` — gaining a server identity is not a user edit and must
not re-dirty the note.

Nothing in this file throws. A file that cannot move is **skipped and counted**, with the reason in
`Outcome.reasons`, because one oversized video must not stop a hundred notes from syncing. The
caller logs those reasons under the `SyncRepository` tag so one log filter shows both.

### After a successful upload, back in `SyncRepository.flushMediaUploads`

1. `stampUploadedRows(before, after)` → `notesDao.stampMediaAsset(...)` for **only** the blocks that
   gained an `assetId` (writing the pre-upload snapshot back would lose edits made meanwhile).
2. `notesDao.markNoteDirtyForMedia(noteId)` — the note has to travel again so the id reaches the
   other device.
3. `publishToOpenEditor(note)`.
4. `requestSync()` — push **this** note now, not after the whole queue.

Progress is reported to the card through `MediaUploadTracker`
(`core/media/.../upload/MediaUploadTracker.kt`): `started` / `progress` / `finished` / `failed`.

---

## 8. Media coming down

**Queued by:** `SyncRepository.backfillMedia()` — walks `notesDao.selectNoteIdsNeedingMedia(...)`
and calls `mediaDownloads.startAutomatically(content)` for every block where `needsDownload()` is
true (`assetId` set, `localPath` empty).

**Files:**

| File | Role |
|---|---|
| `core/media/.../download/MediaDownloadCoordinator.kt` | `startAutomatically()`, `transferUi()`, `currentTransferUi()`, `isLocalFileMissing()`, `cancel()` — the one thing a media card talks to |
| `core/media/.../download/MediaDownloadManager.kt` | `enqueue()`, `pause()`, `resume()`, `cancel()`, and privately `admit()` / `transfer()` / `finish()` / `land()` — streamed, resumable, 2 in parallel |
| `core/media/.../download/ApiMediaByteSource.kt` | The bytes, via ranged GET |
| `core/media/.../download/FileMediaPartStore.kt` | Part files, so a paused download resumes |
| `core/media/.../download/MediaLandingHandler.kt` | The one-method interface `onLanded(assetId, holderId, absolutePath)` |

`SyncRepository` **implements `MediaLandingHandler`**. When bytes land, `onLanded(...)` calls
`notesDao.stampMediaLocalPath(contentId, absolutePath)` for the matching rows, republishes to the
open editor and calls `localNotes.refreshFromDb()`. It deliberately does not take the run lock — a
cycle can hold it for minutes, and a lost path simply re-lands next cycle.

A card that is on screen and has never been downloaded starts its own download from
`transferUi()`, so nothing needs to be tapped. A card whose block has **no** `assetId` yet polls
every `WAITING_CHECK_MILLIS` (10 s) via `MediaUploadRetry` — it is waiting for the *other* device's
upload to finish.

---

## 9. Audio, and the `.mp4` trap

This is worth its own section because it broke audio sync twice.

**MP4 is a container, not a format.** Android's `MediaRecorder` (`OUTPUT_FORMAT_MPEG_4`) and iOS's
`AVAudioRecorder` both write **AAC inside an MP4 box**. Magic-byte sniffing can only see the box.

| Where | File | Method | What it does |
|---|---|---|---|
| App, going up | `core/media/.../naming/MediaFileNaming.kt` | `declaredMimeFor(type, mimeType, fileName)` | For `ContentType.AUDIO`, derives the MIME from the **file extension** (`.m4a`/`.mp4` → `audio/mp4`). `MimeCatalog.mimeFor(AUDIO)` says `audio/mpeg` even for an m4a. |
| Server, receiving | `PustakmServer/lib/fileType.js` | `detectType(buffer, declaredMime)` | `MP4_CONTAINER_MIMES = {video/mp4, application/mp4}`. If the sniff says MP4 container **and** the client declared `audio/*`, the file is stored as `MimeType.M4A`. This is the *one* case where the client is the better authority, and only ever downwards. |
| App, coming down | `core/media/.../naming/MediaFileNaming.kt` | `fileNameFor(assetId, mimeType, type)` → `audioExtensionFor(mimeType)` | Keeps the real extension. `MimeCatalog.extensionFor(AUDIO)` returns `.mp3`, and AAC named `.mp3` will not open. |

Landing path: `MediaFileNaming.destinationFor(noteId, assetId, mimeType, type)` →
`imported/<noteId>/<assetId><ext>`.

---

## 10. The network layer

**File:** `core/network/src/commonMain/kotlin/com/app/pustakam/core/network/ApiCallClient.kt`
**Routes:** `core/network/.../ApiRoute.kt`

| Method | HTTP |
|---|---|
| `syncPush(userId, request)` | `POST {base}/sync/{userId}/push` |
| `syncPull(userId, since, sinceId, limit)` | `GET {base}/sync/{userId}/pull?since=…&limit=…&sinceId=…` |
| `uploadMedia(file, onProgress)` | `POST {base}/images`, multipart, field name **`files`**, one file per request |
| `downloadMedia(userId, assetId)` | `GET {base}/media/{userId}/{assetId}` — raw bytes, **not** wrapped in `BaseResponse` |
| `addNewNote` / `updateNote` / `deleteNote` / `getNote` / `getNotes` | `{base}/notes/{userId}[/{noteId}]` — the older per-note routes, unused by the engine |

`uploadMedia` sends **one file per request on purpose**: the server persists a batch in a loop and
throws on the first bad file, so a batch would let one unsupported attachment fail all the others.
It also calls `withoutRequestTimeout()` — a recorded video takes longer than 30 s.

**Wire shapes:** `core/model/.../models/sync/SyncModels.kt` —
`SyncPushRequest`, `SyncPushResponse`, `SyncAccepted`, `SyncConflict`, `SyncRejected`,
`SyncPullResponse`, `MediaUploadResponse`, `MediaAsset`, and the constant `PULL_WATERMARK_TOO_OLD`.

Token refresh lives in `core/network/BaseClient.kt` against `ApiRoute.AUTH_REFRESH`
(`POST /auth/refresh`) — access tokens live 15 minutes and background sync outlives that.

---

## 11. Server — the routes

**File:** `PustakmServer/routes/routes.js`

```
POST   /sync/:userId/push      authGuard → authorizeOwner → syncLimiter → validate(syncPushSchema) → syncPush
GET    /sync/:userId/pull      authGuard → authorizeOwner → syncLimiter → validate(syncPullSchema) → syncPull
POST   /images                 authGuard → uploadLimiter → uploadMultiple → validate(uploadFilesSchema) → uploadImages
GET    /media/:userId/:assetId authGuard → authorizeOwner → serveMedia
DELETE /media/:userId/:assetId authGuard → authorizeOwner → uploadLimiter → deleteMediaApi
```

Thin handlers in `PustakmServer/middleware/syncHandler.js` — `syncPush` and `syncPull` — just unwrap
the request and call the service.

---

## 12. Server — push

**File:** `PustakmServer/services/syncService.js`, function **`push(userId, { notes })`**

| Step | Helper |
|---|---|
| Same note twice in one batch → last wins | `dedupe(notes)` |
| Validate **each note on its own** | `noteBodySchema.safeParse(raw)` |
| Turn Zod issues into `{ 'contents.0._id': ['…'] }` | `issuesByField(error)` |
| Look up the existing note | `getNoteFromDb(id, userId, { includeDeleted: true })` |
| New note | `toStoredNote(incoming, { userId })` → `insertNoteOnDb(doc)` → **accepted** |
| Replay (same `version` **and** same `updatedAt`) | → **accepted**, nothing written |
| Stale (`incoming.updatedAt < existing.updatedAt`) | `archiveNoteVersion(…, CONFLICT_LOSER)` → **conflict**, carrying `toWireNote(existing)` |
| Otherwise | `archiveNoteVersion(existing, SYNC_PUSH)` → `updateNoteOnDb(...)` → **accepted** |
| Anything thrown | → **rejected**, with `code` and `fields` |

**Per-note results, never all-or-nothing.** One bad note must not take the batch with it — that is
the whole point of answering per note.

The replay check matches on **version *and* timestamp**. Version strings are derived from the
`userId`, so two devices on one account mint identical ones; matching on version alone read the
second device's genuine edit as a replay and dropped it.

A rejection also logs the **shape** of what arrived — field names and types only, never the user's
note text, file paths or coordinates.

---

## 13. Server — pull

**File:** `PustakmServer/services/syncService.js`, function **`pull(userId, { since, sinceId, limit })`**

1. Clamp: `since > serverTime` becomes `serverTime` — a device clock ahead of the server cannot skip its own inbox.
2. If `since` is older than `tombstoneHorizon()` (`db/notesdb.js`, `now() - TOMBSTONE_RETENTION_DAYS`),
   throw `BadRequestError(..., ErrorCode.PULL_WATERMARK_TOO_OLD)` — the app turns that 400 into a full resync.
3. `getNotesChangedSince(userId, { since, sinceId, limit: limit + 1 })`.
4. `limit + 1` is how `hasMore` is computed without a second query.
5. Answer `{ serverTime, notes: page.map(toWireNote), hasMore, nextSince, nextSinceId }`.

The cursor is a **tuple**, `(serverUpdatedAt, _id)`:

```js
{ userId, $or: [ { serverUpdatedAt: { $gt: since } },
                 { serverUpdatedAt: since, _id: { $gt: sinceId } } ] }
sort({ serverUpdatedAt: 1, _id: 1 })
```

A plain `> since` would skip notes sharing a millisecond; the `_id` tiebreak is what makes paging
exact.

---

## 14. Server — the wire contract

**File:** `PustakmServer/domain/noteMapper.js` — *the single place that knows the wire contract.*

| Function | Job |
|---|---|
| `toStoredNote(body, { userId, existing })` | Builds the document. Keeps `existing.createdAt`, stamps `serverUpdatedAt = now()`, sets `deletedAt` when `deleted === true`, drops `CLIENT_ONLY_NOTE_FIELDS = ['isSynced', 'syncStatus']`. |
| `toStoredContent(content, noteId, timestamp)` | Per block: mints `_id` if absent, forces `noteId`, defaults `position`, and drops `CLIENT_ONLY_CONTENT_FIELDS = ['thumbnailPath', 'id']`. |
| `keepKnownAsset(stored, existingById)` | **A block never loses its uploaded file.** If the incoming block has no `assetId` but the stored one does, copy `assetId`, `url`, `checksum`, `mimeType`, `sizeBytes` back. This covers a phone that saved before its own upload finished. |
| `toWireContent(content)` | Emits the content id as **both `_id` and `id`** — the compat shim for builds that predate the `@SerialName("_id")` fix. Delete once every install is past that build. |
| `toWireNote(note)` | The pull shape. Always sets `syncStatus: SYNCED` and `isSynced: true`. |
| `toWireSummary(note)` | Mirrors `Notes.kt toSummary()` so both platforms render the list identically. |

`clampClientTimestamp` (`lib/time.js`) is what stops a device with a wrong clock stamping a note in
2049 and winning every conflict forever.

---

## 15. Server — validation

**File:** `PustakmServer/validation/noteSchemas.js`

- `noteContentSchema` is a **`z.preprocess` + discriminator dispatch**, not a `z.union`.
  - The preprocess step copies `value.id` into `value._id` when `_id` is missing — an un-rebuilt
    client still parses.
  - Then `CONTENT_BY_KIND[value.type]` picks exactly one of `textContent`, `mediaContent`,
    `linkContent`, `locationContent`, falling back to `unknownContent` (a `.passthrough()` schema, so
    a *newer* client's block is stored verbatim rather than dropped).
  - It was a `z.union` until 20-Sep-2026. A union that fails every branch reports one issue —
    `contents.0: Invalid input` — with no field and no reason, which is exactly the log a device sat
    producing while it rejected every note it owned.
- `baseContent` requires `_id`, `noteId`, `position` (finite number), `createdAt`, `updatedAt`.
- `noteBodySchema` additionally `.refine()`s that content `_id`s are **unique within one note**.

`PustakmServer/validation/syncSchemas.js` holds `syncPushSchema` and `syncPullSchema`.

---

## 16. Server — the bytes

**File:** `PustakmServer/fileupload/upload.js`

| Function | Job |
|---|---|
| `uploadMultiple` | `multer.memoryStorage()` on field `files` — bytes are checked before anything touches disk |
| `persist(file, userId, { noteId, keyFor })` | `detectType()` → sha-256 checksum → **`findReadyAssetByChecksum` dedupe** → `storage.put(key, …)` → `insertAsset(asset)` |
| `uploadImages` | Calls `persist` per file, answers `{ files: [toWireAsset(...)], count }` |
| `serveMedia` | Ranged, `private, max-age=3600`, `X-Content-Type-Options: nosniff`. Not `express.static` — a stored file served inline from the API origin is stored XSS. |

The checksum dedupe means re-uploading the same file returns the existing asset instead of storing a
second copy.

`PustakmServer/domain/mediaMapper.js` → `toWireAsset(asset)` produces exactly what
`MediaSyncer` expects: `{ assetId, url, mimeType, sizeBytes, checksum }`, with
`url = /media/{userId}/{assetId}`.

Deletion is a tombstone here too — `softDeleteNoteFromDb` (`db/notesdb.js`) sets
`deleted: true, contents: []` and stamps `serverUpdatedAt`; `reapTombstones()` removes rows older
than the horizon, and `findNotesReferencingAssets` / `isAssetReferencedByLiveNote` guard the media
reaper so a file still referenced by a live note is never collected.

---

## 17. The whole trip, end to end

Phone A creates note `n_ABC` with all seven blocks. Phone B is signed in to the same account.

| # | Where | File → method |
|---|---|---|
| 1 | A | `NoteContentObjectHelper.createText/createMedia/createHyperLink/createLocation` — ids minted |
| 2 | A | `NoteEditorViewModel` collects ids into `dirtyContentIds` |
| 3 | A | `CreateORUpdateNoteUseCase` → `NoteRepository.insertOrUpdateNote(note, dirtyContentIds)` |
| 4 | A | `NotesDao.insertOrUpdateNoteFromDb` — note + contents, one transaction, `syncStatus = PENDING` |
| 5 | A | `_notes` / `_noteSummaries` update → the list screen already shows the note |
| 6 | A | `remoteSync.requestSyncSoon()` — 1.2 s debounce |
| 7 | A | `SyncRepository.runCycle` → `startMediaUploads()` — audio/photo/pdf on the small lane, video on the large |
| 8 | A | `MediaSyncer.uploadPending` → `ApiCallClient.uploadMedia` → `POST /images` |
| 9 | Server | `upload.js persist()` → `lib/fileType.js detectType()` keeps the recording as **m4a** → `insertAsset` |
| 10 | A | `stampUploadedRows` → `NotesDao.stampMediaAsset`; `markNoteDirtyForMedia`; `requestSync()` |
| 11 | A | `pushDirtyNotes` → `NoteWireMapper.toWire` (local paths stripped) → `POST /sync/{userId}/push` |
| 12 | Server | `syncService.push` → `noteBodySchema` per note → `toStoredNote` → `insertNoteOnDb` → `accepted` |
| 13 | A | `NotesDao.markNoteSynced` — version guarded; the note is clean |
| 14 | B | timer or foreground → `pullChanges` → `GET /sync/{userId}/pull?since=…` |
| 15 | Server | `syncService.pull` → `getNotesChangedSince` on `(serverUpdatedAt, _id)` → `toWireNote` (ids as both `_id` and `id`) |
| 16 | B | `canApplyOver` per note → `applyServerWins` → `NoteWireMapper.mergeLocalMedia` → `NotesDao.applyServerNote` |
| 17 | B | `NotesDao.commitPulledPage` — page **and** watermark in one transaction |
| 18 | B | `publishToOpenEditor` → `NoteSyncRepository.publishContents` — an open editor updates live |
| 19 | B | `backfillMedia` → `MediaDownloadCoordinator.startAutomatically` per block |
| 20 | B | `MediaDownloadManager` streams → `SyncRepository.onLanded` → `NotesDao.stampMediaLocalPath` |
| 21 | B | `localNotes.refreshFromDb()` — text, link and location were already visible at step 17; the files fill in as they land |

Text, link and location arrive **whole** at step 17 — they are small and travel inside the note.
Media arrives in **two parts**: the block (with its `assetId`) at step 17, the bytes at step 20. That
is why a photo shows a placeholder with a progress bar for a moment.

---

## 18. Offline mode, precisely

Preference: `userPrefs.offlineModeFlow`.

| Behaviour | Where |
|---|---|
| Local saves, edits, deletes | **Unchanged** — they write to SQLite and stay dirty |
| Push | **Held.** `runCycle` reads `publishing` fresh each cycle and skips `pushDirtyNotes` |
| Media upload | **Held.** `startMediaUploads()` checks the cached flag; `flushMediaUploads` re-reads the flow per note and stops mid-queue |
| Pull | **Runs.** No gate anywhere on the pull path |
| Media download | **Runs.** Inbound |
| `getNotesForUserApi` / `getNoteApi` | **Run.** Reading is never blocked |
| `upsertNewNoteApi` / `updateNoteApi` / `deleteNoteApi` | Held by `offlineModeOn()`, fall back to the local write |
| Turning it **off** | The collector in `SyncRepository.start()` fires `requestSync()` and the whole backlog flushes |

**Offline mode is one-way: this device stops publishing, it does not stop listening.**

---

## 19. Rules the code will not bend on

1. **Push before pull.** A local edit must reach the server before the server's copy can overwrite it.
2. **Media bytes before their note.** A note must never reference an asset the server does not have.
3. **Push and pull are independent halves.** One failing must not silence the other.
4. **A pull stops at the first note it may not overwrite** — and the watermark stops with it.
5. **Only the server marks a note clean,** and only with the version it accepted.
6. **A rejected note stays dirty.** Cleaning it would lose the edit silently.
7. **Deletes are tombstones** on both sides. A hard delete cannot reach another device.
8. **`localPath` never leaves the device.** The app strips it; the server strips it again.
9. **Gaining an `assetId` is not a user edit** — `withAsset()` never touches `updatedAt`.
10. **One upload attempt per file per cycle, one push attempt per note per cycle.** Otherwise the
    rate limiter cuts the device off.
11. **Nothing in the media path throws.** A file that cannot move is skipped, counted, and its reason
    logged under the `SyncRepository` tag.

---

## 20. File index

### App — `Pustakm/`

| File | Contains |
|---|---|
| `core/model/.../response/notes/Note.kt` | `Note`, `NoteContentModel` + its 4 subclasses, `SyncStatus` |
| `core/model/.../response/notes/NoteContentObjectHelper.kt` | `createText`, `createMedia`, `createHyperLink`, `createLocation` |
| `core/model/.../models/sync/SyncModels.kt` | Push/pull request + response shapes, `MediaAsset` |
| `core/model/.../models/sync/SyncRunState.kt` | `SyncRunState`, `SyncSummary`, `object SyncConfig` |
| `core/database/.../database/NotesDao.kt` | Every local read/write named in §4 |
| `core/database/src/commonMain/sqldelight/.../NotesDatabase.sq` | The SQL behind them |
| `core/network/.../ApiCallClient.kt` | `syncPush`, `syncPull`, `uploadMedia`, `downloadMedia` |
| `core/network/.../ApiRoute.kt` | `SYNC`, `IMAGES`, `MEDIA`, `AUTH_REFRESH` |
| `core/network/BaseClient.kt` | Auth header, 401 → `/auth/refresh` → single replay |
| `core/media/.../download/MediaDownloadCoordinator.kt` | `startAutomatically`, `transferUi` |
| `core/media/.../download/MediaDownloadManager.kt` | `enqueue`, `pause`, `resume`, `cancel` |
| `core/media/.../download/MediaLandingHandler.kt` | `onLanded` |
| `core/media/.../naming/MediaFileNaming.kt` | `destinationFor`, `fileNameFor`, `declaredMimeFor` |
| `core/media/.../upload/MediaUploadTracker.kt` | `started`, `progress`, `finished`, `failed` |
| `feature/notes/.../repositoryImpl/NoteRepository.kt` | `insertOrUpdateNote`, `deleteNote`, `refreshFromDb`, offline wrappers |
| `feature/notes/.../repositoryImpl/SyncRepository.kt` | The engine |
| `feature/notes/.../repositoryImpl/NoteSyncRepository.kt` | In-app content bus |
| `feature/notes/.../data/sync/NoteWireMapper.kt` | `toWire`, `mergeLocalMedia`, `canApplyOverLocal` |
| `feature/notes/.../data/sync/MediaSyncer.kt` | `uploadPending`, `pendingBytes`, `Outcome` |
| `feature/notes/.../domain/usecase/SyncUseCase.kt` | The six sync use cases |
| `feature/notes/.../domain/repository/ISyncRepository.kt` | The engine's contract |
| `androidApp/.../sync/SyncWorker.kt` | WorkManager, `NetworkType.CONNECTED` |
| `androidApp/.../screen/noteEditor/NoteEditorViewModel.kt` | `dirtyContentIds`, save and delete |
| `feature/notes/src/iosMain/.../bridge/SyncBridge.kt` | The iOS seam |
| `feature/notes/src/commonTest/.../NoteWireMapperTest.kt` | Wire-mapper tests |

### Server — `PustakmServer/`

| File | Contains |
|---|---|
| `routes/routes.js` | The route table |
| `middleware/syncHandler.js` | `syncPush`, `syncPull` |
| `services/syncService.js` | `push`, `pull`, `dedupe`, `issuesByField` |
| `domain/noteMapper.js` | `toStoredNote`, `toWireNote`, `toWireContent`, `keepKnownAsset`, `toWireSummary` |
| `domain/mediaMapper.js` | `toWireAsset`, `mediaUrlFor` |
| `validation/noteSchemas.js` | `noteContentSchema`, `noteBodySchema` |
| `validation/syncSchemas.js` | `syncPushSchema`, `syncPullSchema` |
| `db/notesdb.js` | `getNotesChangedSince`, `archiveNoteVersion`, `softDeleteNoteFromDb`, `tombstoneHorizon`, `reapTombstones` |
| `db/mediadb.js` | `findReadyAssetByChecksum`, `insertAsset` |
| `fileupload/upload.js` | `persist`, `uploadImages`, `serveMedia` |
| `lib/fileType.js` | `detectType` and the MP4-container guard |
| `lib/time.js` | `now`, `toEpochMillis`, `toWire`, `clampClientTimestamp` |
| `constants/errorCodes.js` | `PULL_WATERMARK_TOO_OLD`, `VALIDATION_FAILED`, `STALE_VERSION`, … |
