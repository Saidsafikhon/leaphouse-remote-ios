package uz.electro.remote.ui

import uz.electro.remote.i18n.S
import uz.electro.remote.ui.components.Lx
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import uz.electro.remote.CarViewModel
import uz.electro.remote.CmdEvent
import uz.electro.remote.ConnectPhase
import uz.electro.remote.EventKind
import uz.electro.remote.R
import uz.electro.remote.data.*
import uz.electro.remote.ui.components.*
import uz.electro.remote.ui.components.BrandLockup
import uz.electro.remote.ui.components.CarArt
import uz.electro.remote.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Главный экран по обложке (Figma: «Electro Remote — Design System»,
 * страница Screens → «Home — обложка»).
 *
 * Порядок сверху вниз: марка и модель → фото машины → одна полоса состояния
 * (охрана, запас хода, заряд) → восемь быстрых действий сеткой 4×2 → климат.
 * Подробное управление живёт в свёрнутых разделах ниже и на вкладке «Управление».
 */
@Composable
fun PhoneControlScreen(vm: CarViewModel = viewModel()) {
    val connect by vm.connect.collectAsState()

    // пока машина не разбужена, внутрь не пускаем — команды ушли бы в спящий модем
    if (connect.phase != ConnectPhase.Connected) {
        ConnectScreen(vm, connect)
        return
    }

    val car by vm.car.collectAsState()
    val controls by vm.controls.collectAsState()
    val pending by vm.pending.collectAsState()
    val chosen by vm.selectedVehicle.collectAsState()
    val support by vm.support.collectAsState()
    val paint by vm.paint.collectAsState()
    val caps by vm.capabilities.collectAsState()
    val scenes by vm.scenes.collectAsState()
    val schedules by vm.schedules.collectAsState()
    val voiceIntents by vm.voiceIntents.collectAsState()
    val news by vm.news.collectAsState()
    val newsRead by vm.newsRead.collectAsState()
    val unreadNews by vm.unreadNews.collectAsState()

    var tab by remember { mutableStateOf("car") }
    var toast by remember { mutableStateOf<CmdEvent?>(null) }

    // опрос идёт только пока экран на переднем плане
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> vm.startPolling()
                Lifecycle.Event.ON_STOP -> vm.stopPolling()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            vm.stopPolling()
        }
    }

    LaunchedEffect(Unit) { vm.events.collect { toast = it } }
    LaunchedEffect(toast) {
        if (toast != null) { delay(3000); toast = null }
    }

    val send: (Int, String) -> Unit = { type, value -> vm.send(type, value) }
    val state: (Int) -> ControlState = { type ->
        when {
            type in pending -> ControlState.Pending
            controls[type] == "1" -> ControlState.Active
            else -> ControlState.Default
        }
    }


    Scaffold(
        containerColor = ElectroColors.Background,
        bottomBar = {
            BottomNav(tab) { selected -> tab = selected }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize().background(ElectroColors.Background)) {
            when (tab) {
                "map" -> MapScreen(car.location)
                "settings" -> SettingsScreen(vm) { tab = "car" }
                "climate", "seats" -> ClimateSeatsScreen(
                    car = car, controls = controls, initialTab = tab, send = send,
                    sendAll = { cmds, label -> vm.send(cmds, label) },
                    onClimateOn = { minutes -> vm.climateOn(minutes) },
                    climateBlockReason = { c, ctl -> climateBlocker(c, ctl) },
                    onApplySeats = { levels, timer -> vm.applySeats(levels, timer) },
                    onClose = { tab = "car" },
                )
                "scenes" -> ScenesScreen(
                    scenes,
                    onRun = { vm.runScene(it) },
                    onDelete = { vm.deleteScene(it.template_id) },
                    onCreate = { name, steps -> vm.createScene(name, steps) },
                    onBack = { tab = "car" },
                )
                "schedule" -> ScheduleScreen(
                    schedules,
                    onCreate = { vm.createSchedule(it) },
                    onToggle = { s, on -> vm.setScheduleEnabled(s.schedule_id, on) },
                    onDelete = { vm.deleteSchedule(it.schedule_id) },
                    onBack = { tab = "car" },
                )
                "voice" -> VoiceScreen(
                    voiceIntents,
                    onRun = { vm.runVoice(it) },
                    onBack = { tab = "car" },
                )
                "shop" -> {
                    LaunchedEffect(Unit) { vm.loadProducts() }
                    val products by vm.products.collectAsState()
                    ShopScreen(products, loggedIn = true, phoneHint = "", model = chosen?.model,
                        onOrder = { id, qty, phone, comment, done -> vm.order(id, qty, phone, comment, done) }, onBack = { tab = "car" })
                }
                "news" -> {
                    LaunchedEffect(Unit) { vm.loadNews() }
                    NewsScreen(news, newsRead, onRead = { vm.markNewsRead(it.id) },
                        onReadAll = { vm.markAllNewsRead() }, onBack = { tab = "car" })
                }
                else -> HomeTab(
                    car = car,
                    model = chosen?.model ?: "C16",
                    paint = paint,
                    support = support,
                    caps = caps,
                    onDisconnect = { vm.disconnect() },
                    unreadNews = unreadNews,
                    controls = controls,
                    stateOf = state,
                    send = send,
                    onSendAll = { cmds, label -> vm.send(cmds, label) },
                    onRefresh = { vm.refreshNow() },
                    onOpenClimate = { tab = "climate" },
                    onOpen = { dest -> tab = dest },
                    seatPresetSet = vm.seatPresetSet(),
                    onSeatsOn = { vm.seatsOn() },
                    onSeatsOff = { vm.seatsOff() },
                    onFeedback = { kind, text, files, done -> vm.sendFeedback(kind, text, files, done) },
                )
            }

            // результат команды: один снекбар на действие, вид зависит от исхода
            AnimatedVisibility(
                visible = toast != null,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
                modifier = Modifier.align(Alignment.BottomCenter).padding(Space.x4),
            ) {
                toast?.let { e ->
                    ElectroToast(
                        kind = when (e.kind) {
                            EventKind.Success -> BadgeKind.Success
                            EventKind.Failed -> BadgeKind.Failed
                            EventKind.Unsupported -> BadgeKind.Unconfirmed
                            EventKind.Offline -> BadgeKind.Offline
                        },
                        title = e.title,
                        message = e.message,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeTab(
    car: CarState,
    model: String,
    paint: String?,
    support: SupportDto?,
    caps: CapabilitiesDto?,
    onDisconnect: () -> Unit,
    unreadNews: Int,
    controls: Map<Int, String>,
    stateOf: (Int) -> ControlState,
    send: (Int, String) -> Unit,
    onSendAll: (List<VehicleCommand>, String) -> Unit,
    onRefresh: () -> Unit,
    onOpenClimate: () -> Unit,
    onOpen: (String) -> Unit,
    seatPresetSet: Boolean,
    onSeatsOn: () -> Unit,
    onSeatsOff: () -> Unit,
    onFeedback: (kind: String, text: String, files: List<android.net.Uri>, done: (String?) -> Unit) -> Unit,
) {
    // на виду четыре действия, ради которых открывают приложение; под
    // кнопкой — только сиденья
    var confirm by remember { mutableStateOf<HomeConfirm?>(null) }
    var blocked by remember { mutableStateOf<String?>(null) }
    var showHelp by remember { mutableStateOf(false) }
    var showFeedback by remember { mutableStateOf(false) }
    if (showHelp) HelpDialog(support, onFeedback = { showFeedback = true }) { showHelp = false }
    if (showFeedback) FeedbackDialog(onSend = onFeedback, onDismiss = { showFeedback = false })

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = Space.x5).padding(top = Space.x5, bottom = Space.x4),
        verticalArrangement = Arrangement.spacedBy(Space.x5),
    ) {
        HeaderLockup(car, model, unreadNews,
            onHelp = { showHelp = true }, onNews = { onOpen("news") }, onShop = { onOpen("shop") },
            onRefresh = onRefresh, onDisconnect = onDisconnect)
        Hero(model, paint)
        CarStatusStrip(car)

        SectionTitle(S("Панель быстрого доступа"))
        QuickRow(
            car, controls, stateOf, onSendAll,
            quick = caps?.quick ?: DEFAULT_QUICK,
            onClimate = { blocked = toggleClimate(car, controls, onSendAll) },
        ) { confirm = it }

        // Климат и сиденья — двумя карточками в ряд, как на панели GWM: у
        // каждой тумблер справа, включает и гасит целиком, а тап по карточке
        // открывает подробный экран.
        val hasClimate = caps?.quick?.contains("climate") != false
        val hasSeats = caps?.groups?.contains("seats") != false
        if (hasClimate || hasSeats) {
            // IntrinsicSize.Max + fillMaxHeight: обе плитки одной высоты, по большей
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(Space.x3)) {
                if (hasClimate) {
                    GwmClimateCard(
                        car, controls, Modifier.weight(1f).fillMaxHeight(),
                        onToggle = { blocked = toggleClimate(car, controls, onSendAll) },
                        onOpen = onOpenClimate,
                    )
                }
                if (hasSeats) {
                    GwmSeatsCard(
                        controls, Modifier.weight(1f).fillMaxHeight(),
                        hasPreset = seatPresetSet,
                        onOn = onSeatsOn, onOff = onSeatsOff,
                        onOpen = { onOpen("seats") },
                    )
                }
            }
        }

        // Входы в разделы: сцены, расписание, голос — по возможностям машины
        EntryRow(caps, onOpen)
    }

    confirm?.let { c ->
        ElectroDialog(
            Lx.Warning, ElectroColors.Warn, c.title, c.msg,
            confirmText = c.action,
            onConfirm = { onSendAll(c.cmds, c.label); confirm = null },
            onDismiss = { confirm = null },
        )
    }

    blocked?.let { reason ->
        ElectroDialog(
            Lx.Warning, ElectroColors.Warn,
            S("Климат не включаем"), reason,
            confirmText = S("Понятно"),
            onConfirm = { blocked = null },
            onDismiss = { blocked = null },
            dismissText = null,
        )
    }
}

/**
 * Одна кнопка климата: включить или погасить всё.
 *
 * Возвращает причину отказа, если машина открыта, и null, если команда ушла.
 * Проверку держит и сервер — он тут главный, — но телефон обязан объяснить
 * отказ до отправки: иначе человек видит красный ответ вместо «опущены стёкла».
 *
 * Выключение — это `allOff()`, а не один кондиционер: человек уходит от машины
 * и хочет, чтобы не осталось работать ничего, включая обогревы и сиденья.
 */
private fun toggleClimate(
    car: CarState,
    controls: Map<Int, String>,
    onSendAll: (List<VehicleCommand>, String) -> Unit,
): String? {
    if (climateOn(controls)) {
        // Только климат — сиденья и массаж не трогаем: это независимая система.
        onSendAll(Cmd.climateOff(), S("Выключить климат"))
        return null
    }
    climateBlocker(car, controls)?.let { return it }
    onSendAll(listOf(VehicleCommand(Cmd.AC, "1", S("Климат"))), S("Включить климат"))
    return null
}

/** Климат считаем включённым только по сигналу машины, а не по эху нажатия. */
/**
 * Климат включён, если сигнал «ac» с машины не ноль. Раньше сравнивали строго
 * с «1», а голова на части машин отдаёт режим (2 — авто и т.п.): тумблер гас,
 * хотя климат работал. Панель на самой голове считает так же (`!= "0"`).
 */
internal fun climateOn(controls: Map<Int, String>): Boolean = acOn(controls[Cmd.AC])

internal fun acOn(v: String?): Boolean {
    val s = v?.trim()?.lowercase() ?: return false
    if (s.isEmpty() || s == "false" || s == "null") return false
    return s.toFloatOrNull()?.let { it != 0f } ?: (s == "true" || s == "on")
}

/**
 * Почему сейчас нельзя включать климат. null — можно.
 *
 * Гонять кондиционер в открытую машину — это отопление улицы за счёт тяговой
 * батареи. Дверей по отдельности голова не отдаёт, поэтому «открыта» здесь —
 * это то, что она сообщает: стёкла, багажник, замки.
 */
// По требованию пользователя климату НИЧЕГО не мешает включаться/выключаться:
// прежние проверки (стёкла, багажник, двери, замок) сняты полностью — всегда
// null. Оставлено функцией, чтобы не трогать все точки вызова.
internal fun climateBlocker(car: CarState, controls: Map<Int, String>): String? = null

/** Подтверждение действия, которое снимает машину с охраны. */
private data class HomeConfirm(
    val title: String,
    val msg: String,
    val action: String,
    val cmds: List<VehicleCommand>,
    val label: String,
)

private fun toggle(current: String?): String = if (current == "1") "0" else "1"

private fun climateSummary(car: CarState, controls: Map<Int, String>): String {
    val on = acOn(controls[Cmd.AC])
    val cabin = car.cabinTemp?.let { S("в салоне {0}°", it.asTemp()) } ?: S("температура неизвестна")
    return (if (on) S("Включён") else S("Выключен")) + " · " + cabin
}

internal fun seatSummary(controls: Map<Int, String>): String {
    fun count(types: List<Int>) = types.count { (controls[it]?.toFloatOrNull()?.toInt() ?: 0) > 0 }
    val heat = count(listOf(
        Cmd.SEAT_HEAT_DRIVER, Cmd.SEAT_HEAT_PASSENGER,
        Cmd.SEAT_HEAT_REAR_L, Cmd.SEAT_HEAT_REAR_R,
    ))
    val vent = count(listOf(
        Cmd.SEAT_VENT_DRIVER, Cmd.SEAT_VENT_PASSENGER,
        Cmd.SEAT_VENT_REAR_L, Cmd.SEAT_VENT_REAR_R,
    ))
    // коротко — подпись живёт в узкой плитке на главной, ей нельзя переноситься
    return when {
        heat == 0 && vent == 0 -> S("Выключены")
        vent == 0 -> S("Обогрев · {0}", heat)
        heat == 0 -> S("Обдув · {0}", vent)
        else -> S("Обогрев {0} · обдув {1}", heat, vent)
    }
}

/** Уровень 0..3 по сигналу с машины или по последней команде. */
internal fun seatLevel(controls: Map<Int, String>, type: Int): Int =
    controls[type]?.toFloatOrNull()?.toInt()?.coerceIn(0, SEAT_LEVELS) ?: 0

@Composable
internal fun SeatCell(
    name: String,
    heatType: Int,
    ventType: Int,
    controls: Map<Int, String>,
    send: (Int, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Активация должна ощущаться мгновенно: показываем нажатый уровень сразу
    // (optHeat/optVent), фактический с машины (/state) подтягивается в фоне.
    // Подмену снимаем, как только реальное совпало, либо через 5 с.
    val realHeat = seatLevel(controls, heatType)
    val realVent = seatLevel(controls, ventType)
    var optHeat by remember { mutableStateOf<Int?>(null) }
    var optVent by remember { mutableStateOf<Int?>(null) }
    val heat = optHeat ?: realHeat
    val vent = optVent ?: realVent
    LaunchedEffect(realHeat) { if (optHeat == realHeat) optHeat = null }
    LaunchedEffect(realVent) { if (optVent == realVent) optVent = null }
    LaunchedEffect(optHeat) { if (optHeat != null) { kotlinx.coroutines.delay(5000); optHeat = null } }
    LaunchedEffect(optVent) { if (optVent != null) { kotlinx.coroutines.delay(5000); optVent = null } }
    SeatControl(
        name = name,
        heat = heat,
        vent = vent,
        modifier = modifier,
        onHeat = { lvl ->
            optHeat = lvl
            if (lvl > 0) optVent = 0        // обогрев и обдув взаимоисключаются
            send(heatType, lvl.toString())
        },
        onVent = { lvl ->
            optVent = lvl
            if (lvl > 0) optHeat = 0
            send(ventType, lvl.toString())
        },
    )
}

// ---------- блоки главного экрана ----------

/** Марка мелко, модель крупно и легко, под ней — когда обновлялись данные. */
@Composable
private fun HeaderLockup(
    car: CarState, model: String, unread: Int,
    onHelp: () -> Unit, onNews: () -> Unit, onShop: () -> Unit, onRefresh: () -> Unit, onDisconnect: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            BrandLockup(markSize = 22.dp)
            Spacer(Modifier.height(Space.x1))
            // Модель выбранной машины. Выбор машины — в настройках окна
            // подключения («Мои машины»), а не здесь: на экране управления ты
            // уже подключён к конкретной машине.
            Text(model, style = ElectroType.Display, color = ElectroColors.TextPrimary)
            Spacer(Modifier.height(Space.x1))
            Row(
                Modifier.clickable(onClick = onRefresh),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Lx.Refresh, null, tint = ElectroColors.TextMuted,
                    modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(updatedText(car), style = ElectroType.Caption, color = ElectroColors.TextMuted)
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(Space.x2)) {
            DisconnectChip(onDisconnect)
            Row(horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                ShopFab(onShop)
                NewsBell(unread, onNews)
                HelpFab(onHelp)
            }
        }
    }
}

/**
 * «Отключиться» в углу: отпускает машину обратно в сон и закрывает сеанс.
 *
 * Подпись, а не одна иконка: значок питания в углу читается и как «выйти из
 * приложения», и как «заглушить машину», а разница тут велика.
 */
@Composable
private fun DisconnectChip(onDisconnect: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(ElectroColors.SurfaceElevated)
            .clickable(onClick = onDisconnect)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Lx.PowerSettingsNew, null,
            tint = ElectroColors.TextSecondary, modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(S("Отключиться"), style = ElectroType.Caption, color = ElectroColors.TextSecondary)
    }
}

private fun updatedText(car: CarState): String = when {
    car.updatedAt == 0L -> S("Обновление…")
    car.link == Link.NONE -> S("Нет данных с машины")
    else -> S("Обновлено ") + SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(car.updatedAt))
}

