import BackgroundTasks
import Foundation
import UIKit

/// Подкачка ленты новостей в фоне: BGAppRefreshTask (система даёт окно раз в несколько
/// часов) и push с content-available (сразу после рассылки). Открывая приложение,
/// человек видит свежую ленту с диска, а не ждёт сервер. Двойник `NewsSyncWorker` на Android.
enum BackgroundRefresh {
    /// Совпадает с BGTaskSchedulerPermittedIdentifiers в Info.plist.
    static let id = "uz.electro.remote.newsrefresh"

    /// Регистрация — строго до конца didFinishLaunching.
    static func register() {
        BGTaskScheduler.shared.register(forTaskWithIdentifier: id, using: nil) { task in
            guard let task = task as? BGAppRefreshTask else { task.setTaskCompleted(success: false); return }
            handle(task)
        }
    }

    /// Просим следующее окно; вызывается при уходе в фон и после каждого выполнения.
    static func schedule() {
        let req = BGAppRefreshTaskRequest(identifier: id)
        req.earliestBeginDate = Date(timeIntervalSinceNow: 6 * 3600)
        try? BGTaskScheduler.shared.submit(req)
    }

    private static func handle(_ task: BGAppRefreshTask) {
        schedule()
        let job = Task {
            _ = await NewsStore.sync(settings: Settings.shared, force: true)
            task.setTaskCompleted(success: true)
        }
        task.expirationHandler = { job.cancel(); task.setTaskCompleted(success: false) }
    }

    /// Push с content-available: подкачать ленту и отчитаться системе.
    static func handlePush(completion: @escaping (UIBackgroundFetchResult) -> Void) {
        Task {
            let items = await NewsStore.sync(settings: Settings.shared, force: true)
            completion(items.isEmpty ? .noData : .newData)
        }
    }
}
