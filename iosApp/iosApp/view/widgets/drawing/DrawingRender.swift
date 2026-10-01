import CoreGraphics
import SwiftUI
import UIKit
import shared

final class DrawingPathCache {

    private struct Cached {
        let item: DrawRenderItem
        let path: CGPath
    }

    private var current: [ObjectIdentifier: Cached] = [:]

    private var next: [ObjectIdentifier: Cached] = [:]

    func path(of item: DrawRenderItem) -> CGPath {
        let key = ObjectIdentifier(item)
        let path = current[key]?.path ?? next[key]?.path ?? DrawingRender.path(of: item.path)
        next[key] = Cached(item: item, path: path)
        return path
    }

    func endFrame() {
        current = next
        next = [:]
    }
}

final class DrawingCommittedLayer {

    private var key: String?

    private var image: UIImage?

    func image(for newKey: String, size: CGSize, scale: CGFloat, content: (CGContext) -> Void) -> UIImage {
        if let image, key == newKey, image.size == size, image.scale == scale { return image }
        let format = UIGraphicsImageRendererFormat()
        format.scale = scale
        format.opaque = false
        let rendered = UIGraphicsImageRenderer(size: size, format: format).image { content($0.cgContext) }
        image = rendered
        key = newKey
        return rendered
    }

    func clear() {
        image = nil
        key = nil
    }
}

enum DrawingRender {

    private static let softEdge: Float = 0.95

    static func path(of data: KotlinFloatArray) -> CGPath {
        let commands = DrawCommands.shared
        let path = CGMutablePath()
        let count = Int(data.size)
        var index = 0
        while index < count {
            let command = data.get(index: Int32(index))
            if commands.isMove(command: command) {
                path.move(to: point(data, index + 1))
            } else if commands.isLine(command: command) {
                path.addLine(to: point(data, index + 1))
            } else if commands.isQuad(command: command) {
                path.addQuadCurve(to: point(data, index + 3), control: point(data, index + 1))
            } else if commands.isCubic(command: command) {
                path.addCurve(to: point(data, index + 5), control1: point(data, index + 1), control2: point(data, index + 3))
            } else if commands.isClose(command: command) {
                path.closeSubpath()
            }
            index += 1 + Int(commands.pathStride(command: command))
        }
        return path
    }

    static func draw(_ entries: [DrawRenderEntry], in context: CGContext, paths: DrawingPathCache?) {
        let commands = DrawCommands.shared
        for entry in entries {
            let item = entry.item
            context.saveGState()
            context.concatenate(entry.matrix.affine)
            if commands.isDabs(item: item) {
                drawDabs(item, in: context)
            } else {
                context.addPath(paths?.path(of: item) ?? path(of: item.path))
                context.setBlendMode(item.blend.cgBlend)
                if commands.isStroke(item: item) {
                    context.setStrokeColor(item.color.cgColor(opacity: item.opacity))
                    context.setLineWidth(CGFloat(item.strokeWidth))
                    context.setLineCap(item.cap.cgCap)
                    context.setLineJoin(item.join.cgJoin)
                    context.strokePath()
                } else {
                    context.setFillColor(item.color.cgColor(opacity: item.opacity))
                    context.fillPath()
                }
            }
            context.restoreGState()
        }
        paths?.endFrame()
    }

    private static func drawDabs(_ item: DrawRenderItem, in context: CGContext) {
        let dabs = item.dabs
        let count = Int(dabs.size)
        guard count > 0 else { return }
        let stride = Int(DrawCommands.shared.dabStride())
        let blend = item.blend.cgBlend
        let color = item.color.cgColor(opacity: 1)
        let hardness = min(max(item.hardness, 0), softEdge)
        let gradient = item.hardness < softEdge ? softGradient(color, hardness: CGFloat(hardness)) : nil
        context.saveGState()
        context.setAlpha(CGFloat(min(max(item.opacity, 0), 1)))
        context.setBlendMode(blend)
        context.beginTransparencyLayer(in: item.bounds.cgRect, auxiliaryInfo: nil)
        context.setBlendMode(.normal)
        context.setFillColor(color)
        var index = 0
        while index + stride <= count {
            let center = point(dabs, index)
            let radius = CGFloat(dabs.get(index: Int32(index + 2)))
            context.setAlpha(CGFloat(min(max(dabs.get(index: Int32(index + 3)), 0), 1)))
            if let gradient {
                context.drawRadialGradient(
                    gradient,
                    startCenter: center,
                    startRadius: 0,
                    endCenter: center,
                    endRadius: radius,
                    options: []
                )
            } else {
                context.fillEllipse(in: CGRect(x: center.x - radius, y: center.y - radius, width: radius * 2, height: radius * 2))
            }
            index += stride
        }
        context.setBlendMode(blend)
        context.endTransparencyLayer()
        context.restoreGState()
    }

