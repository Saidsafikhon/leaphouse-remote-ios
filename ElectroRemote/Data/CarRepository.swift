import Foundation

/// Чем закончилась попытка разбудить машину через сервер.
enum WakeResult {
    /// Команда принята; `how` — каким путём машину будили.
    case sent(String)
    /// Сервер ответил отказом или не ответил вовсе.
    case failed(String)
    /// Сервер как путь недоступен: отключён в настройках или нет входа.
    case noServer
}

/// Ошибка с человекочитаемой причиной — для Result из репозитория.
struct RepoError: LocalizedError {
    let message: String
    let code: Int?
    init(_ message: String, code: Int? = nil) { self.message = message; self.code = code }
    var errorDescription: String? { message }
}

/// Единая точка доступа к машине — через наш сервер, и только через него.
final class CarRepository {
    private let settings: Settings
    private let cloud: CloudClient

    init(settings: Settings) {
        self.settings = settings
        self.cloud = CloudClient(settings: settings)
    }

    /// Снять состояние с сервера: он спрашивает машину сам.
    func refresh() async -> CarState {
        if Demo.enabled { return Demo.state() }
        let now = nowMillis()
        var empty = CarState(); empty.updatedAt = now
        guard settings.cloudEnabled, let id = await resolveVehicleId() else { return empty }
        do { return CarState.fromCloud(try await cloud.status(id), now: now) } catch { return empty }
    }

    /// Отправить команду. Путь один — сервер.
    func send(_ cmd: VehicleCommand) async -> CmdResult {
        if Demo.enabled { return .ok }
        guard settings.cloudEnabled else { return .failed(L("Сервер отключён в настройках")) }
        guard settings.loggedIn else { return .failed(L("Вход в аккаунт не выполнен")) }
        guard let id = await resolveVehicleId() else { return .failed(L("Машина не выбрана")) }

        // Замок и климат — своими эндпоинтами; остальное — одной командой кузова.
        do {
            let dto: CommandDto
            switch (cmd.type, cmd.value) {
            case (Cmd.LOCK, "1"): dto = try await cloud.unlock(id)
            case (Cmd.LOCK, "0"): dto = try await cloud.lock(id)
            case (Cmd.AC, "1"): dto = try await cloud.climateOn(id)
            case (Cmd.AC, "0"): dto = try await cloud.climateOff(id)
            default: dto = try await cloud.headControl(id, HeadControlRequest(type: cmd.type, value: cmd.value))
            }
            return Self.outcome(dto)
        } catch {
            return .failed(reasonOf(error))
        }
    }

    /// Включить климат с таймером авто-выключения; nil — умолчание сервера.
    func climateOn(runMinutes: Int?) async -> CmdResult {
        if Demo.enabled { return .ok }
        guard settings.cloudEnabled else { return .failed(L("Сервер отключён в настройках")) }
        guard settings.loggedIn else { return .failed(L("Вход в аккаунт не выполнен")) }
        guard let id = await resolveVehicleId() else { return .failed(L("Машина не выбрана")) }
        var req = CommandRequest()
        if let m = runMinutes { req.params = ["run_minutes": .number(Double(m))] }
        do { return Self.outcome(try await cloud.climateOn(id, req)) } catch { return .failed(reasonOf(error)) }
    }

    /// SUCCESS_UNVERIFIED — тоже успех: голова подтвердила приём.
    private static func outcome(_ dto: CommandDto) -> CmdResult {
        dto.status.hasPrefix("SUCCESS") ? .ok : .failed((dto.error?.isEmpty == false ? dto.error : nil) ?? dto.status)
    }

    // --- пробуждение ---

    /// Контакты поддержки (открытый эндпоинт). nil — не достали.
    func support() async -> SupportDto? { Demo.enabled ? Demo.support : try? await cloud.support() }
    func products() async -> [ProductDto] { (try? await cloud.products()) ?? [] }
    /// Заявка на товар; nil — отправлено, иначе текст ошибки.
    func order(_ req: OrderRequest) async -> String? {
        do { _ = try await cloud.order(req); return nil } catch { return reasonOf(error) }
    }

    /// Файл к отзыву, уже прочитанный в память.
    struct FeedbackFile { let name: String; let mime: String; let data: Data }
    /// Что вышло: сколько вложений долетело и сколько отвалилось.
    struct FeedbackOutcome { let id: String; let attached: Int; let failed: Int }

    /// Отзыв в админку; вложения уходят следом по одному, отзыв без них всё равно отправлен.
    func sendFeedback(kind: String, text: String, appVersion: String, files: [FeedbackFile]) async throws -> FeedbackOutcome {
        if Demo.enabled { return FeedbackOutcome(id: "demo", attached: files.count, failed: 0) }
        let created = try await cloud.sendFeedback(FeedbackRequest(
            kind: kind, text: text, vehicle_id: settings.vehicleId.flatMap { $0.isEmpty ? nil : $0 }, app_version: appVersion))
        var failed = 0
        for f in files {
            do { _ = try await cloud.attachToFeedback(created.id, name: f.name, mime: f.mime, data: f.data) } catch { failed += 1 }
        }
        return FeedbackOutcome(id: created.id, attached: files.count - failed, failed: failed)
    }

