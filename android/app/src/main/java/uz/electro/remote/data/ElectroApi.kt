package uz.electro.remote.data

import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.*
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.util.UUID
import java.util.concurrent.TimeUnit

// --- модели (зеркало схем backend) ----------------------------------------

data class LoginRequest(val email: String, val password: String)
data class TokenResponse(val access_token: String, val token_type: String, val expires_in: Int)

data class RegisterRequest(
    val email: String,
    val password: String,
    val name: String? = null,
    val phone: String? = null,
)

data class UserDto(
    val user_id: String,
    val email: String,
    val role: String,
    val is_active: Boolean,
    val name: String?,
    val phone: String?,
)

/** Регистрация сразу открывает сессию — второй раз логиниться не нужно. */
data class RegisterResponse(val user: UserDto, val access_token: String, val expires_in: Int)

/**
 * «Забыл пароль». Писем сервер не шлёт — заявка уходит мастеру, он выдаёт
 * новый пароль и сообщает его по указанному контакту.
 */
data class PasswordResetRequest(val email: String, val contact: String? = null)
data class PasswordResetAccepted(val detail: String)

/** Удаление своего аккаунта — необратимо, поэтому с паролем. */
data class AccountDeleteRequest(val current_password: String)

data class PushTokenBody(val platform: String = "android", val token: String)
data class PushTokenRemove(val token: String)

/** Новость или уведомление из админки. */
data class NewsItemDto(
    val id: String,
    val title: String,
    val body: String = "",
    val kind: String = "info",      // info | news | alert
    val created_at: String,
)

/** Одноразовый код с QR на экране машины. */
data class PairClaimRequest(val code: String)

data class VehicleDto(
    val vehicle_id: String,
    val vin: String,
    val name: String,
    val provider: String,
    val remote_control_enabled: Boolean,
    val model: String? = null,
)

/** Контакты поддержки — то же, что показывает «Помощь» на голове. */
data class SupportDto(
    val phone: String = "",
    val telegram: String = "",
    val instagram: String = "",
    val site: String = "",
)

/** Отзыв из приложения: kind = bug | idea | other. */
data class FeedbackRequest(
    val kind: String,
    val text: String,
    val vehicle_id: String? = null,
    val app: String = "phone",
    val app_version: String? = null,
)

data class FeedbackCreated(val id: String, val created_at: String)

data class AttachmentDto(val id: String, val name: String, val content_type: String, val size: Long)

data class StatusDto(
    val vehicle_id: String,
    val security_state: String,   // ARMED | DISARMED | UNKNOWN
    val doors: String,            // LOCKED | UNLOCKED | UNKNOWN
    val battery_percent: Int?,
    val online: Boolean,
    val climate_on: Boolean,
    val headunit_awake: Boolean,  // определяет режим экрана: спит / активна
    val observed_at: String,
    val source: String,           // CACHE | CLOUD_POLL | AGENT
    /**
     * Сырые сигналы головы: температуры, окна, сиденья — всё, чему нет
     * отдельного поля. Сервер завёл их именно для телефона, потерявшего прямой
     * канал на голову; без них половина экранов показывает только эхо
     * собственных нажатий.
     */
    val raw: Map<String, Any>? = null,
    val range_km: Int? = null,
    val odometer_km: Int? = null,
    //: широта без долготы бессмысленна — считаем точку только когда есть обе
    val latitude: Double? = null,
    val longitude: Double? = null,
    /**
     * Через сколько минут сервер сам погасит климат; 0 — не гасит.
     * Срок приходит с сервера, а не зашит в сборку: иначе обещание на экране
     * разъедется с настройкой при первой же её правке.
     */
    val climate_auto_off_minutes: Int = 0,
)

/** Команда кузова C16 через голову: пара (type, value) из карты команд. */
data class HeadControlRequest(
    val type: Int,
    val value: String,
    val command_id: String = UUID.randomUUID().toString(),
    val wait: Boolean = true,
)

data class CommandRequest(
    val command_id: String = UUID.randomUUID().toString(),
    val force_channel: String? = null,
    val params: Map<String, Any> = emptyMap(),
    val wait: Boolean = true,
)

data class CommandDto(
    val command_id: String,
    val vehicle_id: String,
    val command: String,
    val channel: String?,
    val status: String,           // SUCCESS | UNSAFE_STATE | FAILED | ...
    val created_at: String,
    val completed_at: String?,
    val compensated: Boolean,
    val error: String?,
)

