package uz.electro.remote.data

import uz.electro.remote.i18n.S
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Убрать из строки любые адреса: URL, домены, IP[:порт]. Нужно, чтобы адрес
 * нашего сайта и IP сервера не утекали на экран через тексты ошибок вроде
 * «Unable to resolve host leapmotor.evon.uz» или «Failed to connect to /1.2.3.4».
 */
fun scrubAddresses(text: String?): String? {
    if (text.isNullOrBlank()) return text
    var s = text
    s = s.replace(Regex("https?://\\S+", RegexOption.IGNORE_CASE), S("сервер"))
    // домены вида name.tld (uz/com/net/org/ru/io…)
    s = s.replace(Regex("\\b[a-z0-9-]+(?:\\.[a-z0-9-]+)+\\.[a-z]{2,}\\b", RegexOption.IGNORE_CASE), S("сервер"))
    s = s.replace(Regex("\\b[a-z0-9-]+\\.[a-z]{2,}\\b", RegexOption.IGNORE_CASE), S("сервер"))
    // IPv4 с необязательным портом и ведущим слэшем
    s = s.replace(Regex("/?\\b(?:\\d{1,3}\\.){3}\\d{1,3}(?::\\d+)?\\b"), S("сервер"))
    // схлопнуть подряд идущие «сервер сервер» (если рядом были URL и домен) —
    // с учётом кириллицы: два и более слова «сервер» через пробел/двоеточие.
    val w = Regex.escape(S("сервер"))
    s = s.replace(Regex("(?:$w[\\s/:]+){1,}$w", RegexOption.IGNORE_CASE), S("сервер"))
    return s.replace(Regex("\\s+"), " ").trim()
}

/**
 * Обобщённая причина сетевого сбоя без адресов. null — это не про сеть, тогда
 * причину берут из текста (уже прогнанного через [scrubAddresses]).
 */
fun friendlyNetworkError(error: Throwable): String? = when (error) {
    is UnknownHostException -> S("Нет связи с сервером — проверьте интернет")
    is SocketTimeoutException -> S("Сервер не ответил вовремя")
    is SSLException -> S("Не удалось установить защищённое соединение")
    is ConnectException -> S("Сервер недоступен")
    is IOException -> {
        val m = error.message.orEmpty().lowercase()
        when {
            "unable to resolve host" in m -> S("Нет связи с сервером — проверьте интернет")
            "failed to connect" in m || "connection refused" in m -> S("Сервер недоступен")
            "timeout" in m -> S("Сервер не ответил вовремя")
            "not verified" in m || "hostname" in m -> S("Не удалось установить защищённое соединение")
            else -> null
        }
    }
    else -> null
}
