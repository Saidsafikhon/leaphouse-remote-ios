package uz.electro.remote.data

/**
 * Карта команд кузова Leapmotor C16 (type для ILpCarControl.sendValue).
 * Значения проверены на машине — менять только вместе с проверкой на железе.
 *
 * Здесь остались только те типы, которые действительно уходят в машину. Свет,
 * плафоны, складывание зеркал, направление обдува и режимы массажа убраны
 * вместе с кнопками: часть из них шла значениями, которые на машине не
 * сверяли, а остальное дистанционно не нужно.
 */
object Cmd {
    // кузов
    const val LOCK = 8257554          // 1 = открыть двери, 0 = закрыть
    const val TRUNK = 196615          // 1 = открыть, 0 = закрыть
    const val WINDOW_FL = 196609      // 0 = поднять, 100 = опустить
    const val WINDOW_FR = 196610
    const val WINDOW_RL = 196611
    const val WINDOW_RR = 196612
    const val SUNSHADE = 196614

    val WINDOWS = listOf(WINDOW_FL, WINDOW_FR, WINDOW_RL, WINDOW_RR)

    // климат
    const val AC = 65537              // 1 / 0
    const val AC_MAX_COOL = 65540
    const val DEFROST_FRONT = 65541   // 2 = макс, 0 = выкл
    const val DEFROST_REAR = 65542
    const val RECIRC = 65545
    const val TEMP_L = 65574          // 16..32
    const val TEMP_R = 65575
    const val FAN = 65577             // 0..7
    //: направление обдува. Порядок ступеней как в интерфейсе GWM — лицо, ноги,
    //: всё; сами значения на нашей машине не сверяли, поэтому подписи есть, а
    //: цифры остаются под проверку на стенде.
    const val BLOW_MODE = 65538
    const val BLOW_FACE = "1"
    const val BLOW_FEET = "2"
    const val BLOW_ALL = "3"
    //: обогрев зеркал кнопки не имеет — он входит в «выключить всё», чтобы
    //: уходящий от машины человек не оставил его греть
    const val MIRROR_HEAT = 393217

    // сиденья (0..3)
    const val SEAT_HEAT_DRIVER = 262145
    const val SEAT_VENT_DRIVER = 262146
    const val SEAT_HEAT_PASSENGER = 262147
    const val SEAT_VENT_PASSENGER = 262148
    const val SEAT_HEAT_REAR_L = 262149
    const val SEAT_VENT_REAR_L = 262150
    const val SEAT_HEAT_REAR_R = 262151
    const val SEAT_VENT_REAR_R = 262152
    //: массаж кнопки не имеет по той же причине, что и обогрев зеркал
    const val MASSAGE_DRIVER = 262153
    const val MASSAGE_PASSENGER = 262160

    /**
     * Имя сигнала в /state, по которому читается фактическое состояние команды.
     * Если null — голова обратной связи не отдаёт, состояние только оптимистичное.
     */
    fun signalFor(type: Int): String? = when (type) {
        AC -> "ac"
        TEMP_L -> "temp_l"
        TEMP_R -> "temp_r"
        FAN -> "fan"
        TRUNK -> "trunk"
        // окна голова отдаёт не на всех прошивках; если сигнала нет,
        // состояние остаётся оптимистичным — по последней команде
        WINDOW_FL -> "window_fl"
        WINDOW_FR -> "window_fr"
        WINDOW_RL -> "window_rl"
        WINDOW_RR -> "window_rr"
        RECIRC -> "recirc"
        DEFROST_FRONT -> "defrost_f"
        DEFROST_REAR -> "defrost_r"
        MIRROR_HEAT -> "mirror_heat"   // голова отдаёт strCarMirrorHeart под ключом mirror_heat (было mir_heat → кнопка не выключалась)
        SEAT_VENT_REAR_L -> "seat_vent_rl"
        SEAT_VENT_REAR_R -> "seat_vent_rr"
        MASSAGE_DRIVER -> "massage_drv"
        MASSAGE_PASSENGER -> "massage_pas"
        else -> null
    }

    //: край шкалы климата. Ниже 18 и выше 32 у машины не градусы, а режимы:
    //: LO — дуть холодным на максимум, HI — греть на максимум.
    val TEMP_MIN = 18
    val TEMP_MAX = 32
    val FAN_MAX = 7

