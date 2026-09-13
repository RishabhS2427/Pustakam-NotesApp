package com.app.pustakam.core.network

import com.app.pustakam.core.model.models.BaseResponse
import com.app.pustakam.core.model.models.request.Login

import com.app.pustakam.core.model.models.request.RegisterReq
import com.app.pustakam.core.model.models.response.DeleteDataModel
import com.app.pustakam.core.model.models.response.User
import com.app.pustakam.core.model.models.response.notes.Note
import com.app.pustakam.core.model.models.response.notes.Notes
import com.app.pustakam.core.model.models.sync.SyncPullResponse
import com.app.pustakam.core.model.models.sync.SyncPushRequest
import com.app.pustakam.core.model.models.sync.SyncPushResponse
import com.app.pustakam.core.model.models.sync.MediaUploadResponse
import com.app.pustakam.core.model.models.chat.ChatConversationWire
import com.app.pustakam.core.model.models.chat.ChatMessageWire
import com.app.pustakam.core.model.models.chat.ChatParticipantWire
import com.app.pustakam.core.model.models.chat.DeleteConversationResponse
import com.app.pustakam.core.model.models.chat.MarkReadRequest
import com.app.pustakam.core.model.models.chat.OpenConversationRequest
import com.app.pustakam.core.model.models.chat.ReadReceiptWire
import com.app.pustakam.core.model.models.chat.SendMessageRequest
import com.app.pustakam.core.model.models.profile.PublicUser
import com.app.pustakam.core.model.models.profile.SetUsernameReq
import com.app.pustakam.core.model.models.profile.UpdateProfileReq
import com.app.pustakam.core.model.models.profile.UsernameAvailability
import com.app.pustakam.core.common.util.NetworkError
import io.ktor.client.request.delete
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import com.app.pustakam.core.common.util.Error
import com.app.pustakam.core.common.util.Result
import io.ktor.client.request.get

class ApiCallClient : BaseClient() {

    suspend fun login(login: Login): Result<BaseResponse<User>, Error> =
     post(ApiRoute.LOGIN.getName(), requestData = login)

    suspend fun register(user: RegisterReq): Result<BaseResponse<User>, Error> =
    post(url = ApiRoute.REGISTER.getName(), requestData = user)

    suspend fun getNotes(userId: String): Result<BaseResponse<Notes>, Error> =
        get(url = "${ApiRoute.NOTES.getName()}/$userId")

    suspend fun getNote(userId: String, noteId: String): Result<BaseResponse<Note>, Error> =
        get(url = "${ApiRoute.NOTES.getName()}/$userId/$noteId")


    suspend fun updateNote(userId: String, note: Note): Result<BaseResponse<Note>, Error> =
        post(url = "${ApiRoute.NOTES.getName()}/$userId/${note.id}", requestData = note)


    suspend fun deleteNote(
        userId: String, noteId: String
    ): Result<BaseResponse<DeleteDataModel>, Error> =

            delete(url = "${ApiRoute.NOTES.getName()}/$userId/$noteId")

    suspend fun addNewNote(userId: String, note: Note): Result<BaseResponse<Note>, Error> =
        post(url= "${ApiRoute.NOTES.getName()}/$userId", requestData = note)


    // 🔄 20-Aug-2026 sync: per-note results, never all-or-nothing. Idempotent on (noteId, version).
    suspend fun syncPush(userId: String, request: SyncPushRequest): Result<BaseResponse<SyncPushResponse>, Error> =
        post(url = "${ApiRoute.SYNC.getName()}/$userId/push", requestData = request)

    // 🔄 `since` is ALWAYS a serverUpdatedAt the server handed us — never a device clock reading
    suspend fun syncPull(
        userId: String, since: Long, sinceId: String?, limit: Int
    ): Result<BaseResponse<SyncPullResponse>, Error> {
        val cursor = sinceId?.takeIf { it.isNotBlank() }?.let { "&sinceId=$it" } ?: ""
        return get(url = "${ApiRoute.SYNC.getName()}/$userId/pull?since=$since&limit=$limit$cursor")
    }

    /** 🖼️ 20-Aug-2026 sync: ONE file per request on purpose. The server persists a batch in a loop
     *  and throws on the first bad file, so a batch would let one unsupported attachment fail all
     *  the others. Per-file requests give per-file failure. */
    suspend fun uploadMedia(file: MediaUpload): Result<BaseResponse<MediaUploadResponse>, Error> =
        baseApiCall<BaseResponse<MediaUploadResponse>, NetworkError> {
            httpClient.submitFormWithBinaryData(
                url = ApiRoute.IMAGES.getName(),
                formData = formData {
                    append(
                        key = UPLOAD_FIELD_FILES,
                        value = file.bytes,
                        headers = Headers.build {
                            append(HttpHeaders.ContentType, file.mimeType)
                            append(HttpHeaders.ContentDisposition, "filename=\"${file.fileName}\"")
                        }
                    )
                }
            )
        }

    /** 🖼️ raw bytes — this route streams the file itself and does NOT wrap it in BaseResponse. */
    suspend fun downloadMedia(userId: String, assetId: String): Result<ByteArray, Error> =
        baseApiCall<ByteArray, NetworkError> {
            httpClient.get(urlString = "${ApiRoute.MEDIA.getName()}/$userId/$assetId")
        }

    suspend fun getUser(userId: String): Result<BaseResponse<User>, Error> =
        get(url = "${ApiRoute.USERS.getName()}/$userId")



    suspend fun updateUser(user: User): Result<BaseResponse<User>, Error> =
         post(url = "${ApiRoute.USERS.getName()}/${user._id}", requestData = user)


