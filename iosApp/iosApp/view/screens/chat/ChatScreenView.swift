import SwiftUI
import shared

/// 💬 The full-screen thread. It is a title bar and ChatWidgetView — every behaviour lives in the
/// widget, so the sheet, the panel and this screen can never drift apart.
struct ChatScreenView: View {

    let conversationId: String
    var title: String = "Chat"

    @Environment(Router.self) private var router: Router
    @Environment(\.palette) private var palette

    var body: some View {
        ChatWidgetView(
            conversationId: conversationId,
            onOpenMedia: { media in router.navigate(to: .NoteBookReader(noteId: conversationId, startContentId: media.id)) },
            onOpenDocument: { media in router.navigate(to: .BookReader(bookId: media.id)) }
        )
        .navigationTitle(title)
        .navigationBarTitleDisplayMode(.inline)
        .navigationBarBackButtonHidden(true)
        .toolbar {
            ToolbarItem(placement: .navigationBarLeading) {
                Button { router.navigateBack() } label: {
                    Image(systemName: "chevron.left").foregroundColor(palette.accent)
                }
            }
        }
    }
}
