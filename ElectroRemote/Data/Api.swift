import Foundation

// MARK: - Произвольное JSON-значение (для `raw` статуса и `params` команд)

enum JSONValue: Codable, Equatable {
    case string(String)
    case number(Double)
    case bool(Bool)
    case null
    case array([JSONValue])
    case object([String: JSONValue])

    init(from decoder: Decoder) throws {
        let c = try decoder.singleValueContainer()
        if c.decodeNil() { self = .null; return }
        if let b = try? c.decode(Bool.self) { self = .bool(b); return }
        if let n = try? c.decode(Double.self) { self = .number(n); return }
        if let s = try? c.decode(String.self) { self = .string(s); return }
        if let a = try? c.decode([JSONValue].self) { self = .array(a); return }
        if let o = try? c.decode([String: JSONValue].self) { self = .object(o); return }
        throw DecodingError.dataCorruptedError(in: c, debugDescription: L("неизвестный JSON"))
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.singleValueContainer()
        switch self {
        case .string(let s): try c.encode(s)
        case .number(let n): try c.encode(n)
        case .bool(let b): try c.encode(b)
        case .null: try c.encodeNil()
        case .array(let a): try c.encode(a)
        case .object(let o): try c.encode(o)
        }
    }

    /// Строковое представление как в Android (`v.toString()`): числа — "23.0".
    var text: String {
        switch self {
        case .string(let s): return s
        case .number(let n): return n == n.rounded() && abs(n) < 1e15 ? String(format: "%.1f", n) : String(n)
        case .bool(let b): return b ? "true" : "false"
        case .null: return "null"
        case .array, .object: return ""
        }
    }
}

// MARK: - Модели (зеркало схем backend)

struct LoginRequest: Encodable { let email: String; let password: String }
struct TokenResponse: Decodable { let access_token: String; let token_type: String?; let expires_in: Int? }

struct RegisterRequest: Encodable {
    let email: String
    let password: String
    let name: String?
    let phone: String?
}

struct UserDto: Decodable {
    let user_id: String
    let email: String
    let role: String
    let is_active: Bool
    let name: String?
    let phone: String?
}

struct RegisterResponse: Decodable { let user: UserDto; let access_token: String; let expires_in: Int? }

struct PasswordResetRequest: Encodable { let email: String; let contact: String? }
struct AccountDeleteRequest: Encodable { let current_password: String }
struct PushTokenBody: Encodable { let platform: String; let token: String }
struct PushTokenRemove: Encodable { let token: String }

/// Новость или уведомление из админки.
struct NewsItem: Decodable, Identifiable, Equatable {
    let id: String
    let title: String
    let body: String
    let kind: String      // info | news | alert
    let created_at: String
    var category: String = ""        // update | guide | event | news | ""
    var source: String? = nil        // nil — своя новость оператора; иначе имя RSS-источника
    var image_url: String? = nil
    var link: String? = nil
    var lang: String = "ru"

    private enum CodingKeys: String, CodingKey { case id, title, body, kind, created_at, category, source, image_url, link, lang }
    init(from d: Decoder) throws {
        let c = try d.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        title = try c.decode(String.self, forKey: .title)
        body = try c.decodeIfPresent(String.self, forKey: .body) ?? ""
        kind = try c.decodeIfPresent(String.self, forKey: .kind) ?? "info"
        created_at = try c.decode(String.self, forKey: .created_at)
        category = try c.decodeIfPresent(String.self, forKey: .category) ?? ""
        source = try c.decodeIfPresent(String.self, forKey: .source)
        image_url = try c.decodeIfPresent(String.self, forKey: .image_url)
        link = try c.decodeIfPresent(String.self, forKey: .link)
        lang = try c.decodeIfPresent(String.self, forKey: .lang) ?? "ru"
    }
}
struct PasswordResetAccepted: Decodable { let detail: String? }

struct PairClaimRequest: Encodable { let code: String }

/// Отзыв из приложения: kind = bug | idea | other.
struct FeedbackRequest: Encodable {
    let kind: String
    let text: String
    let vehicle_id: String?
    var app: String = "phone"
    let app_version: String?
}
struct FeedbackCreated: Decodable { let id: String; let created_at: String }
struct AttachmentDto: Decodable { let id: String; let name: String; let content_type: String; let size: Int }

struct VehicleDto: Decodable, Identifiable, Equatable {
    let vehicle_id: String
    let vin: String
    let name: String
    let provider: String
    let remote_control_enabled: Bool
    let model: String?
    var id: String { vehicle_id }
}

struct SupportDto: Decodable, Equatable {
    var phone: String = ""
    var telegram: String = ""
    var instagram: String = ""
    var site: String = ""

