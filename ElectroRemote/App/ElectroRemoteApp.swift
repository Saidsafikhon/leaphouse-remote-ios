import SwiftUI

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

    private enum Branch { case register, forgot }
    @State private var branch: Branch? = nil
    @State private var offerLock = false
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
        .environment(\.palette, palette)
        .background(palette.background.ignoresSafeArea())
        .preferredColorScheme(themeMode == "auto" ? nil : (dark ? .dark : .light))
        .onChange(of: vm.loggedIn) { _, on in
            if on {
                branch = nil
                // после входа один раз предлагаем поставить код
                if !lock.enabled && !lock.offerDeclined && lock.available() { offerLock = true }
            }
        }
        .alert("Защитить вход?", isPresented: $offerLock) {
            Button("Включить") {
                // включаем только после успешного подтверждения — иначе можно запереть самого себя
                lock.prompt { ok in if ok { lock.enabled = true } else { lock.offerDeclined = true } }
            }
            Button("Не сейчас", role: .cancel) { lock.offerDeclined = true }
        } message: {
            Text("При запуске и возврате в приложение будет запрашиваться Face ID / Touch ID или код-пароль iPhone. Можно включить позже в настройках.")
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .background && vm.loggedIn && lock.enabled { lock.locked = true }
        }
    }
}