@Composable
private fun Hero(model: String?, paint: String?) {
    // 3D-машина (крутится пальцем), пока модель не скачалась — студийный рендер.
    var ready by remember { mutableStateOf(false) }
    val swatch = CarArt.paints(model).firstOrNull { it.code == paint }?.swatch
        ?: CarArt.paints(model).firstOrNull()?.swatch ?: androidx.compose.ui.graphics.Color(0xFFE9EAEC)
    Box(Modifier.fillMaxWidth().height(230.dp)) {
        if (!ready) Image(
            painterResource(CarArt.image(model, paint)), null,
            modifier = Modifier.fillMaxWidth().height(190.dp).align(Alignment.Center).padding(horizontal = Space.x2),
            contentScale = ContentScale.Fit,
        )
        uz.electro.remote.ui.components.CarModelView(
            model = model, paint = swatch,
            modifier = Modifier.fillMaxSize().then(if (ready) Modifier else Modifier.alpha(0f)),
            onReady = { ready = it },
        )
    }
}

/** Одна полоса вместо четырёх карточек: охрана слева, запас и заряд справа. */
@Composable
private fun CarStatusStrip(car: CarState) {
    val open = car.doors.anyOpen || car.trunkOpen == true || car.hoodOpen
    val locked = car.locked == true
    // Охрана и замки — разные вещи, и подменять одно другим нельзя: снятая с
    // охраны машина с запертыми дверьми раньше показывалась как «на охране».
    val doorLine = when (car.locked) {
        true -> S("двери закрыты")
        false -> S("двери отперты")
        null -> S("состояние дверей неизвестно")
    }
    val (icon, title, subtitle, accent, tint) = when {
        car.link == Link.NONE -> Quint(
            Lx.CloudOff, S("Нет связи с машиной"), S("Показаны последние данные"),
            ElectroColors.TextMuted, ElectroColors.SurfaceElevated,
        )
        open -> Quint(
            Lx.Warning, S("Автомобиль открыт"), openDetail(car),
            ElectroColors.Danger, ElectroColors.DangerTint,
        )
        // Машина на охране с отпертыми дверьми — небезопасное состояние, а не
        // мелочь: именно его сервер компенсирует блокировкой.
        car.security == Security.ARMED && car.locked == false -> Quint(
            Lx.Warning, S("На охране, но двери отперты"), S("Закройте двери"),
            ElectroColors.Danger, ElectroColors.DangerTint,
        )
        car.security == Security.ARMED -> Quint(
            Lx.Shield, S("Автомобиль на охране"), doorLine,
            ElectroColors.Ok, ElectroColors.OkTint,
        )
        car.security == Security.DISARMED -> Quint(
            Lx.LockOpen, S("Снят с охраны"), doorLine,
            ElectroColors.Warn, ElectroColors.WarnTint,
        )
        // Охрану машина не сообщила — говорим только про замки и не выдаём
        // догадку за факт.
        locked -> Quint(
            Lx.Lock, S("Двери закрыты"), S("Охрана не сообщается"),
            ElectroColors.TextSecondary, ElectroColors.SurfaceElevated,
        )
        else -> Quint(
            Lx.LockOpen, S("Двери отперты"), S("Охрана не сообщается"),
            ElectroColors.Warn, ElectroColors.WarnTint,
        )
    }
    StatusStrip(
        icon = icon, title = title, subtitle = subtitle,
        accent = accent, accentTint = tint,
        metrics = listOfNotNull(
            car.rangeKm?.let { Metric("$it", S("км"), (it / 500f)) },
            car.soc?.let { Metric("$it", "%", it / 100f) },
        ),
    )
}

