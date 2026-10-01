import Foundation

enum DrawingIcons {

    private static let penKey = "tool_pen"

    private static let eraserKey = "tool_eraser"

    private static let shapeKey = "tool_shape"

    private static let handKey = "tool_hand"

    static let done = "checkmark"

    static let undo = "arrow.uturn.backward"

    static let redo = "arrow.uturn.forward"

    static let zoomOut = "minus.magnifyingglass"

    static let zoomIn = "plus.magnifyingglass"

    static let fit = "viewfinder"

    static let resetRotation = "safari"

    static let paper = "squareshape.split.3x3"

    static let settings = "slider.horizontal.3"

    static let favorite = "star.fill"

    static let notFavorite = "star"

    static let delete = "trash"

    static let draw = "pencil.tip.crop.circle"

    static func tool(_ iconKey: String) -> String {
        switch iconKey {
        case penKey: return "pencil.tip"
        case eraserKey: return "eraser"
        case shapeKey: return "square.on.circle"
        case handKey: return "hand.raised"
        default: return "pencil.tip"
        }
    }
}
