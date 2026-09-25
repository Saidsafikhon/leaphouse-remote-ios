package uz.electro.remote.data

import android.content.Context

/**
 * Единственное место, где живут настройки подключения. Раньше IP головы и адрес
 * backend были захардкожены в объектах — теперь всё читается и правится отсюда.
 */
class Settings(ctx: Context) {
    private val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Базовый адрес backend, всегда со схемой и слэшем на конце.
     *
     * Адреса прежних стендов подменяются боевым: телефон, обновлённый с версии,
     * где сервер жил в локальной сети, иначе продолжил бы стучаться туда, где
     * его давно нет, — и выглядел бы сломанным, хотя настройка «своя». Свой,
     * вручную вписанный адрес при этом сохраняется.
     */
    var cloudUrl: String
        get() {
            val saved = sp.getString(K_CLOUD_URL, "").orEmpty().trim()
            return if (saved.isBlank() || saved in LEGACY_CLOUD_URLS) DEFAULT_CLOUD_URL else saved
        }
        set(v) = sp.edit().putString(K_CLOUD_URL, normalizeUrl(v)).apply()

    // Вход и выбранная машина пишутся commit(), а не apply(): apply()
    // возвращается сразу и дописывает файл потом, и если процесс умрёт раньше
    // (обновление apk, «остановить» в настройках Android), пользователь молча
    // окажется разлогинен — а сессия живёт неделю и вводить пароль заново незачем.
    var token: String?
        get() = sp.getString(K_TOKEN, null)
        set(v) { sp.edit().putString(K_TOKEN, v).commit() }

    var email: String?
        get() = sp.getString(K_EMAIL, null)
        set(v) { sp.edit().putString(K_EMAIL, v).commit() }

    var vehicleId: String?
        get() = sp.getString(K_VEHICLE, null)
        set(v) { sp.edit().putString(K_VEHICLE, v).commit() }

    /** Локальное имя машины: как показывать её в этом телефоне. */
    fun vehicleNick(id: String): String? =
        sp.getString(K_NICK_PREFIX + id, null)?.takeIf { it.isNotBlank() }

    fun setVehicleNick(id: String, name: String?) {
        sp.edit().apply {
            if (name.isNullOrBlank()) remove(K_NICK_PREFIX + id) else putString(K_NICK_PREFIX + id, name.trim())
        }.apply()
    }

    /** Цвет кузова для рендера — локально, сервер его не знает. */
    fun vehiclePaint(id: String): String? = sp.getString(K_PAINT_PREFIX + id, null)

    fun setVehiclePaint(id: String, paint: String?) {
        sp.edit().apply {
            if (paint.isNullOrBlank()) remove(K_PAINT_PREFIX + id) else putString(K_PAINT_PREFIX + id, paint)
        }.apply()
    }

    /** Разрешено ли ходить в backend. */
    var cloudEnabled: Boolean
        get() = sp.getBoolean(K_CLOUD_ON, true)
        set(v) = sp.edit().putBoolean(K_CLOUD_ON, v).apply()

    /**
     * Профиль сидений: что включать по тумблеру на главной. JSON вида
     * `{"levels":{"<type>":<0..3>,...},"timer":<мин>}`. Пусто — профиль ещё не
     * задан (тумблер тогда открывает экран сидений). Пишется, когда пользователь
     * жмёт «Активировать» на экране сидений: последняя настройка и есть профиль.
     */
    var seatPreset: String
        get() = sp.getString(K_SEAT_PRESET, "") ?: ""
        set(v) = sp.edit().putString(K_SEAT_PRESET, v).apply()

    /** Прочитанные новости (id) — отмечаются вручную, бейдж считает остальные. */
    var newsRead: Set<String>
        get() = sp.getStringSet(K_NEWS_READ, emptySet())?.toSet() ?: emptySet()
        set(v) = sp.edit().putStringSet(K_NEWS_READ, v.toSet()).apply()

    /** Push-токен, который сервер уже получил (снимаем при выходе). */
    var pushToken: String?
        get() = sp.getString(K_PUSH_TOKEN, null)
        set(v) = sp.edit().putString(K_PUSH_TOKEN, v).apply()

    val loggedIn: Boolean get() = !token.isNullOrBlank()

    fun logout() {
        sp.edit().remove(K_TOKEN).remove(K_VEHICLE).commit()
    }

    companion object {
        private const val PREFS = "electro"
        private const val K_CLOUD_URL = "cloudUrl"
        private const val K_TOKEN = "token"
        private const val K_EMAIL = "email"
        private const val K_VEHICLE = "vehicleId"
        private const val K_CLOUD_ON = "cloudEnabled"
        private const val K_NICK_PREFIX = "nick_"
        private const val K_PAINT_PREFIX = "paint_"
        private const val K_SEAT_PRESET = "seatPreset"
        private const val K_NEWS_READ = "newsRead"
        private const val K_PUSH_TOKEN = "pushToken"

        /** Публичные страницы сайта. */
        const val PRIVACY_URL = "https://leapmotor.evon.uz/privacy"
        const val SUPPORT_URL = "https://leapmotor.evon.uz/support"

        const val DEFAULT_CLOUD_URL = "https://leapmotor.evon.uz/"
        /** Магазин живёт на своём домене (витрина market.evon.uz + тот же API магазина). */
        const val MARKET_URL = "https://market.evon.uz/"

        /**
         * Адреса, с которых уводим на текущий сервер.
         *
         * `62.171.159.63` — прежний боевой по IP: сервер переехал на домен с
         * настоящим TLS (`leapmotor.evon.uz`), и по IP сертификат теперь не
         * проходит проверку имени («Hostname 62.171.159.63 not verified»). Домен
         * `electro.rsgstudy.uz` и стенд `10.230.1.52` — ещё более ранние адреса.
         * Телефон, обновлённый со старой версии, иначе продолжил бы стучаться
         * туда и падать на логине. Свой вручную вписанный адрес сохраняется.
         */
        private val LEGACY_CLOUD_URLS = setOf(
            "http://62.171.159.63/",
            "http://62.171.159.63",
            "https://62.171.159.63/",
            "https://62.171.159.63",
            "http://10.230.1.52:8000/",
            "http://10.230.1.52:8000",
            "https://electro.rsgstudy.uz/",
            "https://electro.rsgstudy.uz",
            "http://electro.rsgstudy.uz/",
            "http://electro.rsgstudy.uz",
        )

        fun normalizeUrl(raw: String): String {
            var s = raw.trim()
            if (s.isEmpty()) return DEFAULT_CLOUD_URL
            if (!s.startsWith("http://") && !s.startsWith("https://")) {
                // Домен без схемы — это боевой сервер за TLS; голый IP или
                // localhost — стенд в локальной сети, где сертификата нет.
                s = (if (isLocalHost(s)) "http://" else "https://") + s
            }
            if (!s.endsWith("/")) s += "/"
            return s
        }

        /** Хост без имени: IP-адрес или localhost — сертификату там взяться неоткуда. */
        private fun isLocalHost(value: String): Boolean {
            val host = value.substringBefore('/').substringBefore(':')
            return host == "localhost" || host.all { it.isDigit() || it == '.' }
        }
    }
}
