package uz.electro.remote

import uz.electro.remote.i18n.S
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import org.json.JSONObject
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import retrofit2.HttpException
import uz.electro.remote.data.*

/** Шаги пробуждения машины перед входом в приложение. */
enum class ConnectPhase {
    /** Экран подключения, ничего ещё не делали. */
    Idle,

    /** Отправляем SMS на SIM автомобиля. */
    Sending,

    /** SMS ушла, ждём, пока голова поднимется и ответит. */
    Waiting,

    /** Машина на связи (или пользователь решил войти без неё) — приложение открыто. */
    Connected,

    /** Машина не отозвалась за отведённое время. */
    Timeout,

    /** Не смогли даже отправить SMS: нет номера, нет разрешения, сбой модема. */
    Error,
}

data class ConnectStatus(
    val phase: ConnectPhase = ConnectPhase.Idle,
    val message: String? = null,
    /** Сколько уже ждём отклика, секунд — показывается на экране подключения. */
    val waitedSec: Int = 0,
    /** Вошли, не дождавшись машины. */
    val degraded: Boolean = false,
)

/** Чем закончилась команда — определяет вид снекбара. */
enum class EventKind { Success, Failed, Unsupported, Offline }

/** Что показать пользователю после команды. */
data class CmdEvent(val title: String, val message: String?, val kind: EventKind) {
    val ok: Boolean get() = kind == EventKind.Success
}

/**
 * Состояние приложения в одном месте: опрос машины, отправка команд,
 * фактические значения переключателей и результаты команд.
 *
 * Опрос запускается и останавливается экраном по жизненному циклу — в фоне
 * приложение в сеть не ходит.
 */
class CarViewModel(app: Application) : AndroidViewModel(app) {

    val settings = Settings(app)
    private val repo = CarRepository(settings)

    private val _car = MutableStateFlow(CarState())
    val car: StateFlow<CarState> = _car.asStateFlow()

    /** Последнее отправленное значение по команде — для тех, у кого нет обратной связи. */
    private val _optimistic = MutableStateFlow<Map<Int, String>>(emptyMap())

    private val _events = MutableSharedFlow<CmdEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<CmdEvent> = _events.asSharedFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** Команды, которые прямо сейчас в пути — плитка показывает это сама. */
    private val _pending = MutableStateFlow<Set<Int>>(emptySet())
    val pending: StateFlow<Set<Int>> = _pending.asStateFlow()

    private val _loggedIn = MutableStateFlow(settings.loggedIn)
    val loggedIn: StateFlow<Boolean> = _loggedIn.asStateFlow()

    /** Парк аккаунта. Пустой список — законный ответ: машин просто не выдали. */
    private val _vehicles = MutableStateFlow<List<VehicleDto>>(emptyList())
    val vehicles: StateFlow<List<VehicleDto>> = _vehicles.asStateFlow()

    private val _vehicleId = MutableStateFlow(settings.vehicleId)
    val vehicleId: StateFlow<String?> = _vehicleId.asStateFlow()

    /**
     * Почему список пуст.
     *
     * «Машин нет» и «не удалось спросить» выглядят на экране одинаково пустым
     * списком, а означают разное: в первом случае нужно выдать доступ, во
     * втором — проверить связь. Поэтому причина хранится отдельно.
     */
    private val _parkNote = MutableStateFlow<String?>(null)
    val parkNote: StateFlow<String?> = _parkNote.asStateFlow()

    /**
     * Спросили ли уже парк у сервера.
     *
     * Пустой список — это и «машин не выдали», и «ещё не спрашивали», а экраны
     * от этого расходятся: во втором случае показывать «Подключите машину»
     * нельзя, она мелькнёт и пропадёт на каждом входе.
     */
    private val _parkKnown = MutableStateFlow(false)
    val parkKnown: StateFlow<Boolean> = _parkKnown.asStateFlow()

