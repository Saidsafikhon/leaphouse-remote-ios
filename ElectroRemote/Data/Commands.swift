import Foundation

/// Одна команда к машине; label уходит в уведомление о результате.
struct VehicleCommand: Equatable {
    let type: Int
    let value: String
    var label: String = ""
}

/// Результат отправки — экраны показывают его пользователю, а не молчат.
enum CmdResult: Equatable {
    case ok
    case failed(String)
    case unsupported(String)

    var isOk: Bool { if case .ok = self { return true } else { return false } }
}

/// Карта команд кузова Leapmotor C16 (type для ILpCarControl.sendValue).
/// Значения проверены на машине — менять только вместе с проверкой на железе.
enum Cmd {
    // кузов
    static let LOCK = 8257554          // 1 = открыть двери, 0 = закрыть
    static let TRUNK = 196615          // 1 = открыть, 0 = закрыть
    static let WINDOW_FL = 196609      // 0 = поднять, 100 = опустить
    static let WINDOW_FR = 196610
    static let WINDOW_RL = 196611
    static let WINDOW_RR = 196612
    static let SUNSHADE = 196614

    static let WINDOWS = [WINDOW_FL, WINDOW_FR, WINDOW_RL, WINDOW_RR]

    // климат
    static let AC = 65537              // 1 / 0
    static let AC_MAX_COOL = 65540
    static let DEFROST_FRONT = 65541   // 2 = макс, 0 = выкл
    static let DEFROST_REAR = 65542
    static let RECIRC = 65545
    static let TEMP_L = 65574          // 16..32
    static let TEMP_R = 65575
    static let FAN = 65577             // 0..7
    static let BLOW_MODE = 65538
    static let MIRROR_HEAT = 393217

    // сиденья (0..3)
    static let SEAT_HEAT_DRIVER = 262145
    static let SEAT_VENT_DRIVER = 262146
    static let SEAT_HEAT_PASSENGER = 262147
    static let SEAT_VENT_PASSENGER = 262148
    static let SEAT_HEAT_REAR_L = 262149
    static let SEAT_VENT_REAR_L = 262150
    static let SEAT_HEAT_REAR_R = 262151
    static let SEAT_VENT_REAR_R = 262152
    static let MASSAGE_DRIVER = 262153
    static let MASSAGE_PASSENGER = 262160

    /// Имя сигнала в /state, по которому читается фактическое состояние команды.
    static func signalFor(_ type: Int) -> String? {
        switch type {
        case AC: return "ac"
        case TEMP_L: return "temp_l"
        case TEMP_R: return "temp_r"
        case FAN: return "fan"
        case TRUNK: return "trunk"
        case WINDOW_FL: return "window_fl"
        case WINDOW_FR: return "window_fr"
        case WINDOW_RL: return "window_rl"
        case WINDOW_RR: return "window_rr"
        case RECIRC: return "recirc"
        case DEFROST_FRONT: return "defrost_f"
        case DEFROST_REAR: return "defrost_r"
        case MIRROR_HEAT: return "mirror_heat"
        case SEAT_VENT_REAR_L: return "seat_vent_rl"
        case SEAT_VENT_REAR_R: return "seat_vent_rr"
        case MASSAGE_DRIVER: return "massage_drv"
        case MASSAGE_PASSENGER: return "massage_pas"
        default: return nil
        }
    }

    /// Край шкалы климата: ниже 18 и выше 32 у машины не градусы, а режимы LO/HI.
    static let TEMP_MIN = 18
    static let TEMP_MAX = 32
    static let FAN_MAX = 7

    static let SEAT_HEATS = [SEAT_HEAT_DRIVER, SEAT_HEAT_PASSENGER, SEAT_HEAT_REAR_L, SEAT_HEAT_REAR_R]
    static let SEAT_VENTS = [SEAT_VENT_DRIVER, SEAT_VENT_PASSENGER, SEAT_VENT_REAR_L, SEAT_VENT_REAR_R]

