package uz.electro.remote.data

import uz.electro.remote.i18n.S
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import retrofit2.HttpException

/** Чем закончилась попытка разбудить машину через сервер. */
sealed interface WakeResult {
    /** Команда принята; [how] — каким путём машину будили. */
    data class Sent(val how: String) : WakeResult

    /** Сервер ответил отказом или не ответил вовсе. */
    data class Failed(val reason: String) : WakeResult

    /** Сервер как путь недоступен: отключён в настройках или нет входа. */
    data object NoServer : WakeResult
}

/**
 * Единая точка доступа к машине — через наш сервер, и только через него.
 *
 * Прямого канала на голову больше нет. Он требовал, чтобы телефон и машина
 * сидели в одной сети, а на голове был включён adb — в боевой машине его
 * выключают сразу после установки. Один путь вместо двух заодно снимает
 * разнобой: раньше набор команд и скорость опроса зависели от того, каким
 * каналом повезло пройти.
 */
/** Файл к отзыву, уже прочитанный в память: имя для админки, тип, байты. */
class FeedbackFile(val name: String, val mime: String, val bytes: ByteArray)

/** Что вышло: id отзыва и сколько вложений долетело/отвалилось. */
data class FeedbackOutcome(val id: String, val attached: Int, val failed: Int)

class CarRepository(private val settings: Settings) {

    private val cloud = CloudClient(settings)

