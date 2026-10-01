import SwiftUI
import shared

struct DrawingBlockView: View {

    let block: ReaderBlock.Drawing
    let policy: PageLayoutPolicy

    var body: some View {
        let content = block.item
        DrawingBlockContent(content: content)
            .id("\(content.id)#\(content.updatedAt ?? "")")
            .frame(maxWidth: .infinity)
            .frame(height: CGFloat(BlockHeightEstimator.shared.estimate(block: block, policy: policy)))
    }
}

private struct DrawingBlockContent: View {

    let content: NoteContentModel.Drawing

    @StateObject private var session: DrawingSession

    init(content: NoteContentModel.Drawing) {
        self.content = content
        _session = StateObject(
            wrappedValue: DrawingSession(
                initial: DrawNoteContents.shared.stateFor(content: content, author: DrawAuthors.shared.local(), readOnly: true),
                onDocumentChanged: { _ in }
            )
        )
    }

    var body: some View {
        DrawingFrame(content: content, session: session)
    }
}