    private static func softGradient(_ color: CGColor, hardness: CGFloat) -> CGGradient? {
        let clear = color.copy(alpha: 0) ?? color
        return CGGradient(
            colorsSpace: CGColorSpace(name: CGColorSpace.sRGB),
            colors: [color, color, clear] as CFArray,
            locations: [0, hardness, 1]
        )
    }

    private static func point(_ data: KotlinFloatArray, _ index: Int) -> CGPoint {
        CGPoint(x: CGFloat(data.get(index: Int32(index))), y: CGFloat(data.get(index: Int32(index + 1))))
    }
}

extension DrawMatrix {
    var affine: CGAffineTransform {
        CGAffineTransform(a: CGFloat(a), b: CGFloat(b), c: CGFloat(c), d: CGFloat(d), tx: CGFloat(tx), ty: CGFloat(ty))
    }
}

extension DrawColor {
    private var channels: (red: CGFloat, green: CGFloat, blue: CGFloat, alpha: CGFloat) {
        let value = UInt32(bitPattern: argbInt)
        return (
            red: CGFloat((value >> 16) & 0xFF) / 255,
            green: CGFloat((value >> 8) & 0xFF) / 255,
            blue: CGFloat(value & 0xFF) / 255,
            alpha: CGFloat((value >> 24) & 0xFF) / 255
        )
    }

    func cgColor(opacity: Float) -> CGColor {
        let rgba = channels
        let alpha = min(max(rgba.alpha * CGFloat(opacity), 0), 1)
        return CGColor(srgbRed: rgba.red, green: rgba.green, blue: rgba.blue, alpha: alpha)
    }

    var swiftUI: Color {
        let rgba = channels
        return Color(.sRGB, red: Double(rgba.red), green: Double(rgba.green), blue: Double(rgba.blue), opacity: Double(rgba.alpha))
    }
}

extension Color {
    var drawColor: DrawColor {
        let rgba = UIColor(self).rgba ?? (r: 0, g: 0, b: 0, a: 1)
        let channel: (CGFloat) -> Int32 = { Int32((min(max($0, 0), 1) * 255).rounded()) }
        return DrawCommands.shared.colorFromRgb(
            red: channel(rgba.r),
            green: channel(rgba.g),
            blue: channel(rgba.b),
            opacity: Float(min(max(rgba.a, 0), 1))
        )
    }
}

extension DrawBlend {
    var cgBlend: CGBlendMode {
        switch self {
        case DrawBlend.multiply: return .multiply
        case DrawBlend.screen: return .screen
        case DrawBlend.overlay: return .overlay
        case DrawBlend.softLight: return .softLight
        case DrawBlend.hardLight: return .hardLight
        case DrawBlend.darken: return .darken
        case DrawBlend.lighten: return .lighten
        case DrawBlend.color: return .color
        case DrawBlend.difference: return .difference
        case DrawBlend.clear: return .clear
        default: return .normal
        }
    }
}

extension DrawCap {
    var cgCap: CGLineCap {
        switch self {
        case DrawCap.butt: return .butt
        case DrawCap.square: return .square
        default: return .round
        }
    }
}

extension DrawJoin {
    var cgJoin: CGLineJoin {
        switch self {
        case DrawJoin.miter: return .miter
        case DrawJoin.bevel: return .bevel
        default: return .round
        }
    }
}
