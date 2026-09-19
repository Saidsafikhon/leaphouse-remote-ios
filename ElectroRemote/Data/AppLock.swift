import Foundation
import LocalAuthentication

/// Защита входа системной блокировкой устройства: Face ID / Touch ID, а запасной
/// путь — код-пароль iPhone (`.deviceOwnerAuthentication`). Своего кода нет —
/// всё делает система. Зеркало `security/AppLock.kt`.
final class AppLock: ObservableObject {
    static let shared = AppLock()
    private let d = UserDefaults.standard

    /// Заблокировано ли приложение сейчас (снимается системным диалогом).
    @Published var locked = false
    @Published var enabled: Bool {
        didSet { d.set(enabled, forKey: "lock_system") }
    }

    private init() {
        // старый 4-значный код (до 0.44) переносится в «включено», сам код стирается
        let legacy = !(d.string(forKey: "lock_pin_hash") ?? "").isEmpty
        enabled = d.object(forKey: "lock_system") == nil ? legacy : d.bool(forKey: "lock_system")
        for k in ["lock_pin_hash", "lock_pin_salt", "lock_biometric", "lock_fails", "lock_until"] { d.removeObject(forKey: k) }
        locked = enabled && Settings.shared.loggedIn
    }

    /// Предлагали ли уже включить после входа; «не сейчас» запоминаем.
    var offerDeclined: Bool {
        get { d.bool(forKey: "lock_offer_declined") }
        set { d.set(newValue, forKey: "lock_offer_declined") }
    }

    /// Есть ли чем защищать: биометрия или хотя бы код-пароль устройства.
    func available() -> Bool {
        LAContext().canEvaluatePolicy(.deviceOwnerAuthentication, error: nil)
    }

    /// Какая биометрия есть — для подписи в настройках; nil — только код-пароль.
    var biometricName: String? {
        let ctx = LAContext()
        guard ctx.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: nil) else { return nil }
        switch ctx.biometryType {
        case .faceID: return "Face ID"
        case .touchID: return "Touch ID"
        default: return nil
        }
    }

    /// Системный диалог: биометрия, при отказе/отсутствии — код-пароль iPhone.
    func prompt(_ done: @escaping (Bool) -> Void) {
        let ctx = LAContext()
        ctx.evaluatePolicy(.deviceOwnerAuthentication, localizedReason: "Подтвердите вход в LeapRemote") { ok, _ in
            DispatchQueue.main.async { done(ok) }
        }
    }
}