    func wake() async -> WakeResult {
        if Demo.enabled { return .sent(L("через пробуждалку в машине")) }
        guard settings.cloudEnabled, settings.loggedIn else { return .noServer }
        guard let id = await resolveVehicleId() else { return .noServer }
        do {
            let dto = try await cloud.wake(id)
            if dto.status.hasPrefix("SUCCESS") { return .sent(channelName(dto.channel)) }
            return .failed((dto.error?.isEmpty == false ? dto.error : nil) ?? dto.status)
        } catch { return .failed(reasonOf(error)) }
    }

    /// Отпустить машину обратно в сон — через ту же железку.
    func sleep() async -> WakeResult {
        if Demo.enabled { return .sent(L("через пробуждалку в машине")) }
        guard settings.cloudEnabled, settings.loggedIn else { return .noServer }
        guard let id = await resolveVehicleId() else { return .noServer }
        do {
            let dto = try await cloud.sleep(id)
            if dto.status.hasPrefix("SUCCESS") { return .sent(channelName(dto.channel)) }
            return .failed((dto.error?.isEmpty == false ? dto.error : nil) ?? dto.status)
        } catch { return .failed(reasonOf(error)) }
    }

    private func channelName(_ channel: String?) -> String {
        switch channel {
        case "WAKE_DEVICE": return L("через пробуждалку в машине")
        case "LEAPMOTOR_CLOUD": return L("через облако")
        default: return L("через сервер")
        }
    }

    /// Причина отказа человеческим языком: из тела 5xx/4xx (`error`/`detail`),
    /// иначе обобщённая сетевая причина без адресов.
    func reasonOf(_ error: Error) -> String {
        if case ApiError.http(_, let body) = error, !body.isEmpty,
           let obj = try? JSONSerialization.jsonObject(with: Data(body.utf8)) as? [String: Any] {
            let text = (obj["error"] as? String).flatMap { $0.isEmpty ? nil : $0 }
                ?? (obj["detail"] as? String).flatMap { $0.isEmpty ? nil : $0 }
            if let text { return scrubAddresses(text) ?? text }
        }
        if let f = friendlyNetworkError(error) { return f }
        if let e = error as? ApiError { return scrubAddresses(e.errorDescription) ?? L("Сервер недоступен") }
        return scrubAddresses(error.localizedDescription) ?? L("Сервер недоступен")
    }

    func reason(_ error: Error) -> String { reasonOf(error) }

    // --- вход в аккаунт ---

    func login(email: String, password: String) async throws {
        if Demo.enabled { settings.token = "demo"; settings.email = email; settings.vehicleId = Demo.vehicle.vehicle_id; return }
        let token = try await cloud.login(LoginRequest(email: email, password: password)).access_token
        settings.token = token
        settings.email = email
        _ = await resolveVehicleId()
    }

    /// Регистрация. Сервер сразу отдаёт токен — второй раз входить не нужно.
    func register(email: String, password: String, name: String?, phone: String?) async throws {
        let created = try await cloud.register(RegisterRequest(
            email: email, password: password,
            name: (name?.isEmpty ?? true) ? nil : name,
            phone: (phone?.isEmpty ?? true) ? nil : phone
        ))
        settings.token = created.access_token
        settings.email = created.user.email
    }

    func requestPasswordReset(email: String, contact: String?) async throws {
        _ = try await cloud.requestPasswordReset(PasswordResetRequest(email: email, contact: (contact?.isEmpty ?? true) ? nil : contact))
    }

    /// Погасить код с QR и получить доступ к машине; она сразу становится выбранной.
    func claimPairing(code: String) async throws -> VehicleDto {
        let v = try await cloud.pairClaim(PairClaimRequest(code: code))
        settings.vehicleId = v.vehicle_id
        return v
    }

    /// Удалить свой аккаунт на сервере (необратимо). После — локальный выход.
    func deleteAccount(password: String) async throws {
        try await cloud.deleteAccount(AccountDeleteRequest(current_password: password))
        settings.logout()
    }

    func news() async -> [NewsItem] { Demo.enabled ? Demo.news : ((try? await cloud.news()) ?? []) }
    func newsPublic() async -> [NewsItem] { Demo.enabled ? Demo.news : ((try? await cloud.newsPublic()) ?? []) }

    func logout() { settings.logout() }

    // --- парк ---

    func vehicles() async throws -> [VehicleDto] { Demo.enabled ? [Demo.vehicle] : try await cloud.vehicles() }

