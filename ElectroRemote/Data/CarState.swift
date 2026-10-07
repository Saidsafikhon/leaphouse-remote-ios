import Foundation

/// Канал, по которому сейчас доступна машина.
enum CarLink { case none, cloud }

struct GeoPoint: Equatable {
    let lat: Double
    let lon: Double
    var bearing: Double? = nil
}

struct Doors: Equatable {
    var frontLeft = false
    var frontRight = false
    var rearLeft = false
    var rearRight = false
    var anyOpen: Bool { frontLeft || frontRight || rearLeft || rearRight }
}

/// Охрана. Отдельно от замков: «заперто» не значит «на охране».
enum Security { case armed, disarmed, unknown }

/// Разобранное состояние машины — зеркало `CarState.kt`.
struct CarState: Equatable {
    var link: CarLink = .none
    var raw: [String: String] = [:]
    var security: Security = .unknown
    var vin: String? = nil
    var soc: Int? = nil
    var rangeKm: Int? = nil
    var locked: Bool? = nil
    var trunkOpen: Bool? = nil
    /// Двери и капот по отдельности — с головы 4.61 (C16 2026: Car API; C16 2024/2025 и C10: журнал CarControl).
    var hoodOpen: Bool = false
    var doors = Doors()
    /// Машина прислала хоть одну дверь или багажник — можно судить, закрыто ли всё.
    var bodyKnown = false
    /// Машина сообщает капот (C16 2026 не сообщает — тогда не пишем «капот закрыт»).
    var hoodKnown: Bool = false
    var cabinTemp: Double? = nil
    var outsideTemp: Double? = nil
    var setTempLeft: Int? = nil
    var setTempRight: Int? = nil
    var fan: Int? = nil
    var acOn: Bool? = nil
    var odometerKm: Int? = nil
    var speedKmh: Int? = nil
    var chargeMinutes: Int? = nil
    /// Передача не P (gear ≠ 0): управление с телефона отключено, голова тоже не исполнит.
    var notInPark: Bool = false
    /// Машина сейчас заряжается (по времени до полной или статусу зарядки).
    var charging = false
    var location: GeoPoint? = nil
    var climateAutoOffMinutes: Int = 0
    /// Время снимка, мс от эпохи; 0 — ещё не снимали.
    var updatedAt: Int64 = 0

    var online: Bool { link != .none }

    /// Значение сигнала по имени; ключи головы приходят как "имя(id)".
    func signal(_ name: String) -> String? { raw.sig(name) }

    /// Что сейчас открыто — по-человечески, для предупреждений и ошибок.
    func openParts() -> [String] {
        var out: [String] = []
        if doors.frontLeft { out.append(L("водительская дверь")) }
        if doors.frontRight { out.append(L("передняя пассажирская дверь")) }
        if doors.rearLeft { out.append(L("задняя левая дверь")) }
        if doors.rearRight { out.append(L("задняя правая дверь")) }
        if trunkOpen == true { out.append(L("багажник")) }
        if hoodOpen { out.append(L("капот")) }
        return out
    }

    /// Открыта дверь или багажник — машину нельзя отпускать в сон («Отключиться»).
    var doorOrTrunkOpen: Bool { doors.anyOpen || trunkOpen == true }

    static func fromCloud(_ dto: StatusDto, now: Int64) -> CarState {
        var raw: [String: String] = [:]
        for (k, v) in dto.raw ?? [:] {
            let t = v.text
            if !t.isEmpty && t != "null" { raw[k] = t }
        }
        func num(_ n: String) -> Double? { raw.sig(n).flatMap { Double($0) } }
        func flag(_ n: String) -> Bool? { raw.sig(n).map { $0 != "0" } }
        func open(_ n: String) -> Bool { flag(n) == true }
        // Голова отдаёт замок словами («locked» / «open») — «open» не ноль.
        func lockFlag() -> Bool? {
            switch raw.sig("lock")?.lowercased() {
            case "locked", "1", "1.0": return true
            case "open", "unlocked", "0", "0.0": return false
            default: return nil
            }
        }

        var s = CarState()
        s.link = dto.online ? .cloud : .none
        s.raw = raw
        switch dto.security_state {
        case "ARMED": s.security = .armed
        case "DISARMED": s.security = .disarmed
        default: s.security = .unknown
        }
        s.soc = dto.battery_percent ?? num("soc").map { Int($0) }
        s.rangeKm = dto.range_km ?? num("range_ev").map { Int($0) } ?? num("range_total").map { Int($0) }
        s.odometerKm = dto.odometer_km ?? num("odometer").map { Int($0) }
        switch dto.doors {
        case "LOCKED": s.locked = true
        case "UNLOCKED": s.locked = false
        default: s.locked = lockFlag()
        }
        s.trunkOpen = flag("trunk")
        s.hoodOpen = open("hood")
        s.doors = Doors(frontLeft: open("door_fl"), frontRight: open("door_fr"),
                        rearLeft: open("door_rl"), rearRight: open("door_rr"))
        s.hoodKnown = raw.sig("hood") != nil
        s.bodyKnown = ["door_fl", "door_fr", "door_rl", "door_rr", "trunk"].contains { raw.sig($0) != nil }
        s.acOn = dto.climate_on
        s.cabinTemp = num("cabin_temp")
        s.outsideTemp = num("outside_temp")
        s.setTempLeft = num("temp_l").map { Int($0) }
        s.setTempRight = num("temp_r").map { Int($0) }
        s.fan = num("fan").map { Int($0) }
        s.speedKmh = num("speed").map { Int($0) }
        // без кабеля машина отдаёт «нет значения» 16777215 — больше двух суток не бывает
        s.notInPark = num("gear").map { Int($0) != 0 } ?? false
        s.chargeMinutes = num("charge_time").map { Int($0) }.flatMap { (1...2880).contains($0) ? $0 : nil }
        // Заряжается: время до полной; или статус зарядки QNX (143, 0 — нет);
        // или BMS_BATTCHARGERSTS на 2026 (1 на стоянке, 5 в движении).
        // "charging" (9900) — беспроводная зарядка телефона, не машины.
        // Статус главнее времени: на C16 2024 без кабеля 147 держит старую
        // оценку (200 мин при 143 = 0) — по времени судим, только если статуса нет.
        if let st = num("charge_status") { s.charging = st != 0 }
        else if let st = num("charge_state") { s.charging = [1, 5].contains(Int(st)) }
        else { s.charging = (num("charge_time") ?? 0) > 0 && (num("charge_time") ?? 0) <= 2880 }
        if !s.charging { s.chargeMinutes = nil }
        if let lat = dto.latitude, let lon = dto.longitude { s.location = GeoPoint(lat: lat, lon: lon) }
        s.climateAutoOffMinutes = dto.climate_auto_off_minutes ?? 0
        s.updatedAt = now
        return s
    }
}

extension Double {
    /// Температура для показа: целые — без дробной части («24», не «24.0»).
    var asTemp: String {
        self == self.rounded() ? String(Int(self)) : String(self)
    }
}

extension Dictionary where Key == String, Value == String {
    /// Ключи головы приходят как "имя(id)"; пустые значения считаем отсутствующими.
    func sig(_ name: String) -> String? {
        let v = self[name] ?? self.first(where: { $0.key.hasPrefix(name + "(") })?.value
        guard let v, !v.isEmpty, v != "null" else { return nil }
        return v
    }
}

func nowMillis() -> Int64 { Int64(Date().timeIntervalSince1970 * 1000) }
