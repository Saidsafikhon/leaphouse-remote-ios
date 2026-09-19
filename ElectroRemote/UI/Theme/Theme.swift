import SwiftUI

/// Токены цвета Electro Remote — один в один с Android (`ui/theme/Color.kt`)
/// и коллекцией переменных `Electro` в Figma.
struct ElectroPalette {
    // поверхности
    let background: Color
    let surface: Color
    let surfaceElevated: Color
    let surfacePressed: Color
    let surfaceRaised: Color
    let outline: Color

    // акцент
    let accent: Color
    let accentPressed: Color
    let accentSoft: Color
    let onAccent: Color

    // текст
    let textPrimary: Color
    let textSecondary: Color
    let textMuted: Color
    let textDisabled: Color

    // статусы
    let ok: Color
    let warn: Color
    let danger: Color
    let info: Color

    // подложки статусных плашек
    let okTint: Color
    let warnTint: Color
    let dangerTint: Color
    let infoTint: Color

    /// Ночной набор: поверхности почти чёрные, акцент лаймовый.
    static let dark = ElectroPalette(
        background: Color(hex: 0x070A0D),
        surface: Color(hex: 0x11161A),
        surfaceElevated: Color(hex: 0x171E23),
        surfacePressed: Color(hex: 0x1F282E),
        surfaceRaised: Color(hex: 0x26313A),
        outline: Color(hex: 0x212A30),
        accent: Color(hex: 0x7CFF3B),
        accentPressed: Color(hex: 0x6BE032),
        accentSoft: Color(hex: 0x172C14),
        onAccent: Color(hex: 0x08130A),
        textPrimary: Color(hex: 0xF5F7F8),
        textSecondary: Color(hex: 0xA7ADB3),
        textMuted: Color(hex: 0x7B838B),
        textDisabled: Color(hex: 0x3A4249),
        ok: Color(hex: 0x22C55E),
        warn: Color(hex: 0xF0A73C),
        danger: Color(hex: 0xFF5A52),
        info: Color(hex: 0x4EA8F5),
        okTint: Color(hex: 0x0C2418),
        warnTint: Color(hex: 0x2A2010),
        dangerTint: Color(hex: 0x2B1414),
        infoTint: Color(hex: 0x0E2033)
    )

    /// Дневной набор: акцент травяной, статусы притемнены, подложки высветлены.
    static let light = ElectroPalette(
        background: Color(hex: 0xF2F5F7),
        surface: Color(hex: 0xFFFFFF),
        surfaceElevated: Color(hex: 0xE8EDF0),
        surfacePressed: Color(hex: 0xDCE3E7),
        surfaceRaised: Color(hex: 0xCFD8DE),
        outline: Color(hex: 0xCBD4DA),
        accent: Color(hex: 0x2F7D14),
        accentPressed: Color(hex: 0x25620F),
        accentSoft: Color(hex: 0xE3F5D7),
        onAccent: Color(hex: 0xFFFFFF),
        textPrimary: Color(hex: 0x0C1114),
        textSecondary: Color(hex: 0x49535A),
        textMuted: Color(hex: 0x667079),
        textDisabled: Color(hex: 0xA9B2B9),
        ok: Color(hex: 0x15803D),
        warn: Color(hex: 0x9A5B08),
        danger: Color(hex: 0xC62828),
        info: Color(hex: 0x1565C0),
        okTint: Color(hex: 0xDFF3E5),
        warnTint: Color(hex: 0xFAEEDB),
        dangerTint: Color(hex: 0xFBE4E2),
        infoTint: Color(hex: 0xE1EDFA)
    )
}

extension Color {
    init(hex: UInt32, alpha: Double = 1) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: alpha
        )
    }
}

/// Тему выбирает устройство (системная тёмная тема), а не приложение.
private struct PaletteKey: EnvironmentKey {
    static let defaultValue: ElectroPalette = .dark
}

extension EnvironmentValues {
    var palette: ElectroPalette {
        get { self[PaletteKey.self] }
        set { self[PaletteKey.self] = newValue }
    }
}

/// Шкала из 8 ступеней — та же, что текстовыми стилями в Figma.
enum ElectroType {
    /// Фирменный шрифт LeapRemote (Unbounded) — словесный знак и крупные величины.
    /// PostScript-имена из TTF: `Unbounded-Light`, `Unbounded-ExtraBold`.
    static func brandLight(_ size: CGFloat) -> Font { .custom("Unbounded-Light", size: size) }
    static func brandBold(_ size: CGFloat) -> Font { .custom("Unbounded-ExtraBold", size: size) }
    /// Модель автомобиля, температура — одна крупная величина на экран.
    static let display = brandLight(32)
    static let title = Font.system(size: 26, weight: .light)
    static let headline = Font.system(size: 19, weight: .regular)
    /// Значение в полосе состояния: «520 км», «85 %».
    static let value = Font.system(size: 16, weight: .medium)
    static let body = Font.system(size: 14, weight: .regular)
    static let label = Font.system(size: 12, weight: .regular)
    static let caption = Font.system(size: 12, weight: .regular)
    /// Марка над моделью, заголовки секций — единственное, что набрано капсом.
    static let overline = Font.system(size: 11, weight: .bold)
    /// Единица измерения рядом с числом.
    static let unit = Font.system(size: 11, weight: .regular)
}

/// Шаг сетки.
enum Space {
    static let x1: CGFloat = 4
    static let x2: CGFloat = 8
    static let x3: CGFloat = 12
    static let x4: CGFloat = 16
    static let x5: CGFloat = 20
    static let x6: CGFloat = 24
    static let x8: CGFloat = 32
}

/// Радиусы.
enum Radius {
    static let sm: CGFloat = 12
    static let md: CGFloat = 16
    static let lg: CGFloat = 20
    static let xl: CGFloat = 28
    static let pill: CGFloat = 999
}

/// Высоты контролов.
enum ControlSize {
    static let tile: CGFloat = 88
    static let chip: CGFloat = 44
    static let round: CGFloat = 44
    static let button: CGFloat = 48
}

/// Публичные страницы сайта: политика конфиденциальности и поддержка.
enum Links {
    static let privacy = URL(string: "https://leapmotor.evon.uz/privacy")!
    static let support = URL(string: "https://leapmotor.evon.uz/support")!
}

/// Строка версии для подвалов: «LeapRemote 0.38.19 (75)».
func appVersion() -> String {
    let info = Bundle.main.infoDictionary
    let v = info?["CFBundleShortVersionString"] as? String ?? "?"
    let b = info?["CFBundleVersion"] as? String ?? "?"
    return "LeapRemote \(v) (\(b))"
}