    /**
     * «Выключить всё»: погасить климат целиком, а не один кондиционер.
     *
     * Человек уходит от машины и хочет, чтобы ничего не осталось работать —
     * поэтому сюда входят и обогревы, и сиденья, и массаж, а не только AC.
     * Обдув гасим последним: он общий для всей системы, и если выключить его
     * первым, обогревы успеют включить его обратно.
     */
    fun allOff(): List<VehicleCommand> = listOf(
        VehicleCommand(AC, "0", "Кондиционер"),
        VehicleCommand(AC_MAX_COOL, "0", "Макс. охлаждение"),
        VehicleCommand(DEFROST_FRONT, "0", "Обогрев лобового"),
        VehicleCommand(DEFROST_REAR, "0", "Обогрев заднего"),
        VehicleCommand(MIRROR_HEAT, "0", "Обогрев зеркал"),
        VehicleCommand(RECIRC, "0", "Циркуляция"),
        VehicleCommand(SEAT_HEAT_DRIVER, "0", "Подогрев сиденья водителя"),
        VehicleCommand(SEAT_HEAT_PASSENGER, "0", "Подогрев сиденья пассажира"),
        VehicleCommand(SEAT_HEAT_REAR_L, "0", "Подогрев заднего левого"),
        VehicleCommand(SEAT_HEAT_REAR_R, "0", "Подогрев заднего правого"),
        VehicleCommand(SEAT_VENT_DRIVER, "0", "Вентиляция сиденья водителя"),
        VehicleCommand(SEAT_VENT_PASSENGER, "0", "Вентиляция сиденья пассажира"),
        VehicleCommand(SEAT_VENT_REAR_L, "0", "Вентиляция заднего левого"),
        VehicleCommand(SEAT_VENT_REAR_R, "0", "Вентиляция заднего правого"),
        VehicleCommand(MASSAGE_DRIVER, "0", "Массаж водителя"),
        VehicleCommand(MASSAGE_PASSENGER, "0", "Массаж пассажира"),
        VehicleCommand(FAN, "0", "Обдув"),
    )

    /**
     * Выключить ТОЛЬКО климат — без сидений и массажа.
     *
     * Сиденья и массаж — независимая система комфорта: гасить их вместе с
     * кондиционером неверно (пользователь грел сиденье, выключил AC — сиденье
     * должно остаться). Поэтому тумблер климата шлёт этот набор, а не [allOff].
     */
    fun climateOff(): List<VehicleCommand> = listOf(
        VehicleCommand(AC, "0", "Кондиционер"),
        VehicleCommand(AC_MAX_COOL, "0", "Макс. охлаждение"),
        VehicleCommand(DEFROST_FRONT, "0", "Обогрев лобового"),
        VehicleCommand(DEFROST_REAR, "0", "Обогрев заднего"),
        VehicleCommand(MIRROR_HEAT, "0", "Обогрев зеркал"),
        VehicleCommand(RECIRC, "0", "Циркуляция"),
        VehicleCommand(FAN, "0", "Обдув"),
    )

    // ---- Составные сцены климата (кнопки-тумблеры во вкладке «Климат») ----
    // Второе нажатие выключает всё, что сцена включила (в т.ч. сам AC).
    private val SEAT_HEATS = listOf(SEAT_HEAT_DRIVER, SEAT_HEAT_PASSENGER, SEAT_HEAT_REAR_L, SEAT_HEAT_REAR_R)
    private val SEAT_VENTS = listOf(SEAT_VENT_DRIVER, SEAT_VENT_PASSENGER, SEAT_VENT_REAR_L, SEAT_VENT_REAR_R)

    /** Макс. обогрев: HI + вентилятор макс + обдув лобового макс + обогрев зеркал и
     *  заднего стекла + все подогревы сидений на 3. Включает AC. */
    fun maxHeat(on: Boolean): List<VehicleCommand> = if (on) buildList {
        add(VehicleCommand(AC, "1", "Климат"))
        add(VehicleCommand(TEMP_L, "$TEMP_MAX", "Температура")); add(VehicleCommand(TEMP_R, "$TEMP_MAX", "Температура"))
        add(VehicleCommand(FAN, "$FAN_MAX", "Обдув"))
        add(VehicleCommand(DEFROST_FRONT, "2", "Обдув лобового"))
        add(VehicleCommand(DEFROST_REAR, "1", "Обогрев заднего стекла"))
        add(VehicleCommand(MIRROR_HEAT, "1", "Обогрев зеркал"))
        SEAT_HEATS.forEach { add(VehicleCommand(it, "3", "Подогрев сиденья")) }
    } else buildList {
        add(VehicleCommand(FAN, "0", "Обдув"))
        add(VehicleCommand(DEFROST_FRONT, "0", "Обдув лобового"))
        add(VehicleCommand(DEFROST_REAR, "0", "Обогрев заднего стекла"))
        add(VehicleCommand(MIRROR_HEAT, "0", "Обогрев зеркал"))
        SEAT_HEATS.forEach { add(VehicleCommand(it, "0", "Подогрев сиденья")) }
        add(VehicleCommand(AC, "0", "Климат"))
    }