    val selectedVehicle: StateFlow<VehicleDto?> =
        combine(_vehicles, _vehicleId) { list, id -> list.firstOrNull { it.vehicle_id == id } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * Фактические значения элементов управления: реальный сигнал с машины,
     * а при его отсутствии — последнее отправленное значение.
     */
    val controls: StateFlow<Map<Int, String>> =
        combine(_car, _optimistic) { car, optimistic ->
            val merged = optimistic.toMutableMap()
            (optimistic.keys + SIGNAL_BACKED).forEach { type ->
                Cmd.signalFor(type)?.let { car.signal(it) }?.let { merged[type] = it }
            }
            merged
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val _connect = MutableStateFlow(ConnectStatus())
    val connect: StateFlow<ConnectStatus> = _connect.asStateFlow()

    /** Есть ли чем будить: путь один — сервер с выполненным входом. */
    val wakeConfigured: Boolean get() = settings.cloudEnabled && settings.loggedIn

    // --- возможности, сцены, расписание климата --------------------------
    // Набор кнопок решает сервер (capabilities), а не приложение: у C11 другой
    // список команд, и зашивать его в APK — значит переустанавливать у всех.

    private val _capabilities = MutableStateFlow<CapabilitiesDto?>(null)
    val capabilities: StateFlow<CapabilitiesDto?> = _capabilities.asStateFlow()

    private val _scenes = MutableStateFlow<List<SceneTemplateDto>>(emptyList())
    val scenes: StateFlow<List<SceneTemplateDto>> = _scenes.asStateFlow()

    private val _schedules = MutableStateFlow<List<ClimateScheduleDto>>(emptyList())
    val schedules: StateFlow<List<ClimateScheduleDto>> = _schedules.asStateFlow()

    private val _voiceIntents = MutableStateFlow<List<VoiceIntentDto>>(emptyList())
    val voiceIntents: StateFlow<List<VoiceIntentDto>> = _voiceIntents.asStateFlow()

    // --- новости и push ------------------------------------------------------

    private val _news = MutableStateFlow<List<NewsItemDto>>(emptyList())
    val news: StateFlow<List<NewsItemDto>> = _news.asStateFlow()
    private val _newsRead = MutableStateFlow(settings.newsRead)
    val newsRead: StateFlow<Set<String>> = _newsRead.asStateFlow()

    /** Сколько новостей ещё не отмечено прочитанными — бейдж на колокольчике. */
    val unreadNews: StateFlow<Int> =
        combine(_news, _newsRead) { list, read -> list.count { it.id !in read } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /** До входа — публичная лента (без адресных уведомлений), после — полная. */
    fun loadNews() = viewModelScope.launch { _news.value = if (_loggedIn.value) repo.news() else repo.newsPublic() }

    fun markNewsRead(id: String) {
        val next = _newsRead.value + id
        settings.newsRead = next; _newsRead.value = next
    }

    /** «Прочитать всё» — бейдж гаснет. */
    fun markAllNewsRead() {
        val next = _newsRead.value + _news.value.map { it.id }
        settings.newsRead = next; _newsRead.value = next
    }

    private var pollJob: Job? = null
    private var connectJob: Job? = null

    // Сохраняем оптимистичное состояние между запусками: вернувшись в приложение,
    // пользователь видит последнее включённое (климат/функции), а не «выкл» до
    // прихода /state. Реальный статус машины при поступлении перекрывает по-сигнально.
    private val optiPrefs = app.getSharedPreferences("electro", android.content.Context.MODE_PRIVATE)
    private fun persistOptimistic(m: Map<Int, String>) = runCatching {
        val o = JSONObject(); m.forEach { (k, v) -> o.put(k.toString(), v) }
        optiPrefs.edit().putString("optimistic", o.toString()).apply()
    }
    private fun restoreOptimistic(): Map<Int, String> = runCatching {
        val s = optiPrefs.getString("optimistic", null) ?: return emptyMap()
        val o = JSONObject(s)
        o.keys().asSequence().associate { it.toInt() to o.getString(it) }
    }.getOrDefault(emptyMap())

    init {
        // Восстанавливаем последнее оптимистичное состояние и держим его на диске.
        _optimistic.value = restoreOptimistic()
        viewModelScope.launch { _optimistic.collect { persistOptimistic(it) } }
        // Вход мог быть выполнен в прошлый запуск — тогда парк нужен сразу,
        // иначе в настройках он появится только после ручного обновления.
        if (settings.loggedIn) { loadVehicles(); loadNews() }
        // Контакты поддержки нужны и на экране подключения (до входа), и на
        // главной — тянем сразу: эндпоинт открытый, ключ не нужен.
        loadSupport()
    }

    // --- подключение -----------------------------------------------------

    /**
     * Будим машину и ждём отклика. Приложение открывается только после этого:
     * пока T-BOX спит, любая команда ушла бы в пустоту.
     */
    fun connect() {
        if (connectJob?.isActive == true) return
        connectJob = viewModelScope.launch {
            _connect.value = ConnectStatus(ConnectPhase.Sending)

            val woken = wakeUp()
            val failure = woken.exceptionOrNull()
            if (failure != null) {
                _connect.value = ConnectStatus(
                    ConnectPhase.Error,
                    scrubAddresses(failure.message) ?: S("Не удалось разбудить машину"),
                )
                return@launch
            }
            val how = woken.getOrDefault(S("Команда отправлена"))

            // машина может уже быть в сети — тогда ждать нечего.
            // Время считаем по часам: сам опрос головы занимает несколько секунд,
            // и счёт по количеству пауз растягивал таймаут в разы.
            val startedAt = System.currentTimeMillis()
            fun waitedSec() = ((System.currentTimeMillis() - startedAt) / 1000).toInt()

            _connect.value = ConnectStatus(ConnectPhase.Waiting, how, 0)

            // Отдельный тикер рисует секунды. Раньше счётчик обновлялся только
            // после опроса головы, а он занимает несколько секунд — и на экране
            // время шло рывками через три-четыре секунды, будто приложение зависло.
            val ticker = launch {
                while (true) {
                    delay(TICK_MS)
                    val current = _connect.value
                    if (current.phase != ConnectPhase.Waiting) break
                    _connect.value = current.copy(waitedSec = waitedSec())
                }
            }

            try {
                while (true) {
                    val state = repo.refresh()
                    _car.value = state
                    if (state.link != Link.NONE) {
                        _connect.value = ConnectStatus(ConnectPhase.Connected)
                        startPolling()
                        return@launch
                    }
                    if (waitedSec() >= WAKE_WAIT_SEC) break
                    delay(PROBE_STEP_MS)
                }
            } finally {
                ticker.cancel()
            }
            _connect.value = ConnectStatus(
                ConnectPhase.Timeout,
                S("Машина не ответила за {0} с", WAKE_WAIT_SEC),
                waitedSec(),
            )
        }
    }

    /**
     * Разбудить машину — через сервер, и только через него.
     *
     * Запасной путь по SMS на SIM в T-BOX убран вместе с разрешением SEND_SMS:
     * обратной связи у него нет вовсе, «отправлено» означало лишь «отдано
     * оператору», и сломанная пробуждалка годами подменялась бы сообщениями,
     * которых никто не проверяет. Сервер хотя бы отвечает, дошла ли команда.
     */
    private suspend fun wakeUp(): Result<String> = when (val viaServer = repo.wake()) {
        is WakeResult.Sent -> Result.success(S("Разбудили {0}", viaServer.how))
        is WakeResult.Failed -> Result.failure(IllegalStateException(viaServer.reason))
        WakeResult.NoServer -> Result.failure(
            IllegalStateException(S("Будить нечем: нет входа на сервер"))
        )
    }

    /**
     * Отпустить машину в сон и вернуться на экран подключения.
     *
     * Возвращаемся туда в любом случае: если команда не прошла, машина осталась
     * бодрствовать, но сеанс мы уже закончили — и делать вид, что связь жива,
     * значит показывать данные, за которыми никто не следит.
     */
    fun disconnect() = viewModelScope.launch {
        _busy.value = true
        val result = try { repo.sleep() } finally { _busy.value = false }
        val note = when (result) {
            is WakeResult.Sent -> S("Машина отпущена в сон")
            is WakeResult.Failed -> S("Не отключилось: {0}", result.reason)
            WakeResult.NoServer -> S("Не отключилось: нет входа на сервер")
        }
        // Итог показываем на экране подключения, а не всплывающим сообщением:
        // всплывашка живёт на главном экране, а мы с него как раз уходим — и
        // причина отказа мелькнула бы и пропала.
        resetConnection()
        _connect.value = ConnectStatus(ConnectPhase.Idle, note)
    }

    /**
     * «Найти машину»: шлём команду отключения (sleep) три раза подряд, не уводя
     * с экрана подключения — чтобы окликнуть машину при поиске.
     */
    fun findCar() = viewModelScope.launch {
        _busy.value = true
        try { repeat(3) { repo.sleep() } } finally { _busy.value = false }
        _connect.value = ConnectStatus(ConnectPhase.Idle, S("Команда отправлена 3 раза"))
    }

    /** Войти, не дождавшись машины: данные будут последними известными. */
    fun enterAnyway() {
        connectJob?.cancel()
        _connect.value = ConnectStatus(ConnectPhase.Connected, degraded = true)
        startPolling()
    }

    /** Вернуться на экран подключения — например, после смены настроек. */
    fun resetConnection() {
        connectJob?.cancel()
        stopPolling()
        _connect.value = ConnectStatus()
    }

    fun connectFailed(reason: String) {
        _connect.value = ConnectStatus(ConnectPhase.Error, scrubAddresses(reason) ?: reason)
    }

    // --- опрос -----------------------------------------------------------

    fun startPolling() {
        if (_connect.value.phase != ConnectPhase.Connected) return
        loadMeta()
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            while (true) {
                val state = repo.refresh()
                _car.value = state
                delay(
                    when (state.link) {
                        Link.CLOUD -> POLL_CLOUD_MS
                        Link.NONE -> POLL_OFFLINE_MS
                    }
                )
            }
        }
    }

    fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    fun refreshNow() = viewModelScope.launch { _car.value = repo.refresh() }

    /** Включить климат с таймером авто-выключения (мин); null — умолчание сервера. */
    fun climateOn(runMinutes: Int?) = viewModelScope.launch {
        _optimistic.update { it + (Cmd.AC to "1") }
        _pending.update { it + Cmd.AC }
        val r = repo.climateOn(runMinutes)
        _pending.update { it - Cmd.AC }
        when (r) {
            is CmdResult.Ok -> emit(EventKind.Success, S("Климат включён"),
                if (runMinutes != null && runMinutes > 0) S("Выключу через {0} мин", runMinutes) else S("Выполнено"))
            is CmdResult.Failed -> { _optimistic.update { it - Cmd.AC }; emit(EventKind.Failed, S("Климат"), r.reason) }
            is CmdResult.Unsupported -> emit(EventKind.Unsupported, S("Климат"), r.reason)
        }
    }

    private suspend fun emit(kind: EventKind, title: String, message: String?) {
        // Ни в одном уведомлении не показываем адрес сайта/IP сервера.
        _events.emit(CmdEvent(title, scrubAddresses(message), kind))
    }

    /** Возможности, сцены, расписание и голос — один раз при подключении. */
    fun loadMeta() = viewModelScope.launch {
        _capabilities.value = repo.capabilities()
        _scenes.value = repo.scenes()
        _schedules.value = repo.climateSchedules()
        _voiceIntents.value = repo.voiceIntents()
        _news.value = repo.news()
    }

    fun refreshScenes() = viewModelScope.launch { _scenes.value = repo.scenes() }
    fun refreshSchedules() = viewModelScope.launch { _schedules.value = repo.climateSchedules() }

    fun createScene(name: String, steps: List<SceneStepDto>) = viewModelScope.launch {
        repo.createScene(name, steps).fold(
            onSuccess = { _scenes.value = repo.scenes(); emit(EventKind.Success, S("Сцена сохранена"), it.name) },
            onFailure = { emit(EventKind.Failed, S("Не сохранилось"), it.message) },
        )
    }

    fun deleteScene(templateId: String) = viewModelScope.launch {
        repo.deleteScene(templateId)
        _scenes.value = repo.scenes()
    }

    fun runScene(template: SceneTemplateDto) = viewModelScope.launch {
        when (val r = repo.runScene(template.template_id)) {
            is CmdResult.Ok -> emit(EventKind.Success, template.name, S("Выполнено"))
            is CmdResult.Failed -> emit(EventKind.Failed, template.name, r.reason)
            is CmdResult.Unsupported -> emit(EventKind.Unsupported, template.name, r.reason)
        }
    }

    fun createSchedule(body: ClimateScheduleRequest) = viewModelScope.launch {
        repo.createClimateSchedule(body).fold(
            onSuccess = { _schedules.value = repo.climateSchedules(); emit(EventKind.Success, S("Расписание сохранено"), null) },
            onFailure = { emit(EventKind.Failed, S("Не сохранилось"), it.message) },
        )
    }

    fun setScheduleEnabled(scheduleId: String, enabled: Boolean) = viewModelScope.launch {
        repo.setScheduleEnabled(scheduleId, enabled)
        _schedules.value = repo.climateSchedules()
    }

    fun deleteSchedule(scheduleId: String) = viewModelScope.launch {
        repo.deleteClimateSchedule(scheduleId)
        _schedules.value = repo.climateSchedules()
    }

    fun runVoice(intent: VoiceIntentDto) = viewModelScope.launch {
        when (val r = repo.runVoice(intent.intent)) {
            is CmdResult.Ok -> emit(EventKind.Success, intent.phrases.firstOrNull() ?: intent.intent, S("Выполнено"))
            is CmdResult.Failed -> emit(EventKind.Failed, intent.intent, r.reason)
            is CmdResult.Unsupported -> emit(EventKind.Unsupported, intent.intent, r.reason)
        }
    }

    // --- команды ---------------------------------------------------------

    fun send(type: Int, value: String, label: String = "") =
        send(listOf(VehicleCommand(type, value, label)))

    /** Отправляет пачку команд как одно действие: одно уведомление на результат. */
    fun send(cmds: List<VehicleCommand>, label: String = "") = viewModelScope.launch {
        if (cmds.isEmpty()) return@launch
        val previous = _optimistic.value
        val types = cmds.map { it.type }.toSet()
        // сразу показываем ожидаемое состояние, чтобы кнопка не «залипала»
        _optimistic.update { it + cmds.associate { c -> c.type to c.value } }
        _pending.update { it + types }
        _busy.value = true

        // Команды пачки шлём параллельно, а не по очереди: активация набора
        // (например, всех сидений) должна быть быстрой, а не ждать 8 round-trip.
        val results = try {
            coroutineScope { cmds.map { c -> async { repo.send(c) } }.awaitAll() }
        } finally {
            _pending.update { it - types }
            _busy.value = false
        }

        val title = label.ifBlank { cmds.firstOrNull()?.label.orEmpty() }
            .ifBlank { S("Команда") }
        val failure = results.firstOrNull { !it.ok }
        val failed = results.count { !it.ok }
        if (failure == null) {
            _events.emit(CmdEvent(title, S("Выполнено"), EventKind.Success))
            refreshNow()
        } else if (failed < results.size) {
            // Часть пачки не прошла. Для «Выключить всё» это нормально: в машине
            // может не быть массажа или вентиляции задних сидений, и объявлять
            // всё действие проваленным из-за одной такой команды — врать. Что
            // прошло, то прошло; откатываем только непрошедшее.
            val broken = results.withIndex().filter { !it.value.ok }.map { cmds[it.index].type }
            _optimistic.update { current ->
                val restored = current.toMutableMap()
                broken.forEach { type ->
                    val before = previous[type]
                    if (before == null) restored.remove(type) else restored[type] = before
                }
                restored
            }
            // Частичный неуспех человеку не показываем: что прошло — прошло,
            // непрошедшее откатили выше. Счётчик «N из M» только пугал.
            _events.emit(CmdEvent(title, S("Выполнено"), EventKind.Success))
            refreshNow()
        } else {
            // не прошло ничего — откатываем оптимистичное состояние обратно
            _optimistic.value = previous
            val kind = when {
                failure is CmdResult.Unsupported -> EventKind.Unsupported
                _car.value.link == Link.NONE -> EventKind.Offline
                else -> EventKind.Failed
            }
            val reason = when (failure) {
                is CmdResult.Failed -> failure.reason
                is CmdResult.Unsupported -> failure.reason
                CmdResult.Ok -> ""
            }
            _events.emit(CmdEvent(title, reason, kind))
        }
    }

    // --- помощь (контакты поддержки) -------------------------------------

    private val _support = MutableStateFlow<SupportDto?>(null)
    val support: StateFlow<SupportDto?> = _support.asStateFlow()

    /** Удаление аккаунта: сервер стирает учётку, приложение выходит. */
    suspend fun deleteAccount(password: String): Result<Unit> {
        val r = repo.deleteAccount(password)
        if (r.isSuccess) {
            uz.electro.remote.push.Push.forget(getApplication())
            logout()
        }
        return r.recoverCatching { throw IllegalStateException(authError(it, S("Не удалось удалить аккаунт"))) }
    }

    /** Подтянуть контакты поддержки (тот же экран «Помощь», что на голове). */
    fun loadSupport() = viewModelScope.launch {
        repo.support()?.let { _support.value = it }
    }

    // --- отзыв -------------------------------------------------------------

    /**
     * Отправить отзыв с вложениями (uri из галереи: скриншоты, записи экрана);
     * в колбэк — null при успехе или причина отказа. Файлы читаются здесь, т.к.
     * только у ViewModel есть Context для contentResolver.
     */
    fun sendFeedback(
        kind: String, text: String, attachments: List<android.net.Uri>, onDone: (String?) -> Unit,
    ) = viewModelScope.launch {
        val version = uz.electro.remote.BuildConfig.VERSION_NAME +
            " (" + uz.electro.remote.BuildConfig.VERSION_CODE + ")"
        val files = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            attachments.mapNotNull { readFeedbackFile(it) }
        }
        val tooBig = attachments.size - files.size
        val r = repo.sendFeedback(kind, text, version, files)
        val failure = r.exceptionOrNull()?.let { repo.reason(it) }
        val outcome = r.getOrNull()
        onDone(when {
            failure != null -> failure
            outcome != null && (outcome.failed > 0 || tooBig > 0) ->
                S("Отзыв отправлен, но {0} из {1} вложений не приложились (слишком большие или нет связи)", outcome.failed + tooBig, attachments.size)
            else -> null
        })
    }

    private fun readFeedbackFile(uri: android.net.Uri): uz.electro.remote.data.FeedbackFile? {
        val cr = getApplication<Application>().contentResolver
        val mime = cr.getType(uri) ?: return null
        if (!(mime.startsWith("image/") || mime.startsWith("video/"))) return null
        val bytes = runCatching { cr.openInputStream(uri)?.use { it.readBytes() } }.getOrNull() ?: return null
        if (bytes.size > MAX_ATTACHMENT_BYTES) return null
        var name = uri.lastPathSegment?.substringAfterLast('/')?.take(100) ?: "file"
        runCatching {
            cr.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0)?.let { name = it.take(100) }
            }
        }
        return uz.electro.remote.data.FeedbackFile(name, mime, bytes)
    }

    // --- сиденья по профилю ----------------------------------------------

    private var seatTimer: Job? = null

    /** Задан ли профиль сидений (есть хотя бы одно включённое место). */
    fun seatPresetSet(): Boolean = readSeatPreset().first.isNotEmpty()

    /**
     * Сохранить набор с экрана сидений как профиль и сразу применить его.
     * `levels` — тип→уровень (0..3); `timerMin` — авто-выключение, 0 = без таймера.
     */
    fun applySeats(levels: Map<Int, Int>, timerMin: Int) {
        saveSeatPreset(levels, timerMin)
        sendSeats(levels, timerMin)
    }

    /** Тумблер на главной: включить сиденья по сохранённому профилю. */
    fun seatsOn() {
        val (levels, timer) = readSeatPreset()
        if (levels.isEmpty()) {
            viewModelScope.launch {
                _events.emit(CmdEvent(S("Сиденья"), S("Сначала настройте профиль на экране сидений"), EventKind.Failed))
            }
            return
        }
        sendSeats(levels, timer)
    }

    /** Тумблер на главной: выключить все сиденья и снять таймер. */
    fun seatsOff() {
        seatTimer?.cancel(); seatTimer = null
        send(Cmd.seatsOff(), S("Выключить сиденья"))
    }

    private fun sendSeats(levels: Map<Int, Int>, timerMin: Int) {
        // Шлём весь набор мест: нулевые снимают то, чего в профиле нет.
        val cmds = Cmd.SEAT_TYPES.map { t -> VehicleCommand(t, (levels[t] ?: 0).toString()) }
        send(cmds, S("Сиденья"))
        startSeatTimer(timerMin)
    }

    private fun startSeatTimer(min: Int) {
        seatTimer?.cancel(); seatTimer = null
        if (min <= 0) return
        // Клиентский таймер: работает, пока приложение живо. Дотянет сиденья до
        // авто-выключения в обычном сценарии (прогрел — ушёл); переживать выгрузку
        // приложения ему нечем — для этого нужен серверный планировщик, как у климата.
        seatTimer = viewModelScope.launch {
            delay(min.toLong() * 60_000L)
            send(Cmd.seatsOff(), S("Сиденья: таймер"))
        }
    }

    private fun saveSeatPreset(levels: Map<Int, Int>, timerMin: Int) {
        val json = JSONObject()
        val lv = JSONObject()
        levels.filterValues { it > 0 }.forEach { (t, l) -> lv.put(t.toString(), l) }
        json.put("levels", lv)
        json.put("timer", timerMin.coerceIn(0, 60))
        settings.seatPreset = json.toString()
    }

    private fun readSeatPreset(): Pair<Map<Int, Int>, Int> {
        val raw = settings.seatPreset
        if (raw.isBlank()) return emptyMap<Int, Int>() to 0
        return runCatching {
            val j = JSONObject(raw)
            val lv = j.optJSONObject("levels") ?: JSONObject()
            val map = HashMap<Int, Int>()
            lv.keys().forEach { k -> k.toIntOrNull()?.let { map[it] = lv.optInt(k) } }
            (map as Map<Int, Int>) to j.optInt("timer", 0)
        }.getOrDefault(emptyMap<Int, Int>() to 0)
    }

    // --- настройки и аккаунт ---------------------------------------------

    fun setCloudUrl(url: String) {
        settings.cloudUrl = url
    }

    fun setCloudEnabled(enabled: Boolean) {
        settings.cloudEnabled = enabled
    }

    fun login(email: String, password: String, onDone: (String?) -> Unit) = viewModelScope.launch {
        _busy.value = true
        val result = signIn(email, password)
        _busy.value = false
        onDone(result.exceptionOrNull()?.let { authError(it, S("Не удалось войти")) })
    }

    /** Вход с экрана входа. Тот же путь, что и из настроек, но без обратного вызова. */
    suspend fun signIn(email: String, password: String): Result<Unit> {
        val result = repo.login(email, password)
        _loggedIn.value = settings.loggedIn
        if (result.isSuccess) afterAuth()
        return result
    }

    /**
     * Регистрация. Сервер сразу отдаёт сессию, поэтому дальше — тот же путь,
     * что и после входа: подтянуть парк и снять состояние.
     */
    suspend fun register(
        email: String, password: String, name: String, phone: String,
    ): Result<Unit> {
        val result = repo.register(email, password, name, phone)
        _loggedIn.value = settings.loggedIn
        if (result.isSuccess) afterAuth()
        return result
    }

    /** «Забыл пароль»: заявка мастеру. Сессии не создаёт и состояние не меняет. */
    suspend fun requestPasswordReset(email: String, contact: String): Result<Unit> =
        repo.requestPasswordReset(email, contact)

    /** Привязка машины по QR с экрана головы. */
    suspend fun claimPairing(payload: String): Result<String> {
        val result = Pairing.claim(payload, repo)
        if (result.isSuccess) {
            _vehicleId.value = settings.vehicleId
            loadVehicles().join()
            refreshNow()
        }
        return result
    }

    /**
     * Текст отказа для экранов входа.
     *
     * 409 — занятый email, и это не «что-то пошло не так»: человеку надо
     * предложить вход, а не повтор с тем же адресом. Остальное отдаёт
     * репозиторий: он достаёт причину из тела ответа, а не показывает код.
     */
    fun authError(error: Throwable, fallback: String): String = when {
        (error as? HttpException)?.code() == 409 ->
            S("Этот email уже зарегистрирован — войдите или восстановите пароль")
        (error as? HttpException)?.code() == 422 ->
            S("Проверьте email и пароль (от 8 знаков)")
        error is HttpException -> repo.reason(error)
        else -> friendlyNetworkError(error)
            ?: scrubAddresses(error.message)?.takeIf { it.isNotBlank() } ?: fallback
    }

    /** Общий хвост входа и регистрации. */
    private fun afterAuth() {
        _vehicleId.value = settings.vehicleId
        loadVehicles()
        refreshNow()
        loadNews()
        uz.electro.remote.push.Push.register(getApplication())
    }

    fun logout() {
        uz.electro.remote.push.Push.forget(getApplication())
        repo.logout()
        _news.value = emptyList()
        _loggedIn.value = false
        _vehicles.value = emptyList()
        _vehicleId.value = null
        _parkNote.value = null
        _parkKnown.value = false
    }

    // --- выбор машины -----------------------------------------------------

    /** Перечитать парк с сервера. Вызывается при открытии настроек. */
    fun loadVehicles() = viewModelScope.launch {
        if (!settings.loggedIn) return@launch
        repo.vehicles().fold(
            onSuccess = { list ->
                _vehicles.value = list
                _parkKnown.value = true
                _parkNote.value = if (list.isEmpty()) {
                    S("Аккаунту не выдана ни одна машина — доступ выдаёт мастер")
                } else null
                // Машина не выбрана — берём C16: команды этого приложения для неё.
                // Пустой парк — законный ответ, а не сбой: доступ мог ещё не
                // выдать мастер или слететь при переприёмке машины. Раньше
                // здесь стоял list.first(), и приложение падало вместо того,
                // чтобы отправить человека на привязку по QR.
                val preferred = list.firstOrNull { it.model.equals("C16", true) }
                    ?: list.firstOrNull()
                if (_vehicleId.value.isNullOrBlank() && preferred != null) {
                    selectVehicle(preferred.vehicle_id)
                }
                // Машина, к которой доступ уже отозван, не должна оставаться
                // выбранной: команды в неё уходили бы в никуда.
                if (list.none { it.vehicle_id == _vehicleId.value }) {
                    _vehicleId.value = preferred?.vehicle_id
                    settings.vehicleId = preferred?.vehicle_id
                }
            },
            onFailure = { failure ->
                // Токен мог быть выдан другим сервером или просто истечь. Показывать
                // «вход выполнен» и рядом ошибку — вводить в заблуждение: сессии нет,
                // и пока не войдёшь заново, ничего работать не будет.
                if (repo.isUnauthorized(failure)) {
                    repo.logout()
                    _loggedIn.value = false
                    _vehicles.value = emptyList()
                    _vehicleId.value = null
                    _parkKnown.value = false
                    _parkNote.value = S("Сессия недействительна — войдите заново")
                } else {
                    val why = friendlyNetworkError(failure) ?: scrubAddresses(failure.message)
                    _parkNote.value = S("Не удалось получить список машин") + (why?.let { ": $it" } ?: "")
                }
            },
        )
    }

    fun selectVehicle(vehicleId: String) {
        _paint.value = settings.vehiclePaint(vehicleId)
        repo.selectVehicle(vehicleId)
        _vehicleId.value = vehicleId
        refreshNow()
        // У новой машины может быть другая модель → другой набор кнопок. Без
        // этого при переключении C16↔C10 остался бы набор прошлой машины.
        loadMeta()
    }

    /** Отвязать машину от аккаунта, затем перечитать парк. */
    fun unlinkVehicle(vehicleId: String) = viewModelScope.launch {
        repo.unlinkVehicle(vehicleId).onSuccess {
            if (_vehicleId.value == vehicleId) { _vehicleId.value = null; settings.vehicleId = null }
            loadVehicles()
            emit(EventKind.Success, S("Машина отвязана"), null)
        }.onFailure {
            emit(EventKind.Failed, S("Не удалось отвязать"), it.message)
        }
    }

    /** Локальное имя машины (ник) — как показывать её в этом телефоне. */
    fun vehicleNick(id: String): String? = settings.vehicleNick(id)

    fun setVehicleNick(id: String, name: String?) {
        settings.setVehicleNick(id, name)
        // перечитывать не нужно: имя локальное, но дёрнем список, чтобы UI обновился
        _vehicles.value = _vehicles.value.toList()
    }

    /** Цвет кузова выбранной машины (локально). */
    private val _paint = MutableStateFlow(settings.vehicleId?.let { settings.vehiclePaint(it) })
    val paint: StateFlow<String?> = _paint.asStateFlow()

    fun vehiclePaint(id: String): String? = settings.vehiclePaint(id)

    fun setVehiclePaint(id: String, paint: String?) {
        settings.setVehiclePaint(id, paint)
        if (id == settings.vehicleId) _paint.value = paint
        _vehicles.value = _vehicles.value.toList()
    }

    private companion object {
        /** Лимит одного вложения к отзыву — совпадает с сервером (40 МБ). */
        const val MAX_ATTACHMENT_BYTES = 40L * 1024 * 1024
        /** Сколько ждём отклика машины после побудки. */
        const val WAKE_WAIT_SEC = 20
        /** Шаг тикера на экране подключения — секунды должны идти ровно. */
        const val TICK_MS = 250L

        const val POLL_CLOUD_MS = 10_000L
        const val POLL_OFFLINE_MS = 15_000L
        const val PROBE_STEP_MS = 3_000L

        /** Команды, состояние которых машина отдаёт обратно. */
        val SIGNAL_BACKED = setOf(
            Cmd.AC, Cmd.TEMP_L, Cmd.TEMP_R, Cmd.FAN, Cmd.TRUNK,
            Cmd.RECIRC, Cmd.DEFROST_FRONT, Cmd.DEFROST_REAR, Cmd.MIRROR_HEAT,
            Cmd.SEAT_VENT_REAR_L, Cmd.SEAT_VENT_REAR_R,
            Cmd.MASSAGE_DRIVER, Cmd.MASSAGE_PASSENGER,
        ) + Cmd.WINDOWS
    }
}
