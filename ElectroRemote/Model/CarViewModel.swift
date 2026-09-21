import Foundation
import SwiftUI

/// Шаги пробуждения машины перед входом в приложение.
enum ConnectPhase { case idle, sending, waiting, connected, timeout, error }

struct ConnectStatus: Equatable {
    var phase: ConnectPhase = .idle
    var message: String? = nil
    /// Сколько уже ждём отклика, секунд.
    var waitedSec: Int = 0
    /// Вошли, не дождавшись машины.
    var degraded: Bool = false
}

/// Чем закончилась команда — определяет вид снекбара.
enum EventKind { case success, failed, unsupported, offline }

/// Что показать пользователю после команды.
struct CmdEvent: Identifiable, Equatable {
    let id = UUID()
    let title: String
    let message: String?
    let kind: EventKind
    var ok: Bool { kind == .success }
}

/// Состояние приложения в одном месте — зеркало `CarViewModel.kt`.
@MainActor
final class CarViewModel: ObservableObject {
    let settings = Settings.shared
    private let repo: CarRepository

    @Published private(set) var car = CarState()
    /// Последнее отправленное значение по команде — для тех, у кого нет обратной связи.
    @Published private var optimistic: [Int: String] = [:] {
        didSet { settings.optimistic = optimistic }
    }
    /// Результат последней команды — экран показывает его снекбаром.
    @Published var event: CmdEvent? = nil
    @Published private(set) var busy = false
    /// Команды, которые прямо сейчас в пути — плитка показывает это сама.
    @Published private(set) var pending: Set<Int> = []
    @Published private(set) var loggedIn: Bool
    @Published private(set) var vehicles: [VehicleDto] = []
    @Published private(set) var vehicleId: String?
    @Published private(set) var parkNote: String? = nil
    @Published private(set) var parkKnown = false
    @Published private(set) var connectStatus = ConnectStatus()
    @Published private(set) var capabilities: CapabilitiesDto? = nil
    @Published private(set) var scenes: [SceneTemplateDto] = []
    @Published private(set) var schedules: [ClimateScheduleDto] = []
    @Published private(set) var voiceIntents: [VoiceIntentDto] = []
    @Published private(set) var support: SupportDto? = nil
    /// Лента новостей и уведомлений из админки.
    @Published private(set) var news: [NewsItem] = []
    @Published private(set) var newsRead: Set<String> = Settings.shared.newsRead
    /// Меняется при смене ника — чтобы список перерисовался.
    @Published private(set) var nickVersion = 0
    /// Цвет кузова выбранной машины (локально) — рендер на экранах.
    @Published private(set) var paint: String? = Settings.shared.vehicleId.flatMap { Settings.shared.vehiclePaint($0) }

    private var pollTask: Task<Void, Never>?
    private var connectTask: Task<Void, Never>?
    /// Идёт ли сейчас подключение — второй запуск поверх первого не нужен.
    private var connecting = false
    private var seatTimer: Task<Void, Never>?

    private static let wakeWaitSec = 20
    private static let tickNs: UInt64 = 250_000_000
    private static let pollCloudNs: UInt64 = 10_000_000_000
    private static let pollOfflineNs: UInt64 = 15_000_000_000
    private static let probeStepNs: UInt64 = 3_000_000_000

    /// Команды, состояние которых машина отдаёт обратно.
    private static let signalBacked: Set<Int> = Set([
        Cmd.AC, Cmd.TEMP_L, Cmd.TEMP_R, Cmd.FAN, Cmd.TRUNK,
        Cmd.RECIRC, Cmd.DEFROST_FRONT, Cmd.DEFROST_REAR, Cmd.MIRROR_HEAT,
        Cmd.SEAT_VENT_REAR_L, Cmd.SEAT_VENT_REAR_R,
        Cmd.MASSAGE_DRIVER, Cmd.MASSAGE_PASSENGER,
    ] + Cmd.WINDOWS)

