import Foundation

/// Продукт EvOn для экрана «Наши продукты».
struct EvonProduct: Codable, Identifiable, Equatable {
    let id: String
    let title: String
    let description: String
    let iconUrl: String?
    let url: String

    enum CodingKeys: String, CodingKey {
        case id, title, description, url
        case iconUrl = "icon_url"
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        let ident = try c.decode(String.self, forKey: .id)
        id = ident
        let t = ((try? c.decodeIfPresent(String.self, forKey: .title)) ?? nil) ?? ""
        title = t.isEmpty ? ident : t
        description = ((try? c.decodeIfPresent(String.self, forKey: .description)) ?? nil) ?? ""
        let icon = ((try? c.decodeIfPresent(String.self, forKey: .iconUrl)) ?? nil)?.trimmingCharacters(in: .whitespaces)
        iconUrl = (icon?.isEmpty == false) ? icon : nil
        url = (((try? c.decodeIfPresent(String.self, forKey: .url)) ?? nil) ?? "").trimmingCharacters(in: .whitespaces)
    }
}

/// Список «Наши продукты» — общий для всех программ EvOn, живёт на сервере AppsMarket
/// (apps.evon.uz, не наш бэкенд leapmotor.evon.uz). Публичный, без токена и VIN.
///
/// Последний ответ лежит в Application Support отдельно по языку: экран показывает его
/// сразу (в том числе без сети), а запрос идёт с If-None-Match — неизменный список
/// сервер отвечает 304 без тела. Двойник `data/EvonProducts.kt` на Android.
enum EvonProducts {
    private static let base = "https://apps.evon.uz/api/v1/products"
    private static let d = UserDefaults.standard

    private struct Envelope: Decodable { let products: [EvonProduct] }

    private static func file(_ lang: String) -> URL {
        let dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir.appendingPathComponent("evon-products-\(lang).json")
    }

    private static func parse(_ data: Data) throws -> [EvonProduct] {
        var seen = Set<String>()
        // id — ключ ForEach, повторы отбрасываем
        return try JSONDecoder().decode(Envelope.self, from: data).products.filter { seen.insert($0.id).inserted }
    }

    /// Сохранённый список на языке `lang` (пусто, если ещё не качали).
    static func cached(lang: String) -> [EvonProduct] {
        guard let data = try? Data(contentsOf: file(lang)) else { return [] }
        return (try? parse(data)) ?? []
    }

    /// Свежий список с сервера. 304 — остаётся сохранённый. Ошибка сети — исключение
    /// (экран покажет «нет связи» поверх кэша).
    static func refresh(lang: String) async throws -> [EvonProduct] {
        guard let url = URL(string: "\(base)?lang=\(lang)&platform=phone") else { throw URLError(.badURL) }
        var req = URLRequest(url: url, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 15)
        let f = file(lang)
        if FileManager.default.fileExists(atPath: f.path), let tag = d.string(forKey: "evonProductsEtag-\(lang)") {
            req.setValue(tag, forHTTPHeaderField: "If-None-Match")
        }
        let (data, resp) = try await URLSession.shared.data(for: req)
        let code = (resp as? HTTPURLResponse)?.statusCode ?? 0
        if code == 304 { return cached(lang: lang) }
        guard (200..<300).contains(code) else { throw URLError(.badServerResponse) }
        let items = try parse(data)   // разобрать до записи: битый ответ не затрёт кэш
        try? data.write(to: f, options: .atomic)
        d.set((resp as? HTTPURLResponse)?.value(forHTTPHeaderField: "ETag"), forKey: "evonProductsEtag-\(lang)")
        return items
    }
}
