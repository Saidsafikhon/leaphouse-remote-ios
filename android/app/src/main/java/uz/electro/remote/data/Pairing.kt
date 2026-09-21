package uz.electro.remote.data

import uz.electro.remote.i18n.S
import android.net.Uri

/**
 * Разбор QR с экрана машины. Формат payload:
 *     electro://pair?c=<код привязки>&h=<адрес головы в локальной сети>
 *
 * Параметр `h` остался в QR от прежней схемы, когда телефон ходил в голову
 * напрямую. Этого канала больше нет — он требовал общей сети с машиной и
 * включённого на голове adb, — поэтому адрес читаем и игнорируем: ломать
 * QR, который уже показывают головы в парке, ради одного мёртвого поля незачем.
 */
object Pairing {

    data class Parsed(val code: String?)

    fun parse(payload: String): Parsed = runCatching {
        val uri = Uri.parse(payload.trim())
        if (uri.scheme != "electro") return@runCatching Parsed(null)
        Parsed(uri.getQueryParameter("c"))
    }.getOrDefault(Parsed(null))

    /**
     * Привязывает машину по отсканированному коду.
     *
     * @return человекочитаемый результат — его показывают как есть.
     */
    suspend fun claim(payload: String, repo: CarRepository): Result<String> {
        val code = parse(payload).code
            ?: return Result.failure(IllegalArgumentException(S("Это не QR машины Electro")))
        if (code.isBlank()) {
            return Result.failure(IllegalArgumentException(S("В коде нет кода привязки")))
        }
        return repo.claimPairing(code)
            .map { S("Машина привязана: {0}", it.name) }
            .recoverCatching { throw IllegalStateException(repo.reason(it)) }
    }
}
