
import SwiftUI
@Observable final class Router{
    
    var navPath = NavigationPath()
    public enum Destination : Hashable {
        case Login
        case Signup
        case Notes
        case NoteEditor (noteId : String? = nil )
        case Profile
        case Notification
        case Search
        case Home
        case Settings
        case Camera (onCapture : (CapturedMedia?) -> Void)
        case BookReader (bookId : String)
        case NoteBookReader (noteId : String, startContentId : String? = nil)
        case MasterEditor (noteId : String? = nil)
        // 💬 31-Aug-2026 chat: one full-screen conversation
        case ChatThread (conversationId : String, title : String = "Chat")

        func hash(into hasher: inout Hasher) {
            switch self {
                case .NoteEditor(let noteId):
                    hasher.combine(noteId)
                case .BookReader(let bookId):
                    hasher.combine(bookId)
                case .NoteBookReader(let noteId, let contentId):
                    hasher.combine(noteId); hasher.combine(contentId)
                case .MasterEditor(let noteId):
                    hasher.combine("MasterEditor"); hasher.combine(noteId)
                case .ChatThread(let conversationId, _):
                    hasher.combine("ChatThread"); hasher.combine(conversationId)
                default:
                    hasher.combine(String(describing: self))
            }
        }

        static func == (lhs: Router.Destination, rhs: Router.Destination) -> Bool {
            switch (lhs, rhs) {
                case (.NoteEditor(let lhsId), .NoteEditor(let rhsId)):
                    return lhsId == rhsId
                case (.BookReader(let lB), .BookReader(let rB)):
                    return lB == rB
                case (.NoteBookReader(let lId, let lC), .NoteBookReader(let rId, let rC)):
                    return lId == rId && lC == rC
                case (.MasterEditor(let lhsId), .MasterEditor(let rhsId)):
                    return lhsId == rhsId
                // The title is decoration; two pushes of the same thread are the same destination
                case (.ChatThread(let lhsId, _), .ChatThread(let rhsId, _)):
                    return lhsId == rhsId
                default:
                    return String(describing: lhs) == String(describing: rhs)
            }
        }
        
    }
    func navigate(to destination : Destination){
        navPath.append(destination)
    }
    func navigateBack() {
        navPath.removeLast()
    }
    
    func navigateToRoot() {
        navPath.removeLast(navPath.count)
    }
}
