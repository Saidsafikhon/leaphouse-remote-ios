import Foundation
import SwiftUI
import UserNotifications

/// Push-уведомления: спрашиваем разрешение после входа, получаем APNs-токен
/// через AppDelegate и отдаём его серверу. Токен может смениться — шлём на
/// каждом запуске, сервер хранит по одному на устройство.
final class PushRegistrar: NSObject, UNUserNotificationCenterDelegate {
    static let shared = PushRegistrar()

    private(set) var token: String? {
        didSet { if token != nil { Task { await sendIfLoggedIn() } } }
    }
    /// Пришло уведомление (в фоне или на переднем плане) — экраны перечитывают ленту.
    var onNotification: (() -> Void)?
    /// Человек нажал на уведомление — открыть ленту новостей.
    var onNotificationTap: (() -> Void)?

    private let settings = Settings.shared
    private let tokenKey = "pushTokenSent"

    /// Вызывается после входа и при запуске с живой сессией.
    @MainActor
    func enable() {
        if Demo.enabled { return }   // на скриншотах системный запрос разрешения не нужен
        let center = UNUserNotificationCenter.current()
        center.delegate = self
        center.requestAuthorization(options: [.alert, .sound, .badge]) { granted, _ in
            guard granted else { return }
            DispatchQueue.main.async { UIApplication.shared.registerForRemoteNotifications() }
        }
    }

    func didRegister(deviceToken: Data) {
        token = deviceToken.map { String(format: "%02x", $0) }.joined()
    }

    func sendIfLoggedIn() async {
        guard let token, settings.loggedIn, !settings.cloudUrl.isEmpty else { return }
        let client = CloudClient(settings: settings)
        do {
            try await client.registerPushToken(platform: "ios", token: token)
            UserDefaults.standard.set(token, forKey: tokenKey)
        } catch {
            // не критично: повторим при следующем запуске
        }
    }

    /// При выходе снимаем токен с сервера, чтобы чужие уведомления сюда не пришли.
    func forget() async {
        let sent = UserDefaults.standard.string(forKey: tokenKey) ?? token
        guard let sent else { return }
        let client = CloudClient(settings: settings)
        try? await client.removePushToken(sent)
        UserDefaults.standard.removeObject(forKey: tokenKey)
    }

    // Уведомление на переднем плане показываем баннером, как и в фоне.
    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification,
                                withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        onNotification?()
        completionHandler([.banner, .sound])
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse,
                                withCompletionHandler completionHandler: @escaping () -> Void) {
        onNotification?()
        onNotificationTap?()
        completionHandler()
    }
}

final class AppDelegate: NSObject, UIApplicationDelegate {
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        UNUserNotificationCenter.current().delegate = PushRegistrar.shared
        return true
    }

    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        PushRegistrar.shared.didRegister(deviceToken: deviceToken)
    }

    func application(_ application: UIApplication, didFailToRegisterForRemoteNotificationsWithError error: Error) {
        // без сертификата push (сборка Sideloadly) — это нормально
    }
}
