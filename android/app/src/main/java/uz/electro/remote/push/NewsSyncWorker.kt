package uz.electro.remote.push

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import uz.electro.remote.data.NewsStore
import java.util.concurrent.TimeUnit

/**
 * Подкачка ленты новостей в фоне: раз в несколько часов при наличии сети и сразу по
 * push. Открывая приложение, человек видит свежую ленту с диска, а не ждёт сервер.
 */
class NewsSyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        NewsStore.sync(applicationContext, force = true)
        return Result.success()
    }

    companion object {
        private const val PERIODIC = "news-sync"
        private const val ONCE = "news-sync-now"

        /** Периодическая задача; повторный вызов ничего не дублирует. */
        fun schedule(ctx: Context) {
            // Первый запуск не сразу: при старте ленту уже тянет экран, второй запрос ни к чему.
            val req = PeriodicWorkRequestBuilder<NewsSyncWorker>(6, TimeUnit.HOURS)
                .setInitialDelay(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, req)
        }

        /** По push: подкачать прямо сейчас (срочная задача, если система позволяет). */
        fun now(ctx: Context) {
            val req = OneTimeWorkRequestBuilder<NewsSyncWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(ctx).enqueueUniqueWork(ONCE, ExistingWorkPolicy.REPLACE, req)
        }
    }
}