    init() {
        repo = CarRepository(settings: settings)
        loggedIn = settings.loggedIn
        vehicleId = settings.vehicleId
        optimistic = settings.optimistic
        if settings.loggedIn { loadVehicles(); loadNews(); PushRegistrar.shared.enable() }
        loadSupport()
        PushRegistrar.shared.onNotification = { [weak self] in Task { @MainActor in self?.loadNews() } }
    }

    // --- новости и push ---

    var unreadNews: Int { news.filter { !newsRead.contains($0.id) }.count }

    func loadNews() { Task { news = await repo.news() } }

    func isRead(_ item: NewsItem) -> Bool { newsRead.contains(item.id) }

    func markRead(_ item: NewsItem) {
        newsRead.insert(item.id)
        settings.newsRead = newsRead
    }

    /// «Прочитать всё» — бейдж гаснет.
    func markAllRead() {
        newsRead.formUnion(news.map { $0.id })
        settings.newsRead = newsRead
    }

    var selectedVehicle: VehicleDto? { vehicles.first { $0.vehicle_id == vehicleId } }

    /// Фактические значения элементов управления: сигнал с машины, иначе эхо команды.
    var controls: [Int: String] {
        var merged = optimistic
        for type in Set(optimistic.keys).union(Self.signalBacked) {
            if let name = Cmd.signalFor(type), let v = car.signal(name) { merged[type] = v }
        }
        return merged
    }

    /// Есть ли чем будить: путь один — сервер с выполненным входом.
    var wakeConfigured: Bool { settings.cloudEnabled && settings.loggedIn }

    // --- подключение ---

    /// Будим машину и ждём отклика. Приложение открывается только после этого.
    func connect() {
        guard !connecting else { return }
        connecting = true
        connectTask = Task { [weak self] in
            guard let self else { return }
            defer { self.connecting = false }
            self.connectStatus = ConnectStatus(phase: .sending)

            let how: String
            switch await self.repo.wake() {
            case .sent(let h): how = L("Разбудили {0}", h)
            case .failed(let reason):
                self.connectStatus = ConnectStatus(phase: .error, message: scrubAddresses(reason) ?? L("Не удалось разбудить машину"))
                return
            case .noServer:
                self.connectStatus = ConnectStatus(phase: .error, message: L("Будить нечем: нет входа на сервер"))
                return
            }

            let startedAt = Date()
            func waited() -> Int { Int(Date().timeIntervalSince(startedAt)) }
            self.connectStatus = ConnectStatus(phase: .waiting, message: how, waitedSec: 0)

            // Отдельный тикер рисует секунды ровно, не дожидаясь опроса.
            let ticker = Task { [weak self] in
                while !Task.isCancelled {
                    try? await Task.sleep(nanoseconds: Self.tickNs)
                    guard let self, self.connectStatus.phase == .waiting else { break }
                    var c = self.connectStatus; c.waitedSec = waited(); self.connectStatus = c
                }
            }
            defer { ticker.cancel() }

            while !Task.isCancelled {
                let state = await self.repo.refresh()
                if Task.isCancelled { return }
                self.car = state
                if state.link != .none {
                    self.connectStatus = ConnectStatus(phase: .connected)
                    self.startPolling()
                    return
                }
                if waited() >= Self.wakeWaitSec { break }
                try? await Task.sleep(nanoseconds: Self.probeStepNs)
            }
            if Task.isCancelled { return }
            self.connectStatus = ConnectStatus(phase: .timeout, message: L("Машина не ответила за {0} с", Self.wakeWaitSec), waitedSec: waited())
        }
    }

    /// Отпустить машину в сон и вернуться на экран подключения.
    func disconnect() {
        Task {
            busy = true
            let result = await repo.sleep()
            busy = false
            let note: String
            switch result {
            case .sent: note = L("Машина отпущена в сон")
            case .failed(let r): note = L("Не отключилось: {0}", r)
            case .noServer: note = L("Не отключилось: нет входа на сервер")
            }
            resetConnection()
            connectStatus = ConnectStatus(phase: .idle, message: note)
        }
    }

