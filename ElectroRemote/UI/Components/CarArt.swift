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
        let label: String
        let swatch: Color
        var id: String { code }
    }

    private static let paints: [String: Paint] = [
        "pearl-white": Paint(code: "pearl-white", label: "Жемчужно-белый", swatch: Color(hex: 0xE9EAEC)),
        "metallic-black": Paint(code: "metallic-black", label: "Чёрный металлик", swatch: Color(hex: 0x1B1D21)),
        "canopy-gray": Paint(code: "canopy-gray", label: "Серый", swatch: Color(hex: 0x8E9296)),
        "jade-green": Paint(code: "jade-green", label: "Глазурно-зелёный", swatch: Color(hex: 0x2E5B45)),
        "galaxy-silver": Paint(code: "galaxy-silver", label: "Серебристый", swatch: Color(hex: 0xB8BCC2)),
        "glacier-blue": Paint(code: "glacier-blue", label: "Ледниковый голубой", swatch: Color(hex: 0xB6CDE0)),
        "walden-green": Paint(code: "walden-green", label: "Тёмно-зелёный", swatch: Color(hex: 0x2F4A3C)),
        "seaweed-green": Paint(code: "seaweed-green", label: "Зелёный", swatch: Color(hex: 0x3D6B4A)),
        "light-white": Paint(code: "light-white", label: "Белый", swatch: Color(hex: 0xF2F3F5)),
        "sakura-pink": Paint(code: "sakura-pink", label: "Сакура розовый", swatch: Color(hex: 0xE8C4C8)),
        "acorn-brown": Paint(code: "acorn-brown", label: "Ореховый коричневый", swatch: Color(hex: 0x6E5A4E)),
        "berry-blue": Paint(code: "berry-blue", label: "Ягодный синий", swatch: Color(hex: 0x3E5A8A)),
        "star-purple": Paint(code: "star-purple", label: "Звёздный фиолетовый", swatch: Color(hex: 0x5A4A6E)),
        "tundra-grey": Paint(code: "tundra-grey", label: "Тундра-серый", swatch: Color(hex: 0xC9C6C0)),
        "sky-grey": Paint(code: "sky-grey", label: "Небесно-серый", swatch: Color(hex: 0x4C5157)),
        "dawn-purple": Paint(code: "dawn-purple", label: "Рассветный фиолетовый", swatch: Color(hex: 0x4A3550)),
        "starry-night-blue": Paint(code: "starry-night-blue", label: "Звёздная ночь (синий)", swatch: Color(hex: 0x4A5F85)),
    ]

    /// Какие цвета есть у модели — ровно те, на которые есть рендер (порядок = порядок кружков).
    private static let art: [String: [String]] = [
        "C16": ["pearl-white", "metallic-black", "canopy-gray", "jade-green", "glacier-blue"],
        "C10": ["pearl-white", "metallic-black", "canopy-gray", "tundra-grey", "jade-green"],
        "C11": ["pearl-white", "metallic-black", "canopy-gray", "galaxy-silver", "walden-green", "light-white"],
        "C01": ["metallic-black", "galaxy-silver", "walden-green"],
        "B10": ["pearl-white", "metallic-black", "tundra-grey", "galaxy-silver", "starry-night-blue", "dawn-purple", "sakura-pink"],
        "A10": ["seaweed-green", "galaxy-silver", "tundra-grey", "berry-blue", "acorn-brown", "star-purple"],
        "D19": ["metallic-black", "pearl-white", "sky-grey", "jade-green"],
    ]
    private static let order = ["C16", "C10", "C11", "C01", "B10", "A10", "D19"]

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