// --- возможности машины, сцены и расписание климата ------------------------

/** Что умеет машина — по этому списку рисуются кнопки (см. backend capabilities). */
data class CapabilitiesDto(
    val quick: List<String> = emptyList(),
    val groups: List<String> = emptyList(),
    val voice: Boolean = false,
    val scenes: Boolean = false,
    val climate_schedule: Boolean = false,
)

data class SceneStepDto(
    val type: Int,
    val value: String,
    val title: String = "",
    val required: Boolean = true,
)

data class SceneTemplateDto(
    val template_id: String,
    val name: String,
    val steps: List<SceneStepDto>,
)

data class SceneTemplateRequest(val name: String, val steps: List<SceneStepDto>)

data class ClimateScheduleDto(
    val schedule_id: String,
    val hour: Int,
    val minute: Int,
    val weekdays: List<Int> = emptyList(),
    val temp_c: Int = 22,
    val enabled: Boolean = true,
)

data class ClimateScheduleRequest(
    val hour: Int,
    val minute: Int,
    val weekdays: List<Int> = emptyList(),
    val temp_c: Int = 22,
    val enabled: Boolean = true,
)

data class EnabledRequest(val enabled: Boolean)

data class VoiceIntentDto(
    val intent: String,
    val subsystem: String,
    val phrases: List<String> = emptyList(),
)

data class VoiceRequestBody(
    val intent: String,
    val command_id: String = UUID.randomUUID().toString(),
    val wait: Boolean = true,
)

// --- retrofit -------------------------------------------------------------

interface ElectroApi {
    @POST("api/v1/auth/login")
    suspend fun login(@Body body: LoginRequest): TokenResponse

    @POST("api/v1/auth/register")
    suspend fun register(@Body body: RegisterRequest): RegisterResponse

    @POST("api/v1/auth/password-reset")
    suspend fun requestPasswordReset(@Body body: PasswordResetRequest): PasswordResetAccepted

    @POST("api/v1/pairing/claim")
    suspend fun pairClaim(@Body body: PairClaimRequest): VehicleDto

    @POST("api/v1/auth/delete-account")
    suspend fun deleteAccount(@Body body: AccountDeleteRequest): retrofit2.Response<Unit>

    @POST("api/v1/auth/push-token")
    suspend fun registerPushToken(@Body body: PushTokenBody): retrofit2.Response<Unit>

    @retrofit2.http.HTTP(method = "DELETE", path = "api/v1/auth/push-token", hasBody = true)
    suspend fun removePushToken(@Body body: PushTokenRemove): retrofit2.Response<Unit>

    @GET("api/v1/news")
    suspend fun news(@Query("limit") limit: Int = 50): List<NewsItemDto>

    @GET("api/v1/agent/support")
    suspend fun support(): SupportDto

    @POST("api/v1/feedback")
    suspend fun sendFeedback(@Body body: FeedbackRequest): FeedbackCreated

    /** Сырой файл телом запроса, тип — в Content-Type самого RequestBody. */
    @POST("api/v1/feedback/{id}/attachments")
    suspend fun attachToFeedback(
        @Path("id") id: String, @Query("name") name: String, @Body file: okhttp3.RequestBody,
    ): AttachmentDto

    @GET("api/v1/vehicles")
    suspend fun vehicles(): List<VehicleDto>

    @GET("api/v1/vehicles/{id}/status")
    suspend fun status(
        @Path("id") id: String,
        @Query("force_refresh") forceRefresh: Boolean = true,
    ): StatusDto

    @POST("api/v1/vehicles/{id}/lock")
    suspend fun lock(@Path("id") id: String, @Body body: CommandRequest): CommandDto

    @POST("api/v1/vehicles/{id}/unlock")
    suspend fun unlock(@Path("id") id: String, @Body body: CommandRequest): CommandDto

    @POST("api/v1/vehicles/{id}/head/control")
    suspend fun headControl(@Path("id") id: String, @Body body: HeadControlRequest): CommandDto

    @POST("api/v1/vehicles/{id}/wake")
    suspend fun wake(@Path("id") id: String, @Body body: CommandRequest): CommandDto

    @POST("api/v1/vehicles/{id}/sleep")
    suspend fun sleep(@Path("id") id: String, @Body body: CommandRequest): CommandDto

    @POST("api/v1/vehicles/{id}/climate/on")
    suspend fun climateOn(@Path("id") id: String, @Body body: CommandRequest): CommandDto

