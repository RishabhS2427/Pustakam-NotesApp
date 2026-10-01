// 🔧 20-Jul-2026: NEW — full-screen image preview for the note editor (was missing on iOS: tapping
//   an image card did nothing). Whole image FITS (never fills/crops) and is pinch/double-tap
//   zoomable via the shared ZoomableView (DRY). Tap outside / Close dismisses.

import SwiftUI
import shared

struct ImagePreviewView : View {
    let path: String
    var noteId: String? = nil
    var mediaId: String? = nil
    var onClose: () -> Void = {}
    @State private var isLandscape: Bool
    @StateObject private var annotation = ImageAnnotationViewModel()

    init(path: String, noteId: String? = nil, mediaId: String? = nil, onClose: @escaping () -> Void = {}) {
        self.path = path
        self.noteId = noteId
        self.mediaId = mediaId
        self.onClose = onClose
        self.isLandscape = (UIApplication.shared.connectedScenes.first as? UIWindowScene)?
            .interfaceOrientation
            .isLandscape ?? false

    }
    var body: some View {
        ZStack(alignment: .topTrailing) {
            Color.black.ignoresSafeArea()
            if annotation.isDrawing, let base = annotation.base, let target = annotation.target,
               let session = annotation.drawing.overlay {
                Image(uiImage: base)
                    .resizable()
                    .scaledToFit()
                    .overlay {
                        DrawingPageLayer(
                            session: session,
                            anchorId: DrawNoteContents.shared.imageAnchorId(imageId: target.id),
                            active: true
                        )
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                // 🔧 20-Jul-2026: scaledToFit — full image visible; ZoomableView adds pinch/pan/double-tap
                ZoomableView {
                    if FileManager.default.fileExists(atPath: path), let ui = UIImage(contentsOfFile: path) {
                        Image(uiImage: ui)
                            .renderingMode(.original)
                            .resizable()
                            .scaledToFit()
                            .ignoresSafeArea()

                    } else {
                        AsyncImage(url: URL(string: path)) { img in img.resizable()
                            .renderingMode(.original).scaledToFit() }
                            placeholder: { ProgressView().tint(.white) }
                    }
                }
                .ignoresSafeArea()
            }
            if annotation.isDrawing, let session = annotation.drawing.active() {
                DrawingChrome(session: session, onDone: { annotation.finish() })
                    .padding(.top, 64)
            }
            HStack(){
                Button(action: {
                    isLandscape = !isLandscape
                    if(isLandscape){ OrientationManager.shared.set(.landscape) } else{ OrientationManager.shared.set(.portrait)
                    }
                }) {
                    Image(systemName: isLandscape ?  "rectangle.landscape.rotate" : "rectangle.portrait.rotate" )
                        .font(.system(size: 20))
                        .foregroundStyle(.white.opacity(0.9))
                        .padding(16)
                }
                Spacer()
                if annotation.isDrawing, let session = annotation.drawing.active() {
                    DrawingHistoryButtons(session: session, tint: .white)
                } else if annotation.canDraw {
                    Button(action: { annotation.toggle() }) {
                        Image(systemName: DrawingIcons.draw)
                            .font(.system(size: 20))
                            .foregroundStyle(.white.opacity(0.9))
                            .padding(16)
                    }
                    .accessibilityLabel(Text("Draw on image"))
                }
                Button(action: close) {
                    Image(systemName: "xmark.circle.fill")
                        .font(.system(size: 30))
                        .foregroundStyle(.white.opacity(0.9), .black.opacity(0.4))
                        .padding(16)
                }
            }
            if annotation.saving {
                ProgressView()
                    .tint(.white)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .onAppear { annotation.open(noteId: noteId, mediaId: mediaId) }
        .onDisappear(){
            //Todo change orientation according to prefs
            OrientationManager.shared.set(.portrait)
        }
    }

    private func close() {
        if annotation.isDrawing { annotation.finish() }
        onClose()
    }
}