private data class Quint(
    val a: ImageVector, val b: String, val c: String,
    val d: androidx.compose.ui.graphics.Color, val e: androidx.compose.ui.graphics.Color,
)

private fun openDetail(car: CarState): String = listOfNotNull(
    if (car.hoodOpen) S("капот") else null,
    if (car.trunkOpen == true) S("багажник") else null,
    if (car.doors.anyOpen) S("дверь") else null,
).joinToString(", ").replaceFirstChar { it.uppercase() }.ifBlank { S("Проверьте автомобиль") }

/**
 * Четыре действия, ради которых открывают приложение: замки, багажник,
 * климат и все окна разом. Каждое — один переключатель: открыто закрывает,
 * закрыто открывает, и подпись говорит, что случится по нажатию.
 */
@Composable
private fun QuickRow(
    car: CarState,
    controls: Map<Int, String>,
    stateOf: (Int) -> ControlState,
    onSendAll: (List<VehicleCommand>, String) -> Unit,
    quick: List<String>,
    onClimate: () -> Unit,
    onConfirm: (HomeConfirm) -> Unit,
) {
    // Приоритет — статус машины; пока его нет (стенд без кузова, машина ещё не
    // отдала состояние) — показываем по последней команде: LOCK «0»=закрыть →
    // заперто, «1»=открыть → отперто; TRUNK «1»=открыть. Иначе значок «двери»
    // висел на «Открыть», хотя команда уже ушла в машину.
    // Сначала ЭХО последней команды (controls[LOCK]: 0=закрыто/1=открыто), потом
    // статус машины. Иначе на C10 статус замка ненадёжен/залипает, и кнопка
    // застревала в «Закрыть двери», не реагируя на нажатие.
    val locked = (controls[Cmd.LOCK]?.let { it.trim() == "0" }) ?: car.locked ?: true
    val trunkOpen = car.trunkOpen ?: (controls[Cmd.TRUNK]?.let { it.trim() == "1" } ?: false)
    val windowsOpen = windowsOpen(controls)

    // Плитки строит сервер (capabilities): у C11 другой набор, и рисовать
    // кнопку, которой у машины нет, — значит слать команду в никуда.
    val tiles: List<@Composable RowScope.() -> Unit> = quick.mapNotNull { key ->
        when (key) {
            "lock" -> ({
                ControlTile(
                    if (locked) S("Открыть двери") else S("Закрыть двери"),
                    if (locked) Lx.Lock else Lx.LockOpen,
                    stateOf(Cmd.LOCK).orActive(!locked),
                    Modifier.weight(1f),
                ) {
                    if (locked) onConfirm(HomeConfirm(
                        S("Открыть двери?"), S("Автомобиль будет разблокирован."), S("Открыть"),
                        listOf(VehicleCommand(Cmd.LOCK, "1")), S("Открыть двери"),
                    )) else onSendAll(listOf(VehicleCommand(Cmd.LOCK, "0")), S("Закрыть двери"))
                }
            })
            "trunk" -> ({
                ControlTile(
                    S("Багажник"), Lx.DirectionsCar,
                    stateOf(Cmd.TRUNK).orActive(trunkOpen), Modifier.weight(1f),
                ) {
                    if (trunkOpen) onSendAll(listOf(VehicleCommand(Cmd.TRUNK, "0")), S("Закрыть багажник"))
                    else onConfirm(HomeConfirm(
                        S("Открыть багажник?"), S("Багажник будет разблокирован."), S("Открыть"),
                        listOf(VehicleCommand(Cmd.TRUNK, "1")), S("Открыть багажник"),
                    ))
                }
            })
            "climate" -> ({
                ControlTile(
                    if (climateOn(controls)) S("Климат выкл") else S("Климат"),
                    Lx.AcUnit,
                    stateOf(Cmd.AC).orActive(climateOn(controls)),
                    Modifier.weight(1f), onClick = onClimate,
                )
            })
            "windows" -> ({
                ControlTile(
                    if (windowsOpen) S("Закрыть окна") else S("Открыть окна"),
                    if (windowsOpen) Lx.ExpandLess else Lx.ExpandMore,
                    stateOf(Cmd.WINDOW_FL).orActive(windowsOpen), Modifier.weight(1f),
                ) {
                    val value = if (windowsOpen) "0" else "100"
                    onSendAll(
                        Cmd.WINDOWS.map { VehicleCommand(it, value) },
                        if (windowsOpen) S("Закрыть все окна") else S("Открыть все окна"),
                    )
                }
            })
            else -> null
        }
    }

    // По четыре в ряд, как на панели GWM.
    tiles.chunked(4).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
            row.forEach { it() }
            // добиваем ряд пустыми ячейками, чтобы плитки не растягивались на всю ширину
            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

/** Входы в разделы под панелью — по возможностям машины. */
@Composable
private fun EntryRow(caps: CapabilitiesDto?, onOpen: (String) -> Unit) {
    val entries = buildList {
        if (caps?.scenes != false) add(Triple("scenes", S("Мои сцены"), Lx.Dashboard))
        if (caps?.climate_schedule != false) add(Triple("schedule", S("Расписание"), Lx.Schedule))
        if (caps?.voice != false) add(Triple("voice", S("Голос"), Lx.RecordVoiceOver))
    }
    if (entries.isEmpty()) return
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
        entries.forEach { (dest, title, icon) ->
            ControlTile(title, icon, ControlState.Default, Modifier.weight(1f)) { onOpen(dest) }
        }
        repeat(3 - entries.size) { Spacer(Modifier.weight(1f)) }
    }
}

