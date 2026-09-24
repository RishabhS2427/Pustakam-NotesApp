import SwiftUI


struct NoteTextFieldWrapper : View {
    @State var text : String = ""
    var onTextChange: (NSAttributedString) -> Void = {_ in  }
    var body: some View{
        NoteTextEditor(
            text: $text,
           placeholder: "Keep your thoughts alive.",
           fontSize: 16
        ).frame(minHeight: 20, maxHeight: .infinity)
        // 🔧 EDITOR-FIX: onTextChange was NEVER invoked — typed text died inside the
        //   editor, TextContent saved as "" and reopened notes looked empty.
        .onChange(of: text) { _, newValue in
            onTextChange(NSAttributedString(string: newValue))
        }
    }
}
struct NoteTextEditor: View {
    @Binding var text : String
    @State private var attText: NSAttributedString = NSAttributedString(string: "")
    @State private var selectedRange: NSRange?
    var placeholder: String = ""
    @State var leftpadding: CGFloat = 10
    @State var fontSize: CGFloat = 28
    @State var isRulledEnabled: Bool = false
    @State var selection : TextSelection?
    @State private var toolbarRect: CGRect?
    @State private var showToolbar = false

    let lineColor = Color.gray.opacity(0.5)
    let marginColor = Color.red
    let lineSpacing: CGFloat = 28
    var body: some View {
        ZStack(alignment: .topLeading){
            if isRulledEnabled {
                RulledPage(
                    lineColor: lineColor,
                    marginColor:
                        marginColor, fontSize: fontSize,
                    lineSpacing: lineSpacing,
                    leftpadding: leftpadding
                ).frame(maxHeight: .infinity)
                .background(Color.white)
                .ignoresSafeArea()
            }
            if attText.string.isEmpty{
                Text(placeholder)
                    .padding(.leading, leftpadding)
                    .frame(minHeight: 20)
                    .lineSpacing(10)
                    .font(.system(size: fontSize, weight: .light))
                    .foregroundColor(.gray)
            }
        
            NoteTextField(attributedText: $attText, selectedRange: $selectedRange, onSelectionRectChange: { rect in
                toolbarRect = rect
                showToolbar = rect != nil
            }).frame(maxHeight: .infinity)
              // 🔧 EDITOR-FIX: seed the initial value — onChange doesn't fire for it,
              //   so previously-saved text rendered as blank on reopen.
              .onAppear {
                  if attText.string != text { attText = NSAttributedString(string: text) }
              }
              .onChange(of: text){
                  // downward sync (equality guard prevents ping-pong with the upward sync)
                  if attText.string != text { attText = NSAttributedString(string : text) }
              }
              // 🔧 EDITOR-FIX: upward sync was MISSING entirely — typing updated attText
              //   (via textViewDidChange) but the $text binding (title, content text)
              //   never received it. This is why titles/content were lost.
              .onChange(of: attText) { _, newValue in
                  if text != newValue.string { text = newValue.string }
              }
                .padding(.leading, leftpadding)
                .accentColor(.brown)
                .font(.system(size: fontSize))
                .scrollContentBackground(.hidden)
            if showToolbar, let rect = toolbarRect {
                FormattingToolbar(){_ in }
                    .offset(
                        x: safeX(rect.midX),
                        y: safeY(rect.maxY)
                    ).zIndex(10).transition(.opacity)
                    }
            
            }.animation(.easeInOut, value: showToolbar)
        
    }
    private func safeX(_ x: CGFloat) -> CGFloat {
        let screenWidth = UIScreen.main.bounds.width
        let toolbarWidth: CGFloat = screenWidth/2
        
            if x + toolbarWidth  <= screenWidth {
                return x
            }

            return screenWidth - toolbarWidth
    }

        private func safeY(_ y: CGFloat) -> CGFloat {
            let screenHeight = UIScreen.main.bounds.height
                let toolbarHeight: CGFloat = 44

            if y + toolbarHeight <= screenHeight {
                    return y
                }

        
                return y - toolbarHeight
        }
    
}