    @POST("api/v1/vehicles/{id}/climate/off")
    suspend fun climateOff(@Path("id") id: String, @Body body: CommandRequest): CommandDto

    @GET("api/v1/vehicles/{id}/commands")
    suspend fun history(@Path("id") id: String, @Query("limit") limit: Int = 20): List<CommandDto>

    @DELETE("api/v1/vehicles/{id}/grant")
    suspend fun unlinkVehicle(@Path("id") id: String): retrofit2.Response<Unit>

    @GET("api/v1/vehicles/{id}/capabilities")
    suspend fun capabilities(@Path("id") id: String): CapabilitiesDto

    @GET("api/v1/vehicles/{id}/scenes/templates")
    suspend fun scenes(@Path("id") id: String): List<SceneTemplateDto>

    @POST("api/v1/vehicles/{id}/scenes/templates")
    suspend fun createScene(@Path("id") id: String, @Body body: SceneTemplateRequest): SceneTemplateDto

    @DELETE("api/v1/vehicles/{id}/scenes/templates/{tid}")
    suspend fun deleteScene(@Path("id") id: String, @Path("tid") tid: String): retrofit2.Response<Unit>

    @POST("api/v1/vehicles/{id}/scenes/templates/{tid}/run")
    suspend fun runScene(@Path("id") id: String, @Path("tid") tid: String, @Body body: CommandRequest): CommandDto

    @GET("api/v1/vehicles/{id}/climate/schedules")
    suspend fun climateSchedules(@Path("id") id: String): List<ClimateScheduleDto>

    @POST("api/v1/vehicles/{id}/climate/schedules")
    suspend fun createClimateSchedule(@Path("id") id: String, @Body body: ClimateScheduleRequest): ClimateScheduleDto

    @POST("api/v1/vehicles/{id}/climate/schedules/{sid}/enabled")
    suspend fun setScheduleEnabled(@Path("id") id: String, @Path("sid") sid: String, @Body body: EnabledRequest): ClimateScheduleDto

    @DELETE("api/v1/vehicles/{id}/climate/schedules/{sid}")
    suspend fun deleteClimateSchedule(@Path("id") id: String, @Path("sid") sid: String): retrofit2.Response<Unit>

    @GET("api/v1/vehicles/voice")
    suspend fun voiceIntents(): List<VoiceIntentDto>

    @POST("api/v1/vehicles/{id}/voice")
    suspend fun voice(@Path("id") id: String, @Body body: VoiceRequestBody): CommandDto
}

/**
 * Клиент backend. Базовый адрес и токен берутся из [Settings]; при смене адреса
 * retrofit пересоздаётся, при смене токена — нет (он подставляется на лету).
 */
class CloudClient(private val settings: Settings) {

    private val authInterceptor = Interceptor { chain ->
        val req = chain.request().newBuilder().apply {
            settings.token?.takeIf { it.isNotBlank() }?.let { addHeader("Authorization", "Bearer $it") }
        }.build()
        chain.proceed(req)
    }

    private val http = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        // Подключение — быстро; ответ бывает долгим: /wake и /sleep сервер
        // держит, пока пробуждалка (4G-железка) не подтвердит приём — это ~10 с
        // и больше при ретраях. С прежними 8 с на чтение запрос падал с
        // «timeout» ровно на побудке, хотя реле уже щёлкнуло.
        .connectTimeout(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS)
        // вложение к отзыву (запись экрана) — десятки мегабайт по мобильной сети
        .writeTimeout(WRITE_TIMEOUT_S, TimeUnit.SECONDS)
        .build()

    @Volatile private var cachedUrl: String? = null
    @Volatile private var cachedApi: ElectroApi? = null

    /** Актуальный api для текущего адреса из настроек. */
    val api: ElectroApi
        get() {
            val url = settings.cloudUrl
            cachedApi?.takeIf { cachedUrl == url }?.let { return it }
            val created = Retrofit.Builder()
                .baseUrl(url)
                .client(http)
                .addConverterFactory(MoshiConverterFactory.create())
                .build()
                .create(ElectroApi::class.java)
            synchronized(this) { cachedUrl = url; cachedApi = created }
            return created
        }

    private companion object {
        const val CONNECT_TIMEOUT_S = 10L
        const val READ_TIMEOUT_S = 40L
        const val WRITE_TIMEOUT_S = 180L
    }
}
