import Foundation
import CryptoKit
import LocalAuthentication

/// Защита входа в приложение: 4-значный код и биометрия (Face ID / Touch ID) —
/// зеркало `security/AppLock.kt`. Код не хранится, только SHA-256 от соли и
/// кода. Биометрия — надстройка над кодом: без кода её не включить, и отказ
/// системного диалога всегда оставляет запасной путь через код.
final class AppLock: ObservableObject {
    static let shared = AppLock()
    private let d = UserDefaults.standard

    private enum K {
        static let hash = "lock_pin_hash", salt = "lock_pin_salt", bio = "lock_biometric"
        static let fails = "lock_fails", until = "lock_until"
    }
    static let pinLength = 4
    private static let maxFails = 5
    private static let cooldown: TimeInterval = 30

    /// Заблокировано ли приложение сейчас (снимается кодом или биометрией).
    @Published var locked = false
    @Published private(set) var enabled: Bool
    @Published var biometricEnabled: Bool {
        didSet { d.set(biometricEnabled, forKey: K.bio) }
    }

    private init() {
        enabled = !(d.string(forKey: K.hash) ?? "").isEmpty
        biometricEnabled = d.bool(forKey: K.bio)
        locked = enabled && Settings.shared.loggedIn
    }

    func setPin(_ pin: String) {
        var bytes = [UInt8](repeating: 0, count: 16)
        _ = SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes)
        let salt = bytes.map { String(format: "%02x", $0) }.joined()
        d.set(salt, forKey: K.salt); d.set(Self.hash(salt, pin), forKey: K.hash)
        enabled = true
    }

    func clear() {
        for k in [K.hash, K.salt, K.bio, K.fails, K.until] { d.removeObject(forKey: k) }
        enabled = false; biometricEnabled = false; locked = false
    }

    func check(_ pin: String) -> Bool {
        guard let salt = d.string(forKey: K.salt) else { return false }
        let ok = Self.hash(salt, pin) == d.string(forKey: K.hash)
        if ok { d.removeObject(forKey: K.fails); d.removeObject(forKey: K.until) }
        else {
            let fails = d.integer(forKey: K.fails) + 1
            // после пяти промахов — пауза 30 с, чтобы код нельзя было перебрать
            if fails >= Self.maxFails { d.set(Date().addingTimeInterval(Self.cooldown).timeIntervalSince1970, forKey: K.until); d.set(0, forKey: K.fails) }
            else { d.set(fails, forKey: K.fails) }
        }
        return ok
    }

    /// Сколько секунд ещё ждать после серии промахов; 0 — можно вводить.
    func cooldownSec() -> Int { max(0, Int(d.double(forKey: K.until) - Date().timeIntervalSince1970)) }

    /// Есть ли на устройстве биометрия, которой можно пользоваться.
    func biometricAvailable() -> Bool {
        LAContext().canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: nil)
    }

    /// Какая именно: для подписи в настройках и на кнопке.
    var biometricName: String {
        let ctx = LAContext()
        _ = ctx.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: nil)
        switch ctx.biometryType {
        case .faceID: return "Face ID"
        case .touchID: return "Touch ID"
        default: return "Биометрия"
        }
    }

    /// Системный диалог биометрии; кнопка отказа — «Код».
    func promptBiometric(_ done: @escaping (Bool) -> Void) {
        let ctx = LAContext()
        ctx.localizedFallbackTitle = "Код"
        ctx.localizedCancelTitle = "Код"
        ctx.evaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, localizedReason: "Подтвердите вход в LeapRemote") { ok, _ in
            DispatchQueue.main.async { done(ok) }
        }
    }

    private static func hash(_ salt: String, _ pin: String) -> String {
        SHA256.hash(data: Data((salt + ":" + pin).utf8)).map { String(format: "%02x", $0) }.joined()
    }
}
