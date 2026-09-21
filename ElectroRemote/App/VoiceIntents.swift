import AppIntents
import SwiftUI

/// Siri / Быстрые команды / ссылки `leapremote://action/<имя>` — паритет с Android (`VoiceActions.kt`).
///
/// «Привет, Siri, закрой машину в LeapRemote»: интент открывает приложение и передаёт
/// действие в `PendingVoiceAction`; RootView исполняет его через `CarViewModel.quickAction`
/// (если машина спит — сначала будим, как при «Подключиться»). Открываем приложение, а не
/// шлём из фона: пробуждение занимает до 90 с, Siri столько не ждёт, а результат человеку
/// нужно видеть. Фразы Siri должны содержать имя приложения — требование Apple.
enum CarAction: String, AppEnum {
    case lock, unlock, climateOn = "climate_on", climateOff = "climate_off", trunk, windowsClose = "windows_close"

    static var typeDisplayRepresentation = TypeDisplayRepresentation(name: "Действие")
    static var caseDisplayRepresentations: [CarAction: DisplayRepresentation] = [
        .lock: "Закрыть двери", .unlock: "Открыть двери", .climateOn: "Включить климат",
        .climateOff: "Выключить климат", .trunk: "Открыть багажник", .windowsClose: "Закрыть окна",
    ]
}

final class PendingVoiceAction: ObservableObject {
    static let shared = PendingVoiceAction()
    @Published var action: String? = nil

    /// Из ссылки leapremote://action/lock и т.п.
    func take(url: URL) {
        guard url.scheme == "leapremote", url.host == "action", let a = url.pathComponents.dropFirst().first,
              CarAction(rawValue: a) != nil else { return }
        action = a
    }
}

struct CarCommandIntent: AppIntent {
    static var title: LocalizedStringResource = "Команда машине"
    static var description = IntentDescription("Закрыть или открыть машину, включить климат, открыть багажник.")
    static var openAppWhenRun = true

    @Parameter(title: "Действие") var action: CarAction

    init() {}
    init(action: CarAction) { self.action = action }

    @MainActor
    func perform() async throws -> some IntentResult {
        PendingVoiceAction.shared.action = action.rawValue
        return .result()
    }
}

struct LeapShortcuts: AppShortcutsProvider {
    static var appShortcuts: [AppShortcut] {
        AppShortcut(intent: CarCommandIntent(action: .lock),
                    phrases: ["Закрой машину в \(.applicationName)", "Закрой двери в \(.applicationName)",
                              "Lock my car in \(.applicationName)", "Lock the car with \(.applicationName)"],
                    shortTitle: "Закрыть машину", systemImageName: "lock.fill")
        AppShortcut(intent: CarCommandIntent(action: .unlock),
                    phrases: ["Открой машину в \(.applicationName)", "Открой двери в \(.applicationName)",
                              "Unlock my car in \(.applicationName)", "Unlock the car with \(.applicationName)"],
                    shortTitle: "Открыть машину", systemImageName: "lock.open.fill")
        AppShortcut(intent: CarCommandIntent(action: .climateOn),
                    phrases: ["Включи климат в \(.applicationName)", "Прогрей машину в \(.applicationName)",
                              "Охлади машину в \(.applicationName)", "Turn on the climate in \(.applicationName)"],
                    shortTitle: "Включить климат", systemImageName: "snowflake")
        AppShortcut(intent: CarCommandIntent(action: .climateOff),
                    phrases: ["Выключи климат в \(.applicationName)", "Turn off the climate in \(.applicationName)"],
                    shortTitle: "Выключить климат", systemImageName: "snowflake.slash")
        AppShortcut(intent: CarCommandIntent(action: .trunk),
                    phrases: ["Открой багажник в \(.applicationName)", "Open the trunk in \(.applicationName)"],
                    shortTitle: "Открыть багажник", systemImageName: "car.rear")
        AppShortcut(intent: CarCommandIntent(action: .windowsClose),
                    phrases: ["Закрой окна в \(.applicationName)", "Close the windows in \(.applicationName)"],
                    shortTitle: "Закрыть окна", systemImageName: "car.window.right")
    }
}