    suspend fun deleteUser(userId: String): Result<BaseResponse<User>, Error> =
        delete(url = "${ApiRoute.USERS.getName()}/$userId")




    // ── profile ───────────────────────────────────────────────────────────
    // 👤 31-Aug-2026. /users/{id} is the SELF view (owner-guarded); /u/* is what anyone may see.

    suspend fun checkUsername(username: String): Result<BaseResponse<UsernameAvailability>, Error> =
        get(url = "${ApiRoute.PROFILE_PUBLIC.getName()}/check?username=$username")

    suspend fun setUsername(userId: String, request: SetUsernameReq): Result<BaseResponse<User>, Error> =
        post(url = "${ApiRoute.USERS.getName()}/$userId/username", requestData = request)

    /** A partial patch — null fields are omitted by the Json config, never sent as null. */
    suspend fun updateProfile(userId: String, request: UpdateProfileReq): Result<BaseResponse<User>, Error> =
        post(url = "${ApiRoute.USERS.getName()}/$userId", requestData = request)

    suspend fun getPublicProfile(username: String): Result<BaseResponse<PublicUser>, Error> =
        get(url = "${ApiRoute.PROFILE_PUBLIC.getName()}/$username")

    suspend fun searchPeople(query: String, limit: Int): Result<BaseResponse<List<PublicUser>>, Error> =
        get(url = "${ApiRoute.PROFILE_PUBLIC.getName()}/search?q=$query&limit=$limit")

    /**
     * 🖼️ Replaces the old profileImage() stub, which posted an empty form-urlencoded body and could
     * never work. One round trip: uploads, sets avatarAssetId server-side, and answers with the
     * updated user — the same User shape login returns.
     */
    suspend fun uploadAvatar(file: MediaUpload): Result<BaseResponse<User>, Error> =
        baseApiCall<BaseResponse<User>, NetworkError> {
            httpClient.submitFormWithBinaryData(
                url = ApiRoute.PROFILE.getName(),
                formData = formData {
                    append(
                        key = UPLOAD_FIELD_AVATAR,
                        value = file.bytes,
                        headers = Headers.build {
                            append(HttpHeaders.ContentType, file.mimeType)
                            append(HttpHeaders.ContentDisposition, "filename=\"${file.fileName}\"")
                        }
                    )
                }
            )
        }

    // ── chat ──────────────────────────────────────────────────────────────
    // 💬 31-Aug-2026: REST is the FALLBACK for everything the socket does. A device behind a proxy
    //   that blocks websockets still sends and reads history — it just is not pushed to.

    suspend fun listConversations(limit: Int, before: Long? = null): Result<BaseResponse<List<ChatConversationWire>>, Error> {
        val cursor = before?.takeIf { it > 0 }?.let { "&before=$it" } ?: ""
        return get(url = "${ApiRoute.CHAT.getName()}/conversations?limit=$limit$cursor")
    }

    suspend fun openConversation(request: OpenConversationRequest): Result<BaseResponse<ChatConversationWire>, Error> =
        post(url = "${ApiRoute.CHAT.getName()}/conversations", requestData = request)

    suspend fun getConversation(conversationId: String): Result<BaseResponse<ChatConversationWire>, Error> =
        get(url = "${ApiRoute.CHAT.getName()}/conversations/$conversationId")

    suspend fun deleteConversation(conversationId: String): Result<BaseResponse<DeleteConversationResponse>, Error> =
        delete(url = "${ApiRoute.CHAT.getName()}/conversations/$conversationId")

    suspend fun getMessages(conversationId: String, limit: Int, before: Long? = null): Result<BaseResponse<List<ChatMessageWire>>, Error> {
        val cursor = before?.takeIf { it > 0 }?.let { "&before=$it" } ?: ""
        return get(url = "${ApiRoute.CHAT.getName()}/conversations/$conversationId/messages?limit=$limit$cursor")
    }

    suspend fun sendMessage(conversationId: String, request: SendMessageRequest): Result<BaseResponse<ChatMessageWire>, Error> =
        post(url = "${ApiRoute.CHAT.getName()}/conversations/$conversationId/messages", requestData = request)

    suspend fun deleteMessage(conversationId: String, messageId: String): Result<BaseResponse<ChatMessageWire>, Error> =
        delete(url = "${ApiRoute.CHAT.getName()}/conversations/$conversationId/messages/$messageId")

    suspend fun markConversationRead(conversationId: String, request: MarkReadRequest): Result<BaseResponse<ReadReceiptWire>, Error> =
        post(url = "${ApiRoute.CHAT.getName()}/conversations/$conversationId/read", requestData = request)

    suspend fun getChatPeers(query: String?, limit: Int): Result<BaseResponse<List<ChatParticipantWire>>, Error> {
        val search = query?.takeIf { it.isNotBlank() }?.let { "&q=$it" } ?: ""
        return get(url = "${ApiRoute.CHAT.getName()}/peers?limit=$limit$search")
    }

    // actual api calls
    private suspend inline fun <reified T> get(
        url: String, contentType: ContentType = ContentType.Application.Json
    ) = baseApiCall<T, NetworkError> {
        httpClient.get(urlString = url) {
            contentType(contentType)
        }
    }

    private suspend inline fun <reified T> post(
        url: String, contentType: ContentType = ContentType.Application.Json, requestData: Any?
    ) = baseApiCall<T, NetworkError> {
        httpClient.post (urlString = url) {
            contentType(contentType)
            setBody(requestData)
        }
    }

    private suspend inline fun <reified T> delete(url: String, contentType: ContentType = ContentType.Application.Json)
    = baseApiCall<T, NetworkError> {
        httpClient.delete(urlString = url) {
            contentType(contentType)
        }
    }
}