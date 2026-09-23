import SwiftUI
import shared

struct NoteEditorView: View {
    @Environment(Router.self) private var router: Router
    @Environment(\.dismiss) private var dismiss
    @State private var errorField: ErrorField = ErrorField()
    @State private var noteContent: String = ""
    @State private var isRulledEnabled: Bool = false
    @State private var  showDelete : Bool = false
    @State private var deleteContentId : String? = nil
    @State private var readerPrefs = ReaderPrefsAdapter()
    // 🔧 20-Jul-2026: NEW — path of the image shown in the full-screen preview (nil = hidden)
    @State private var previewImagePath: String? = nil
    // 🔧 20-Jul-2026: NEW FEATURE (export) — format chooser + spinner while generating
    @State private var showExportOptions = false
    @State private var isExporting = false
    // 🔧 V1 fix: @StateObject (was @ObservedObject + inline init → VM recreated on every
    //           re-render, wiping edits). Note passed via init — setNote() no longer exists.
    @StateObject private var noteEditorViewModel: NoteEditorViewModel

    private let isNewNote: Bool
    @State private var didConsumeShare = false
    private var cameraPermission = CameraPermission()
    private var micPermission = MicPermission()
    init(noteId: String? = nil) {
        _noteEditorViewModel = StateObject(wrappedValue: NoteEditorViewModel(noteId: noteId))
        // title now lives in the VM state (fixes lost-title bug); only showDelete stays local
        _showDelete = State(initialValue: noteId != nil)
        isNewNote = noteId == nil
    }
    var body: some View {
        ZStack(alignment: .topLeading) {
            ScrollView(.vertical){
            VStack(alignment: .leading) {
                NoteTextEditor(
                    text: $noteEditorViewModel.state.title,   // 🔧 title owned by VM → actually saved
                    placeholder: "Title : Keep your thoughts alive.",
                    fontSize: 22
                ).frame(minHeight: 20, maxHeight:.infinity)
                    ForEach(noteEditorViewModel.state.noteContents){ noteContent in
                        renderWidget(content: noteContent){
                            updatedContent in
                            noteEditorViewModel.updateContent(content: updatedContent)
                        }
                    }
                    // 🔧 07-Aug-2026 — room so the caret clears the keyboard accessory toolbar
                    Color.clear.frame(height: 80)
                }
            }
            .scrollDismissesKeyboard(.interactively)
            .frame(maxHeight: .infinity, alignment: .top)
            if noteEditorViewModel.state.isLoading || isExporting {  // 🔧 spinner: VM state or export
                LoadingUI().frame(alignment: .center)
                Color.black.opacity(0.4).edgesIgnoringSafeArea(.all)
            }
            OverlayEditorButtons(
                showDelete: noteEditorViewModel.state.note != nil,  // 🔧 state.note — updates when async note arrives
                onMediaCapture: { noteEditorViewModel.requestCapture(ContentType.image) },
                onShare: { print("Share action") },
                onRecordMic: { noteEditorViewModel.requestCapture(ContentType.audio) },
                onAddTextField: { noteEditorViewModel.addNewText() },
                onArrowButton: {},
                onImportFile: { noteEditorViewModel.openImportSheet() }
            )
            .frame(alignment: .bottomTrailing)
            .padding()
        }
        .editorCapabilities(
            state: noteEditorViewModel.capabilities,
            noteTitle: noteEditorViewModel.state.title,
            callbacks: EditorCapabilityCallbacks(
                onState: { noteEditorViewModel.onCapabilityState($0) },
                onOpenCamera: {
                    noteEditorViewModel.saveThenOpen {
                        router.navigate(to: .Camera { data in
                            noteEditorViewModel.getCapturedData(media: data)
                        })
                    }
                },
                onCaptured: { noteEditorViewModel.getCapturedData(media: $0) },
                onFilesPicked: { noteEditorViewModel.importFiles(urls: $0) },
                onImportLink: { noteEditorViewModel.importFromLink($0) },
                onDeleteContent: { noteEditorViewModel.deleteContent(contentId: $0) },
                onDeleteNote: { noteEditorViewModel.deleteNote { dismiss() } },
                // a denied camera/mic used to raise the "open Settings" alert; without this the
                // tap looks like nothing happened at all
                onPermissionDenied: { kind in
                    switch kind {
                    case ContentType.audio: setAlert(alertType: .MIC)
                    case ContentType.location: setAlert(alertType: .LOCATION)
                    default: setAlert(alertType: .CAMERA)
                    }
                }
            )
        )

        .fullScreenCover(isPresented: Binding(
            get: { previewImagePath != nil },
            set: { if !$0 { previewImagePath = nil } }
        )) {
            if let path = previewImagePath {
                ImagePreviewView(path: path) { previewImagePath = nil }
            }
        }
        .confirmationDialog("Export note", isPresented: $showExportOptions, titleVisibility: .visible) {
            Button("Export as PDF") { runExport(.pdf) }
            Button("Export as Image") { runExport(.image) }
            Button("Export as Word (DOCX)") { runExport(.docx) }
            Button("Cancel", role: .cancel) {}
        }
        .alert(isPresented: $errorField.showErrorAlert) {
            throwAlert()
        }
        .padding(.horizontal, 12)
        .navigationBarBackButtonHidden(true)
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                BackButton(action: {
                    dismiss()
                })
            }
            