    /// «Найти машину»: команда отключения три раза подряд, не уходя с экрана.
    func findCar() {
        Task {
            busy = true
            for _ in 0..<3 { _ = await repo.sleep() }
            busy = false
            connectStatus = ConnectStatus(phase: .idle, message: L("Команда отправлена 3 раза"))
        }
    }

    /// Войти, не дождавшись машины: данные будут последними известными.
    func enterAnyway() {
        connectTask?.cancel(); connectTask = nil
        connectStatus = ConnectStatus(phase: .connected, degraded: true)
        startPolling()
    }

    /// Вернуться на экран подключения — например, после смены настроек.
    func resetConnection() {
        connectTask?.cancel(); connectTask = nil
        stopPolling()
        connectStatus = ConnectStatus()
    }

    // --- опрос ---

    func startPolling() {
        guard connectStatus.phase == .connected else { return }
        loadMeta()
        if let t = pollTask, !t.isCancelled { return }
        pollTask = Task { [weak self] in
            while !Task.isCancelled {
                guard let self else { return }
                let state = await self.repo.refresh()
                if Task.isCancelled { return }
                self.car = state
                try? await Task.sleep(nanoseconds: state.link == .cloud ? Self.pollCloudNs : Self.pollOfflineNs)
            }
        }
    }

    func stopPolling() {
        pollTask?.cancel()
        pollTask = nil
    }

    func refreshNow() { Task { car = await repo.refresh() } }

    /// Включить климат с таймером авто-выключения (мин); nil — умолчание сервера.
    func climateOn(runMinutes: Int?) {
        Task {
            optimistic[Cmd.AC] = "1"
            pending.insert(Cmd.AC)
            let r = await repo.climateOn(runMinutes: runMinutes)
            pending.remove(Cmd.AC)
            switch r {
            case .ok:
                emit(.success, L("Климат включён"), (runMinutes ?? 0) > 0 ? L("Выключу через {0} мин", runMinutes!) : L("Выполнено"))
            case .failed(let reason):
                optimistic.removeValue(forKey: Cmd.AC)
                emit(.failed, L("Климат"), reason)
            case .unsupported(let reason):
                emit(.unsupported, L("Климат"), reason)
            }
        }
    }

    private func emit(_ kind: EventKind, _ title: String, _ message: String?) {
        // Ни в одном уведомлении не показываем адрес сайта/IP сервера.
        event = CmdEvent(title: title, message: scrubAddresses(message), kind: kind)
    }

    /// Возможности, сцены, расписание и голос — один раз при подключении.
    func loadMeta() {
        Task {
            capabilities = await repo.capabilities()
            scenes = await repo.scenes()
            schedules = await repo.climateSchedules()
            voiceIntents = await repo.voiceIntents()
            news = await repo.news()
        }
    }

    func refreshScenes() { Task { scenes = await repo.scenes() } }
    func refreshSchedules() { Task { schedules = await repo.climateSchedules() } }

    func createScene(name: String, steps: [SceneStepDto]) {
        Task {
            do {
                let created = try await repo.createScene(name: name, steps: steps)
                scenes = await repo.scenes()
                emit(.success, L("Сцена сохранена"), created.name)
            } catch { emit(.failed, L("Не сохранилось"), repo.reason(error)) }
        }
    }

    func deleteScene(_ templateId: String) {
        Task { try? await repo.deleteScene(templateId); scenes = await repo.scenes() }
    }

    func runScene(_ template: SceneTemplateDto) {
        Task {
            switch await repo.runScene(template.template_id) {
            case .ok: emit(.success, template.name, L("Выполнено"))
            case .failed(let r): emit(.failed, template.name, r)
            case .unsupported(let r): emit(.unsupported, template.name, r)
            }
        }
    }

    func createSchedule(_ body: ClimateScheduleRequest) {
        Task {
            do {
                _ = try await repo.createClimateSchedule(body)
                schedules = await repo.climateSchedules()
                emit(.success, L("Расписание сохранено"), nil)
            } catch { emit(.failed, L("Не сохранилось"), repo.reason(error)) }
        }
    }

