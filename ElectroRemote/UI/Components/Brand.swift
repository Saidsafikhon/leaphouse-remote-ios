import SwiftUI

/// Знак LeapRemote: эмблема владельца («L + машина + сигнал», asset `brand_glyph`,
/// template) на плашке цвета кнопок (акцент темы). `size` — сторона плашки.
struct BrandMark: View {
    @Environment(\.palette) private var p
    let size: CGFloat

    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: size * 0.24, style: .continuous).fill(p.accent)
            Image("brand_glyph").resizable().renderingMode(.template).scaledToFit()
                .foregroundStyle(p.onAccent)
                .frame(width: size * 0.78, height: size * 0.78)
        }
        .frame(width: size, height: size)
        .accessibilityLabel("LeapRemote")
    }
}

/// Словесный знак: «Leap» жирным, «Remote» тонким, шрифт Unbounded — как на
/// странице Brand в Figma. Цвет один на оба слова, чтобы читалось как имя.
struct BrandWordmark: View {
    @Environment(\.palette) private var p
    let fontSize: CGFloat
    var color: Color? = nil

    var body: some View {
        (Text("Leap").font(ElectroType.brandBold(fontSize)) + Text("Remote").font(ElectroType.brandLight(fontSize)))
            .kerning(-0.03 * fontSize)
            .foregroundStyle(color ?? p.textPrimary)
            .lineLimit(1)
            .fixedSize()
    }
}

/// Знак + слово в строку: шапки экранов и логин. `markSize` задаёт масштаб.
struct BrandLockup: View {
    let markSize: CGFloat
    var body: some View {
        HStack(spacing: markSize * 0.28) {
            BrandMark(size: markSize)
            BrandWordmark(fontSize: markSize * 0.58)
        }
    }
}
