import Foundation

/// Лента новостей на диске. Экономия трафика: приложение показывает сохранённую ленту
/// сразу и ходит на сервер не при каждом открытии, а когда копия старше `freshFor`,
/// по жесту обновления, по push или из фоновой задачи (`BackgroundRefresh`).
/// Запрос идёт с If-None-Match: неизменную ленту сервер отвечает 304 без тела.
///
/// Две области: «user» (после входа, с адресными уведомлениями) и «public» (до входа).
/// При выходе область «user» стирается — чужие уведомления следующему человеку не видны.
/// Двойник `data/NewsStore.kt` на Android.
enum NewsStore {
    static let freshFor: TimeInterval = 30 * 60
    private static let d = UserDefaults.standard

    private static func scope(_ loggedIn: Bool) -> String { loggedIn ? "user" : "public" }

    private static func file(_ scope: String) -> URL {
        let dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir.appendingPathComponent("news-\(scope).json")
    }

    /// Сохранённая лента для текущего состояния входа (пусто, если ещё не качали).
    static func cached(loggedIn: Bool) -> [NewsItem] {
        guard let data = try? Data(contentsOf: file(scope(loggedIn))) else { return [] }
        return (try? JSONDecoder().decode([NewsItem].self, from: data)) ?? []
    }

    /// Актуальная лента: с диска, если копия свежая и не просили `force`; иначе с сервера
    /// (304 — оставляем свою). При ошибке сети — что было на диске.
    static func sync(settings: Settings, force: Bool) async -> [NewsItem] {
        if Demo.enabled { return Demo.news }
        let loggedIn = settings.loggedIn
        let sc = scope(loggedIn)
        let cached = cached(loggedIn: loggedIn)
        let at = d.double(forKey: "newsAt-\(sc)")
        if !force, !cached.isEmpty, Date().timeIntervalSince1970 - at < freshFor { return cached }
        let etag = cached.isEmpty ? nil : d.string(forKey: "newsEtag-\(sc)")
        do {
            switch try await CloudClient(settings: settings).fetchNews(public: !loggedIn, etag: etag) {
            case .notModified:
                touch(sc); return cached
            case .fresh(let items, let tag):
                if let data = try? JSONEncoder().encode(items) { try? data.write(to: file(sc), options: .atomic) }
                d.set(tag, forKey: "newsEtag-\(sc)")
                touch(sc)
                return items
            }
        } catch {
            return cached
        }
    }

    private static func touch(_ sc: String) { d.set(Date().timeIntervalSince1970, forKey: "newsAt-\(sc)") }

    /// При выходе: личная лента стирается, публичная остаётся.
    static func clearUser() {
        try? FileManager.default.removeItem(at: file("user"))
        d.removeObject(forKey: "newsEtag-user"); d.removeObject(forKey: "newsAt-user")
    }
}