    func setScheduleEnabled(_ scheduleId: String, _ enabled: Bool) {
        Task { try? await repo.setScheduleEnabled(scheduleId, enabled); schedules = await repo.climateSchedules() }
    }

    func deleteSchedule(_ scheduleId: String) {
        Task { try? await repo.deleteClimateSchedule(scheduleId); schedules = await repo.climateSchedules() }
    }

    func runVoice(_ intent: VoiceIntentDto) {
        Task {
            switch await repo.runVoice(intent.intent) {
            case .ok: emit(.success, intent.phrases.first ?? intent.intent, L("Выполнено"))
            case .failed(let r): emit(.failed, intent.intent, r)
            case .unsupported(let r): emit(.unsupported, intent.intent, r)
            }
        }
    }

    // --- команды ---

    func send(_ type: Int, _ value: String, label: String = "") {
        send([VehicleCommand(type: type, value: value, label: label)])
    }

    /// Отправляет пачку команд как одно действие: одно уведомление на результат.
    func send(_ cmds: [VehicleCommand], label: String = "") {
        guard !cmds.isEmpty else { return }
        Task {
            let previous = optimistic
            let types = Set(cmds.map { $0.type })
            // сразу показываем ожидаемое состояние, чтобы кнопка не «залипала»
            for c in cmds { optimistic[c.type] = c.value }
            pending.formUnion(types)
            busy = true

            // Команды пачки шлём параллельно, а не по очереди.
            let results: [CmdResult] = await withTaskGroup(of: (Int, CmdResult).self) { group in
                for (i, c) in cmds.enumerated() {
                    group.addTask { [repo] in (i, await repo.send(c)) }
                }
                var out = Array(repeating: CmdResult.ok, count: cmds.count)
                for await (i, r) in group { out[i] = r }
                return out
            }
            pending.subtract(types)
            busy = false

            let title = [label, cmds.first?.label ?? ""].first { !$0.isEmpty } ?? L("Команда")
            let failure = results.first { !$0.isOk }
            let failed = results.filter { !$0.isOk }.count
            if failure == nil {
                event = CmdEvent(title: title, message: L("Выполнено"), kind: .success)
                refreshNow()
            } else if failed < results.count {
                // Часть пачки не прошла — откатываем только непрошедшее.
                for (i, r) in results.enumerated() where !r.isOk {
                    let t = cmds[i].type
                    if let before = previous[t] { optimistic[t] = before } else { optimistic.removeValue(forKey: t) }
                }
                // Частичный неуспех человеку не показываем: что прошло — прошло,
                // непрошедшее откатили выше. Счётчик «N из M» только пугал.
                event = CmdEvent(title: title, message: L("Выполнено"), kind: .success)
                refreshNow()
            } else {
                optimistic = previous
                let kind: EventKind
                if case .unsupported = failure! { kind = .unsupported }
                else if car.link == .none { kind = .offline }
                else { kind = .failed }
                let reason: String
                switch failure! {
                case .failed(let r), .unsupported(let r): reason = r
                case .ok: reason = ""
                }
                event = CmdEvent(title: title, message: scrubAddresses(reason), kind: kind)
            }
        }
    }

    // --- помощь ---

    func loadSupport() { Task { if let s = await repo.support() { support = s } } }

    // --- сиденья по профилю ---

    /// Задан ли профиль сидений (есть хотя бы одно включённое место).
    func seatPresetSet() -> Bool { !readSeatPreset().levels.isEmpty }

    /// Сохранить набор с экрана сидений как профиль и сразу применить его.
    func applySeats(levels: [Int: Int], timerMin: Int) {
        saveSeatPreset(levels, timerMin)
        sendSeats(levels, timerMin)
    }

    /// Тумблер на главной: включить сиденья по сохранённому профилю.
    func seatsOn() {
        let (levels, timer) = readSeatPreset()
        if levels.isEmpty {
            event = CmdEvent(title: L("Сиденья"), message: L("Сначала настройте профиль на экране сидений"), kind: .failed)
            return
        }
        sendSeats(levels, timer)
    }

