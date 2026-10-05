import Foundation

/// Контакты поддержки на диске. Экран «Помощь» показывает сохранённую копию сразу
/// (в том числе без сети), а сервер спрашиваем не чаще раза в `freshFor` и с
/// If-None-Match: неизменные контакты сервер отвечает 304 без тела. Раньше контакты
/// тянулись при каждом запуске приложения. Двойник `data/SupportStore.kt` на Android.
enum SupportStore {
    static let freshFor: TimeInterval = 24 * 60 * 60
    private static let d = UserDefaults.standard

    private static func file() -> URL {
        let dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir.appendingPathComponent("support.json")
    }

    /// Сохранённые контакты; nil — ещё ни разу не качали.
    static func cached() -> SupportDto? {
        guard let data = try? Data(contentsOf: file()) else { return nil }
        return try? JSONDecoder().decode(SupportDto.self, from: data)
    }

    /// Актуальные контакты: с диска, если копия свежая и не просили `force`; иначе с
    /// сервера (304 — оставляем свои). Ошибка сети — то, что было на диске.
    static func sync(settings: Settings, force: Bool) async -> SupportDto? {
        if Demo.enabled { return Demo.support }
        let cached = cached()
        let at = d.double(forKey: "supportAt")
        if !force, cached != nil, Date().timeIntervalSince1970 - at < freshFor { return cached }
        let etag = cached == nil ? nil : d.string(forKey: "supportEtag")
        do {
            switch try await CloudClient(settings: settings).fetchSupport(etag: etag) {
            case .notModified:
                touch(); return cached
            case .fresh(let data, let tag):
                // разобрать до записи: битый ответ не затрёт кэш
                guard let dto = try? JSONDecoder().decode(SupportDto.self, from: data) else { return cached }
                try? data.write(to: file(), options: .atomic)
                d.set(tag, forKey: "supportEtag")
                touch()
                return dto
            }
        } catch {
            return cached
        }
    }

    private static func touch() { d.set(Date().timeIntervalSince1970, forKey: "supportAt") }
}