/** Набор кнопок по умолчанию, пока сервер не ответил про возможности. */
private val DEFAULT_QUICK = listOf("lock", "trunk", "climate", "windows")

/** Открытым считаем стекло, опущенное больше чем на пять процентов. *//** Открытым считаем стекло, опущенное больше чем на пять процентов. */
internal fun windowsOpen(controls: Map<Int, String>): Boolean =
    Cmd.WINDOWS.any { (controls[it]?.toFloatOrNull() ?: 0f) > 5f }

/** Плитка показывает состояние машины, а не значение команды; ожидание важнее. */
private fun ControlState.orActive(active: Boolean): ControlState = when {
    this == ControlState.Pending -> this
    active -> ControlState.Active
    else -> ControlState.Default
}

/**
 * Карточка климата: крупная уставка и круглые ±.
 *
 * Скорость и направление обдува с экрана убраны: направление шло в машину
 * номерами, которые на ней не сверяли, а скорость машина выбирает сама — на то
 * у неё автоматический режим. Остаётся то, ради чего в карточку смотрят:
 * сколько выставлено и сколько в салоне.
 */
@Composable
private fun ClimateCard(
    car: CarState,
    controls: Map<Int, String>,
    send: (Int, String) -> Unit,
    onOpen: () -> Unit,
) {
    val temp = controls[Cmd.TEMP_L]?.toFloatOrNull()?.toInt() ?: DEFAULT_TEMP
    Surface(color = ElectroColors.Surface, shape = Radius.Md, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Space.x4), verticalArrangement = Arrangement.spacedBy(Space.x3)) {
            Text(S("Климат в салоне"), style = ElectroType.Body, color = ElectroColors.TextSecondary,
                modifier = Modifier.clickable(onClick = onOpen))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(Cmd.tempLabel(temp), style = ElectroType.Display, color = ElectroColors.TextPrimary)
                    Text(
                        car.cabinTemp?.let { S("В салоне {0}°", it.asTemp()) } ?: S("В салоне —"),
                        style = ElectroType.Caption, color = ElectroColors.TextMuted,
                    )
                }
                RoundAction("", Lx.Remove) {
                    if (temp > Cmd.TEMP_MIN) setBothSides(send, temp - 1)
                }
                Spacer(Modifier.width(Space.x2))
                RoundAction("", Lx.Add) {
                    if (temp < Cmd.TEMP_MAX) setBothSides(send, temp + 1)
                }
            }
            autoOffNote(car, controls)?.let {
                Text(it, style = ElectroType.Caption, color = ElectroColors.TextMuted)
            }
        }
    }
}

