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

    init(id: String, title: String, description: String, iconUrl: String?, url: String) {
        self.id = id; self.title = title; self.description = description; self.iconUrl = iconUrl; self.url = url
    }

    /// Та же карточка, но иконка — файл на диске.
    func withIcon(_ local: URL) -> EvonProduct {
        EvonProduct(id: id, title: title, description: description, iconUrl: local.absoluteString, url: url)
    }
}

/// Список «Наши продукты» — общий для всех программ EvOn, живёт на сервере AppsMarket
/// (apps.evon.uz, не наш бэкенд leapmotor.evon.uz). Публичный, без токена и VIN.
///
/// Последний ответ лежит в Application Support отдельно по языку, иконки — файлами:
/// экран показывает всё сразу (в том числе без сети). С сервером сверяемся не чаще
/// раза в сутки (`freshFor`) и с If-None-Match — неизменный список сервер отвечает 304.
/// Двойник `data/EvonProducts.kt` на Android.
enum EvonProducts {
    private static let base = "https://apps.evon.uz/api/v1/products"
    private static let d = UserDefaults.standard
    /// Сверка со списком на сервере — не чаще раза в сутки («потянуть вниз» — сразу).
    static let freshFor: TimeInterval = 24 * 60 * 60

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

    /// Список с диска как есть — иконки с адресами сервера.
    private static func stored(lang: String) -> [EvonProduct] {
        guard let data = try? Data(contentsOf: file(lang)) else { return [] }
        return (try? parse(data)) ?? []
    }

    /// Сохранённый список на языке `lang` (пусто, если ещё не качали); иконки — с диска, где есть.
    static func cached(lang: String) -> [EvonProduct] { withLocalIcons(stored(lang: lang)) }

    /// Актуальный список. Если копия свежая и не просили `force` — с диска, без сети.
    /// Иначе запрос с If-None-Match: 304 — остаётся сохранённый. Ошибка сети — исключение
    /// (экран покажет «нет связи» поверх кэша). Иконки докачиваются на диск.
    static func refresh(lang: String, force: Bool = false) async throws -> [EvonProduct] {
        let f = file(lang)
        let exists = FileManager.default.fileExists(atPath: f.path)
        let at = d.double(forKey: "evonProductsAt-\(lang)")
        if !force, exists, Date().timeIntervalSince1970 - at < freshFor {
            return await withIcons(stored(lang: lang), force: false)
        }
        guard let url = URL(string: "\(base)?lang=\(lang)&platform=phone") else { throw URLError(.badURL) }
        var req = URLRequest(url: url, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 15)
        if exists, let tag = d.string(forKey: "evonProductsEtag-\(lang)") {
            req.setValue(tag, forHTTPHeaderField: "If-None-Match")
        }
        let (data, resp) = try await URLSession.shared.data(for: req)
        let code = (resp as? HTTPURLResponse)?.statusCode ?? 0
        let items: [EvonProduct]
        if code == 304 {
            items = stored(lang: lang)
        } else {
            guard (200..<300).contains(code) else { throw URLError(.badServerResponse) }
            items = try parse(data)   // разобрать до записи: битый ответ не затрёт кэш
            try? data.write(to: f, options: .atomic)
            d.set((resp as? HTTPURLResponse)?.value(forHTTPHeaderField: "ETag"), forKey: "evonProductsEtag-\(lang)")
        }
        d.set(Date().timeIntervalSince1970, forKey: "evonProductsAt-\(lang)")
        return await withIcons(items, force: true)
    }

    // --- иконки на диске ---

    private static func iconFile(_ id: String) -> URL {
        let dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("evon-product-icons", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        let safe = String(id.map { ($0.isLetter || $0.isNumber || $0 == "-" || $0 == "_") ? $0 : "_" })
        return dir.appendingPathComponent(safe)
    }

    private static func hasIcon(_ id: String) -> Bool {
        let size = ((try? FileManager.default.attributesOfItem(atPath: iconFile(id).path))?[.size] as? NSNumber)?.intValue ?? 0
        return size > 0
    }

    /// Список, где у продуктов с сохранённой иконкой адрес — файл на диске.
    private static func withLocalIcons(_ list: [EvonProduct]) -> [EvonProduct] {
        list.map { p in (p.iconUrl != nil && hasIcon(p.id)) ? p.withIcon(iconFile(p.id)) : p }
    }

    /// Иконки качаются один раз и лежат файлами; сверяются (If-None-Match) только вместе
    /// со списком — то есть тоже не чаще раза в сутки. Без сети — что есть.
    private static func withIcons(_ list: [EvonProduct], force: Bool) async -> [EvonProduct] {
        for p in list {
            guard let s = p.iconUrl, let url = URL(string: s), url.scheme?.hasPrefix("http") == true else { continue }
            let have = hasIcon(p.id)
            if have && !force { continue }
            var req = URLRequest(url: url, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 15)
            if have, let tag = d.string(forKey: "evonIconEtag-\(p.id)") {
                req.setValue(tag, forHTTPHeaderField: "If-None-Match")
            }
            guard let got = try? await URLSession.shared.data(for: req),
                  let http = got.1 as? HTTPURLResponse, (200..<300).contains(http.statusCode), !got.0.isEmpty
            else { continue }   // 304 или нет сети — оставляем, что лежит
            try? got.0.write(to: iconFile(p.id), options: .atomic)
            d.set(http.value(forHTTPHeaderField: "ETag"), forKey: "evonIconEtag-\(p.id)")
        }
        return withLocalIcons(list)
    }
}
