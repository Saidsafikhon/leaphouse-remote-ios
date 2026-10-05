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
 * Последний ответ лежит на диске отдельно по языку: экран показывает его сразу
 * (в том числе без сети), а запрос идёт с If-None-Match — неизменный список
 * сервер отвечает 304 без тела.
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

    /** Сохранённый список на языке [lang] (пусто, если ещё не качали). */
    fun cached(ctx: Context, lang: String): List<EvonProduct> = runCatching {
        file(ctx, lang).takeIf { it.exists() }?.readText()?.let(::parse)
    }.getOrNull().orEmpty()

    /**
     * Свежий список с сервера. 304 — остаётся сохранённый. Ошибка сети — исключение
     * (экран покажет «нет связи» поверх кэша).
     */
    suspend fun refresh(ctx: Context, lang: String): List<EvonProduct> = withContext(Dispatchers.IO) {
        val f = file(ctx, lang)
        val etag = if (f.exists()) prefs(ctx).getString("etag-$lang", null) else null
        val req = Request.Builder()
            .url("$BASE?lang=$lang&platform=phone")
            .apply { etag?.let { header("If-None-Match", it) } }
            .build()
        http.newCall(req).execute().use { resp ->
            when {
                resp.code == 304 -> cached(ctx, lang)
                resp.isSuccessful -> {
                    val body = resp.body?.string().orEmpty()
                    val items = parse(body)   // разобрать до записи: битый ответ не затрёт кэш
                    f.writeText(body)
                    prefs(ctx).edit().putString("etag-$lang", resp.header("ETag")).apply()
                    items
                }
                else -> throw java.io.IOException("HTTP ${resp.code}")
            }
        }
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