/**
 * Обещание про таймер даём только когда климат работает и сервер сообщил срок.
 *
 * Срок держит сервер: приложение закрывают сразу после нажатия, и таймер в
 * телефоне умер бы ровно в том случае, ради которого он заведён.
 */
private fun autoOffNote(car: CarState, controls: Map<Int, String>): String? {
    if (!climateOn(controls)) return null
    val minutes = car.climateAutoOffMinutes
    return if (minutes > 0) S("Сервер погасит климат через {0} мин", minutes) else null
}

private fun setBothSides(send: (Int, String) -> Unit, value: Int) {
    send(Cmd.TEMP_L, value.toString())
    send(Cmd.TEMP_R, value.toString())
}

/** Сиденья, которые можно погасить разом: обогрев и вентиляция всех мест. */
internal fun seatsAllOff(): List<VehicleCommand> = listOf(
    Cmd.SEAT_HEAT_DRIVER, Cmd.SEAT_HEAT_PASSENGER, Cmd.SEAT_HEAT_REAR_L, Cmd.SEAT_HEAT_REAR_R,
    Cmd.SEAT_VENT_DRIVER, Cmd.SEAT_VENT_PASSENGER, Cmd.SEAT_VENT_REAR_L, Cmd.SEAT_VENT_REAR_R,
).map { VehicleCommand(it, "0") }

