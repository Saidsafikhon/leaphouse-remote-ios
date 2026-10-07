package uz.electro.remote.data

import uz.electro.remote.i18n.S

/** Канал, по которому сейчас доступна машина. */
enum class Link {
    /** Сервер машину не видит: голова не на связи или сервер недоступен. */
    NONE,

    /** Через наш backend — единственный путь к машине. */
    CLOUD,
}

data class GeoPoint(val lat: Double, val lon: Double, val bearing: Double? = null)

data class Doors(
    val frontLeft: Boolean = false,
    val frontRight: Boolean = false,
    val rearLeft: Boolean = false,
    val rearRight: Boolean = false,
) {
    val anyOpen: Boolean get() = frontLeft || frontRight || rearLeft || rearRight
}

/**
 * Разобранное состояние машины: экраны читают типизированные поля, а не
 * выковыривают строки из сырой карты сигналов на каждой отрисовке.
 *
 * `raw` оставлен для диагностики и для сигналов, под которые ещё нет поля.
 */
/**
 * Охрана. Отдельно от замков намеренно: снятие с охраны обязано оставить двери
 * запертыми, и «заперто» не значит «на охране». Сервер их различает, и экран
 * обязан различать тоже.
 */
enum class Security { ARMED, DISARMED, UNKNOWN }

