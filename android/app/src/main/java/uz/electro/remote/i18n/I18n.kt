package uz.electro.remote.i18n

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.util.Locale

/**
 * Язык интерфейса: русский (ключи в коде), английский, узбекский.
 *
 * Таблица переводов — `assets/i18n.json` (`{"en": {ключ: перевод}, "uz": {…}}`),
 * общая с iOS. Ключ — русский текст с плейсхолдерами `{0}`, `{1}`; если перевода
 * нет, показывается ключ. Текущий язык — Compose-state, так что всё, что читает
 * [S], перерисуется при смене.
 */
object Lang {
    const val RU = "ru"; const val EN = "en"; const val UZ = "uz"
    val all = listOf(RU, EN, UZ)
    private const val PREF = "lang"

    var current: String by mutableStateOf(RU)
        private set

    private var tables: Map<String, Map<String, String>> = emptyMap()

    fun load(ctx: Context) {
        tables = runCatching {
            val root = JSONObject(ctx.assets.open("i18n.json").bufferedReader().readText())
            all.filter { root.has(it) }.associateWith { code ->
                val o = root.getJSONObject(code)
                o.keys().asSequence().associateWith { o.getString(it) }
            }
        }.getOrDefault(emptyMap())
        val saved = ctx.getSharedPreferences("electro", Context.MODE_PRIVATE).getString(PREF, null)
        current = saved?.takeIf { it in all } ?: systemDefault()
    }

    fun set(ctx: Context, code: String) {
        if (code !in all) return
        current = code
        ctx.getSharedPreferences("electro", Context.MODE_PRIVATE).edit().putString(PREF, code).apply()
    }

    /** Название языка на нём самом — для переключателя. */
    fun title(code: String): String = when (code) { EN -> "English"; UZ -> "O‘zbekcha"; else -> "Русский" }
    fun short(code: String): String = code.uppercase()

    private fun systemDefault(): String {
        val sys = Locale.getDefault().language.lowercase()
        return if (sys in all) sys else RU
    }

    internal fun lookup(key: String): String {
        val lang = current
        if (lang == RU) return key
        return tables[lang]?.get(key) ?: key
    }
}

/** Перевод строки на текущий язык. `{0}`, `{1}`… подставляются из [args]. */
fun S(key: String, vararg args: Any?): String {
    var s = Lang.lookup(key)
    if (args.isNotEmpty()) args.forEachIndexed { i, a -> s = s.replace("{$i}", a?.toString() ?: "") }
    return s
}