internal fun seatsAnyOn(controls: Map<Int, String>): Boolean = listOf(
    Cmd.SEAT_HEAT_DRIVER, Cmd.SEAT_HEAT_PASSENGER, Cmd.SEAT_HEAT_REAR_L, Cmd.SEAT_HEAT_REAR_R,
    Cmd.SEAT_VENT_DRIVER, Cmd.SEAT_VENT_PASSENGER, Cmd.SEAT_VENT_REAR_L, Cmd.SEAT_VENT_REAR_R,
).any { (controls[it]?.toFloatOrNull()?.toInt() ?: 0) > 0 }

/**
 * Карточка климата в стиле GWM: заголовок с тумблером справа и крупная уставка.
 * Тумблер включает и гасит климат целиком, тап по карточке открывает экран.
 */
@Composable
private fun GwmClimateCard(
    car: CarState,
    controls: Map<Int, String>,
    modifier: Modifier = Modifier,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
) {
    val on = climateOn(controls)
    val temp = controls[Cmd.TEMP_L]?.toFloatOrNull()?.toInt() ?: DEFAULT_TEMP
    Surface(color = ElectroColors.Surface, shape = Radius.Md,
        modifier = modifier.clickable(onClick = onOpen)) {
        Column(Modifier.padding(Space.x4).heightIn(min = 132.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(S("Климат"), style = ElectroType.Body, color = ElectroColors.TextPrimary,
                    modifier = Modifier.weight(1f))
                Switch(
                    checked = on, onCheckedChange = { onToggle() },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = ElectroColors.Accent,
                        checkedThumbColor = ElectroColors.OnAccent,
                    ),
                )
            }
            Spacer(Modifier.weight(1f))
            Text(Cmd.tempLabel(temp), style = ElectroType.Display, color = ElectroColors.TextPrimary)
            Text(
                car.cabinTemp?.let { S("в салоне {0}°", it.asTemp()) } ?: S("уставка"),
                style = ElectroType.Caption, color = ElectroColors.TextMuted,
            )
        }
    }
}

