import Combine
import AVKit
import shared
import SwiftUI
import MediaPlayer

@Observable final class MediaManager {
    private var player : MediaControlledPlayer?

   static let mediaManager = MediaManager()

    var session = AVAudioSession.sharedInstance()
    private var playingMediaList = [NoteContentModel.MediaContent]()

    var playList :[String:PlayerUiState] = [:]
    var currentPlaying : NoteContentModel.MediaContent?

    // 🔧 14-Jul-2026: single reusable timer (was leaking a NEW Timer on every play call,
    //   accumulating across NoteEditor⇄video navigation → jank/crashes).
    private var elapsedTimer: Timer?
    // 🔧 14-Jul-2026: configure remote transport controls exactly once (addTarget was being
    //   called repeatedly, stacking duplicate handlers on each navigation).
    private var remoteControlsConfigured = false

    // 🔧 P0/4: use-case-backed bridge replaces NoteRepositoryHelper (last legacy consumer).
    //   Same underlying selectedNoteMediaContent flow → identical emissions, no behavior change.
    //   MediaManager is an app-lifetime singleton, so the observer is retained for app lifetime
    //   (the old helper's never-cancelled collector had the same lifetime — now it's explicit).
    private let contentBridge = NoteContentBridge()
    private var mediaObserver: Closeable?

    init(){
        mediaObserver = contentBridge.observeSelectedMedia { [weak self] mediaList in
            guard let self else { return }
            self.playingMediaList = mediaList
            self.convertAVPlayerItem(list: mediaList)
        }
        playNextAutomatically()
    }
    private func playNextAutomatically(){
        guard let playing = currentPlaying else { return }
        NotificationCenter.default.addObserver(
            forName: .AVPlayerItemDidPlayToEndTime,
            object: getPlayingItem(media: playing),
            queue: .main
        ) { [weak self] _ in
            self?.playNext()
        }
    }
    
    // 🔧 14-Jul-2026: FIX — only build an AVPlayerItem for media we haven't seen yet. Previously
    //   this rebuilt EVERY item on each emission of the media list (which fires on navigation and
    //   after saving media), swapping out the item the player was mid-playing → playback silently
    //   stopped (the "audio won't play after coming back from a video" bug).
    private func convertAVPlayerItem(list : [NoteContentModel.MediaContent]){
        list.forEach{ item in
            guard playList[item.id] == nil else { return }   // keep the existing (possibly playing) item
            let path = item.getMediaUrl()
            // 📥 21-Sep-2026 — never cache an item for a file not on disk yet; it outlived the download and never played
            guard !path.isEmpty, FileManager.default.fileExists(atPath: path) else { return }
            self.playList[item.id] = PlayerUiState(
                mediaPlayerItem :AVPlayerItem(url: URL(fileURLWithPath: path)),
                mediaContent: item
            )
        }
    }

    // 🔧 14-Jul-2026: Resolve (and lazily build) the AVPlayerItem for a media block. If the item
    //   isn't cached yet, build it from the file path so playback never silently no-ops. Returns
    //   nil only when the file is missing (guards against AVPlayer crashes on bad URLs).
    private func resolvePlayerItem(for media: NoteContentModel.MediaContent) -> AVPlayerItem? {
        if let existing = playList[media.id]?.mediaPlayerItem { return existing }
        let path = media.getMediaUrl()
        guard !path.isEmpty, FileManager.default.fileExists(atPath: path) else { return nil }
        let item = AVPlayerItem(url: URL(fileURLWithPath: path))
        playList[media.id] = PlayerUiState(mediaPlayerItem: item, mediaContent: media)
        return item
    }

    // 🔧 14-Jul-2026: Prepare media for a card WITHOUT auto-playing. Called from onAppear so
    //   scrolling a video/audio card into view no longer hijacks the shared player and forces a
    //   load. Actual playback happens on tap (selectAndPlayMedia / resumePlaying).
    func prepareMedia(media: NoteContentModel.MediaContent) {
        _ = resolvePlayerItem(for: media)
    }
    private func activateSession() {
        do {
            try session.setCategory(
                .playback,
                mode: .moviePlayback,
                options: []
            )
        } catch _ {}
        
        do {
            try session.setActive(true, options: .notifyOthersOnDeactivation)
        } catch _ {}
        
        do {
            try session.overrideOutputAudioPort(.speaker)
        } catch _ {}
    }
    
    func deactivateSession() {
        do {
            try session.setActive(false, options: .notifyOthersOnDeactivation)
        } catch let error as NSError {
            print("Failed to deactivate audio session: \(error.localizedDescription)")
        }
    }
    