    /** Макс. охлаждение: LO + MAX A/C + обдув всех сидений на 3. Включает AC. */
    fun maxCool(on: Boolean): List<VehicleCommand> = if (on) buildList {
        add(VehicleCommand(AC, "1", "Климат"))
        add(VehicleCommand(AC_MAX_COOL, "1", "Макс. охлаждение"))
        add(VehicleCommand(TEMP_L, "$TEMP_MIN", "Температура")); add(VehicleCommand(TEMP_R, "$TEMP_MIN", "Температура"))
        SEAT_VENTS.forEach { add(VehicleCommand(it, "3", "Обдув сиденья")) }
    } else buildList {
        add(VehicleCommand(AC_MAX_COOL, "0", "Макс. охлаждение"))
        SEAT_VENTS.forEach { add(VehicleCommand(it, "0", "Обдув сиденья")) }
        add(VehicleCommand(AC, "0", "Климат"))
    }

    /** Циркуляция: включает рециркуляцию и приоткрывает все окна на 25%; выкл — рецирк off и окна закрыть. */
    fun recircScene(on: Boolean): List<VehicleCommand> = if (on) buildList {
        add(VehicleCommand(RECIRC, "1", "Циркуляция"))
        WINDOWS.forEach { add(VehicleCommand(it, "25", "Окно")) }
    } else buildList {
        add(VehicleCommand(RECIRC, "0", "Циркуляция"))
        WINDOWS.forEach { add(VehicleCommand(it, "0", "Окно")) }
    }

    /** Обогрев стёкол: заднее стекло + зеркала + HI + обдув лобового макс. Включает AC. */
    /**
     * «Обогрев всех стёкол»: зеркала + обдув лобового + обогрев заднего.
     * Климат включаем (без него обдув не пойдёт), но уставку не трогаем.
     * Выключение гасит только эти три — климат остаётся, как был.
     */
    fun defogGlass(on: Boolean): List<VehicleCommand> = if (on) listOf(
        VehicleCommand(AC, "1", "Климат"),
        VehicleCommand(MIRROR_HEAT, "1", "Обогрев зеркал"),
        VehicleCommand(DEFROST_FRONT, "2", "Обдув лобового"),
        VehicleCommand(DEFROST_REAR, "1", "Обогрев заднего стекла"),
    ) else listOf(
        VehicleCommand(MIRROR_HEAT, "0", "Обогрев зеркал"),
        VehicleCommand(DEFROST_FRONT, "0", "Обдув лобового"),
        VehicleCommand(DEFROST_REAR, "0", "Обогрев заднего стекла"),
    )

    /** Выключить все сиденья (обогрев и обдув всех мест). */
    fun seatsOff(): List<VehicleCommand> = listOf(
        SEAT_HEAT_DRIVER, SEAT_HEAT_PASSENGER, SEAT_HEAT_REAR_L, SEAT_HEAT_REAR_R,
        SEAT_VENT_DRIVER, SEAT_VENT_PASSENGER, SEAT_VENT_REAR_L, SEAT_VENT_REAR_R,
    ).map { VehicleCommand(it, "0") }

    /** Типы сидений (обогрев+обдув всех мест) — для чтения/сборки профиля. */
    val SEAT_TYPES: List<Int> = listOf(
        SEAT_HEAT_DRIVER, SEAT_HEAT_PASSENGER, SEAT_HEAT_REAR_L, SEAT_HEAT_REAR_R,
        SEAT_VENT_DRIVER, SEAT_VENT_PASSENGER, SEAT_VENT_REAR_L, SEAT_VENT_REAR_R,
    )

    /** Как показать заданную температуру: края шкалы — это LO и HI, а не числа. */
    fun tempLabel(value: Int): String = when {
        value <= TEMP_MIN -> "LO"
        value >= TEMP_MAX -> "HI"
        else -> "$value°"
    }
}

/** Одна команда к машине; label уходит в уведомление о результате. */
data class VehicleCommand(val type: Int, val value: String, val label: String = "")

/** Результат отправки — экраны показывают его пользователю, а не молчат. */
sealed interface CmdResult {
    data object Ok : CmdResult
    data class Failed(val reason: String) : CmdResult
    data class Unsupported(val reason: String) : CmdResult

    val ok: Boolean get() = this is Ok
}