/**
 * Карточка сидений в стиле GWM: тумблер-мастер и четыре кресла сеткой.
 * Тумблер гасит все сиденья разом, тап по карточке открывает уровни по местам.
 */
@Composable
private fun GwmSeatsCard(
    controls: Map<Int, String>,
    modifier: Modifier = Modifier,
    hasPreset: Boolean,
    onOn: () -> Unit,
    onOff: () -> Unit,
    onOpen: () -> Unit,
) {
    val anyOn = seatsAnyOn(controls)
    // Акцент карточки по состоянию: обогрев → оранжевый, обдув → синий.
    val heatOn = listOf(Cmd.SEAT_HEAT_DRIVER, Cmd.SEAT_HEAT_PASSENGER,
        Cmd.SEAT_HEAT_REAR_L, Cmd.SEAT_HEAT_REAR_R).any { seatLevel(controls, it) > 0 }
    val ventOn = listOf(Cmd.SEAT_VENT_DRIVER, Cmd.SEAT_VENT_PASSENGER,
        Cmd.SEAT_VENT_REAR_L, Cmd.SEAT_VENT_REAR_R).any { seatLevel(controls, it) > 0 }
    val accent = when {
        heatOn -> ElectroColors.Warn
        ventOn -> ElectroColors.Info
        else -> ElectroColors.Accent
    }
    Surface(color = ElectroColors.Surface, shape = Radius.Md,
        modifier = modifier.clickable(onClick = onOpen)) {
        Column(Modifier.padding(Space.x4).heightIn(min = 132.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(S("Сиденья"), style = ElectroType.Body, color = ElectroColors.TextPrimary,
                    modifier = Modifier.weight(1f))
                Switch(
                    // Тумблер работает в обе стороны: вкл — по сохранённому
                    // профилю (какие места + обогрев/обдув + таймер, заданные на
                    // экране сидений), выкл — гасит все места. Пока профиля нет,
                    // включать нечего — тап открывает экран настройки.
                    checked = anyOn,
                    enabled = anyOn || hasPreset,
                    onCheckedChange = { on -> if (on) onOn() else onOff() },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = accent,
                        checkedThumbColor = ElectroColors.OnAccent,
                    ),
                )
            }
            Spacer(Modifier.weight(1f))
            SeatGlyphs(controls)
            Spacer(Modifier.height(Space.x2))
            Text(seatSummary(controls), style = ElectroType.Caption, color = ElectroColors.TextMuted,
                maxLines = 1)
        }
    }
}

