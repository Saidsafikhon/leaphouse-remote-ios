package uz.electro.remote

import android.content.Intent
import uz.electro.remote.data.Cmd
import uz.electro.remote.data.VehicleCommand
import uz.electro.remote.i18n.S

/**
 * Быстрые действия снаружи приложения: «Окей Google», ярлыки по долгому тапу на иконке,
 * ссылки `leapremote://action/<имя>`. Одно имя — одна пачка команд, та же, что у плиток
 * на главной. Выполнение — [CarViewModel.quickAction]: если машина спит, сначала будим.
 *
 * Google Assistant: `res/xml/shortcuts.xml` объявляет эти же действия как App Actions
 * (custom intents с фразами из `res/values/voice_queries.xml`). Ассистент начинает их
 * понимать после публикации приложения в Google Play; до этого работают ярлыки и ссылки.
 */
object VoiceActions {
    const val SCHEME = "leapremote"
    const val HOST = "action"

    val all = listOf("lock", "unlock", "climate_on", "climate_off", "trunk", "windows_close")
    /** Что открывает машину — только после подтверждения на экране (ссылка могла прийти извне). */
    val needsConfirm = setOf("unlock", "trunk")

    fun commands(action: String): List<VehicleCommand>? = when (action) {
        "lock" -> listOf(VehicleCommand(Cmd.LOCK, "0", S("Закрыть двери")))
        "unlock" -> listOf(VehicleCommand(Cmd.LOCK, "1", S("Открыть двери")))
        "climate_on" -> listOf(VehicleCommand(Cmd.AC, "1", S("Климат")))
        "climate_off" -> Cmd.climateOff()
        "trunk" -> listOf(VehicleCommand(Cmd.TRUNK, "1", S("Открыть багажник")))
        "windows_close" -> Cmd.WINDOWS.map { VehicleCommand(it, "0", S("Закрыть окна")) }
        else -> null
    }

    fun label(action: String): String = when (action) {
        "lock" -> S("Закрыть двери"); "unlock" -> S("Открыть двери")
        "climate_on" -> S("Включить климат"); "climate_off" -> S("Выключить климат")
        "trunk" -> S("Открыть багажник"); "windows_close" -> S("Закрыть все окна"); "find" -> S("Найти машину")
        else -> action
    }

    /** Имя действия из intent-а запуска (ссылка или ярлык); null — обычный запуск. */
    fun fromIntent(intent: Intent?): String? {
        val d = intent?.data ?: return null
        if (d.scheme != SCHEME || d.host != HOST) return null
        return d.pathSegments.firstOrNull()?.takeIf { it in all }
    }
}
