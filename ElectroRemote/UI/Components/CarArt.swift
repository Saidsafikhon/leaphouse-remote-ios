import SwiftUI

/// Рендеры машин по модели и цвету кузова — студийные вырезки с прозрачным фоном
/// (конфигуратор leapmotor.uz, asset `car_<model>_<цвет>`), зеркало `CarArt.kt`.
///
/// Модель приходит с сервера (`VehicleDto.model`), цвет — локальная настройка
/// телефона: сервер цвет кузова не знает.
enum CarArt {
    /// Цвет кузова: код как на сайте, подпись для настроек и образец для кружка.
    struct Paint: Identifiable, Equatable {
        let code: String
        /// Русский ключ; подпись переводится при чтении, чтобы смена языка подхватывалась.
        let labelKey: String
        let swatch: Color
        var label: String { L(labelKey) }
        var id: String { code }
    }

    private static let paints: [String: Paint] = [
        "pearl-white": Paint(code: "pearl-white", labelKey: "Жемчужно-белый", swatch: Color(hex: 0xE9EAEC)),
        "metallic-black": Paint(code: "metallic-black", labelKey: "Чёрный металлик", swatch: Color(hex: 0x1B1D21)),
        "canopy-gray": Paint(code: "canopy-gray", labelKey: "Серый", swatch: Color(hex: 0x8E9296)),
        "jade-green": Paint(code: "jade-green", labelKey: "Глазурно-зелёный", swatch: Color(hex: 0x2E5B45)),
        "galaxy-silver": Paint(code: "galaxy-silver", labelKey: "Серебристый", swatch: Color(hex: 0xB8BCC2)),
        "glacier-blue": Paint(code: "glacier-blue", labelKey: "Ледниковый голубой", swatch: Color(hex: 0xB6CDE0)),
        "walden-green": Paint(code: "walden-green", labelKey: "Тёмно-зелёный", swatch: Color(hex: 0x2F4A3C)),
        "seaweed-green": Paint(code: "seaweed-green", labelKey: "Зелёный", swatch: Color(hex: 0x3D6B4A)),
        "light-white": Paint(code: "light-white", labelKey: "Белый", swatch: Color(hex: 0xF2F3F5)),
        "sakura-pink": Paint(code: "sakura-pink", labelKey: "Сакура розовый", swatch: Color(hex: 0xE8C4C8)),
        "acorn-brown": Paint(code: "acorn-brown", labelKey: "Ореховый коричневый", swatch: Color(hex: 0x6E5A4E)),
        "berry-blue": Paint(code: "berry-blue", labelKey: "Ягодный синий", swatch: Color(hex: 0x3E5A8A)),
        "star-purple": Paint(code: "star-purple", labelKey: "Звёздный фиолетовый", swatch: Color(hex: 0x5A4A6E)),
        "tundra-grey": Paint(code: "tundra-grey", labelKey: "Тундра-серый", swatch: Color(hex: 0xC9C6C0)),
        "sky-grey": Paint(code: "sky-grey", labelKey: "Небесно-серый", swatch: Color(hex: 0x4C5157)),
        "dawn-purple": Paint(code: "dawn-purple", labelKey: "Рассветный фиолетовый", swatch: Color(hex: 0x4A3550)),
        "starry-night-blue": Paint(code: "starry-night-blue", labelKey: "Звёздная ночь (синий)", swatch: Color(hex: 0x4A5F85)),
        "oxygen-green": Paint(code: "oxygen-green", labelKey: "Свежий зелёный", swatch: Color(hex: 0x9FA88A)),
        "night-blue": Paint(code: "night-blue", labelKey: "Ночной синий", swatch: Color(hex: 0x1C2440)),
        "coral-orange": Paint(code: "coral-orange", labelKey: "Коралловый оранжевый", swatch: Color(hex: 0xE8452A)),
        "rime-beige": Paint(code: "rime-beige", labelKey: "Бежевый иней", swatch: Color(hex: 0xE9E2D2)),
        "cloud-gold": Paint(code: "cloud-gold", labelKey: "Облачное золото", swatch: Color(hex: 0xC9B99A)),
        "pine-grey": Paint(code: "pine-grey", labelKey: "Сосновый серый", swatch: Color(hex: 0x9DA8AC)),
        "liquid-silver": Paint(code: "liquid-silver", labelKey: "Жидкое серебро", swatch: Color(hex: 0xC4C6CB)),
        "cloud-purple": Paint(code: "cloud-purple", labelKey: "Дымчато-фиолетовый", swatch: Color(hex: 0x9C97B0)),
        "morgan-pink": Paint(code: "morgan-pink", labelKey: "Розовый Морган", swatch: Color(hex: 0xD9B8B0)),
    ]

    /// Какие цвета есть у модели — ровно те, на которые есть рендер (порядок = порядок кружков).
    private static let art: [String: [String]] = [
        "C16": ["pearl-white", "metallic-black", "canopy-gray", "jade-green", "glacier-blue"],
        "C10": ["pearl-white", "metallic-black", "canopy-gray", "tundra-grey", "jade-green"],
        "C11": ["light-white", "rime-beige", "metallic-black", "canopy-gray", "pine-grey", "galaxy-silver", "cloud-gold", "walden-green"],
        "C01": ["light-white", "metallic-black", "canopy-gray", "galaxy-silver", "oxygen-green", "night-blue", "coral-orange", "glacier-blue"],
        "B01": ["light-white", "metallic-black", "tundra-grey", "galaxy-silver", "liquid-silver", "starry-night-blue", "cloud-purple", "morgan-pink"],
        "B10": ["pearl-white", "metallic-black", "tundra-grey", "galaxy-silver", "starry-night-blue", "dawn-purple", "sakura-pink"],
        "A10": ["seaweed-green", "galaxy-silver", "tundra-grey", "berry-blue", "acorn-brown", "star-purple"],
        "D19": ["metallic-black", "pearl-white", "sky-grey", "jade-green"],
    ]
    private static let order = ["C16", "C10", "C11", "C01", "B10", "B01", "A10", "D19"]

    /// Нормализуем «Leapmotor C16», «c16 2025», «C16» → «C16».
    private static func key(_ model: String?) -> String {
        let m = (model ?? "").uppercased()
        return order.first { m.contains($0) } ?? "C16"
    }

    static func paints(_ model: String?) -> [Paint] {
        (art[key(model)] ?? []).compactMap { paints[$0] }
    }

    /// Имя ассета модели в выбранном цвете; нет такого — жемчужно-белый или первый.
    static func imageName(_ model: String?, _ paint: String?) -> String {
        let k = key(model)
        let colors = art[k] ?? ["pearl-white"]
        let c = (paint.flatMap { colors.contains($0) ? $0 : nil }) ?? (colors.contains("pearl-white") ? "pearl-white" : colors[0])
        return "car_\(k.lowercased())_\(c.replacingOccurrences(of: "-", with: "_"))"
    }
}
