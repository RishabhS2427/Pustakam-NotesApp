import shared
import SwiftUI
import Firebase
import FirebaseCrashlytics
@main
struct iOSApp: App {
    init(){
        if AppEnvironment.isRunningForPreviews {
            return
        }

        FirebaseApp.configure()
        KoinKt.doInitKoin(appDeclaration: {_ in})
        // 📐 24-Sep-2026 — once per install every stored canvas moves to the compact layout
        CanvasLayoutUpgrade.runOnce()
        // 🔄 20-Aug-2026 sync: BGTaskScheduler.register MUST happen before launch finishes
        SyncController.shared.registerBackgroundTask()
        SyncController.shared.start()
        SyncController.shared.scheduleBackgroundRefresh()
    }
    var body: some Scene {
        WindowGroup {
            if AppEnvironment.isRunningForPreviews {
                EmptyView()
            } else {
                AppRootView()
            }
        }
	}
}

private struct AppRootView: View {
    @State var themeManager = ThemeManager()
    @State var router = Router()
    // 🔄 20-Aug-2026 sync: returning to the app is a trigger, same as Android's onStart
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        NavigationStack(path: $router.navPath){
            AppView()

                .navigationDestination(for: Router.Destination.self){
                    destination in
                    switch destination {
                        case .Signup : SignupView()
                        case.Notes : NotesView()
                        case .NoteEditor(let noteId) : NoteEditorView(noteId: noteId)
                        case.Login : LoginView()
                        case .Notification : NotificationView()
                        case .Search : SearchView()
                        case .Home : HomeView()
                        case .Settings : SettingsView()
                        case .Camera(let onDone):
                            CameraPreview(onCapture: onDone)
                        // 📖 01-Aug-2026: document reader vs whole-note reader
                        case .BookReader(let bookId):
                            BookReaderView(bookId: bookId)
                        case .NoteBookReader(let noteId, let startContentId):
                            NoteBookReaderView(noteId: noteId, startContentId: startContentId)
                        // 🎨 09-Aug-2026: free canvas editor over the same note
                        case .MasterEditor(let noteId):
                            MasterEditorScreen(noteId: noteId)
                        // 💬 31-Aug-2026 chat
                        case .ChatThread(let conversationId, let title):
                            ChatScreenView(conversationId: conversationId, title: title)
                        // 👤 31-Aug-2026 profile — this case existed in Router but was never
                        //   handled, so navigating to it silently showed the login screen
                        case .Profile: ProfileView()
                        default: LoginView()
                    }
                }
        }
        // 🔧 18-Jul-2026: widget deep link pustakam://book/<noteId> → open the book reader
        .onOpenURL { url in
            // 🔧 17-Aug-2026: Open With — a file url starts a NEW note that holds that file
            if IncomingShare.accept(url) {
                router.navigate(to: .NoteEditor(noteId: nil))
                return
            }
            guard url.scheme == "pustakam", url.host == "book" else { return }
            let noteId = url.lastPathComponent
            if !noteId.isEmpty && noteId != "book" {
                router.navigate(to: .NoteBookReader(noteId: noteId))
            }
        }
        .onChange(of: scenePhase) { _, phase in
            // 🔄 29-Aug-2026 — on screen the engine polls in seconds, which is how the other
            //   device's edit arrives without a pull-to-refresh.
            SyncController.shared.setForeground(phase == .active)
            if phase == .active { SyncController.shared.nudge() }
            if phase == .background { SyncController.shared.scheduleBackgroundRefresh() }
        }
        .environment(router)
        .environment(themeManager)
        // 🎨 22-Jul-2026 — nil for .system so the app follows the OS appearance.
        .preferredColorScheme(themeManager.getTheme())
        // 🎨 22-Jul-2026 — inject \.palette *after* preferredColorScheme so the AMOLED
        //   override sees the scheme iOS actually resolved.
        .themedRoot(mode: themeManager.mode)
    }
}


// 📐 24-Sep-2026 — the adapter is held until the upgrade finishes, or closing it would cancel the work
enum CanvasLayoutUpgrade {
    private static let doneKey = "canvas_layout_compact_v1"
    private static var running: CanvasBridgeAdapter?

    static func runOnce() {
        guard !UserDefaults.standard.bool(forKey: doneKey), running == nil else { return }
        let adapter = CanvasBridgeAdapter()
        running = adapter
        let bounds = UIScreen.main.bounds
        let paperWidth = Float(min(bounds.width, bounds.height)) - CanvasEditorKt.PAGE_SCREEN_MARGIN * 2
        adapter.upgradeLayouts(unitScale: 1, maxPaperWidth: paperWidth) { upgraded in
            if upgraded { UserDefaults.standard.set(true, forKey: doneKey) }
            DispatchQueue.main.async { running = nil }
        }
    }
}

final class AppDelegate: UIResponder, UIApplicationDelegate {
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {
        return true
    }
}

// 🐛 23-Jul-2026: breadcrumbs for the PDF path. A CoreGraphics abort gives a stack with no clue
//   WHICH file killed it; these keys ride along with the next crash report so the offending
//   document is identifiable.
enum CrashBreadcrumb {
    static func openingDocument(contentId: String, path: String?, pageCount: Int) {
        let crashlytics = Crashlytics.crashlytics()
        crashlytics.setCustomValue(contentId, forKey: "doc.contentId")
        crashlytics.setCustomValue(pageCount, forKey: "doc.pageCount")
        crashlytics.setCustomValue((path as NSString?)?.lastPathComponent ?? "nil", forKey: "doc.file")
        crashlytics.log("opening document \(contentId) pages=\(pageCount)")
    }

    static func rejectedUnreadablePdf(path: String?) {
        Crashlytics.crashlytics().log("rejected unreadable pdf: \((path as NSString?)?.lastPathComponent ?? "nil")")
    }
}
