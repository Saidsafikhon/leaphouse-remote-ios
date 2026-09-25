package uz.electro.remote.push

import uz.electro.remote.i18n.S
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import uz.electro.remote.MainActivity
import uz.electro.remote.R
import uz.electro.remote.data.CarRepository
import uz.electro.remote.data.Settings

/**
 * Push через Firebase Cloud Messaging.
 *
 * Без `google-services.json` Firebase не инициализируется — тогда токена нет и
 * всё здесь молча пропускается: приложение работает, просто уведомления не
 * приходят (лента «Новости» при этом живёт своей жизнью — она с сервера).
 */
object Push {
    const val CHANNEL = "news"
    const val ACTION_NEWS = "uz.electro.remote.NEWS"

    /** После входа (и на каждом запуске): взять токен и отдать серверу. */
    fun register(ctx: Context) {
        val settings = Settings(ctx)
        if (!settings.loggedIn) return
        runCatching {
            FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                CoroutineScope(Dispatchers.IO).launch {
                    if (CarRepository(settings).registerPushToken(token)) settings.pushToken = token
                }
            }
        }
    }

    /** При выходе: снять токен с сервера, чтобы чужие уведомления сюда не пришли. */
    fun forget(ctx: Context) {
        val settings = Settings(ctx)
        val token = settings.pushToken ?: return
        CoroutineScope(Dispatchers.IO).launch {
            CarRepository(settings).removePushToken(token)
            settings.pushToken = null
        }
    }

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, S("Новости и уведомления"), NotificationManager.IMPORTANCE_HIGH)
            )
        }
    }

    fun canPost(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}

class PushService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        val settings = Settings(this)
        if (!settings.loggedIn) return
        CoroutineScope(Dispatchers.IO).launch {
            if (CarRepository(settings).registerPushToken(token)) settings.pushToken = token
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: message.data["title"] ?: "LeapRemote"
        val body = message.notification?.body ?: message.data["body"] ?: ""
        // Сервер шлёт data-сообщение, поэтому сюда попадаем и в фоне: подкачиваем ленту
        // на диск (при открытии новость уже на месте), а открытые экраны перечитывают её.
        NewsSyncWorker.now(this)
        sendBroadcast(Intent(Push.ACTION_NEWS).setPackage(packageName))
        if (!Push.canPost(this)) return
        Push.ensureChannel(this)
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).putExtra("open", "news"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(this, Push.CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open)
            .build()
        getSystemService(NotificationManager::class.java).notify(message.messageId?.hashCode() ?: 1, n)
    }
}
