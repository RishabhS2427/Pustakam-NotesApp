import SwiftUI
import UIKit

struct NoteTextField: UIViewRepresentable {

    // MARK: - Configuration
    var fontSize: CGFloat = 28

    // MARK: - Bindings
    @Binding var attributedText: NSAttributedString
    @Binding var selectedRange: NSRange?

    /// Caret rect callback (used for floating toolbar positioning)
    var onSelectionRectChange: (CGRect?) -> Void

    // MARK: - UIViewRepresentable
    func makeUIView(context: Context) -> UITextView {
        let textView = UITextView()
        textView.isEditable = true
        textView.backgroundColor = .clear
        textView.font = UIFont.systemFont(ofSize: fontSize)
        textView.delegate = context.coordinator
        textView.isScrollEnabled = false
        textView.textContainerInset = .zero
        textView.textContainer.lineFragmentPadding = 10
        textView.keyboardDismissMode = .interactive
        return textView
    }

    func updateUIView(_ uiView: UITextView, context: Context) {

        // Sync attributed text
        if uiView.attributedText != attributedText {
            uiView.attributedText = attributedText
        }

        // Sync selection
        if let range = selectedRange,
           uiView.selectedRange != range {
            uiView.selectedRange = range
        }
    }

    func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }

    // MARK: - Coordinator
    class Coordinator: NSObject, UITextViewDelegate {

        var parent: NoteTextField
        private var lastContentOffset: CGPoint = .zero

        init(_ parent: NoteTextField) {
            self.parent = parent
        }

        // Text changes
        func textViewDidChange(_ textView: UITextView) {
            parent.attributedText = textView.attributedText
        }

        // Selection changes
        func textViewDidChangeSelection(_ textView: UITextView) {

            let range = textView.selectedRange
            parent.selectedRange = range

            guard range.length > 0,
                  let textRange = textView.selectedTextRange else {
                parent.onSelectionRectChange(nil)
                return
            }

            let caretRect = textView.caretRect(for: textRange.end)

            let convertedRect = textView.convert(
                caretRect,
                to: textView.superview
            )

            parent.onSelectionRectChange(convertedRect)
        }

        // 🔧 Prevent unwanted scroll on Enter
//        func textView(
//            _ textView: UITextView,
//            shouldChangeTextIn range: NSRange,
//            replacementText text: String
//        ) -> Bool {
//
//            // Save scroll offset BEFORE UIKit adjusts it
//            lastContentOffset = textView.contentOffset
//
//            DispatchQueue.main.async {
//                self.restoreScrollIfNeeded(textView)
//            }
//
//            return true
//        }

//        private func restoreScrollIfNeeded(_ textView: UITextView) {
//
//            guard let textRange = textView.selectedTextRange else { return }
//
//            let caretRect = textView.caretRect(for: textRange.end)
//
//            let visibleRect = CGRect(
//                origin: textView.contentOffset,
//                size: textView.bounds.size
//            )
//
//            // If caret is already visible, restore offset
//            if visibleRect.contains(caretRect) {
//                textView.setContentOffset(lastContentOffset, animated: false)
//            }
//        }
    }
}