    /// Типы сидений (обогрев+обдув всех мест) — для чтения/сборки профиля.
    static let SEAT_TYPES = SEAT_HEATS + SEAT_VENTS

    /// «Выключить всё»: климат целиком, включая обогревы, сиденья и массаж.
    static func allOff() -> [VehicleCommand] {
        [
            VehicleCommand(type: AC, value: "0", label: "Кондиционер"),
            VehicleCommand(type: AC_MAX_COOL, value: "0", label: "Макс. охлаждение"),
            VehicleCommand(type: DEFROST_FRONT, value: "0", label: "Обогрев лобового"),
            VehicleCommand(type: DEFROST_REAR, value: "0", label: "Обогрев заднего"),
            VehicleCommand(type: MIRROR_HEAT, value: "0", label: "Обогрев зеркал"),
            VehicleCommand(type: RECIRC, value: "0", label: "Циркуляция"),
            VehicleCommand(type: SEAT_HEAT_DRIVER, value: "0", label: "Подогрев сиденья водителя"),
            VehicleCommand(type: SEAT_HEAT_PASSENGER, value: "0", label: "Подогрев сиденья пассажира"),
            VehicleCommand(type: SEAT_HEAT_REAR_L, value: "0", label: "Подогрев заднего левого"),
            VehicleCommand(type: SEAT_HEAT_REAR_R, value: "0", label: "Подогрев заднего правого"),
            VehicleCommand(type: SEAT_VENT_DRIVER, value: "0", label: "Вентиляция сиденья водителя"),
            VehicleCommand(type: SEAT_VENT_PASSENGER, value: "0", label: "Вентиляция сиденья пассажира"),
            VehicleCommand(type: SEAT_VENT_REAR_L, value: "0", label: "Вентиляция заднего левого"),
            VehicleCommand(type: SEAT_VENT_REAR_R, value: "0", label: "Вентиляция заднего правого"),
            VehicleCommand(type: MASSAGE_DRIVER, value: "0", label: "Массаж водителя"),
            VehicleCommand(type: MASSAGE_PASSENGER, value: "0", label: "Массаж пассажира"),
            VehicleCommand(type: FAN, value: "0", label: "Обдув"),
        ]
    }

    /// Выключить ТОЛЬКО климат — без сидений и массажа.
    static func climateOff() -> [VehicleCommand] {
        [
            VehicleCommand(type: AC, value: "0", label: "Кондиционер"),
            VehicleCommand(type: AC_MAX_COOL, value: "0", label: "Макс. охлаждение"),
            VehicleCommand(type: DEFROST_FRONT, value: "0", label: "Обогрев лобового"),
            VehicleCommand(type: DEFROST_REAR, value: "0", label: "Обогрев заднего"),
            VehicleCommand(type: MIRROR_HEAT, value: "0", label: "Обогрев зеркал"),
            VehicleCommand(type: RECIRC, value: "0", label: "Циркуляция"),
            VehicleCommand(type: FAN, value: "0", label: "Обдув"),
        ]
    }

    /// Макс. обогрев: HI + вентилятор макс + обдув лобового + зеркала + заднее + подогревы на 3.
    static func maxHeat(_ on: Bool) -> [VehicleCommand] {
        if on {
            var l: [VehicleCommand] = [
                VehicleCommand(type: AC, value: "1", label: "Климат"),
                VehicleCommand(type: TEMP_L, value: String(TEMP_MAX), label: "Температура"),
                VehicleCommand(type: TEMP_R, value: String(TEMP_MAX), label: "Температура"),
                VehicleCommand(type: FAN, value: String(FAN_MAX), label: "Обдув"),
                VehicleCommand(type: DEFROST_FRONT, value: "2", label: "Обдув лобового"),
                VehicleCommand(type: DEFROST_REAR, value: "1", label: "Обогрев заднего стекла"),
                VehicleCommand(type: MIRROR_HEAT, value: "1", label: "Обогрев зеркал"),
            ]
            l += SEAT_HEATS.map { VehicleCommand(type: $0, value: "3", label: "Подогрев сиденья") }
            return l
        } else {
            var l: [VehicleCommand] = [
                VehicleCommand(type: FAN, value: "0", label: "Обдув"),
                VehicleCommand(type: DEFROST_FRONT, value: "0", label: "Обдув лобового"),
                VehicleCommand(type: DEFROST_REAR, value: "0", label: "Обогрев заднего стекла"),
                VehicleCommand(type: MIRROR_HEAT, value: "0", label: "Обогрев зеркал"),
            ]
            l += SEAT_HEATS.map { VehicleCommand(type: $0, value: "0", label: "Подогрев сиденья") }
            l.append(VehicleCommand(type: AC, value: "0", label: "Климат"))
            return l
        }
    }