    func isUnauthorized(_ error: Error) -> Bool { (error as? ApiError)?.code == 401 }

    func selectVehicle(_ vehicleId: String) { settings.vehicleId = vehicleId }

    /// vehicle_id из настроек или подбирается сам: из парка выбирается C16.
    private func resolveVehicleId() async -> String? {
        if let id = settings.vehicleId, !id.isEmpty { return id }
        guard settings.loggedIn else { return nil }
        let all = (try? await cloud.vehicles()) ?? []
        let mine = all.first { ($0.model ?? "").caseInsensitiveCompare("C16") == .orderedSame } ?? all.first
        guard let mine else { return nil }
        settings.vehicleId = mine.vehicle_id
        return mine.vehicle_id
    }

    // --- возможности, сцены, расписание, голос ---

    func capabilities() async -> CapabilitiesDto? {
        if Demo.enabled { return Demo.capabilities }
        guard let id = await resolveVehicleId() else { return nil }
        return try? await cloud.capabilities(id)
    }

    func scenes() async -> [SceneTemplateDto] {
        if Demo.enabled { return Demo.scenes }
        guard let id = await resolveVehicleId() else { return [] }
        return (try? await cloud.scenes(id)) ?? []
    }

    func createScene(name: String, steps: [SceneStepDto]) async throws -> SceneTemplateDto {
        guard let id = await resolveVehicleId() else { throw RepoError(L("Машина не выбрана")) }
        return try await cloud.createScene(id, SceneTemplateRequest(name: name, steps: steps))
    }

    func deleteScene(_ templateId: String) async throws {
        guard let id = await resolveVehicleId() else { throw RepoError(L("Машина не выбрана")) }
        try await cloud.deleteScene(id, templateId)
    }

    func runScene(_ templateId: String) async -> CmdResult {
        guard let id = await resolveVehicleId() else { return .failed(L("Машина не выбрана")) }
        do { return Self.outcome(try await cloud.runScene(id, templateId)) } catch { return .failed(reasonOf(error)) }
    }

    func climateSchedules() async -> [ClimateScheduleDto] {
        if Demo.enabled { return Demo.schedules }
        guard let id = await resolveVehicleId() else { return [] }
        return (try? await cloud.climateSchedules(id)) ?? []
    }

    func createClimateSchedule(_ body: ClimateScheduleRequest) async throws -> ClimateScheduleDto {
        guard let id = await resolveVehicleId() else { throw RepoError(L("Машина не выбрана")) }
        return try await cloud.createClimateSchedule(id, body)
    }

    func setScheduleEnabled(_ scheduleId: String, _ enabled: Bool) async throws {
        guard let id = await resolveVehicleId() else { throw RepoError(L("Машина не выбрана")) }
        _ = try await cloud.setScheduleEnabled(id, scheduleId, enabled)
    }

    func deleteClimateSchedule(_ scheduleId: String) async throws {
        guard let id = await resolveVehicleId() else { throw RepoError(L("Машина не выбрана")) }
        try await cloud.deleteClimateSchedule(id, scheduleId)
    }

    /// Отвязать машину от аккаунта. Сама машина остаётся, снимается только доступ.
    func unlinkVehicle(_ vehicleId: String) async throws { try await cloud.unlinkVehicle(vehicleId) }

    func voiceIntents() async -> [VoiceIntentDto] { Demo.enabled ? Demo.voice : ((try? await cloud.voiceIntents()) ?? []) }

    func runVoice(_ intent: String) async -> CmdResult {
        guard let id = await resolveVehicleId() else { return .failed(L("Машина не выбрана")) }
        do { return Self.outcome(try await cloud.voice(id, intent)) } catch { return .failed(reasonOf(error)) }
    }
}

/// Разбор QR с экрана машины: `electro://pair?c=<код>&h=<адрес головы>`.
/// Параметр `h` остался от прежней схемы и игнорируется.
enum Pairing {
    static func parse(_ payload: String) -> String? {
        guard let comps = URLComponents(string: payload.trimmingCharacters(in: .whitespacesAndNewlines)),
              comps.scheme == "electro" else { return nil }
        return comps.queryItems?.first(where: { $0.name == "c" })?.value
    }

    /// Привязывает машину по отсканированному коду; результат — текст для показа.
    static func claim(_ payload: String, repo: CarRepository) async throws -> String {
        guard let code = parse(payload) else { throw RepoError(L("Это не QR машины Electro")) }
        if code.trimmingCharacters(in: .whitespaces).isEmpty { throw RepoError(L("В коде нет кода привязки")) }
        do {
            let v = try await repo.claimPairing(code: code)
            return L("Машина привязана: {0}", v.name)
        } catch {
            throw RepoError(repo.reason(error), code: (error as? ApiError)?.code)
        }
    }
}