            ToolbarItem(placement: .topBarTrailing) {
                HStack{
                    ActionButtonWithoutBackground(iconName: "workspace",
                                                  action: {
                        if let noteId = noteEditorViewModel.state.note?.id {
                            // flush first: the canvas reads the note from the db on open,
                            // so navigating before the write lands shows stale text
                            noteEditorViewModel.saveThenOpen {
                                router.navigate(to: .MasterEditor(noteId: noteId))
                            }
                        }
                    }, tint: Theme.Colors.secondary)
                    
                    ActionButtonWithoutBackground(iconName: "arrow.uturn.backward",
                                                  enabled : noteEditorViewModel.canUndo,
                                                  action: {
                        noteEditorViewModel.undo()
                    }, tint: Theme.Colors.secondary)
                    ActionButtonWithoutBackground(iconName: "arrow.uturn.forward",
                                                  enabled : noteEditorViewModel.canRedo,
                                                  action: {
                        noteEditorViewModel.redo()
                    }, tint: Theme.Colors.secondary)
                    ActionButtonWithoutBackground(iconName: "book",
                                                  enabled : noteEditorViewModel.isNoteValid(),
                                                  action: {
                        if let noteId = noteEditorViewModel.state.note?.id {
                            noteEditorViewModel.saveThenOpen {
                                router.navigate(to: .NoteBookReader(noteId: noteId))
                            }
                        }
                    }, tint: Theme.Colors.secondary)
                    ActionButtonWithoutBackground(iconName: "square.and.arrow.up.on.square", action: {
                        showExportOptions = true
                    }, tint: Theme.Colors.secondary)
                    ActionButtonWithoutBackground(iconName: "tray.and.arrow.down", action: {
                        saveNote()
                    }, tint :Theme.Colors.secondary)
                        ActionButtonWithoutBackground(iconName: "trash", action: {
                            setAlert(message: "Are you sure you want to delete this note?", title: "Delete note", alertType: .DELETE )
                        }, tint: Color.red)
                }
            }
        }
        .onDisappear { noteEditorViewModel.saveIfChanged() }
        .onAppear { noteEditorViewModel.refresh() }
        // 🔄 28-Aug-2026 — pull down inside the editor to fetch this note's latest content
        .refreshable { await noteEditorViewModel.syncNowAndReload() }
        .onChange(of: noteEditorViewModel.state.isNoteReady, initial: true) { _, ready in
            guard ready, isNewNote, !didConsumeShare, IncomingShare.hasPending else { return }
            didConsumeShare = true
            noteEditorViewModel.importShared(urls: IncomingShare.take())
        }
        .onReceive(
            NotificationCenter.default.publisher(
                for: UIApplication.willResignActiveNotification
            )
        ) { _ in noteEditorViewModel.saveIfChanged() }
    }
    
    
    @ViewBuilder
    func renderWidget(content : NoteContentModel, onUpdate :  @escaping (NoteContentModel)-> Void ) -> some View {
      
        switch content.type {
            case .text:
                let textContent = content as! NoteContentModel.TextContent

                MasterTextContentWidget(
                    text: textContent.text,
                    metadata: textContent.metadata,
                    onDocumentChange: { document in
                        onUpdate(
                            RichTextCodec.shared.applyTo(content: textContent, document: document)
                        )
                    }
                )
            case .image :
                let contentImage = content as! NoteContentModel.MediaContent
            CardImageEditor(content: contentImage, actionClick: {
                previewImagePath = contentImage.getMediaUrl()
            },actionDelete: {
                askDeleteContent(contentId: contentImage.id, kind: "Image")
            }, actionSave: {
                saveMediaToDevice(media: contentImage)
            }
                 )

            case .video:
                let contentVideo = content as! NoteContentModel.MediaContent
                VideoCardPlayer(content: contentVideo, cardPadding: 0, actionDelete: {
                    askDeleteContent(contentId: contentVideo.id, kind: "Video")
                }, actionSave: {saveMediaToDevice(media: contentVideo)})
            case .audio:
                let contentAudio = content as! NoteContentModel.MediaContent
                 AudioPlayView(mediaContent: contentAudio, onDelete: {
                    askDeleteContent(contentId: contentAudio.id, kind: "Audio")
                 }, onSave: {
                    saveMediaToDevice(media: contentAudio)
                 })

            case .pdf, .docx, .epub, .txt, .md, .other:
                let contentDoc = content as! NoteContentModel.MediaContent 
                InlineBookFileView(
                    media: contentDoc,
                    onOpenFull: {
                        let cid = contentDoc.id
                        noteEditorViewModel.saveThenOpen {
                            router.navigate(to: .BookReader(bookId: cid))
                        }
                    },
                    onDelete: { askDeleteContent(contentId: contentDoc.id, kind: "File") },
                    onSave: { saveMediaToDevice(media: contentDoc) },
                    onShare: { shareMediaFile(media: contentDoc) },
                    onPageChange: { page in
                        onUpdate(contentDoc.withProgressPage(page: page))
                    }
                )
                .frame(width: UIScreen.main.bounds.width * 0.7, alignment: .leading)
            case .gif:
                let contentGif = content as! NoteContentModel.MediaContent
                CardImageEditor(content: contentGif, actionClick: {}, actionDelete: {
                    askDeleteContent(contentId: contentGif.id, kind: "Image")
                }, actionSave: {
                    saveMediaToDevice(media: contentGif)
                })

            default : NoteTextFieldWrapper()
        }

    }
    
    
    func shareNote(){
            // share note link via different apps
    }
    
    private func saveNote(){
        noteEditorViewModel.saveNote()
    }

    private func shareMediaFile(media: NoteContentModel.MediaContent) {
        guard let path = LocalFilePathResolver_iosKt.resolveLocalFilePath(path: media.localPath) ?? media.localPath,
              !path.isEmpty, FileManager.default.fileExists(atPath: path) else {
            noteEditorViewModel.state.errorMessage = "File not available to share."
            return
        }
        NoteExporter.share(url: URL(fileURLWithPath: path))
    }
    private func runExport(_ format: ExportFormat) {
        guard let note = noteEditorViewModel.state.note else { return }
        isExporting = true
        DispatchQueue.global(qos: .userInitiated).async {
            let url = NoteExporter.export(note: note, format: format)
            DispatchQueue.main.async {
                isExporting = false
                if let url { NoteExporter.share(url: url) }
            }
        }
    }

        // handler call wrappers
    private func callDelete() {
        noteEditorViewModel.deleteNote {
            dismiss()
        }
    }
    
    /**
     Render Alert on Screen
     */
    func throwAlert() -> Alert {
        switch errorField.alertType {
        case .DELETE_CONTENT: return (Alert(
            title: Text("\(errorField.errorMessageTitle)").font(.headline.weight(.heavy)).foregroundColor(.red),
            message: Text(errorField.errorMessage),
            primaryButton: Alert.Button.default(Text("Cancel"), action: { resetAlert() }),
            secondaryButton: Alert.Button.default(
                Text("Confirm"),
                action: {
                    if let contentId = deleteContentId {
                        noteEditorViewModel.deleteContent(contentId: contentId)
                    }
                    resetAlert()
                }))) 
            case .DELETE:
                return Alert(
                    title: Text("\(errorField.errorMessageTitle)").font(.headline.weight(.heavy)).foregroundColor(.red),
                    message: Text(errorField.errorMessage),
                    primaryButton: Alert.Button.default(Text("Cancel"), action: { resetAlert() }),
                    secondaryButton: Alert.Button.default(
                        Text("Confirm"),
                        action: {
                            callDelete()  // 🔧 state.note
                            resetAlert()
                        }))
            case .CAMERA : return cameraPermission.showAlert { resetAlert() }
                
            case .MIC: return micPermission.showAlert { resetAlert() }
                
            case .LOCATION : return cameraPermission.showAlert { resetAlert() }
                
            default:
                return Alert(
                    title: Text("\(errorField.errorMessageTitle)").font(.headline.weight(.heavy)).foregroundColor(.red),
                    message: Text(errorField.errorMessage),
                    dismissButton: Alert.Button.default(Text("Cancel"), action: { resetAlert() }))
                
        }
        
    }
    /**
     Configure Alert According to condition
     */
    private func setAlert(message: String = "", title: String = "Error", alertType : AlertUCPermission) {
        errorField.alertType = alertType
        errorField.errorMessage = message
        errorField.showErrorAlert = true
        errorField.errorMessageTitle = title
    }

    private func askDeleteContent(contentId: String, kind: String) {
        deleteContentId = contentId
        setAlert(message: "Are you sure you want to delete this \(kind)?",
                 title: "Delete \(kind)", alertType: .DELETE_CONTENT)
    }

    private func resetAlert() {
        errorField.alertType = AlertUCPermission.WARNING
        errorField.errorMessage = ""
        errorField.showErrorAlert = false
        errorField.errorMessageTitle = ""
        deleteContentId = nil   // 🔧 14-Jul-2026: clear selection on cancel/confirm alike
    }
    
}
struct NotebookStyleNoteView_Previews: PreviewProvider {
    static var previews: some View {
        NoteEditorView()
    }
}
