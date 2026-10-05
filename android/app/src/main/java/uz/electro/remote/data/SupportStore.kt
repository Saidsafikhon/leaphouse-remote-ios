package uz.electro.remote.data

import android.content.Context
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Контакты поддержки на диске. Экран «Помощь» показывает сохранённую копию сразу
 * (в том числе без сети), а сервер спрашиваем не чаще раза в [FRESH_MS] и с
 * If-None-Match: неизменные контакты сервер отвечает 304 без тела. Раньше контакты
 * тянулись при каждом запуске приложения.
 */
object SupportStore {
    const val FRESH_MS = 24 * 60 * 60 * 1000L

    private val adapter by lazy { Moshi.Builder().build().adapter(SupportDto::class.java) }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("support_cache", Context.MODE_PRIVATE)
    private fun file(ctx: Context) = File(ctx.filesDir, "support.json")

    /** Сохранённые контакты; null — ещё ни разу не качали. */
    fun cached(ctx: Context): SupportDto? = runCatching {
        file(ctx).takeIf { it.exists() }?.readText()?.let { adapter.fromJson(it) }
    }.getOrNull()

    /**
     * Актуальные контакты: с диска, если копия свежая и не просили [force]; иначе с
     * сервера (304 — оставляем свои). Ошибка сети — то, что было на диске.
     */
    suspend fun sync(ctx: Context, force: Boolean = false, settings: Settings = Settings(ctx)): SupportDto? =
        withContext(Dispatchers.IO) {
            val sp = prefs(ctx)
            val cached = cached(ctx)
            val at = sp.getLong("at", 0L)
            if (!force && cached != null && System.currentTimeMillis() - at < FRESH_MS) return@withContext cached
            val etag = if (cached == null) null else sp.getString("etag", null)
            runCatching {
                val resp = CloudClient(settings).api.support(etag)
                when {
                    resp.code() == 304 -> { touch(ctx); cached }
                    resp.isSuccessful -> {
                        val body = resp.body()?.string().orEmpty()
                        val dto = adapter.fromJson(body) ?: return@runCatching cached  // битый ответ кэш не затирает
                        file(ctx).writeText(body)
                        sp.edit().putString("etag", resp.headers()["ETag"]).apply()
                        touch(ctx)
                        dto
                    }
                    else -> cached
                }
            }.getOrDefault(cached)
        }

    private fun touch(ctx: Context) = prefs(ctx).edit().putLong("at", System.currentTimeMillis()).apply()
}