    /** Снять состояние с сервера: он спрашивает машину сам. */
    suspend fun refresh(): CarState = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!settings.cloudEnabled) return@withContext CarState(updatedAt = now)
        val id = resolveVehicleId() ?: return@withContext CarState(updatedAt = now)
        runCatching { cloud.api.status(id) }
            .map { CarState.fromCloud(it, now) }
            .getOrElse { CarState(updatedAt = now) }
    }

    /** Отправить команду. Путь один — сервер. */
    suspend fun send(cmd: VehicleCommand): CmdResult = withContext(Dispatchers.IO) {
        sendViaCloud(cmd)
    }

    /**
     * Включить климат с таймером авто-выключения (GWM «время работы»).
     *
     * `runMinutes` уходит серверу в params: 0 — без таймера, null — умолчание
     * сервера. Гасит забытый климат сам сервер, а не телефон.
     */
    suspend fun climateOn(runMinutes: Int?): CmdResult = withContext(Dispatchers.IO) {
        if (!settings.cloudEnabled) return@withContext CmdResult.Failed(S("Сервер отключён в настройках"))
        if (!settings.loggedIn) return@withContext CmdResult.Failed(S("Вход в аккаунт не выполнен"))
        val id = resolveVehicleId() ?: return@withContext CmdResult.Failed(S("Машина не выбрана"))
        val params = if (runMinutes != null) mapOf("run_minutes" to runMinutes) else emptyMap()
        runCatching { cloud.api.climateOn(id, CommandRequest(params = params)) }
            .map {
                if (it.status.startsWith("SUCCESS")) CmdResult.Ok
                else CmdResult.Failed(it.error ?: it.status)
            }
            .getOrElse { CmdResult.Failed(reasonOf(it)) }
    }

    private suspend fun sendViaCloud(cmd: VehicleCommand): CmdResult {
        if (!settings.cloudEnabled) return CmdResult.Failed(S("Сервер отключён в настройках"))
        if (!settings.loggedIn) return CmdResult.Failed(S("Вход в аккаунт не выполнен"))
        val id = resolveVehicleId() ?: return CmdResult.Failed(S("Машина не выбрана"))

        // Замок и климат — своими эндпоинтами: у них на сервере отдельные права
        // и своя проверка. Всё остальное уходит одной командой кузова: карту
        // типов знает голова, серверу её знать незачем.
        val call: (suspend () -> CommandDto) = when {
            cmd.type == Cmd.LOCK && cmd.value == "1" -> ({ cloud.api.unlock(id, CommandRequest()) })
            cmd.type == Cmd.LOCK && cmd.value == "0" -> ({ cloud.api.lock(id, CommandRequest()) })
            cmd.type == Cmd.AC && cmd.value == "1" -> ({ cloud.api.climateOn(id, CommandRequest()) })
            cmd.type == Cmd.AC && cmd.value == "0" -> ({ cloud.api.climateOff(id, CommandRequest()) })
            else -> ({ cloud.api.headControl(id, HeadControlRequest(cmd.type, cmd.value)) })
        }

        // SUCCESS_UNVERIFIED — тоже успех: голова подтвердила приём, а состояние
        // читается отдельно и к команде не привязано.
        return runCatching { call.invoke() }
            .map {
                if (it.status.startsWith("SUCCESS")) CmdResult.Ok
                else CmdResult.Failed(it.error ?: it.status)
            }
            .getOrElse { CmdResult.Failed(reasonOf(it)) }
    }

    // --- пробуждение ------------------------------------------------------

    /**
     * Разбудить машину через сервер.
     *
     * Сервер сам выбирает путь — пробуждалку в машине или облако — и, в отличие
     * от SMS, отвечает, дошла ли команда: у SMS обратной связи нет вовсе, и
     * «отправлено» там означало лишь «отдано оператору».
     */
    /** Контакты поддержки (открытый эндпоинт, ключ не нужен). null — не достали. */
    suspend fun support(): SupportDto? = withContext(Dispatchers.IO) {
        runCatching { cloud.api.support() }.getOrNull()
    }

    /**
     * Отзыв в админку: замечание, идея, вопрос. Машина — текущая, если выбрана.
     * Вложения (скриншоты/записи экрана) уходят следом по одному; если какое-то
     * не долетело — отзыв всё равно отправлен, об этом говорит результат.
     */
    suspend fun sendFeedback(
        kind: String, text: String, appVersion: String,
        attachments: List<FeedbackFile> = emptyList(),
    ): Result<FeedbackOutcome> = withContext(Dispatchers.IO) {
        runCatching {
            val created = cloud.api.sendFeedback(FeedbackRequest(
                kind = kind, text = text,
                vehicle_id = settings.vehicleId?.takeIf { it.isNotBlank() },
                app = "phone", app_version = appVersion,
            ))
            var failed = 0
            for (f in attachments) {
                val body = f.bytes.toRequestBody(f.mime.toMediaTypeOrNull())
                runCatching { cloud.api.attachToFeedback(created.id, f.name, body) }
                    .onFailure { failed++ }
            }
            FeedbackOutcome(created.id, attachments.size - failed, failed)
        }
    }

    suspend fun wake(): WakeResult = withContext(Dispatchers.IO) {
        if (!settings.cloudEnabled || !settings.loggedIn) return@withContext WakeResult.NoServer
        val id = resolveVehicleId() ?: return@withContext WakeResult.NoServer
        runCatching { cloud.api.wake(id, CommandRequest()) }.fold(
            onSuccess = { dto ->
                // SUCCESS_UNVERIFIED — тоже успех: команда принята, а проверить,
                // что машина проснулась, этим каналом нечем.
                if (dto.status.startsWith("SUCCESS")) WakeResult.Sent(channelName(dto.channel))
                else WakeResult.Failed(dto.error?.ifBlank { null } ?: dto.status)
            },
            onFailure = { WakeResult.Failed(reasonOf(it)) },
        )
    }

    /**
     * Причина отказа человеческим языком.
     *
     * Непрошедшую команду backend отдаёт кодом 5xx, и без разбора тела на экран
     * попадало бы «HTTP 502» вместо «пробуждалка не ответила за 15 с» — то есть
     * ровно то, что нужно знать, оставалось бы в логах сервера.
     */
    private fun reasonOf(error: Throwable): String {
        val body = (error as? HttpException)?.response()?.errorBody()?.string().orEmpty()
        if (body.isNotBlank()) {
            runCatching {
                val json = JSONObject(body)
                val text = json.optString("error").ifBlank { json.optString("detail") }
                if (text.isNotBlank()) return scrubAddresses(text) ?: text
            }
        }
        // Сетевые ошибки OkHttp несут в тексте хост/адрес («Unable to resolve
        // host leapmotor.evon.uz», «Failed to connect to /62.171.159.63») — их
        // на экран показывать нельзя. Переводим в обобщённую причину.
        friendlyNetworkError(error)?.let { return it }
        return scrubAddresses(error.message) ?: S("Сервер недоступен")
    }

    /**
     * Отпустить машину обратно в сон — через ту же железку.
     *
     * Запасного пути здесь нет: у облака нет такого понятия, а SMS уходит
     * вслепую. Не прошло — значит не прошло.
     */
    suspend fun sleep(): WakeResult = withContext(Dispatchers.IO) {
        if (!settings.cloudEnabled || !settings.loggedIn) return@withContext WakeResult.NoServer
        val id = resolveVehicleId() ?: return@withContext WakeResult.NoServer
        runCatching { cloud.api.sleep(id, CommandRequest()) }.fold(
            onSuccess = { dto ->
                if (dto.status.startsWith("SUCCESS")) WakeResult.Sent(channelName(dto.channel))
                else WakeResult.Failed(dto.error?.ifBlank { null } ?: dto.status)
            },
            onFailure = { WakeResult.Failed(reasonOf(it)) },
        )
    }

    private fun channelName(channel: String?): String = when (channel) {
        "WAKE_DEVICE" -> S("через пробуждалку в машине")
        "LEAPMOTOR_CLOUD" -> S("через облако")
        else -> S("через сервер")
    }

    // --- вход в аккаунт --------------------------------------------------

    suspend fun login(email: String, password: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val token = cloud.api.login(LoginRequest(email, password)).access_token
            settings.token = token
            settings.email = email
            resolveVehicleId()
            Unit
        }
    }

    /**
     * Регистрация. Учётка общая с сайтом: тот же backend, та же таблица людей.
     *
     * Сервер сразу отдаёт токен, поэтому повторный вход не нужен — сохраняем
     * сессию так же, как после login().
     */
    suspend fun register(
        email: String, password: String, name: String?, phone: String?,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val created = cloud.api.register(RegisterRequest(
                email = email,
                password = password,
                name = name?.takeIf { it.isNotBlank() },
                phone = phone?.takeIf { it.isNotBlank() },
            ))
            settings.token = created.access_token
            settings.email = created.user.email
            Unit
        }
    }

    /**
     * «Забыл пароль»: заявка мастеру. Почтового канала у установки нет, новый
     * пароль выдаёт и сообщает человек.
     */
    suspend fun requestPasswordReset(email: String, contact: String?): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                cloud.api.requestPasswordReset(
                    PasswordResetRequest(email, contact?.takeIf { it.isNotBlank() })
                )
                Unit
            }
        }

    /**
     * Погасить код с QR на экране машины и получить доступ к ней.
     *
     * Привязанную машину сразу делаем выбранной: она у аккаунта первая и,
     * скорее всего, единственная, и заставлять выбирать её вручную незачем.
     */
    suspend fun claimPairing(code: String): Result<VehicleDto> = withContext(Dispatchers.IO) {
        runCatching { cloud.api.pairClaim(PairClaimRequest(code)) }
            .onSuccess { settings.vehicleId = it.vehicle_id }
    }

    /** Причина отказа человеческим языком — та же, что и у команд. */
    fun reason(error: Throwable): String = reasonOf(error)

    /** Удалить свой аккаунт на сервере (необратимо). После — локальный выход. */
    suspend fun deleteAccount(password: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val r = cloud.api.deleteAccount(AccountDeleteRequest(password))
            if (!r.isSuccessful) throw retrofit2.HttpException(r)
            settings.logout()
            Unit
        }
    }

    suspend fun news(): List<NewsItemDto> = withContext(Dispatchers.IO) {
        runCatching { cloud.api.news() }.getOrElse { emptyList() }
    }

    /** Push-токен серверу; не критично — при неудаче повторим при следующем запуске. */
    suspend fun registerPushToken(token: String): Boolean = withContext(Dispatchers.IO) {
        if (!settings.loggedIn) return@withContext false
        runCatching { cloud.api.registerPushToken(PushTokenBody(token = token)).isSuccessful }.getOrDefault(false)
    }

    suspend fun removePushToken(token: String) = withContext(Dispatchers.IO) {
        runCatching { cloud.api.removePushToken(PushTokenRemove(token)) }
    }

    fun logout() = settings.logout()

    // --- парк -------------------------------------------------------------

    /** Машины, к которым допущен аккаунт. */
    suspend fun vehicles(): Result<List<VehicleDto>> = withContext(Dispatchers.IO) {
        runCatching { cloud.api.vehicles() }
    }

    /** Отказ по недействительной сессии — его нельзя показывать как ошибку сети. */
    fun isUnauthorized(error: Throwable): Boolean =
        (error as? HttpException)?.code() == 401

    /** Выбранная машина: с этого момента команды и статус идут в неё. */
    fun selectVehicle(vehicleId: String) {
        settings.vehicleId = vehicleId
    }

    /**
     * vehicle_id известен из настроек или подбирается сам.
     *
     * Из парка выбирается C16: это приложение управляет ею, а у C11 и C01 свой
     * клиент и другой набор команд. Взять просто первую машину значило бы
     * отправлять команды C16 в ту, которая их не понимает.
     */
    private suspend fun resolveVehicleId(): String? {
        settings.vehicleId?.takeIf { it.isNotBlank() }?.let { return it }
        if (!settings.loggedIn) return null
        val all = runCatching { cloud.api.vehicles() }.getOrNull().orEmpty()
        val mine = all.firstOrNull { it.model.equals("C16", ignoreCase = true) }
            ?: all.firstOrNull() ?: return null
        settings.vehicleId = mine.vehicle_id
        return mine.vehicle_id
    }

    // --- возможности, сцены, расписание ----------------------------------

    /** Что рисовать: набор кнопок решает сервер, а не приложение. */
    suspend fun capabilities(): CapabilitiesDto? = withContext(Dispatchers.IO) {
        val id = resolveVehicleId() ?: return@withContext null
        runCatching { cloud.api.capabilities(id) }.getOrNull()
    }

    suspend fun scenes(): List<SceneTemplateDto> = withContext(Dispatchers.IO) {
        val id = resolveVehicleId() ?: return@withContext emptyList()
        runCatching { cloud.api.scenes(id) }.getOrElse { emptyList() }
    }

    suspend fun createScene(name: String, steps: List<SceneStepDto>): Result<SceneTemplateDto> =
        withContext(Dispatchers.IO) {
            val id = resolveVehicleId()
                ?: return@withContext Result.failure(IllegalStateException(S("Машина не выбрана")))
            runCatching { cloud.api.createScene(id, SceneTemplateRequest(name, steps)) }
        }

    suspend fun deleteScene(templateId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val id = resolveVehicleId()
            ?: return@withContext Result.failure(IllegalStateException(S("Машина не выбрана")))
        runCatching { cloud.api.deleteScene(id, templateId); Unit }
    }

    suspend fun runScene(templateId: String): CmdResult = withContext(Dispatchers.IO) {
        val id = resolveVehicleId() ?: return@withContext CmdResult.Failed(S("Машина не выбрана"))
        runCatching { cloud.api.runScene(id, templateId, CommandRequest()) }
            .map {
                if (it.status.startsWith("SUCCESS")) CmdResult.Ok
                else CmdResult.Failed(it.error ?: it.status)
            }
            .getOrElse { CmdResult.Failed(reasonOf(it)) }
    }

    suspend fun climateSchedules(): List<ClimateScheduleDto> = withContext(Dispatchers.IO) {
        val id = resolveVehicleId() ?: return@withContext emptyList()
        runCatching { cloud.api.climateSchedules(id) }.getOrElse { emptyList() }
    }

    suspend fun createClimateSchedule(body: ClimateScheduleRequest): Result<ClimateScheduleDto> =
        withContext(Dispatchers.IO) {
            val id = resolveVehicleId()
                ?: return@withContext Result.failure(IllegalStateException(S("Машина не выбрана")))
            runCatching { cloud.api.createClimateSchedule(id, body) }
        }

    suspend fun setScheduleEnabled(scheduleId: String, enabled: Boolean): Result<Unit> =
        withContext(Dispatchers.IO) {
            val id = resolveVehicleId()
                ?: return@withContext Result.failure(IllegalStateException(S("Машина не выбрана")))
            runCatching { cloud.api.setScheduleEnabled(id, scheduleId, EnabledRequest(enabled)); Unit }
        }

    suspend fun deleteClimateSchedule(scheduleId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val id = resolveVehicleId()
                ?: return@withContext Result.failure(IllegalStateException(S("Машина не выбрана")))
            runCatching { cloud.api.deleteClimateSchedule(id, scheduleId); Unit }
        }

    /** Отвязать машину от аккаунта. Сама машина остаётся, снимается только доступ. */
    suspend fun unlinkVehicle(vehicleId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { cloud.api.unlinkVehicle(vehicleId); Unit }
    }

    suspend fun voiceIntents(): List<VoiceIntentDto> = withContext(Dispatchers.IO) {
        runCatching { cloud.api.voiceIntents() }.getOrElse { emptyList() }
    }

    suspend fun runVoice(intent: String): CmdResult = withContext(Dispatchers.IO) {
        val id = resolveVehicleId() ?: return@withContext CmdResult.Failed(S("Машина не выбрана"))
        runCatching { cloud.api.voice(id, VoiceRequestBody(intent)) }
            .map {
                if (it.status.startsWith("SUCCESS")) CmdResult.Ok
                else CmdResult.Failed(it.error ?: it.status)
            }
            .getOrElse { CmdResult.Failed(reasonOf(it)) }
    }
}
