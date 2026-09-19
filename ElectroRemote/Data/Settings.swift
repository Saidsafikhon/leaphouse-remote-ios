import Foundation

/// Единственное место, где живут настройки подключения — зеркало
/// `data/Settings.kt` Android-версии. Хранилище — UserDefaults: ключи те же,
/// что в SharedPreferences, чтобы описания в документации совпадали.
final class Settings {
    static let shared = Settings()

    private let d = UserDefaults.standard

    private enum K {
        static let cloudUrl = "cloudUrl"
        static let token = "token"
        static let email = "email"
        static let vehicle = "vehicleId"
        static let cloudOn = "cloudEnabled"
        static let nickPrefix = "nick_"
        static let paintPrefix = "paint_"
        static let seatPreset = "seatPreset"
        static let optimistic = "optimistic"
    }

    static let defaultCloudURL = "https://leapmotor.evon.uz/"

    /// Адреса, с которых уводим на текущий сервер: прежний боевой по IP (по IP
    /// сертификат не проходит проверку имени), старый домен и стенд.
    private static let legacyCloudURLs: Set<String> = [
        "http://62.171.159.63/", "http://62.171.159.63",
        "https://62.171.159.63/", "https://62.171.159.63",
        "http://10.230.1.52:8000/", "http://10.230.1.52:8000",
        "https://electro.rsgstudy.uz/", "https://electro.rsgstudy.uz",
        "http://electro.rsgstudy.uz/", "http://electro.rsgstudy.uz",
    ]

    /// Базовый адрес backend, всегда со схемой и слэшем на конце.
    var cloudUrl: String {
        get {
            let saved = (d.string(forKey: K.cloudUrl) ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
            if saved.isEmpty || Self.legacyCloudURLs.contains(saved) { return Self.defaultCloudURL }
            return saved
        }
        set { d.set(Self.normalizeUrl(newValue), forKey: K.cloudUrl) }
    }

    var token: String? {
        get { d.string(forKey: K.token) }
        set { d.set(newValue, forKey: K.token); d.synchronize() }
    }

    var email: String? {
        get { d.string(forKey: K.email) }
        set { d.set(newValue, forKey: K.email); d.synchronize() }
    }

    var vehicleId: String? {
        get { d.string(forKey: K.vehicle) }
        set { d.set(newValue, forKey: K.vehicle); d.synchronize() }
    }

    /// Локальное имя машины: как показывать её в этом телефоне.
    func vehicleNick(_ id: String) -> String? {
        let v = d.string(forKey: K.nickPrefix + id)?.trimmingCharacters(in: .whitespaces)
        return (v?.isEmpty ?? true) ? nil : v
    }

    func setVehicleNick(_ id: String, _ name: String?) {
        let trimmed = name?.trimmingCharacters(in: .whitespaces) ?? ""
        if trimmed.isEmpty { d.removeObject(forKey: K.nickPrefix + id) } else { d.set(trimmed, forKey: K.nickPrefix + id) }
    }

    /// Цвет кузова для рендера — локально, сервер его не знает.
    func vehiclePaint(_ id: String) -> String? { d.string(forKey: K.paintPrefix + id) }

    func setVehiclePaint(_ id: String, _ paint: String?) {
        if let paint, !paint.isEmpty { d.set(paint, forKey: K.paintPrefix + id) } else { d.removeObject(forKey: K.paintPrefix + id) }
    }

    /// Разрешено ли ходить в backend.
    var cloudEnabled: Bool {
        get { d.object(forKey: K.cloudOn) == nil ? true : d.bool(forKey: K.cloudOn) }
        set { d.set(newValue, forKey: K.cloudOn) }
    }

    /// Профиль сидений: JSON `{"levels":{"<type>":<0..3>},"timer":<мин>}`.
    var seatPreset: String {
        get { d.string(forKey: K.seatPreset) ?? "" }
        set { d.set(newValue, forKey: K.seatPreset) }
    }

    /// Последние отправленные значения команд — переживают перезапуск.
    var optimistic: [Int: String] {
        get {
            guard let data = d.data(forKey: K.optimistic),
                  let obj = try? JSONSerialization.jsonObject(with: data) as? [String: String] else { return [:] }
            var out: [Int: String] = [:]
            for (k, v) in obj { if let i = Int(k) { out[i] = v } }
            return out
        }
        set {
            var obj: [String: String] = [:]
            for (k, v) in newValue { obj[String(k)] = v }
            if let data = try? JSONSerialization.data(withJSONObject: obj) { d.set(data, forKey: K.optimistic) }
        }
    }

    // Мелкие настройки экранов климата/сидений (как SharedPreferences "electro").
    func int(_ key: String, default def: Int) -> Int { d.object(forKey: key) == nil ? def : d.integer(forKey: key) }
    func setInt(_ key: String, _ v: Int) { d.set(v, forKey: key) }
    func bool(_ key: String, default def: Bool = false) -> Bool { d.object(forKey: key) == nil ? def : d.bool(forKey: key) }
    func setBool(_ key: String, _ v: Bool) { d.set(v, forKey: key) }

    /// Прочитанные новости (id) — отмечаются вручную, бейдж считает остальные.
    var newsRead: Set<String> {
        get { Set(d.stringArray(forKey: "newsRead") ?? []) }
        set { d.set(Array(newValue), forKey: "newsRead") }
    }

    var loggedIn: Bool { !(token ?? "").isEmpty }

    func logout() {
        d.removeObject(forKey: K.token)
        d.removeObject(forKey: K.vehicle)
        d.synchronize()
    }

    static func normalizeUrl(_ raw: String) -> String {
        var s = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        if s.isEmpty { return defaultCloudURL }
        if !s.hasPrefix("http://") && !s.hasPrefix("https://") {
            // Домен без схемы — боевой сервер за TLS; голый IP или localhost — стенд.
            s = (isLocalHost(s) ? "http://" : "https://") + s
        }
        if !s.hasSuffix("/") { s += "/" }
        return s
    }

    private static func isLocalHost(_ value: String) -> Bool {
        let host = value.split(separator: "/").first.map(String.init) ?? value
        let bare = host.split(separator: ":").first.map(String.init) ?? host
        return bare == "localhost" || bare.allSatisfy { $0.isNumber || $0 == "." }
    }
}
