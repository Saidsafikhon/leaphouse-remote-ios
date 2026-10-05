package uz.electro.remote.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/** Продукт EvOn для экрана «Наши продукты». */
data class EvonProduct(
    val id: String,
    val title: String,
    val description: String,
    val iconUrl: String?,
    val url: String,
)

/**
 * Список «Наши продукты» — общий для всех программ EvOn, живёт на сервере AppsMarket
 * (apps.evon.uz, не наш бэкенд leapmotor.evon.uz). Публичный, без токена и VIN.
 *
 * Последний ответ лежит на диске отдельно по языку, иконки — файлами: экран
 * показывает всё сразу (в том числе без сети). С сервером сверяемся не чаще раза
 * в сутки ([FRESH_MS]) и с If-None-Match — неизменный список сервер отвечает 304.
 */
object EvonProducts {
    private const val BASE = "https://apps.evon.uz/api/v1/products"

    private val http by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private fun file(ctx: Context, lang: String) = File(ctx.filesDir, "evon-products-$lang.json")
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("evon_products", Context.MODE_PRIVATE)

    /** Сохранённый список на языке [lang] (пусто, если ещё не качали); иконки — с диска, где есть. */
    fun cached(ctx: Context, lang: String): List<EvonProduct> = runCatching {
        file(ctx, lang).takeIf { it.exists() }?.readText()?.let(::parse)?.let { withLocalIcons(ctx, it) }
    }.getOrNull().orEmpty()

    /** Сверка со списком на сервере — не чаще раза в сутки (жест «потянуть вниз» — сразу). */
    const val FRESH_MS = 24 * 60 * 60 * 1000L

    /**
     * Актуальный список. Если копия свежая и не просили [force] — с диска, без сети.
     * Иначе запрос с If-None-Match: 304 — остаётся сохранённый. Ошибка сети — исключение
     * (экран покажет «нет связи» поверх кэша). Иконки докачиваются на диск.
     */
    suspend fun refresh(ctx: Context, lang: String, force: Boolean = false): List<EvonProduct> = withContext(Dispatchers.IO) {
        val f = file(ctx, lang)
        val sp = prefs(ctx)
        val fresh = f.exists() && System.currentTimeMillis() - sp.getLong("at-$lang", 0L) < FRESH_MS
        if (fresh && !force) return@withContext withIcons(ctx, cached(ctx, lang), force = false)
        val etag = if (f.exists()) sp.getString("etag-$lang", null) else null
        val req = Request.Builder()
            .url("$BASE?lang=$lang&platform=phone")
            .apply { etag?.let { header("If-None-Match", it) } }
            .build()
        val items = http.newCall(req).execute().use { resp ->
            when {
                resp.code == 304 -> cached(ctx, lang)
                resp.isSuccessful -> {
                    val body = resp.body?.string().orEmpty()
                    val items = parse(body)   // разобрать до записи: битый ответ не затрёт кэш
                    f.writeText(body)
                    sp.edit().putString("etag-$lang", resp.header("ETag")).apply()
                    items
                }
                else -> throw java.io.IOException("HTTP ${resp.code}")
            }
        }
        sp.edit().putLong("at-$lang", System.currentTimeMillis()).apply()
        withIcons(ctx, items, force = true)
    }

    // --- иконки на диске ---------------------------------------------------

    private fun iconDir(ctx: Context) = File(ctx.filesDir, "evon-product-icons").apply { mkdirs() }
    private fun iconFile(ctx: Context, id: String) = File(iconDir(ctx), id.replace(Regex("[^A-Za-z0-9_.-]"), "_"))

    /** Список, где у продуктов с сохранённой иконкой адрес — файл на диске. */
    fun withLocalIcons(ctx: Context, list: List<EvonProduct>): List<EvonProduct> = list.map { p ->
        val f = iconFile(ctx, p.id)
        if (p.iconUrl != null && f.exists() && f.length() > 0) p.copy(iconUrl = f.toURI().toString()) else p
    }

    /**
     * Иконки качаются один раз и лежат файлами; сверяются (If-None-Match) только
     * вместе со списком — то есть тоже не чаще раза в сутки. Без сети — что есть.
     */
    private fun withIcons(ctx: Context, list: List<EvonProduct>, force: Boolean): List<EvonProduct> {
        val sp = prefs(ctx)
        list.forEach { p ->
            val url = p.iconUrl ?: return@forEach
            val f = iconFile(ctx, p.id)
            if (f.exists() && f.length() > 0 && !force) return@forEach
            runCatching {
                val etag = if (f.exists()) sp.getString("icon-etag-${p.id}", null) else null
                val req = Request.Builder().url(url).apply { etag?.let { header("If-None-Match", it) } }.build()
                http.newCall(req).execute().use { resp ->
                    if (resp.code != 304 && resp.isSuccessful) {
                        val bytes = resp.body?.bytes() ?: return@use
                        if (bytes.isEmpty()) return@use
                        val tmp = File(f.parentFile, f.name + ".part")
                        tmp.writeBytes(bytes)
                        tmp.renameTo(f) || run { f.writeBytes(bytes); tmp.delete() }
                        sp.edit().putString("icon-etag-${p.id}", resp.header("ETag")).apply()
                    }
                }
            }
        }
        return withLocalIcons(ctx, list)
    }

    private fun parse(json: String): List<EvonProduct> {
        val arr = JSONObject(json).optJSONArray("products") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            // optString на null отдаёт строку "null" — поэтому через isNull
            fun str(key: String) = if (o.isNull(key)) "" else o.optString(key).trim()
            val id = str("id").ifBlank { return@mapNotNull null }
            EvonProduct(
                id = id,
                title = str("title").ifBlank { id },
                description = str("description"),
                iconUrl = str("icon_url").ifBlank { null },
                url = str("url"),
            )
        }.distinctBy { it.id }   // id — ключ строки списка, повтор уронил бы LazyColumn
    }
}