    private enum CodingKeys: String, CodingKey { case phone, telegram, instagram, site }
    init() {}
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        phone = (try? c.decodeIfPresent(String.self, forKey: .phone)) ?? ""
        telegram = (try? c.decodeIfPresent(String.self, forKey: .telegram)) ?? ""
        instagram = (try? c.decodeIfPresent(String.self, forKey: .instagram)) ?? ""
        site = (try? c.decodeIfPresent(String.self, forKey: .site)) ?? ""
    }
    var any: Bool { !phone.isEmpty || !telegram.isEmpty || !instagram.isEmpty || !site.isEmpty }
}

struct StatusDto: Decodable {
    let vehicle_id: String
    let security_state: String
    let doors: String
    let battery_percent: Int?
    let online: Bool
    let climate_on: Bool
    let headunit_awake: Bool?
    let observed_at: String?
    let source: String?
    let raw: [String: JSONValue]?
    let range_km: Int?
    let odometer_km: Int?
    let latitude: Double?
    let longitude: Double?
    let climate_auto_off_minutes: Int?
}

struct HeadControlRequest: Encodable {
    let type: Int
    let value: String
    var command_id: String = UUID().uuidString
    var wait: Bool = true
}

struct CommandRequest: Encodable {
    var command_id: String = UUID().uuidString
    var force_channel: String? = nil
    var params: [String: JSONValue] = [:]
    var wait: Bool = true
}

struct CommandDto: Decodable {
    let command_id: String
    let vehicle_id: String?
    let command: String?
    let channel: String?
    let status: String
    let created_at: String?
    let completed_at: String?
    let compensated: Bool?
    let error: String?
}

struct CapabilitiesDto: Decodable, Equatable {
    var quick: [String] = []
    var groups: [String] = []
    var voice: Bool = false
    var scenes: Bool = false
    var climate_schedule: Bool = false

    private enum CodingKeys: String, CodingKey { case quick, groups, voice, scenes, climate_schedule }
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        quick = (try? c.decodeIfPresent([String].self, forKey: .quick)) ?? []
        groups = (try? c.decodeIfPresent([String].self, forKey: .groups)) ?? []
        voice = (try? c.decodeIfPresent(Bool.self, forKey: .voice)) ?? false
        scenes = (try? c.decodeIfPresent(Bool.self, forKey: .scenes)) ?? false
        climate_schedule = (try? c.decodeIfPresent(Bool.self, forKey: .climate_schedule)) ?? false
    }
}

struct SceneStepDto: Codable, Equatable, Hashable {
    let type: Int
    let value: String
    var title: String = ""
    var required: Bool = true
}

struct SceneTemplateDto: Decodable, Identifiable, Equatable {
    let template_id: String
    let name: String
    let steps: [SceneStepDto]
    var id: String { template_id }
}

struct SceneTemplateRequest: Encodable { let name: String; let steps: [SceneStepDto] }

struct ClimateScheduleDto: Decodable, Identifiable, Equatable {
    let schedule_id: String
    let hour: Int
    let minute: Int
    var weekdays: [Int] = []
    var temp_c: Int = 22
    var enabled: Bool = true
    var id: String { schedule_id }

    private enum CodingKeys: String, CodingKey { case schedule_id, hour, minute, weekdays, temp_c, enabled }
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        schedule_id = try c.decode(String.self, forKey: .schedule_id)
        hour = try c.decode(Int.self, forKey: .hour)
        minute = try c.decode(Int.self, forKey: .minute)
        weekdays = (try? c.decodeIfPresent([Int].self, forKey: .weekdays)) ?? []
        temp_c = (try? c.decodeIfPresent(Int.self, forKey: .temp_c)) ?? 22
        enabled = (try? c.decodeIfPresent(Bool.self, forKey: .enabled)) ?? true
    }
}

struct ClimateScheduleRequest: Encodable {
    let hour: Int
    let minute: Int
    var weekdays: [Int] = []
    var temp_c: Int = 22
    var enabled: Bool = true
}

struct EnabledRequest: Encodable { let enabled: Bool }

struct VoiceIntentDto: Decodable, Identifiable, Equatable {
    let intent: String
    let subsystem: String?
    var phrases: [String] = []
    var id: String { intent }

    private enum CodingKeys: String, CodingKey { case intent, subsystem, phrases }
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        intent = try c.decode(String.self, forKey: .intent)
        subsystem = try? c.decodeIfPresent(String.self, forKey: .subsystem)
        phrases = (try? c.decodeIfPresent([String].self, forKey: .phrases)) ?? []
    }
}

struct VoiceRequestBody: Encodable {
    let intent: String
    var command_id: String = UUID().uuidString
    var wait: Bool = true
}

