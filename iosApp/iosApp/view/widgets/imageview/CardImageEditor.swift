import SwiftUI
import shared

struct CardImageEditor: View {
    var content: NoteContentModel.MediaContent
    var actionEdit: () -> Void = {}
    var actionClick: () -> Void
    var actionDelete: () -> Void = {}
    // 🔧 14-Jul-2026: NEW — save-to-device callback (image → Photos gallery). Default keeps old call sites compiling.
    var actionSave: () -> Void = {}
    // 🧱 24-Sep-2026 — the master canvas gives the card its own size; the defaults keep every other screen's 200x300 card
    var cardWidth: CGFloat = 200
    var cardHeight: CGFloat = 400
    @State private var showActions: Bool = false
    // 🔧 02-Aug-2026: cancels a pending auto-hide when the button is tapped again (Android hideJob parity)
    @State private var hideToken: Int = 0

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

    var body: some View {

        ZStack(alignment: .bottom) {
            AsyncImage(url: URL(fileURLWithPath: content.getMediaUrl())) { phase in
                if let image = phase.image {
                    image
                        .resizable()
                        .scaledToFill()
                } else if phase.error != nil || content.getMediaUrl().isEmpty {
                    Image("avatar")
                        .resizable()
                        .scaledToFill()
                } else {
                    ProgressView()
                }
            }
            .frame(width: cardWidth, height: cardHeight)
            .clipShape(RoundedRectangle(cornerRadius: 12))
            .contentShape(Rectangle())
            .onTapGesture {
                actionClick()
            }
            // 🔧 02-Aug-2026: top-end "⋮" reveals the actions, same as Android's ImageCard
            .overlay(alignment: .topTrailing) {
                CardActionsButton { revealActions() }
                    .padding(.vertical, 12)
                    .padding(.horizontal, 8)
            }

            // 📥 20-Sep-2026 — the generic transfer bar; draws nothing once the bytes are here
            MediaDownloadOverlay(media: content)

            if showActions {

                ZStack(alignment: .bottom) {

                    // Fade only on bottom
                    LinearGradient(
                        colors: [
                            .clear,
                            .black.opacity(0.05),
                            .black.opacity(0.35),
                            .black.opacity(0.55),
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
                .frame(height: 70)
                .transition(.opacity)
            }
        }
        .frame(width: cardWidth, height: cardHeight)
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }
}
