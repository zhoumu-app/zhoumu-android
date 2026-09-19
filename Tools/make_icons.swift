// 生成 Android 需要的各密度图标。
//
// 用法：swift Tools/make_icons.swift <res 目录>
//
// 和 iOS 版用同一套画法（蓝色进度环 + 中间的「周」），保证两端看起来是同一个 App。
// 输出两类：
//   ic_launcher.png             传统图标（自带深色底，给 Android 7 及以下）
//   ic_launcher_foreground.png  自适应图标的前景（透明底，给 Android 8+）
import CoreGraphics
import CoreText
import Foundation
import ImageIO

let bgHex: UInt32 = 0x05080F        // 深色黑蓝底
let trackHex: UInt32 = 0x17253D     // 环的底
let accentHex: UInt32 = 0x4C8DFF    // 主色

func rgb(_ hex: UInt32, _ a: CGFloat = 1) -> CGColor {
    CGColor(srgbRed: CGFloat((hex >> 16) & 0xFF) / 255,
            green: CGFloat((hex >> 8) & 0xFF) / 255,
            blue: CGFloat(hex & 0xFF) / 255,
            alpha: a)
}

/// 画一个图标。
/// - Parameters:
///   - side: 画布边长
///   - opaque: true = 带深色底（传统图标）；false = 透明底（自适应前景）
///   - logoScale: logo 占画布的比例。自适应前景要留安全区，所以小一些。
func drawIcon(side: Int, opaque: Bool, logoScale: CGFloat) -> CGImage? {
    let s = CGFloat(side)
    guard let ctx = CGContext(data: nil, width: side, height: side,
                              bitsPerComponent: 8, bytesPerRow: 0,
                              space: CGColorSpaceCreateDeviceRGB(),
                              bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else { return nil }

    if opaque {
        ctx.setFillColor(rgb(bgHex))
        ctx.fill(CGRect(x: 0, y: 0, width: s, height: s))
        // 一层很淡的中心光晕，避免整块死黑
        for i in stride(from: 14, through: 1, by: -1) {
            let t = CGFloat(i) / 14
            let r = s * 0.62 * t
            ctx.setFillColor(rgb(accentHex, 0.022 * (1 - t) + 0.010))
            ctx.fillEllipse(in: CGRect(x: s/2 - r, y: s/2 - r, width: r*2, height: r*2))
        }
    }

    // logo 的尺寸
    let box = s * logoScale
    let center = CGPoint(x: s/2, y: s/2)
    let lineWidth = box * 0.155
    let radius = (box - lineWidth) / 2
    let ringRect = CGRect(x: center.x - radius, y: center.y - radius,
                          width: radius * 2, height: radius * 2)

    // 轨道
    ctx.setStrokeColor(rgb(trackHex))
    ctx.setLineWidth(lineWidth)
    ctx.strokeEllipse(in: ringRect)

    // 进度弧：走 5/7，和 iOS 图标一致
    ctx.setStrokeColor(rgb(accentHex))
    ctx.setLineWidth(lineWidth)
    ctx.setLineCap(.round)
    ctx.addArc(center: center, radius: radius,
               startAngle: .pi / 2, endAngle: .pi / 2 - 2 * .pi * (5.0 / 7.0),
               clockwise: true)
    ctx.strokePath()

    // 中间的「周」
    let font = CTFontCreateWithName("PingFangSC-Semibold" as CFString, box * 0.46, nil)
    let attrs: [CFString: Any] = [
        kCTFontAttributeName: font,
        kCTForegroundColorAttributeName: rgb(accentHex),
    ]
    guard let attributed = CFAttributedStringCreate(nil, "周" as CFString, attrs as CFDictionary) else { return nil }
    let line = CTLineCreateWithAttributedString(attributed)
    let b = CTLineGetBoundsWithOptions(line, .useOpticalBounds)
    ctx.textPosition = CGPoint(x: center.x - b.width/2 - b.origin.x,
                               y: center.y - b.height/2 - b.origin.y)
    CTLineDraw(line, ctx)

    return ctx.makeImage()
}

func write(_ image: CGImage, to path: String) {
    let url = URL(fileURLWithPath: path)
    guard let dest = CGImageDestinationCreateWithURL(url as CFURL, "public.png" as CFString, 1, nil) else { return }
    CGImageDestinationAddImage(dest, image, nil)
    CGImageDestinationFinalize(dest)
}

let resDir = CommandLine.arguments.count > 1 ? CommandLine.arguments[1] : "app/src/main/res"

// 密度 → (传统图标尺寸, 自适应前景尺寸)
// 自适应前景是 108dp，各密度分别是 108/162/216/324/432
let densities: [(String, Int, Int)] = [
    ("mdpi",     48,  108),
    ("hdpi",     72,  162),
    ("xhdpi",    96,  216),
    ("xxhdpi",  144,  324),
    ("xxxhdpi", 192,  432),
]

var made = 0
for (name, legacy, adaptive) in densities {
    let dir = "\(resDir)/mipmap-\(name)"
    try? FileManager.default.createDirectory(atPath: dir, withIntermediateDirectories: true)

    // 传统图标：满幅，系统会自己切形状
    if let img = drawIcon(side: legacy, opaque: true, logoScale: 0.62) {
        write(img, to: "\(dir)/ic_launcher.png")
        write(img, to: "\(dir)/ic_launcher_round.png")
        made += 2
    }
    // 自适应前景：透明底，logo 缩到 66/108（安全区）以内
    if let img = drawIcon(side: adaptive, opaque: false, logoScale: 66.0 / 108.0 * 0.92) {
        write(img, to: "\(dir)/ic_launcher_foreground.png")
        made += 1
    }
}
print("已生成 \(made) 个图标文件到 \(resDir)")