// MARK: - Ошибки

enum ApiError: LocalizedError {
    /// Ответ с кодом не 2xx; `body` — сырое тело (там `error`/`detail`).
    case http(Int, String)
    case decode
    case badUrl

    var errorDescription: String? {
        switch self {
        case .http(let code, _): return "HTTP \(code)"
        case .decode: return L("Ошибка разбора ответа")
        case .badUrl: return L("Неверный адрес сервера")
        }
    }

    var code: Int? { if case .http(let c, _) = self { return c } else { return nil } }
}

private struct Empty: Decodable {}

// MARK: - Клиент backend

/// Базовый адрес и токен берутся из `Settings` на каждый запрос.
final class CloudClient {
    private let settings: Settings
    private let session: URLSession

    // Подключение — быстро; ответ бывает долгим: /wake и /sleep сервер держит,
    // пока пробуждалка не подтвердит приём (~10 с и больше при ретраях).
    private static let connectTimeout: TimeInterval = 10
    private static let readTimeout: TimeInterval = 40

    init(settings: Settings) {
        self.settings = settings
        let cfg = URLSessionConfiguration.default
        cfg.timeoutIntervalForRequest = Self.readTimeout
        cfg.timeoutIntervalForResource = Self.readTimeout + Self.connectTimeout
        cfg.waitsForConnectivity = false
        session = URLSession(configuration: cfg)
    }

    private func url(_ path: String) throws -> URL {
        guard let u = URL(string: settings.cloudUrl + path) else { throw ApiError.badUrl }
        return u
    }