    func updateTimeElapsed(){
        // 🔧 14-Jul-2026: invalidate the previous timer before scheduling a new one (was leaking).
        elapsedTimer?.invalidate()
        elapsedTimer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { [weak self] _ in
            guard let self, let player = self.player else { return }
            let elapsed = CMTimeGetSeconds(player.currentTime())
            MPNowPlayingInfoCenter.default().nowPlayingInfo?[MPNowPlayingInfoPropertyElapsedPlaybackTime] = elapsed
        }
    }
    func selectAndPlayMedia(media : NoteContentModel.MediaContent){
            // activate our session before playing audio
        activateSession()
        let player = getPlayer()
        // 🔧 14-Jul-2026: nil-safe — don't replace with a nil item (that stalls the player/crashes).
        guard let item = resolvePlayerItem(for: media) else { return }
        player.replaceCurrentItem(with: item)
        currentPlaying = media
        updateNowPlayingInfo(title: media.title, duration: TimeInterval(media.duration))
        updateTimeElapsed()
        setupRemoteTransportControls()
        player.play()
    }
    
    
    
    func pause() {
        if let player = player {
            player.pause()
        }
    }
    
    func resumePlaying(media : NoteContentModel.MediaContent){
        // 🔧 14-Jul-2026: FIX — re-activate the audio session here too. Previously only
        //   selectAndPlayMedia did, so after a video/fullscreen deactivated the session, tapping
        //   an audio item silently did nothing ("audio won't play after coming back" bug).
        activateSession()
        let player = getPlayer()
        guard let item = resolvePlayerItem(for: media) else { return }
        // Only swap the item when switching media — avoids resetting a track that's already loaded.
        if currentPlaying?.id != media.id {
            player.replaceCurrentItem(with: item)
            currentPlaying = media
        }
        updateNowPlayingInfo(title: media.title, duration: TimeInterval(media.duration))
        setupRemoteTransportControls()
        player.play()
        updateTimeElapsed()
    }
    func seekTo(media: NoteContentModel.MediaContent, currentTime : Double){
        // 🔧 14-Jul-2026: ensure the session is active and the item is resolved before seeking.
        activateSession()
        let player = getPlayer()
        if currentPlaying?.id != media.id {
            guard let item = resolvePlayerItem(for: media) else { return }
            currentPlaying = media
            player.replaceCurrentItem(with: item)
        }
        let seekTime = CMTime(seconds: currentTime, preferredTimescale: 600)
        player.seek(to: seekTime)
    }
    
    func getPlaybackDuration() -> Double {
        guard let player = player else {
            return 0
        }
        
        return player.currentItem?.duration.seconds ?? 0
    }
    
 
    func getPlayingItem(media : NoteContentModel.MediaContent) -> PlayerUiState? {
      return playList[media.id]
    }
    
    func getPlayer()-> MediaControlledPlayer {
        if let player = player { return self.player! }
        else {
            self.player = MediaControlledPlayer()
            return self.player!
        }
    }
    // background Playing
    
    func updateNowPlayingInfo(title: String, duration: TimeInterval, artwork: UIImage? = nil) {
        var nowPlayingInfo: [String: Any] = [
            MPMediaItemPropertyTitle: title,
            MPMediaItemPropertyPlaybackDuration: duration,
            MPNowPlayingInfoPropertyPlaybackRate: 1.0
        ]

        if let artwork = artwork {
            nowPlayingInfo[MPMediaItemPropertyArtwork] = MPMediaItemArtwork(boundsSize: artwork.size) { _ in artwork }
        }

        MPNowPlayingInfoCenter.default().nowPlayingInfo = nowPlayingInfo
    }
    
    func setupRemoteTransportControls() {
        guard let player = self.player else { return }
        // 🔧 14-Jul-2026: configure once — repeated addTarget calls stacked duplicate handlers
        //   on every play/navigation, a slow leak that degraded playback and could crash.
        guard !remoteControlsConfigured else { return }
        remoteControlsConfigured = true
        let commandCenter = MPRemoteCommandCenter.shared()

        commandCenter.playCommand.addTarget { event in
            player.play()
            return .success
        }

        commandCenter.pauseCommand.addTarget { event in
            player.pause()
            return .success
        }

        commandCenter.nextTrackCommand.isEnabled = true
        commandCenter.previousTrackCommand.isEnabled = true

        commandCenter.nextTrackCommand.addTarget { _ in
                self.playNext()
                return .success
            }
        commandCenter.previousTrackCommand.addTarget { _ in
                self.playPrevious()
                return .success
            }
    }

    func playNext() {
        guard let current = currentPlaying  else { return }
        if let index = playingMediaList.firstIndex(where : {$0.id == current.id}), index + 1 < playingMediaList.count {
            let media = playingMediaList[index + 1]
              selectAndPlayMedia(media: media)
        }
    }

    func playPrevious() {
        guard let playing = currentPlaying  else { return }
        // 🔧 P0/4: was `index - 1 > 0` — could never play the FIRST track from the second
        if let index = playingMediaList.firstIndex(where : {$0.id == playing.id}), index - 1 >= 0  {
            let media = playingMediaList[index - 1]
              selectAndPlayMedia(media: media)
        }
    }
    deinit{
        NotificationCenter.default.removeObserver(self, name: .AVPlayerItemDidPlayToEndTime, object: currentPlaying)
        // 🔧 P0/4: bridge cleanup (singleton never deinits in practice, but correctness first)
        mediaObserver?.close()
        contentBridge.dispose()
    }
}
