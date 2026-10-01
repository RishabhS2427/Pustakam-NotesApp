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
        textView.isSelectable = true
        textView.backgroundColor = .clear
        textView.textContainer.lineBreakMode = .byWordWrapping
        textView.textContainer.widthTracksTextView = true
        textView.font = UIFont.systemFont(ofSize: fontSize)
        textView.delegate = context.coordinator
        textView.isScrollEnabled = false
        textView.textContainerInset = .zero
        textView.textContainer.lineFragmentPadding = 10
        textView.keyboardDismissMode = .interactive
        textView.showsVerticalScrollIndicator = false
        textView.setContentCompressionResistancePriority(.required, for: .vertical)
        textView.setContentHuggingPriority(.required, for: .vertical)
        return textView
    }

    func updateUIView(_ uiView: UITextView, context: Context) {
        // Sync attributed text
        if uiView.attributedText != attributedText {
            uiView.attributedText = attributedText
            // Recalculate height when text changes from the code side
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

    // MARK: - Height Calculation Engine
    static func recalculateHeight(view: UITextView, result: Binding<CGFloat>) {
        // Measure fitting size accurately based on current width limits
        let newSize = view.sizeThatFits(CGSize(width: view.frame.width, height: CGFloat.greatestFiniteMagnitude))
        if result.wrappedValue != newSize.height {
            DispatchQueue.main.async {
                result.wrappedValue = newSize.height
            }
        }
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
    }
}