    private func perform<T: Decodable>(_ method: String, _ path: String, body: (any Encodable)? = nil) async throws -> T {
        var req = URLRequest(url: try url(path))
        req.httpMethod = method
        req.setValue("application/json", forHTTPHeaderField: "Accept")
        if let token = settings.token, !token.isEmpty {
            req.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        if let body {
            req.setValue("application/json", forHTTPHeaderField: "Content-Type")
            req.httpBody = try JSONEncoder().encode(AnyEncodable(body))
        }
        let (data, resp) = try await session.data(for: req)
        let code = (resp as? HTTPURLResponse)?.statusCode ?? 0
        guard (200..<300).contains(code) else {
            throw ApiError.http(code, String(data: data, encoding: .utf8) ?? "")
        }
        if T.self == Empty.self { return Empty() as! T }
        do { return try JSONDecoder().decode(T.self, from: data) } catch { throw ApiError.decode }
    }

    // --- auth ---
    func login(_ body: LoginRequest) async throws -> TokenResponse { try await perform("POST", "api/v1/auth/login", body: body) }
    func register(_ body: RegisterRequest) async throws -> RegisterResponse { try await perform("POST", "api/v1/auth/register", body: body) }
    func requestPasswordReset(_ body: PasswordResetRequest) async throws -> PasswordResetAccepted { try await perform("POST", "api/v1/auth/password-reset", body: body) }
    func pairClaim(_ body: PairClaimRequest) async throws -> VehicleDto { try await perform("POST", "api/v1/pairing/claim", body: body) }
    func deleteAccount(_ body: AccountDeleteRequest) async throws { let _: Empty = try await perform("POST", "api/v1/auth/delete-account", body: body) }
    func registerPushToken(platform: String, token: String) async throws { let _: Empty = try await perform("POST", "api/v1/auth/push-token", body: PushTokenBody(platform: platform, token: token)) }
    func removePushToken(_ token: String) async throws { let _: Empty = try await perform("DELETE", "api/v1/auth/push-token", body: PushTokenRemove(token: token)) }
    func news() async throws -> [NewsItem] { try await perform("GET", "api/v1/news?limit=50") }
    /// Лента без входа — только новости «для всех» (экран логина).
    func newsPublic() async throws -> [NewsItem] { try await perform("GET", "api/v1/news/public?limit=50") }
    func support() async throws -> SupportDto { try await perform("GET", "api/v1/agent/support") }

    // --- отзывы ---
    func sendFeedback(_ body: FeedbackRequest) async throws -> FeedbackCreated { try await perform("POST", "api/v1/feedback", body: body) }

    /// Вложение — сырой файл телом запроса, тип в Content-Type (сервер без multipart).
    func attachToFeedback(_ feedbackId: String, name: String, mime: String, data: Data) async throws -> AttachmentDto {
        let q = name.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? "file"
        var req = URLRequest(url: try url("api/v1/feedback/\(feedbackId)/attachments?name=\(q)"))
        req.httpMethod = "POST"
        req.setValue("application/json", forHTTPHeaderField: "Accept")
        req.setValue(mime, forHTTPHeaderField: "Content-Type")
        if let token = settings.token, !token.isEmpty { req.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization") }
        // запись экрана — десятки мегабайт по мобильной сети
        req.timeoutInterval = 180
        let (respData, resp) = try await session.upload(for: req, from: data)
        let code = (resp as? HTTPURLResponse)?.statusCode ?? 0
        guard (200..<300).contains(code) else { throw ApiError.http(code, String(data: respData, encoding: .utf8) ?? "") }
        do { return try JSONDecoder().decode(AttachmentDto.self, from: respData) } catch { throw ApiError.decode }
    }

    // --- машины ---
    func vehicles() async throws -> [VehicleDto] { try await perform("GET", "api/v1/vehicles") }
    func status(_ id: String) async throws -> StatusDto { try await perform("GET", "api/v1/vehicles/\(id)/status?force_refresh=true") }
    func lock(_ id: String, _ b: CommandRequest = CommandRequest()) async throws -> CommandDto { try await perform("POST", "api/v1/vehicles/\(id)/lock", body: b) }
    func unlock(_ id: String, _ b: CommandRequest = CommandRequest()) async throws -> CommandDto { try await perform("POST", "api/v1/vehicles/\(id)/unlock", body: b) }
    func headControl(_ id: String, _ b: HeadControlRequest) async throws -> CommandDto { try await perform("POST", "api/v1/vehicles/\(id)/head/control", body: b) }
    func wake(_ id: String, _ b: CommandRequest = CommandRequest()) async throws -> CommandDto { try await perform("POST", "api/v1/vehicles/\(id)/wake", body: b) }
    func sleep(_ id: String, _ b: CommandRequest = CommandRequest()) async throws -> CommandDto { try await perform("POST", "api/v1/vehicles/\(id)/sleep", body: b) }
    func climateOn(_ id: String, _ b: CommandRequest = CommandRequest()) async throws -> CommandDto { try await perform("POST", "api/v1/vehicles/\(id)/climate/on", body: b) }
    func climateOff(_ id: String, _ b: CommandRequest = CommandRequest()) async throws -> CommandDto { try await perform("POST", "api/v1/vehicles/\(id)/climate/off", body: b) }
    func unlinkVehicle(_ id: String) async throws { let _: Empty = try await perform("DELETE", "api/v1/vehicles/\(id)/grant") }
    func capabilities(_ id: String) async throws -> CapabilitiesDto { try await perform("GET", "api/v1/vehicles/\(id)/capabilities") }

    // --- сцены, расписание, голос ---
    func scenes(_ id: String) async throws -> [SceneTemplateDto] { try await perform("GET", "api/v1/vehicles/\(id)/scenes/templates") }
    func createScene(_ id: String, _ b: SceneTemplateRequest) async throws -> SceneTemplateDto { try await perform("POST", "api/v1/vehicles/\(id)/scenes/templates", body: b) }
    func deleteScene(_ id: String, _ tid: String) async throws { let _: Empty = try await perform("DELETE", "api/v1/vehicles/\(id)/scenes/templates/\(tid)") }
    func runScene(_ id: String, _ tid: String) async throws -> CommandDto { try await perform("POST", "api/v1/vehicles/\(id)/scenes/templates/\(tid)/run", body: CommandRequest()) }
    func climateSchedules(_ id: String) async throws -> [ClimateScheduleDto] { try await perform("GET", "api/v1/vehicles/\(id)/climate/schedules") }
    func createClimateSchedule(_ id: String, _ b: ClimateScheduleRequest) async throws -> ClimateScheduleDto { try await perform("POST", "api/v1/vehicles/\(id)/climate/schedules", body: b) }
    func setScheduleEnabled(_ id: String, _ sid: String, _ enabled: Bool) async throws -> ClimateScheduleDto { try await perform("POST", "api/v1/vehicles/\(id)/climate/schedules/\(sid)/enabled", body: EnabledRequest(enabled: enabled)) }
    func deleteClimateSchedule(_ id: String, _ sid: String) async throws { let _: Empty = try await perform("DELETE", "api/v1/vehicles/\(id)/climate/schedules/\(sid)") }
    func voiceIntents() async throws -> [VoiceIntentDto] { try await perform("GET", "api/v1/vehicles/voice") }
    func voice(_ id: String, _ intent: String) async throws -> CommandDto { try await perform("POST", "api/v1/vehicles/\(id)/voice", body: VoiceRequestBody(intent: intent)) }
}

/// Обёртка, чтобы кодировать `any Encodable` одним JSONEncoder.
private struct AnyEncodable: Encodable {
    let value: any Encodable
    init(_ value: any Encodable) { self.value = value }
    func encode(to encoder: Encoder) throws { try value.encode(to: encoder) }
}
