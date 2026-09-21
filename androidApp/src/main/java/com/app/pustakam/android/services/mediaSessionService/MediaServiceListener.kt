package com.app.pustakam.android.services.mediaSessionService

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.app.pustakam.android.hardware.audio.player.PlayerState
import com.app.pustakam.core.common.util.log_d
// 🔧 14-Jul-2026: FIX — dedicated scope for the progress ticker (was GlobalScope + a never-assigned Job)
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent

sealed interface MediaPlayingEvent {
    data class PlayOrPause(val mediaId: String) : MediaPlayingEvent
    data class Resume(val mediaId: String) : MediaPlayingEvent
    data class Restart(val mediaId: String) : MediaPlayingEvent
    data class SeekTo(val mediaId: String) : MediaPlayingEvent
    data class SeekNext(val mediaId: String) : MediaPlayingEvent
    data object Stop : MediaPlayingEvent
    data class SelectedAudioChange(val mediaId: String) : MediaPlayingEvent
    data class UpdateProgress(val newProgress: Float, val mediaId: String) : MediaPlayingEvent
    data object Backward : MediaPlayingEvent
    data object Forward : MediaPlayingEvent
    data object SeekToPrevious : MediaPlayingEvent
}
class MediaServiceListener(
    private  val exoPlayer: ExoPlayer
) : Player.Listener, KoinComponent {
    private val _audioState: MutableStateFlow<PlayerState> =
        MutableStateFlow(PlayerState.Initial)
    val audioState: StateFlow<PlayerState> = _audioState.asStateFlow()
    private val media : MutableList<MediaItem>   = emptyList<MediaItem>().toMutableList()
    // 🔧 14-Jul-2026: FIX — the old `job` was never assigned, so the while(true) progress loop could
    //   never be cancelled and ran inside the CALLER's coroutine (leaking one loop per play press).
    //   The listener is a standalone Koin single (no activity/VM owner), so it owns its own scope.
    private val listenerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var progressJob: Job? = null
    init {
        exoPlayer.addListener(this)
    }
    fun addMediaItem(media : MediaItem){
        exoPlayer.setMediaItem(media)
        exoPlayer.prepare()
    }
    fun getMedia(id: String) = media.find { id == it.mediaId }
    // 🔧 14-Jul-2026: FIX (screen-switch persistence) — every screen creates a fresh PlayMediaViewModel
    //   whose init re-emits the same media list; the old code always did setMediaItems + prepare,
    //   RESETTING live playback (lost position / white surface). Now:
    //   • identical ids  -> leave the player completely untouched (playback continues "as is")
    //   • pure append    -> addMediaItem() for the new tail only (no interruption, e.g. new recording)
    //   • anything else  -> full rebuild as before
    fun addMediaItemList(mediaList : List<MediaItem>){
        val currentIds = (0 until exoPlayer.mediaItemCount).map { exoPlayer.getMediaItemAt(it).mediaId }
        val newIds = mediaList.map { it.mediaId }
        media.clear()
        media.addAll(mediaList)
        when {
            // 📥 21-Sep-2026 — same ids, but a finished download changed a URI; ids alone kept the dead one
            newIds == currentIds -> mediaList.forEachIndexed { index, item ->
                if (exoPlayer.getMediaItemAt(index).localConfiguration?.uri != item.localConfiguration?.uri) {
                    exoPlayer.replaceMediaItem(index, item)
                }
            }
            currentIds.isNotEmpty() && newIds.take(currentIds.size) == currentIds ->
                mediaList.drop(currentIds.size).forEach { exoPlayer.addMediaItem(it) }
            else -> {
                exoPlayer.setMediaItems(mediaList)
                exoPlayer.prepare()
            }
        }
    }

    // 🔧 14-Jul-2026: NEW — resolve a track index from the player's OWN playlist by mediaId.
    //   Selection no longer depends on NoteContentRepository staying index-aligned with the playlist
    //   (that drift was the root cause of "selecting another media doesn't switch").
    private fun indexOfMediaId(id: String): Int {
        for (i in 0 until exoPlayer.mediaItemCount) {
            if (exoPlayer.getMediaItemAt(i).mediaId == id) return i
        }
        return -1
    }

    // 🔧 14-Jul-2026: NEW (nothing plays after app close/reopen) — the service teardown stops the
    //   player (state IDLE) but keeps the playlist. A stopped player ignores play() until prepare()
    //   is called again, so every play/seek path re-arms it here first.
    private fun ensurePrepared() {
        if (exoPlayer.playbackState == ExoPlayer.STATE_IDLE && exoPlayer.mediaItemCount > 0) {
            exoPlayer.prepare()
        }
    }

    suspend fun onPlayerEvents(
        playerEvent : MediaPlayingEvent,
        selectedAudioIndex : Int =-1,
        position : Long = 0
    ){
        when(playerEvent){
            is MediaPlayingEvent.Backward -> exoPlayer.seekBack()
            is MediaPlayingEvent.Forward -> exoPlayer.seekForward()
            is MediaPlayingEvent.PlayOrPause -> playOrPause(playerEvent.mediaId)
            is MediaPlayingEvent.Restart -> {}
            is MediaPlayingEvent.Resume -> exoPlayer.play()
            is MediaPlayingEvent.SeekNext -> exoPlayer.seekToNext()
            // 🔧 14-Jul-2026: FIX (slider = selection) — a seek always targets the media whose slider
            //   the user touched: if that media is not the current track, switch to it AT the seeked
            //   position (seekTo(index, position)), making it the current selection as required.
            is MediaPlayingEvent.SeekTo -> {
                val index = indexOfMediaId(playerEvent.mediaId)
                when {
                    index == -1 -> {} // unknown id — never seek blindly
                    index != exoPlayer.currentMediaItemIndex -> {
                        ensurePrepared()   // 🔧 recover from IDLE after service teardown
                        exoPlayer.seekTo(index, position)
                        _audioState.value = PlayerState.CurrentPlaying(playerEvent.mediaId)
                    }
                    else -> exoPlayer.seekTo(position)
                }
            }
            is MediaPlayingEvent.SeekToPrevious -> exoPlayer.seekToPrevious()
            // 🔧 14-Jul-2026: FIX (I-2) — resolve the target by mediaId from the player's own playlist
            //   instead of trusting the repository index; guard so we NEVER seekToDefaultPosition(-1)
            //   (that was the crash/no-play when a just-recorded item wasn't in the repository list).
            is MediaPlayingEvent.SelectedAudioChange -> {
                val index = indexOfMediaId(playerEvent.mediaId)
                    .takeIf { it != -1 } ?: selectedAudioIndex
                when {
                    index < 0 || index >= exoPlayer.mediaItemCount -> {}
                    index == exoPlayer.currentMediaItemIndex -> {
                        playOrPause(playerEvent.mediaId)
                    }

                    else -> {
                        ensurePrepared()   // 🔧 recover from IDLE after service teardown
                        exoPlayer.seekToDefaultPosition(index)
                        _audioState.value = PlayerState.Playing(
                            isPlaying = true, playerEvent.mediaId
                        )
                        exoPlayer.playWhenReady = true
                        startProgressUpdate()
                    }
                }
            }
            is MediaPlayingEvent.Stop -> stopProgressUpdate()
            is MediaPlayingEvent.UpdateProgress -> {
               val seekto=  (exoPlayer.duration * playerEvent.newProgress).toLong()
                exoPlayer.seekTo(seekto)
                log_d("log MediaPlayingIntent: ${playerEvent.mediaId} :",seekto )
            }


        }
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        val mediaId = getId()
        when (playbackState) {
            ExoPlayer.STATE_BUFFERING -> {
                log_d("log MediaServiceListener ",exoPlayer.currentPosition )
                _audioState.value =
                    PlayerState.Buffering(exoPlayer.currentPosition, mediaId)
            }
            ExoPlayer.STATE_READY ->
                _audioState.value = PlayerState.Ready(exoPlayer.duration,mediaId)
            ExoPlayer.STATE_ENDED -> {
                // 🔧 14-Jul-2026: FIX (replay after end) — was a no-op, leaving the ticker running
                //   and the slider mid-way. Land the UI on the final position and stop the ticker;
                //   the next play press rewinds and restarts (see playOrPause).
                if (exoPlayer.duration > 0) {
                    _audioState.value = PlayerState.Progress(exoPlayer.duration, mediaId)
                }
                stopProgressUpdate()
            }

            ExoPlayer.STATE_IDLE -> {
                // no-op
            }
        }

    }

    // 🔧 14-Jul-2026: FIX — GlobalScope removed; the ticker now lives in listenerScope and is
    //   actually cancellable (the old `job` was never assigned, so loops leaked forever).
    override fun onIsPlayingChanged(isPlaying: Boolean) {
        val mediaId= getId()
        _audioState.value = PlayerState.Playing(isPlaying = isPlaying, mediaId = mediaId )
        _audioState.value = PlayerState.CurrentPlaying(mediaId)
        if (isPlaying) {
            startProgressUpdate()
        } else {
            stopProgressUpdate()
        }
    }

    // 🔧 14-Jul-2026: NEW — keep the UI selection in sync when ExoPlayer moves to another track by
    //   itself (auto-advance at end of media, or a programmatic seek to another index).
    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        val mediaId = mediaItem?.mediaId ?: getId()
        if (mediaId.isNotEmpty()) _audioState.value = PlayerState.CurrentPlaying(mediaId)
    }

    // 🔧 14-Jul-2026: FIX ("play on the media I touched") — if the pressed card's media is not the
    //   current track, switch to it and play, instead of toggling whatever happened to be current.
    private fun playOrPause(mediaId: String) {
        ensurePrepared()   // 🔧 recover from IDLE after service teardown (app close → reopen)
        val index = indexOfMediaId(mediaId)
        if (index != -1 && index != exoPlayer.currentMediaItemIndex) {
            exoPlayer.seekToDefaultPosition(index)
            exoPlayer.playWhenReady = true
            _audioState.value = PlayerState.Playing(isPlaying = true, mediaId)
            startProgressUpdate()
            return
        }
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
            stopProgressUpdate()
        } else {
            // 🔧 14-Jul-2026: FIX (replay after end) — play() is a no-op in STATE_ENDED; rewind to
            //   the start of the current item first so the play button restarts finished media.
            if (exoPlayer.playbackState == ExoPlayer.STATE_ENDED) {
                exoPlayer.seekToDefaultPosition()
            }
            exoPlayer.play()
            _audioState.value = PlayerState.Playing(
                isPlaying = true,mediaId
            )
            startProgressUpdate()
        }
    }
    // 🔧 14-Jul-2026: FIX — a single restartable ticker owned by listenerScope (see field docs above).
    private fun startProgressUpdate() {
        progressJob?.cancel()
        progressJob = listenerScope.launch {
            while (isActive) {
                delay(1000)
                _audioState.value = PlayerState.Progress(exoPlayer.currentPosition, getId())
            }
        }
    }
    private fun stopProgressUpdate() {
        progressJob?.cancel()
        progressJob = null
        _audioState.value = PlayerState.Playing(isPlaying = false, mediaId = getId())
    }

    //always call getId method to update the data otherwise ui will cause issues
    // 🔧 14-Jul-2026: FIX — read from the player itself and never index an empty list
    //   (media[currentMediaItemIndex] threw IndexOutOfBounds when callbacks fired early).
private fun getId() = exoPlayer.currentMediaItem?.mediaId ?: ""
    // 🔧 14-Jul-2026: NEW — lets a freshly created PlayMediaViewModel seed its currentPlayingId so
    //   a screen switch restores the exact playing card ("as is") instead of starting blank.
    fun getCurrentMediaId(): String? = exoPlayer.currentMediaItem?.mediaId
    fun isPlaying(): Boolean = exoPlayer.isPlaying
    fun getExoPlayer(): ExoPlayer = exoPlayer
}