    /// Тумблер на главной: выключить все сиденья и снять таймер.
    func seatsOff() {
        seatTimer?.cancel(); seatTimer = nil
        send(Cmd.seatsOff(), label: L("Выключить сиденья"))
    }

    private func sendSeats(_ levels: [Int: Int], _ timerMin: Int) {
        // Шлём весь набор мест: нулевые снимают то, чего в профиле нет.
        let cmds = Cmd.SEAT_TYPES.map { VehicleCommand(type: $0, value: String(levels[$0] ?? 0)) }
        send(cmds, label: L("Сиденья"))
        startSeatTimer(timerMin)
    }

    private func startSeatTimer(_ min: Int) {
        seatTimer?.cancel(); seatTimer = nil
        guard min > 0 else { return }
        // Клиентский таймер: работает, пока приложение живо.
        seatTimer = Task { [weak self] in
            try? await Task.sleep(nanoseconds: UInt64(min) * 60_000_000_000)
            guard !Task.isCancelled, let self else { return }
            self.send(Cmd.seatsOff(), label: L("Сиденья: таймер"))
        }
    }

    private func saveSeatPreset(_ levels: [Int: Int], _ timerMin: Int) {
        var lv: [String: Int] = [:]
        for (t, l) in levels where l > 0 { lv[String(t)] = l }
        let obj: [String: Any] = ["levels": lv, "timer": max(0, min(60, timerMin))]
        if let data = try? JSONSerialization.data(withJSONObject: obj), let s = String(data: data, encoding: .utf8) {
            settings.seatPreset = s
        }
    }

    private func readSeatPreset() -> (levels: [Int: Int], timer: Int) {
        let raw = settings.seatPreset
        guard !raw.isEmpty, let obj = try? JSONSerialization.jsonObject(with: Data(raw.utf8)) as? [String: Any] else { return ([:], 0) }
        var map: [Int: Int] = [:]
        for (k, v) in (obj["levels"] as? [String: Any]) ?? [:] {
            if let t = Int(k), let l = v as? Int { map[t] = l }
        }
        return (map, (obj["timer"] as? Int) ?? 0)
    }

    // --- настройки и аккаунт ---

    func setCloudUrl(_ url: String) { settings.cloudUrl = url }
    func setCloudEnabled(_ enabled: Bool) { settings.cloudEnabled = enabled }

    func login(email: String, password: String, onDone: @escaping (String?) -> Void) {
        Task {
            busy = true
            do { try await signIn(email: email, password: password); busy = false; onDone(nil) }
            catch { busy = false; onDone(authError(error, fallback: L("Не удалось войти"))) }
        }
    }

    /// Вход с экрана входа.
    func signIn(email: String, password: String) async throws {
        do {
            try await repo.login(email: email, password: password)
            loggedIn = settings.loggedIn
            afterAuth()
        } catch {
            loggedIn = settings.loggedIn
            throw error
        }
    }

    func register(email: String, password: String, name: String, phone: String) async throws {
        do {
            try await repo.register(email: email, password: password, name: name, phone: phone)
            loggedIn = settings.loggedIn
            afterAuth()
        } catch {
            loggedIn = settings.loggedIn
            throw error
        }
    }

    func requestPasswordReset(email: String, contact: String) async throws {
        try await repo.requestPasswordReset(email: email, contact: contact)
    }

    /// Привязка машины по QR с экрана головы.
    func claimPairing(_ payload: String) async throws -> String {
        let note = try await Pairing.claim(payload, repo: repo)
        vehicleId = settings.vehicleId
        await loadVehiclesAsync()
        refreshNow()
        return note
    }

    /// Текст отказа для экранов входа.
    func authError(_ error: Error, fallback: String) -> String {
        if let api = error as? ApiError, let code = api.code {
            if code == 409 { return L("Этот email уже зарегистрирован — войдите или восстановите пароль") }
            if code == 422 { return L("Проверьте email и пароль (от 8 знаков)") }
            return repo.reason(error)
        }
        if let f = friendlyNetworkError(error) { return f }
        if let s = scrubAddresses(error.localizedDescription), !s.isEmpty { return s }
        return fallback
    }

