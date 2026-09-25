package uz.electro.remote.data

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Лента новостей на диске. Экономия трафика: приложение показывает сохранённую ленту
 * сразу и ходит на сервер не при каждом открытии, а когда копия старше [FRESH_MS],
 * по жесту обновления, по push или из фоновой задачи ([uz.electro.remote.push.NewsSyncWorker]).
 * Запрос идёт с If-None-Match: неизменную ленту сервер отвечает 304 без тела.
 *
 * Две области: «user» (после входа, с адресными уведомлениями) и «public» (до входа).
 * При выходе область «user» стирается — чужие уведомления следующему человеку не видны.
 */
object NewsStore {
    const val FRESH_MS = 30 * 60 * 1000L

    private val adapter by lazy {
        Moshi.Builder().build().adapter<List<NewsItemDto>>(
            Types.newParameterizedType(List::class.java, NewsItemDto::class.java),
        )
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("news_cache", Context.MODE_PRIVATE)
    private fun file(ctx: Context, scope: String) = File(ctx.filesDir, "news-$scope.json")
    private fun scope(settings: Settings) = if (settings.loggedIn) "user" else "public"

    /** Сохранённая лента для текущего состояния входа (пусто, если ещё не качали). */
    fun cached(ctx: Context, settings: Settings = Settings(ctx)): List<NewsItemDto> =
        read(ctx, scope(settings))

    private fun read(ctx: Context, scope: String): List<NewsItemDto> = runCatching {
        file(ctx, scope).takeIf { it.exists() }?.readText()?.let { adapter.fromJson(it) }
    }.getOrNull().orEmpty()

    /**
     * Актуальная лента: с диска, если копия свежая и не просили [force]; иначе с сервера
     * (304 — оставляем свою). При ошибке сети — что было на диске.
     */
    suspend fun sync(ctx: Context, force: Boolean, settings: Settings = Settings(ctx)): List<NewsItemDto> =
        withContext(Dispatchers.IO) {
            val scope = scope(settings)
            val sp = prefs(ctx)
            val cached = read(ctx, scope)
            val at = sp.getLong("at-$scope", 0L)
            if (!force && cached.isNotEmpty() && System.currentTimeMillis() - at < FRESH_MS) return@withContext cached
            val etag = if (cached.isEmpty()) null else sp.getString("etag-$scope", null)
            runCatching {
                val api = CloudClient(settings).api
                val resp = if (settings.loggedIn) api.news(etag) else api.newsPublic(etag)
                when {
                    resp.code() == 304 -> { touch(ctx, scope); cached }
                    resp.isSuccessful -> {
                        val items = resp.body().orEmpty()
                        file(ctx, scope).writeText(adapter.toJson(items))
                        sp.edit().putString("etag-$scope", resp.headers()["ETag"]).apply()
                        touch(ctx, scope)
                        items
                    }
                    else -> cached
                }
            }.getOrDefault(cached)
        }

    private fun touch(ctx: Context, scope: String) =
        prefs(ctx).edit().putLong("at-$scope", System.currentTimeMillis()).apply()

    /** При выходе: личная лента стирается, публичная остаётся. */
    fun clearUser(ctx: Context) {
        runCatching { file(ctx, "user").delete() }
        prefs(ctx).edit().remove("etag-user").remove("at-user").apply()
    }
}
