
import shared
import SwiftUI
import AVKit
struct VideoCardPlayer : View {
    var content : NoteContentModel.MediaContent
    var actionEdit : () -> Void = {}
    var actionClick : () -> Void = {}
    // 🔧 14-Jul-2026: NEW — delete callback (video had no delete UI before)
    var actionDelete : () -> Void = {}
    // 🔧 14-Jul-2026: NEW — save-to-device callback (video → Photos gallery)
    var actionSave : () -> Void = {}
    @State private var showActions : Bool = false
    // 🔧 02-Aug-2026: cancels a pending auto-hide when the button is tapped again (Android hideJob parity)
    @State private var hideToken : Int = 0

    // 🔧 02-Aug-2026: reveal + auto-hide after 2.5s, restarting the timer on every tap
    private func revealActions() {
        hideToken += 1
        let token = hideToken
        withAnimation(.easeInOut(duration: 0.25)) { showActions = true }
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.5) {
            guard token == hideToken else { return }
            withAnimation(.easeInOut(duration: 0.5)) { showActions = false }
        }
    }
    var mediaManager = MediaManager.mediaManager
    // 🔧 14-Jul-2026: init now accepts actionDelete + actionSave (defaults keep old call sites compiling)
    //   Usage: VideoCardPlayer(content: media, actionDelete: { ... }, actionSave: { ... })
    // 📖 01-Aug-2026: the reader packs videos into grid cells, so the card must be able to take the
    //   size it is given. Defaults reproduce the editor's 200x300 card exactly.
    var cardWidth: CGFloat = 200
    var cardHeight: CGFloat = 300
    var cardPadding: CGFloat = 12

    init(content: NoteContentModel.MediaContent,
         cardWidth: CGFloat = 200,
         cardHeight: CGFloat = 300,
         cardPadding: CGFloat = 12,
         actionClick: @escaping () -> Void = {},
         actionDelete: @escaping () -> Void = {},
         actionSave: @escaping () -> Void = {}){
        self.content = content
        self.cardWidth = cardWidth
        self.cardHeight = cardHeight
        self.cardPadding = cardPadding
        self.actionClick = actionClick
        self.actionDelete = actionDelete
        self.actionSave = actionSave
    }

    var body: some View {
        // 🔧 14-Jul-2026: outer ZStack aligned .bottom so the actions bar overlays the card bottom
        ZStack(alignment: .bottom){
            ZStack{
                if mediaManager.currentPlaying?.id == content.id {
                                SystemControlledPlayerView(player: mediaManager.getPlayer())
                            } else {
                                ZStack {
                                    // You can show thumbnail or just placeholder when not active
                                    Rectangle().fill(Color.black.opacity(0.2))
                                    Image(systemName: "play.circle.fill")
                                        .resizable()
                                        .frame(width: 40, height: 40)
                                        .foregroundColor(Theme.Colors.primary)
                                }
                            }
            }.frame(width: cardWidth, height: cardHeight)
                .cornerRadius(12)
                .onAppear{
                    mediaManager.prepareMedia(media: content)
                }
                .onTapGesture {
                    mediaManager.resumePlaying(media:content)
                    actionClick()
                }
                // 🔧 02-Aug-2026: top-end "⋮" reveals the actions, same as Android's VideoCard
                .overlay(alignment: .topTrailing) {
                    CardActionsButton { revealActions() }
                        .padding(.vertical, 12)
                        .padding(.horizontal, 8)
                }

            // 📥 20-Sep-2026 — the generic transfer bar; draws nothing once the bytes are here
            MediaDownloadOverlay(media: content)

            // 🔧 14-Jul-2026: NEW — bottom-fade actions bar with delete/edit (CardImageEditor parity)
            if showActions {
                ZStack(alignment: .bottom) {
                    LinearGradient(
                        colors: [
                            .clear,
                            .black.opacity(0.05),
                            .black.opacity(0.35),
                            .black.opacity(0.55)
                        ],
                        startPoint: .top,
                        endPoint: .bottom
                    )
                    .frame(height: 90)

                    HStack(spacing: 40) {
                        Button {
                            actionDelete()
                        } label: {
                            Image(systemName: "trash.fill")
                                .font(.title2)
                                .foregroundColor(.red)
                        }

                        // 🔧 14-Jul-2026: NEW — save-to-gallery button
                        Button {
                            actionSave()
                        } label: {
                            Image(systemName: "square.and.arrow.down.fill")
                                .font(.title2)
                                .foregroundColor(.white)
                        }

                        Button {
                            actionEdit()
                        } label: {
                            Image(systemName: "square.and.arrow.up.fill")
                                .font(.title2)
                                .foregroundColor(.white)
                        }
                    }
                    .padding(.bottom, 20)
                }
                .frame(width: cardWidth, height: 70)
                .transition(.opacity)
            }
        }
        .frame(width: cardWidth, height: cardHeight)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .padding(cardPadding)
    }
}
