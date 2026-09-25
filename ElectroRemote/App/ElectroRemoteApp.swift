import SwiftUI
import Combine

@main
struct ElectroRemoteApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate
    @StateObject private var vm = CarViewModel()

    var body: some Scene {
        WindowGroup {
            RootView(vm: vm)
        }
    }
}

/// Ворота приложения: вход и его ответвления, ожидание парка, привязка, управление.
/// Ворота считаются из состояния, а не хранятся отдельно: сессия живёт в
/// настройках и переживает перезапуск.
struct RootView: View {
    @Environment(\.colorScheme) private var scheme
    @Environment(\.scenePhase) private var scenePhase
    @ObservedObject var vm: CarViewModel
    @ObservedObject private var lock = AppLock.shared
    @ObservedObject private var lang = Lang.shared
    @ObservedObject private var voice = PendingVoiceAction.shared

    private enum Branch { case register, forgot }
    @State private var branch: Branch? = nil
    @State private var offerLock = false
    /// Действие из ссылки/ярлыка, которое открывает машину: ждёт подтверждения на экране.
    @State private var confirmAction: String? = nil
    /// Оформление: auto | light | dark — выбор в настройках, применяется сразу.
    @AppStorage("themeMode") private var themeMode = "auto"

    var body: some View {
        let dark = themeMode == "dark" || (themeMode == "auto" && scheme == .dark)
        let palette: ElectroPalette = dark ? .dark : .light
        Group {
            // Защита входа: код/биометрия при запуске и при каждом возврате из
            // фона. Только для вошедшего — экран логина сам себя защищает.
            if vm.loggedIn && lock.enabled && lock.locked && lock.available() {
                LockScreen(lock: lock)
            } else if !vm.loggedIn {
                switch branch {
                case .register:
                    RegisterScreen(vm: vm, onRegistered: { branch = nil }, onBack: { branch = nil })
                case .forgot:
                    ForgotPasswordScreen(vm: vm, onBack: { branch = nil })
                case nil:
                    LoginScreen(vm: vm, onLoggedIn: { branch = nil }, onRegister: { branch = .register }, onForgot: { branch = .forgot })
                }
            } else if !vm.parkKnown {
                // Пустой список до ответа сервера ещё ничего не значит.
                ParkGateScreen(note: vm.parkNote, onRetry: { vm.loadVehicles() }, onLogout: { vm.logout() })
            } else if vm.vehicles.isEmpty {
                // Управление доступно только тем, у кого есть привязанная машина.
                PairScreen(vm: vm, onPaired: { branch = nil })
            } else {
                PhoneControlScreen(vm: vm)
            }
        }
        .id(lang.code)   // смена языка пересобирает всё дерево — все L("…") перечитываются
        .environment(\.palette, palette)
        .background(palette.background.ignoresSafeArea())
        .preferredColorScheme(themeMode == "auto" ? nil : (dark ? .dark : .light))
        // Siri / Быстрые команды / ссылка leapremote://action/… — выполнить, как только вошли
        .onOpenURL { PendingVoiceAction.shared.take(url: $0) }
        // Ссылку может прислать кто угодно, а браузер открывает её без вопросов: пока
        // приложение заперто — ждём разблокировки; открытие дверей и багажника — только
        // после подтверждения. Зеркало MainActivity.kt (VoiceActions.needsConfirm).
        .onReceive(voice.$action.combineLatest(vm.$loggedIn, lock.$locked)) { action, on, locked in
            guard let a = action, on else { return }
            if lock.enabled && locked && lock.available() { return }
            voice.action = nil
            if ["unlock", "trunk"].contains(a) { confirmAction = a } else { vm.quickAction(a) }
        }
        .alert((confirmAction == "trunk" ? L("Открыть багажник") : L("Открыть двери")) + "?",
               isPresented: Binding(get: { confirmAction != nil }, set: { if !$0 { confirmAction = nil } })) {
            Button(L("Выполнить")) { if let a = confirmAction { vm.quickAction(a) }; confirmAction = nil }
            Button(L("Отмена"), role: .cancel) { confirmAction = nil }
        } message: {
            Text(L("Команда пришла по ссылке или из ярлыка. Выполнить?"))
        }
        .onChange(of: vm.loggedIn) { _, on in
            if on {
                branch = nil
                // после входа один раз предлагаем поставить код
                // в демо-прогоне скриншотов диалог не нужен
                if !lock.enabled && !lock.offerDeclined && lock.available() && !Demo.enabled { offerLock = true }
            }
        }
        .alert(L("Защитить вход?"), isPresented: $offerLock) {
            Button(L("Включить")) {
                // включаем только после успешного подтверждения — иначе можно запереть самого себя
                lock.prompt { ok in if ok { lock.enabled = true } else { lock.offerDeclined = true } }
            }
            Button(L("Не сейчас"), role: .cancel) { lock.offerDeclined = true }
        } message: {
            Text(L("При запуске и возврате в приложение будет запрашиваться Face ID / Touch ID или код-пароль iPhone. Можно включить позже в настройках."))
        }
        .onChange(of: scenePhase) { _, phase in
            // Блокируем не в момент ухода, а при возврате — и только если в фоне
            // пробыли дольше 10 с. Короткие уходы (звонок, уведомление, камера
            // при сканировании QR, диалог Face ID — это .inactive) не запирают.
            switch phase {
            case .background:
                if lock.backgroundedAt == nil { lock.backgroundedAt = Date() }
                BackgroundRefresh.schedule()
            case .active:
                if let t = lock.backgroundedAt, vm.loggedIn, lock.enabled,
                   Date().timeIntervalSince(t) >= AppLock.graceSeconds { lock.locked = true }
                lock.backgroundedAt = nil
            default: break
            }
        }
    }
}