/** Четыре кресла сеткой 2×2: горит то, что включено (обогрев или вентиляция). */
@Composable
private fun SeatGlyphs(controls: Map<Int, String>) {
    val seats = listOf(
        Cmd.SEAT_HEAT_DRIVER to Cmd.SEAT_VENT_DRIVER,
        Cmd.SEAT_HEAT_PASSENGER to Cmd.SEAT_VENT_PASSENGER,
        Cmd.SEAT_HEAT_REAR_L to Cmd.SEAT_VENT_REAR_L,
        Cmd.SEAT_HEAT_REAR_R to Cmd.SEAT_VENT_REAR_R,
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        seats.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { (heat, vent) ->
                    // обогрев → оранжевый, обдув → синий, выкл → тусклый
                    val tint = when {
                        seatLevel(controls, heat) > 0 -> ElectroColors.Warn
                        seatLevel(controls, vent) > 0 -> ElectroColors.Info
                        else -> ElectroColors.TextDisabled
                    }
                    // силуэт кресла (эскиз владельца), тонируется по состоянию
                    Icon(
                        painterResource(R.drawable.seat_front), null,
                        tint = tint,
                        modifier = Modifier.size(width = 20.dp, height = 30.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomNav(tab: String, onSelect: (String) -> Unit) {
    Surface(color = ElectroColors.Surface) {
        Row(
            Modifier.fillMaxWidth().padding(top = Space.x3, bottom = Space.x4),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            NavItem(Lx.Home, S("Главная"), tab == "car") { onSelect("car") }
            NavItem(Lx.AcUnit, S("Климат"), tab == "climate") { onSelect("climate") }
            NavItem(Lx.Map, S("Карта"), tab == "map") { onSelect("map") }
            NavItem(Lx.Settings, S("Настройки"), tab == "settings") { onSelect("settings") }
        }
    }
}

@Composable
private fun NavItem(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    val c = if (active) ElectroColors.Accent else ElectroColors.TextMuted
    Column(horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)) {
        Icon(icon, null, tint = c, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(5.dp))
        Text(label, style = ElectroType.Unit, color = c)
    }
}

// ---------- строки внутри свёрнутых разделов ----------

@Composable
private fun TempRow(label: String, value: Int?, onSet: (Int) -> Unit) {
    val current = value ?: DEFAULT_TEMP
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(S("Темп. {0}", label), style = ElectroType.Body, color = ElectroColors.TextPrimary,
            modifier = Modifier.weight(1f))
        ControlChip("−", Modifier.width(52.dp)) { if (current > Cmd.TEMP_MIN) onSet(current - 1) }
        Text(if (value == null) "—" else Cmd.tempLabel(current), style = ElectroType.Value,
            color = ElectroColors.Accent, modifier = Modifier.padding(horizontal = Space.x3))
        ControlChip("+", Modifier.width(52.dp)) { if (current < Cmd.TEMP_MAX) onSet(current + 1) }
    }
}

private const val DEFAULT_TEMP = 22

@Composable
private fun WindowRow(name: String, type: Int, send: (Int, String) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(name, style = ElectroType.Body, color = ElectroColors.TextPrimary, modifier = Modifier.weight(1f))
        ControlChip(S("Вниз"), Modifier.width(74.dp)) { send(type, "100") }
        Spacer(Modifier.width(Space.x2))
        ControlChip(S("Вверх"), Modifier.width(74.dp)) { send(type, "0") }
    }
}