    /// Макс. охлаждение: LO + MAX A/C + обдув всех сидений на 3.
    static func maxCool(_ on: Bool) -> [VehicleCommand] {
        if on {
            var l: [VehicleCommand] = [
                VehicleCommand(type: AC, value: "1", label: "Климат"),
                VehicleCommand(type: AC_MAX_COOL, value: "1", label: "Макс. охлаждение"),
                VehicleCommand(type: TEMP_L, value: String(TEMP_MIN), label: "Температура"),
                VehicleCommand(type: TEMP_R, value: String(TEMP_MIN), label: "Температура"),
            ]
            l += SEAT_VENTS.map { VehicleCommand(type: $0, value: "3", label: "Обдув сиденья") }
            return l
        } else {
            var l: [VehicleCommand] = [VehicleCommand(type: AC_MAX_COOL, value: "0", label: "Макс. охлаждение")]
            l += SEAT_VENTS.map { VehicleCommand(type: $0, value: "0", label: "Обдув сиденья") }
            l.append(VehicleCommand(type: AC, value: "0", label: "Климат"))
            return l
        }
    }

    /// Циркуляция: рециркуляция + окна на 25%; выкл — рецирк off и окна закрыть.
    static func recircScene(_ on: Bool) -> [VehicleCommand] {
        var l = [VehicleCommand(type: RECIRC, value: on ? "1" : "0", label: "Циркуляция")]
        l += WINDOWS.map { VehicleCommand(type: $0, value: on ? "25" : "0", label: "Окно") }
        return l
    }

    /// «Обогрев всех стёкол»: зеркала + обдув лобового + обогрев заднего.
    /// Климат включаем (без него обдув не пойдёт), уставку не трогаем;
    /// выключение гасит только эти три — климат остаётся, как был.
    static func defogGlass(_ on: Bool) -> [VehicleCommand] {
        if on {
            return [
                VehicleCommand(type: AC, value: "1", label: "Климат"),
                VehicleCommand(type: MIRROR_HEAT, value: "1", label: "Обогрев зеркал"),
                VehicleCommand(type: DEFROST_FRONT, value: "2", label: "Обдув лобового"),
                VehicleCommand(type: DEFROST_REAR, value: "1", label: "Обогрев заднего стекла"),
            ]
        } else {
            return [
                VehicleCommand(type: MIRROR_HEAT, value: "0", label: "Обогрев зеркал"),
                VehicleCommand(type: DEFROST_FRONT, value: "0", label: "Обдув лобового"),
                VehicleCommand(type: DEFROST_REAR, value: "0", label: "Обогрев заднего стекла"),
            ]
        }
    }

    /// Выключить все сиденья (обогрев и обдув всех мест).
    static func seatsOff() -> [VehicleCommand] {
        SEAT_TYPES.map { VehicleCommand(type: $0, value: "0") }
    }

    /// Как показать заданную температуру: края шкалы — это LO и HI.
    static func tempLabel(_ value: Int) -> String {
        if value <= TEMP_MIN { return "LO" }
        if value >= TEMP_MAX { return "HI" }
        return "\(value)°"
    }
}