    /// Общий хвост входа и регистрации.
    private func afterAuth() {
        vehicleId = settings.vehicleId
        loadVehicles()
        refreshNow()
        loadNews()
        PushRegistrar.shared.enable()
    }

    /// Удаление аккаунта: сервер стирает учётку, приложение выходит.
    func deleteAccount(password: String) async throws {
        do { try await repo.deleteAccount(password: password) }
        catch { throw RepoError(authError(error, fallback: L("Не удалось удалить аккаунт"))) }
        logout()
    }

    func logout() {
        Task { await PushRegistrar.shared.forget() }
        repo.logout()
        news = []
        loggedIn = false
        vehicles = []
        vehicleId = nil
        parkNote = nil
        parkKnown = false
    }

    // --- выбор машины ---

    func loadVehicles() { Task { await loadVehiclesAsync() } }

    /// Перечитать парк с сервера.
    func loadVehiclesAsync() async {
        guard settings.loggedIn else { return }
        do {
            let list = try await repo.vehicles()
            vehicles = list
            parkKnown = true
            parkNote = list.isEmpty ? L("Аккаунту не выдана ни одна машина — доступ выдаёт мастер") : nil
            let preferred = list.first { ($0.model ?? "").caseInsensitiveCompare("C16") == .orderedSame } ?? list.first
            if (vehicleId ?? "").isEmpty, let p = preferred { selectVehicle(p.vehicle_id) }
            // Машина, к которой доступ отозван, не должна оставаться выбранной.
            if !list.contains(where: { $0.vehicle_id == vehicleId }) {
                vehicleId = preferred?.vehicle_id
                settings.vehicleId = preferred?.vehicle_id
            }
        } catch {
            if repo.isUnauthorized(error) {
                repo.logout()
                loggedIn = false
                vehicles = []
                vehicleId = nil
                parkKnown = false
                parkNote = L("Сессия недействительна — войдите заново")
            } else {
                let why = friendlyNetworkError(error) ?? scrubAddresses(error.localizedDescription)
                parkNote = L("Не удалось получить список машин") + (why.map { ": \($0)" } ?? "")
            }
        }
    }

    func selectVehicle(_ id: String) {
        repo.selectVehicle(id)
        vehicleId = id
        paint = settings.vehiclePaint(id)
        refreshNow()
        // У новой машины может быть другая модель → другой набор кнопок.
        loadMeta()
    }

    /// Отвязать машину от аккаунта, затем перечитать парк.
    func unlinkVehicle(_ id: String) {
        Task {
            do {
                try await repo.unlinkVehicle(id)
                if vehicleId == id { vehicleId = nil; settings.vehicleId = nil }
                await loadVehiclesAsync()
                emit(.success, L("Машина отвязана"), nil)
            } catch { emit(.failed, L("Не удалось отвязать"), repo.reason(error)) }
        }
    }

    func vehicleNick(_ id: String) -> String? { settings.vehicleNick(id) }

    func setVehicleNick(_ id: String, _ name: String?) {
        settings.setVehicleNick(id, name)
        nickVersion += 1
    }

    func vehiclePaint(_ id: String) -> String? { settings.vehiclePaint(id) }

    func setVehiclePaint(_ id: String, _ code: String?) {
        settings.setVehiclePaint(id, code)
        if id == vehicleId { paint = code }
        nickVersion += 1
    }

    // --- отзыв ---

    /// Отправить отзыв с вложениями; nil — успех, иначе причина/предупреждение.
    /// `requested` — сколько файлов выбрал человек (часть могла не прочитаться или превысить лимит).
    func sendFeedback(kind: String, text: String, files: [CarRepository.FeedbackFile], requested: Int) async -> String? {
        do {
            let r = try await repo.sendFeedback(kind: kind, text: text, appVersion: appVersion(), files: files)
            let lost = r.failed + (requested - files.count)
            if lost > 0 { return L("Отзыв отправлен, но {0} из {1} вложений не приложились (слишком большие или нет связи)", lost, requested) }
            return nil
        } catch { return repo.reason(error) }
    }
}