data class CarState(
    val link: Link = Link.NONE,
    val raw: Map<String, String> = emptyMap(),
    val security: Security = Security.UNKNOWN,
    val vin: String? = null,
    val soc: Int? = null,
    val rangeKm: Int? = null,
    val locked: Boolean? = null,
    val trunkOpen: Boolean? = null,
    //: Двери и капот по отдельности голова отдаёт с 4.61: на C16 2026 из Car API,
    //: на C16 2024/2025 и C10 — из журнала CarControl. Старые головы их не шлют,
    //: тогда [bodyKnown] = false и экран не делает выводов о дверях.
    val hoodOpen: Boolean = false,
    val doors: Doors = Doors(),
    /** Машина прислала хоть одну дверь или багажник — можно судить, закрыто ли всё. */
    val bodyKnown: Boolean = false,
    /** Машина сообщает капот (C16 2026 не сообщает — тогда не пишем «капот закрыт»). */
    val hoodKnown: Boolean = false,
    val cabinTemp: Double? = null,
    val outsideTemp: Double? = null,
    val setTempLeft: Int? = null,
    val setTempRight: Int? = null,
    val fan: Int? = null,
    val acOn: Boolean? = null,
    val odometerKm: Int? = null,
    val speedKmh: Int? = null,
    /** Минут до полной зарядки; null — не заряжается или сигнала нет. */
    val chargeMinutes: Int? = null,
    /** Передача не P (gear ≠ 0): управление с телефона отключено, голова тоже не исполнит. */
    val notInPark: Boolean = false,
    /** Машина сейчас заряжается (по времени до полной или статусу зарядки). */
    val charging: Boolean = false,
    val location: GeoPoint? = null,
    /** Через сколько минут сервер сам погасит климат; 0 — не гасит. */
    val climateAutoOffMinutes: Int = 0,
    val updatedAt: Long = 0L,
) {
    val online: Boolean get() = link != Link.NONE

    /**
     * Значение сигнала по имени — для того, что ещё не вынесено в поля.
     * Ключи головы приходят как "имя(id)", VIN — просто "vin".
     */
    fun signal(name: String): String? = raw.sig(name)

    /** Что сейчас открыто — по-человечески, для предупреждений и ошибок. */
    fun openParts(): List<String> = listOfNotNull(
        if (doors.frontLeft) S("водительская дверь") else null,
        if (doors.frontRight) S("передняя пассажирская дверь") else null,
        if (doors.rearLeft) S("задняя левая дверь") else null,
        if (doors.rearRight) S("задняя правая дверь") else null,
        if (trunkOpen == true) S("багажник") else null,
        if (hoodOpen) S("капот") else null,
    )

    /** Открыта дверь или багажник — машину нельзя отпускать в сон («Отключиться»). */
    val doorOrTrunkOpen: Boolean get() = doors.anyOpen || trunkOpen == true

    companion object {
        fun fromCloud(dto: StatusDto, now: Long): CarState {
            // Значения сигналов приходят как есть — числа станут "23.0", и
            // разбирать их надо через toFloat, а не toInt.
            val raw = dto.raw.orEmpty()
                .mapValues { (_, v) -> v.toString() }
                .filterValues { it.isNotBlank() && it != "null" }

            fun num(name: String): Float? = raw.sig(name)?.toFloatOrNull()
            fun flag(name: String): Boolean? = raw.sig(name)?.let { it != "0" }
            // Голова отдаёт замок словами («locked» / «open») — «open» не ноль,
            // и общий flag() считал его запертым.
            fun lockFlag(): Boolean? = when (raw.sig("lock")?.lowercase()) {
                "locked", "1", "1.0" -> true
                "open", "unlocked", "0", "0.0" -> false
                else -> null
            }
            fun open(name: String): Boolean = flag(name) == true
            val bodyKeys = listOf("door_fl", "door_fr", "door_rl", "door_rr", "trunk")

            return CarState(
                link = if (dto.online) Link.CLOUD else Link.NONE,
                raw = raw,
                security = when (dto.security_state) {
                    "ARMED" -> Security.ARMED
                    "DISARMED" -> Security.DISARMED
                    else -> Security.UNKNOWN
                },
                soc = dto.battery_percent ?: num("soc")?.toInt(),
                // Запас хода и пробег машина считает сама; из сигналов берём
                // только когда сервер не дал готового поля.
                rangeKm = dto.range_km ?: num("range_ev")?.toInt() ?: num("range_total")?.toInt(),
                odometerKm = dto.odometer_km ?: num("odometer")?.toInt(),
                locked = when (dto.doors) {
                    "LOCKED" -> true
                    "UNLOCKED" -> false
                    else -> lockFlag()
                },
                trunkOpen = flag("trunk"),
                hoodOpen = open("hood"),
                doors = Doors(open("door_fl"), open("door_fr"), open("door_rl"), open("door_rr")),
                bodyKnown = bodyKeys.any { raw.sig(it) != null },
                hoodKnown = raw.sig("hood") != null,
                acOn = dto.climate_on,
                cabinTemp = num("cabin_temp")?.toDouble(),
                outsideTemp = num("outside_temp")?.toDouble(),
                setTempLeft = num("temp_l")?.toInt(),
                setTempRight = num("temp_r")?.toInt(),
                fan = num("fan")?.toInt(),
                speedKmh = num("speed")?.toInt(),
                // без кабеля машина отдаёт «нет значения» 16777215 — больше двух суток не бывает
                notInPark = num("gear")?.toInt()?.let { it != 0 } ?: false,
                chargeMinutes = num("charge_time")?.toInt()?.takeIf { it in 1..2880 },
                // Заряжается: есть время до полной; или статус зарядки QNX (143, 0 — нет);
                // или BMS_BATTCHARGERSTS на 2026 (1 на стоянке, 5 в движении).
                // "charging" (9900) — беспроводная зарядка телефона, не машины.
                // Статус главнее времени: на C16 2024 без кабеля 147 держит старую
                // оценку (200 мин при 143 = 0) — по времени судим, только если статуса нет.
                charging = when {
                    num("charge_status") != null -> num("charge_status") != 0f
                    num("charge_state") != null -> num("charge_state")?.toInt() in setOf(1, 5)
                    else -> (num("charge_time") ?: 0f).let { it > 0f && it <= 2880f }
                },
                // Точка — только когда есть обе координаты.
                location = dto.latitude?.let { lat ->
                    dto.longitude?.let { lon -> GeoPoint(lat, lon) }
                },
                climateAutoOffMinutes = dto.climate_auto_off_minutes,
                updatedAt = now,
            )
        }
    }
}

/**
 * Температура для показа: целые — без дробной части.
 *
 * Голова отдаёт «24.0», и без этого на экране стояло бы «в салоне 24.0°».
 * Дробь не отбрасываем совсем: половинки градуса машина отдаёт настоящие.
 */
fun Double.asTemp(): String =
    if (this == this.toInt().toDouble()) this.toInt().toString() else this.toString()

/** Ключи головы приходят как "имя(id)"; пустые значения считаем отсутствующими. */
internal fun Map<String, String>.sig(name: String): String? =
    (this[name] ?: entries.firstOrNull { it.key.startsWith("$name(") }?.value)
        ?.takeIf { it.isNotBlank() && it != "null" }
