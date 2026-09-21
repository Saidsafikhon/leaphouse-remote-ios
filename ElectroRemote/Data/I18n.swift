import Foundation
import SwiftUI

/// Язык интерфейса: русский (ключи в коде), английский, узбекский.
///
/// Таблица переводов — `i18n.json` в бандле (`{"en": {ключ: перевод}, "uz": {…}}`),
/// общая с Android (источник — docs/i18n/strings.json). Ключ — русский текст с
/// плейсхолдерами `{0}`, `{1}`; если перевода нет, показывается ключ.
/// Смена языка публикуется через `Lang.shared`; RootView вешает `.id(code)`,
/// так что всё дерево пересобирается.
final class Lang: ObservableObject {
    static let shared = Lang()
    static let all = ["ru", "en", "uz"]

    @Published private(set) var code: String

    private let tables: [String: [String: String]]

    private init() {
        var t: [String: [String: String]] = [:]
        if let url = Bundle.main.url(forResource: "i18n", withExtension: "json"),
           let data = try? Data(contentsOf: url),
           let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
            for l in Lang.all { if let d = root[l] as? [String: String] { t[l] = d } }
        }
        tables = t
        let saved = UserDefaults.standard.string(forKey: "lang")
        // UI-тест скриншотов запускается с «-screenshots»: экраны ждут русские подписи
        if ProcessInfo.processInfo.arguments.contains("-screenshots") { code = "ru" }
        else if let s = saved, Lang.all.contains(s) { code = s }
        else {
            let sys = Locale.preferredLanguages.first.map { String($0.prefix(2)).lowercased() } ?? "ru"
            code = Lang.all.contains(sys) ? sys : "ru"
        }
    }

    func set(_ new: String) {
        guard Lang.all.contains(new), new != code else { return }
        code = new
        UserDefaults.standard.set(new, forKey: "lang")
    }

    /// Название языка на нём самом — для переключателя.
    static func title(_ code: String) -> String {
        switch code { case "en": return "English"; case "uz": return "O‘zbekcha"; default: return "Русский" }
    }
    static func short(_ code: String) -> String { code.uppercased() }

    fileprivate func lookup(_ key: String) -> String {
        if code == "ru" { return key }
        return tables[code]?[key] ?? key
    }
}

/// Перевод строки на текущий язык. `{0}`, `{1}`… подставляются из `args`.
func L(_ key: String, _ args: Any...) -> String {
    var s = Lang.shared.lookup(key)
    for (i, a) in args.enumerated() { s = s.replacingOccurrences(of: "{\(i)}", with: "\(a)") }
    return s
}

/// Выбор языка — кнопка с текущим языком и выпадающее меню RU / EN / UZ.
/// `compact` — короткая пилюля с кодом («RU ▾») для логина и подключения,
/// иначе — строка с полным названием для настроек.
struct LangPicker: View {
    @Environment(\.palette) private var p
    @ObservedObject private var lang = Lang.shared
    var compact = false

    var body: some View {
        Menu {
            ForEach(Lang.all, id: \.self) { code in
                Button { lang.set(code) } label: {
                    if code == lang.code { Label(Lang.title(code), systemImage: "checkmark") }
                    else { Text(Lang.title(code)) }
                }
            }
        } label: {
            HStack(spacing: 6) {
                Image(systemName: "globe").font(.system(size: compact ? 13 : 16)).foregroundStyle(p.textSecondary)
                Text(compact ? Lang.short(lang.code) : Lang.title(lang.code))
                    .font(.system(size: compact ? 12 : 14, weight: .semibold)).foregroundStyle(p.textPrimary)
                Image(systemName: "chevron.down").font(.system(size: 11, weight: .semibold)).foregroundStyle(p.textSecondary)
            }
            .padding(.horizontal, compact ? 12 : 14)
            .padding(.vertical, compact ? 7 : 12)
            .background(p.surfaceElevated)
            .clipShape(RoundedRectangle(cornerRadius: compact ? 999 : 12, style: .continuous))
        }
    }
}
