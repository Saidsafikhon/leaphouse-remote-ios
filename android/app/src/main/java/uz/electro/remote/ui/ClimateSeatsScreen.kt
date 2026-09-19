package uz.electro.remote.ui

import uz.electro.remote.ui.components.Lx
import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Loop
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.Whatshot
import androidx.compose.material.icons.outlined.Window
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import uz.electro.remote.R
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt
import uz.electro.remote.data.CarState
import uz.electro.remote.data.Cmd
import uz.electro.remote.data.VehicleCommand
import uz.electro.remote.data.asTemp
import uz.electro.remote.ui.components.*
import uz.electro.remote.ui.theme.*

/**
 * Климат и сиденья одним экраном с двумя вкладками — по образцу GWM.
 *
 * Рендеры салона у GWM — их проприетарная графика, поэтому фон здесь наш:
 * воздушная светлая подложка и схема кресел. Всё остальное повторяет их
 * поведение один в один — барабан чисел, плитки на креслах со слайдером
 * уровня, «время работы» и кнопка «Активировать».
 */
private enum class Tab { Climate, Seats }
private enum class SeatMode { Heat, Vent }

@Composable
fun ClimateSeatsScreen(
    car: CarState,
    controls: Map<Int, String>,
    initialTab: String,
    send: (Int, String) -> Unit,
    sendAll: (List<VehicleCommand>, String) -> Unit,
    onClimateOn: (Int?) -> Unit,
    climateBlockReason: (CarState, Map<Int, String>) -> String?,
    onApplySeats: (Map<Int, Int>, Int) -> Unit,
    onClose: () -> Unit,
) {
    var tab by remember { mutableStateOf(if (initialTab == "seats") Tab.Seats else Tab.Climate) }
    var blocked by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().background(ElectroColors.Background)) {
        // шапка: две вкладки по центру и крестик справа
        Box(Modifier.fillMaxWidth().padding(Space.x4)) {
            Row(
                Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(Space.x4),
            ) {
                TopTab("Климат", tab == Tab.Climate) { tab = Tab.Climate }
                TopTab("Сиденья", tab == Tab.Seats) { tab = Tab.Seats }
            }
            Icon(
                Lx.Close, null, tint = ElectroColors.TextSecondary,
                modifier = Modifier.align(Alignment.CenterEnd).size(26.dp).clickable(onClick = onClose),
            )
        }

        when (tab) {
            Tab.Climate -> ClimateTab(
                car, controls, send, sendAll,
                onToggle = {
                    if (climateOn(controls)) sendAll(Cmd.climateOff(), "Выключить климат")
                    else {
                        blocked = climateBlockReason(car, controls)
                        if (blocked == null) onClimateOn(null)
                    }
                },
                onOnWithTimer = { minutes ->
                    blocked = climateBlockReason(car, controls)
                    if (blocked == null) onClimateOn(minutes)
                },
            )
            Tab.Seats -> SeatsTab(controls, onApplySeats)
        }
    }

    blocked?.let { reason ->
        ElectroDialog(
            Lx.Whatshot, ElectroColors.Warn,
            "Климат не включаем", reason,
            confirmText = "Понятно",
            onConfirm = { blocked = null }, onDismiss = { blocked = null },
            dismissText = null,
        )
    }
}

// ---------- вкладки шапки ----------

@Composable
private fun TopTab(text: String, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)) {
        Text(
            text,
            fontSize = 20.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) ElectroColors.TextPrimary else ElectroColors.TextMuted,
        )
        Spacer(Modifier.height(4.dp))
        // подчёркивание-«штрих» как у GWM: короткая жирная черта под активной
        Box(
            Modifier.padding(top = 2.dp).height(3.dp).width(if (selected) 26.dp else 0.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (selected) ElectroColors.Accent else Color.Transparent)
        )
    }
}

// ---------- КЛИМАТ ----------

@Composable
private fun ClimateTab(
    car: CarState,
    controls: Map<Int, String>,
    send: (Int, String) -> Unit,
    sendAll: (List<VehicleCommand>, String) -> Unit,
    onToggle: () -> Unit,
    onOnWithTimer: (Int?) -> Unit,
) {
    val on = climateOn(controls)
    val temp = controls[Cmd.TEMP_L]?.toFloatOrNull()?.toInt() ?: 22
    // Уставка выставляется локально и НЕ уходит на машину сразу — только при
    // нажатии «включить». Смена температуры сама по себе климат не включает.
    var setTemp by remember { mutableStateOf(temp) }
    // Таймер запоминаем между открытиями экрана (SharedPreferences) — выставил
    // однажды, держится до следующего изменения.
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("electro", Context.MODE_PRIVATE) }
    var runMin by remember { mutableStateOf(prefs.getInt("climateRunMin", 15)) }
    var pickTime by remember { mutableStateOf(false) }
    // Функции климата. Тумблеры (циркуляция/зеркала/обдув лобового) читают
    // фактическое состояние из /state; сцены (макс. охл/обогрев, обогрев стёкол)
    // составные — их «включено» держим локальным флагом (оптимистично).
    // Циркуляция — сохраняемый тумблер (как сцены): 1-е нажатие = рецирк + окна на
    // 25%, 2-е = выключить функцию → рецирк off + ВСЕ окна закрыть. Флаг держим
    // сами, т.к. статус рециркуляции на C10 ненадёжен и кнопка не переключалась бы.
    var recircOn by remember { mutableStateOf(prefs.getBoolean("sceneRecirc", false)) }
    val mirrorOn = ctlOn(controls, Cmd.MIRROR_HEAT)
    // Обдув лобового — сохраняемый тумблер (как циркуляция): статус defrost_f на C10
    // приходит от Car API и ЗАЛИПАЕТ (кнопка не выключалась бы), а на голове ключ
    // strCarFrontDefrost ненадёжен. Держим флаг сами → выкл. срабатывает всегда.
    var frontDefrostOn by remember { mutableStateOf(prefs.getBoolean("sceneDefrostF", false)) }
    // Флаги сцен сохраняем, чтобы «включил → выключаешь той же плиткой» работало
    // и после перезахода в приложение (иначе флаг сбрасывался и плитка снова
    // включала вместо выключения).
    var maxCoolOn by remember { mutableStateOf(prefs.getBoolean("sceneMaxCool", false)) }
    var maxHeatOn by remember { mutableStateOf(prefs.getBoolean("sceneMaxHeat", false)) }
    var defogOn by remember { mutableStateOf(prefs.getBoolean("sceneDefog", false)) }
    // Тумблер климата должен реагировать мгновенно: показываем желаемое состояние
    // сразу (optimisticOn), а фактическое (on из /state) подтягивается в фоне.
    // Подмену снимаем, как только реальное состояние совпало, либо через 5 с
    // (если команда не дошла — вернёмся к настоящему состоянию машины).
    var optimisticOn by remember { mutableStateOf<Boolean?>(null) }
    val shownOn = optimisticOn ?: on
    LaunchedEffect(on) { if (optimisticOn == on) optimisticOn = null }
    LaunchedEffect(optimisticOn) {
        if (optimisticOn != null) { kotlinx.coroutines.delay(5000); optimisticOn = null }
    }
    // Включить климат. Без таймера — ОДНОЙ пачкой (температура + AC): иначе три
    // отдельные команды давали три тоста, и падение одной мелькало красным
    // поверх зелёного. Пачка = один результат (частичный неуспех = «Не прошло N
    // из M», зелёным). С таймером — прежним путём (нужен серверный run_minutes).
    val turnOn: (Int?) -> Unit = { minutes ->
        if (minutes == null) {
            sendAll(listOf(
                VehicleCommand(Cmd.TEMP_L, setTemp.toString()),
                VehicleCommand(Cmd.TEMP_R, setTemp.toString()),
                VehicleCommand(Cmd.AC, "1"),
            ), "Включить климат")
        } else {
            send(Cmd.TEMP_L, setTemp.toString()); send(Cmd.TEMP_R, setTemp.toString())
            onOnWithTimer(minutes)
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = Space.x4, vertical = Space.x4),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.x3),
    ) {
        // карточка функций: сцены (макс. охл/обогрев, обогрев стёкол) и
        // тумблеры (циркуляция, обогрев зеркал, обдув лобового)
        Surface(color = ElectroColors.Surface, shape = Radius.Lg, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(Space.x4), verticalArrangement = Arrangement.spacedBy(Space.x3)) {
                Text("Функции", style = ElectroType.Title, color = ElectroColors.TextPrimary)
                Row(horizontalArrangement = Arrangement.spacedBy(Space.x3)) {
                    ClimateButton("Макс. охлаждение", Lx.AcUnit, maxCoolOn, Modifier.weight(1f)) {
                        val next = !maxCoolOn; maxCoolOn = next; if (next) maxHeatOn = false
                        val e = prefs.edit().putBoolean("sceneMaxCool", next)
                        if (next) e.putBoolean("sceneMaxHeat", false)
                        e.apply()
                        sendAll(Cmd.maxCool(next), "Макс. охлаждение")
                    }
                    ClimateButton("Макс. обогрев", Lx.Whatshot, maxHeatOn, Modifier.weight(1f)) {
                        val next = !maxHeatOn; maxHeatOn = next; if (next) maxCoolOn = false
                        val e = prefs.edit().putBoolean("sceneMaxHeat", next)
                        if (next) e.putBoolean("sceneMaxCool", false)
                        e.apply()
                        sendAll(Cmd.maxHeat(next), "Макс. обогрев")
                    }
                    ClimateButton("Обогрев всех стёкол", IconRearDefrost, defogOn, Modifier.weight(1f)) {
                        val next = !defogOn; defogOn = next
                        prefs.edit().putBoolean("sceneDefog", next).apply()
                        sendAll(Cmd.defogGlass(next), "Обогрев всех стёкол")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Space.x3)) {
                    ClimateButton("Циркуляция", Lx.Loop, recircOn, Modifier.weight(1f)) {
                        val next = !recircOn; recircOn = next
                        prefs.edit().putBoolean("sceneRecirc", next).apply()
                        sendAll(Cmd.recircScene(next), "Циркуляция")
                    }
                    ClimateButton("Обогрев зеркал", IconMirrorHeat, mirrorOn, Modifier.weight(1f)) {
                        send(Cmd.MIRROR_HEAT, if (mirrorOn) "0" else "1")
                    }
                    ClimateButton("Обдув лобового", IconWindshieldDefrost, frontDefrostOn, Modifier.weight(1f)) {
                        val next = !frontDefrostOn; frontDefrostOn = next
                        prefs.edit().putBoolean("sceneDefrostF", next).apply()
                        send(Cmd.DEFROST_FRONT, if (next) "2" else "0")
                    }
                }
            }
        }
        // нижняя карточка: заголовок с тумблером, барабан чисел, время работы
        Surface(color = ElectroColors.Surface, shape = Radius.Lg, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(Space.x4), verticalArrangement = Arrangement.spacedBy(Space.x4)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Температура (°C)", style = ElectroType.Title, color = ElectroColors.TextPrimary,
                        modifier = Modifier.weight(1f))
                    Switch(
                        checked = shownOn,
                        onCheckedChange = { desired ->
                            optimisticOn = desired            // мгновенный отклик для клиента
                            if (on) onToggle() else turnOn(null)   // реальная команда — в фоне
                        },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = ElectroColors.Accent,
                            checkedThumbColor = ElectroColors.OnAccent,
                        ),
                    )
                }
                // Полоска температуры LO 18° … HI 32° с градиентом синий→оранжевый:
                // тянется пальцем. Значение запоминается локально (setTemp) и НЕ
                // уходит на машину сразу — уставка применяется при включении климата.
                // При включённом климате полоска заблокирована: температуру задают
                // до включения.
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TemperatureBar(if (shownOn) temp else setTemp, car.cabinTemp, locked = shownOn) { v ->
                        setTemp = v
                    }
                }
                if (shownOn) {
                    Text(
                        "Чтобы изменить температуру, выключите климат — так бережётся компрессор.",
                        style = ElectroType.Caption, color = ElectroColors.TextMuted,
                    )
                } else {
                    Text(
                        "Выбранная температура применится при включении климата.",
                        style = ElectroType.Caption, color = ElectroColors.TextMuted,
                    )
                }
                Divider(color = ElectroColors.Outline)
                Text("Настроить время работы", style = ElectroType.Body,
                    color = ElectroColors.Accent,
                    modifier = Modifier.fillMaxWidth().clickable { pickTime = !pickTime })
                if (pickTime) {
                    NumberCarousel(value = runMin, min = 5, max = 60, step = 5,
                        onChange = { runMin = it; prefs.edit().putInt("climateRunMin", it).apply() })
                    if (!shownOn) {
                        ElectroButton("Включить на $runMin мин", Modifier.fillMaxWidth(),
                            style = ButtonStyle.Primary) { turnOn(runMin) }
                    }
                }
            }
        }
    }
}

/** Тумблер «включён», если из /state пришло непустое ненулевое значение. */
private fun ctlOn(controls: Map<Int, String>, type: Int): Boolean =
    controls[type]?.let { it.isNotEmpty() && it != "0" && it != "0.0" } ?: false

/** Плитка-функция климата: иконка + подпись, подсвечивается когда включена. */
@Composable
private fun ClimateButton(
    label: String,
    icon: ImageVector,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val bg = if (active) ElectroColors.AccentSoft else ElectroColors.SurfaceElevated
    val fg = if (active) ElectroColors.Accent else ElectroColors.TextSecondary
    // Фикс. высота — чтобы все плитки были одного размера независимо от того,
    // в одну или две строки легла подпись.
    Surface(color = bg, shape = Radius.Md, modifier = modifier.height(88.dp).clickable(onClick = onClick)) {
        Column(
            Modifier.padding(horizontal = Space.x2).fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(Space.x1))
            Text(label, style = ElectroType.Caption, color = fg,
                textAlign = TextAlign.Center, maxLines = 2)
        }
    }
}

/**
 * Карусель-число: значения листаются горизонтально пальцем, центральное —
 * выбранное (крупное, акцентом), соседние тусклее; при остановке лента
 * примагничивается к центру. Дискретные ступени [step].
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun NumberCarousel(
    value: Int,
    min: Int,
    max: Int,
    step: Int = 1,
    accent: Color = ElectroColors.Accent,
    onChange: (Int) -> Unit,
) {
    val values = remember(min, max, step) { (min..max step step).toList() }
    val listState = rememberLazyListState()
    val fling = rememberSnapFlingBehavior(lazyListState = listState)
    val itemW = 64.dp

    // индекс элемента, чей центр ближе всего к центру видимой области
    val centerIndex by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            if (info.visibleItemsInfo.isEmpty()) return@derivedStateOf -1
            val mid = (info.viewportStartOffset + info.viewportEndOffset) / 2f
            info.visibleItemsInfo.minByOrNull {
                kotlin.math.abs((it.offset + it.size / 2f) - mid)
            }?.index ?: -1
        }
    }

    // на первом показе встаём на текущее значение (центрируется contentPadding'ом)
    LaunchedEffect(Unit) {
        listState.scrollToItem(values.indexOf(value).coerceAtLeast(0))
    }
    // сообщаем выбор, когда прокрутка остановилась
    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress && centerIndex in values.indices) {
            values[centerIndex].takeIf { it != value }?.let(onChange)
        }
    }

    BoxWithConstraints(Modifier.fillMaxWidth().height(72.dp)) {
        val sidePad = (maxWidth - itemW) / 2
        // подсветка центральной ячейки
        Box(
            Modifier.align(Alignment.Center).width(itemW).height(54.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(accent.copy(alpha = 0.12f))
        )
        LazyRow(
            state = listState,
            flingBehavior = fling,
            contentPadding = PaddingValues(horizontal = sidePad),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(values) { i, v ->
                val selected = i == centerIndex
                Box(Modifier.width(itemW).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    Text(
                        "$v",
                        fontSize = if (selected) 32.sp else 18.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) accent else ElectroColors.TextMuted,
                    )
                }
            }
        }
    }
}

/** Горизонтальный барабан чисел: −  тусклый  КРУПНЫЙ  тусклый  + (как у GWM). */
@Composable
private fun NumberDial(
    value: Int, min: Int, max: Int, step: Int = 1,
    onChange: (Int) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        DialButton(Lx.Remove, enabled = value - step >= min) {
            onChange((value - step).coerceAtLeast(min))
        }
        Text(if (value - step >= min) "${value - step}" else "",
            style = ElectroType.Title, color = ElectroColors.TextDisabled,
            modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Text("$value", fontSize = 34.sp, fontWeight = FontWeight.Bold,
            color = ElectroColors.TextPrimary,
            modifier = Modifier.weight(1.2f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Text(if (value + step <= max) "${value + step}" else "",
            style = ElectroType.Title, color = ElectroColors.TextDisabled,
            modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        DialButton(Lx.Add, enabled = value + step <= max) {
            onChange((value + step).coerceAtMost(max))
        }
    }
}

@Composable
private fun DialButton(icon: ImageVector, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        color = ElectroColors.SurfaceElevated, shape = Radius.Md,
        modifier = Modifier.size(56.dp).clip(Radius.Md).clickable(enabled = enabled, onClick = onClick),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, null, modifier = Modifier.size(22.dp),
                tint = if (enabled) ElectroColors.TextPrimary else ElectroColors.TextDisabled)
        }
    }
}

// ---------- СИДЕНЬЯ ----------

private data class SeatSlot(val name: String, val heat: Int, val vent: Int, val front: Boolean)

@Composable
private fun SeatsTab(
    controls: Map<Int, String>,
    onApply: (Map<Int, Int>, Int) -> Unit,
) {
    var mode by remember { mutableStateOf(SeatMode.Heat) }
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("electro", Context.MODE_PRIVATE) }
    var runMin by remember { mutableStateOf(prefs.getInt("seatRunMin", 5)) }
    var selected by remember { mutableStateOf<Int?>(null) }

    // уровни держим локально и применяем по «Активировать» — как у GWM
    val heat = remember { mutableStateListOf(0, 0, 0, 0) }
    val vent = remember { mutableStateListOf(0, 0, 0, 0) }
    LaunchedEffect(controls) {
        val ht = listOf(Cmd.SEAT_HEAT_DRIVER, Cmd.SEAT_HEAT_PASSENGER, Cmd.SEAT_HEAT_REAR_L, Cmd.SEAT_HEAT_REAR_R)
        val vt = listOf(Cmd.SEAT_VENT_DRIVER, Cmd.SEAT_VENT_PASSENGER, Cmd.SEAT_VENT_REAR_L, Cmd.SEAT_VENT_REAR_R)
        ht.forEachIndexed { i, t -> heat[i] = controls[t]?.toFloatOrNull()?.toInt()?.coerceIn(0, SEAT_LEVELS) ?: 0 }
        vt.forEachIndexed { i, t -> vent[i] = controls[t]?.toFloatOrNull()?.toInt()?.coerceIn(0, SEAT_LEVELS) ?: 0 }
    }
    val levels = if (mode == SeatMode.Heat) heat else vent
    val other = if (mode == SeatMode.Heat) vent else heat
    val accent = if (mode == SeatMode.Heat) ElectroColors.Warn else ElectroColors.Info
    val icon = if (mode == SeatMode.Heat) Lx.Whatshot else Lx.Air

    // Обогрев и обдув одного места взаимоисключающие (в машине это встроено —
    // включаешь одно, другое гаснет). Повторяем правило в приложении, чтобы
    // профиль не отправил на место сразу и обогрев, и обдув.
    fun setLevel(seat: Int, value: Int) {
        levels[seat] = value
        if (value > 0) other[seat] = 0
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Space.x4),
    ) {
        Spacer(Modifier.height(Space.x2))
        // подвкладки Подогрев/Вентиляция белой пилюлей
        Surface(color = ElectroColors.SurfaceElevated, shape = Radius.Pill,
            modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Row(Modifier.padding(4.dp)) {
                PillTab("Подогрев", mode == SeatMode.Heat) { mode = SeatMode.Heat; selected = null }
                PillTab("Вентиляция", mode == SeatMode.Vent) { mode = SeatMode.Vent; selected = null }
            }
        }

        Spacer(Modifier.height(Space.x4))

        // схема салона: два ряда кресел, плитка с иконкой и уровнем на каждом
        Surface(color = ElectroColors.Surface, shape = Radius.Lg, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(Space.x4)) {
                SeatDiagramRow(0, 1, "Водитель", "Пассажир", levels, selected, accent, icon,
                    onSelect = { selected = if (selected == it) null else it })
                Spacer(Modifier.height(Space.x3))
                SeatDiagramRow(2, 3, "Заднее левое", "Заднее правое", levels, selected, accent, icon,
                    onSelect = { selected = if (selected == it) null else it })
            }
        }

        // Ползунок уровня выбранного места — ПОД карточкой, всегда виден (раньше
        // ползунок задних мест прятался за блоком таймера).
        if (selected != null) {
            val seatName = when (selected) {
                0 -> "Водитель"; 1 -> "Пассажир"; 2 -> "Заднее левое"; else -> "Заднее правое"
            }
            Spacer(Modifier.height(Space.x3))
            Text("Уровень — $seatName", style = ElectroType.Caption, color = ElectroColors.TextMuted)
            LevelSlider(levels[selected!!], accent) { setLevel(selected!!, it) }
        }

        Spacer(Modifier.height(Space.x4))
        Text("Время работы (мин.)", style = ElectroType.Body, color = ElectroColors.TextPrimary)
        Spacer(Modifier.height(Space.x2))
        NumberCarousel(value = runMin, min = 1, max = 60, accent = accent,
            onChange = { runMin = it; prefs.edit().putInt("seatRunMin", it).apply() })
        Spacer(Modifier.height(Space.x4))
        ElectroButton("Активировать", Modifier.fillMaxWidth(), style = ButtonStyle.Primary) {
            // Профиль = обогрев И обдув всех мест + таймер. Он и сохраняется как
            // настройка тумблера на главной, и сразу применяется с таймером.
            val ht = listOf(Cmd.SEAT_HEAT_DRIVER, Cmd.SEAT_HEAT_PASSENGER, Cmd.SEAT_HEAT_REAR_L, Cmd.SEAT_HEAT_REAR_R)
            val vt = listOf(Cmd.SEAT_VENT_DRIVER, Cmd.SEAT_VENT_PASSENGER, Cmd.SEAT_VENT_REAR_L, Cmd.SEAT_VENT_REAR_R)
            val levels = HashMap<Int, Int>()
            heat.forEachIndexed { i, l -> levels[ht[i]] = l }
            vent.forEachIndexed { i, l -> levels[vt[i]] = l }
            onApply(levels, runMin)
        }
        Spacer(Modifier.height(Space.x4))
    }
}

@Composable
private fun PillTab(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) ElectroColors.Surface else Color.Transparent,
        shape = Radius.Pill,
        modifier = Modifier.clip(Radius.Pill).clickable(onClick = onClick),
    ) {
        Text(text, style = ElectroType.Body,
            color = if (selected) ElectroColors.TextPrimary else ElectroColors.TextSecondary,
            modifier = Modifier.padding(horizontal = Space.x5, vertical = Space.x2))
    }
}

@Composable
private fun SeatDiagramRow(
    leftIdx: Int, rightIdx: Int, leftName: String, rightName: String,
    levels: List<Int>, selected: Int?, accent: Color, icon: ImageVector,
    onSelect: (Int) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.x4)) {
        SeatTile(leftName, levels[leftIdx], selected == leftIdx, accent, icon,
            Modifier.weight(1f)) { onSelect(leftIdx) }
        SeatTile(rightName, levels[rightIdx], selected == rightIdx, accent, icon,
            Modifier.weight(1f)) { onSelect(rightIdx) }
    }
}

/** Кресло: фигура сверху и белая плитка с иконкой и уровнем в центре спинки. */
@Composable
private fun SeatTile(
    name: String, level: Int, selected: Boolean, accent: Color, icon: ImageVector,
    modifier: Modifier = Modifier, onClick: () -> Unit,
) {
    val active = level > 0
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(name, style = ElectroType.Caption, color = ElectroColors.TextSecondary, maxLines = 1)
        Spacer(Modifier.height(6.dp))
        // кресло — силуэт с эскиза владельца (seat_front): выбранное обведено
        // акцентом, активное окрашено в цвет режима; уровень — отдельной
        // строкой под креслом, чтобы читался и не прятался за иконкой
        Box(
            Modifier.fillMaxWidth().height(112.dp).clip(RoundedCornerShape(16.dp))
                .border(2.dp, if (selected) accent else Color.Transparent, RoundedCornerShape(16.dp))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.seat_front), null,
                tint = if (active) accent.copy(alpha = 0.55f) else ElectroColors.TextDisabled.copy(alpha = 0.55f),
                modifier = Modifier.fillMaxHeight().padding(vertical = 8.dp),
            )
            // иконка режима на спинке кресла
            Surface(
                color = if (active) accent else ElectroColors.Surface,
                shape = CircleShape,
                border = if (active) null else androidx.compose.foundation.BorderStroke(1.dp, ElectroColors.Outline),
                modifier = Modifier.size(30.dp).offset(y = (-6).dp),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = if (active) ElectroColors.OnAccent else ElectroColors.TextMuted,
                        modifier = Modifier.size(16.dp))
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        // уровень: три деления, заполнены по уровню, плюс цифра
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(SEAT_LEVELS) { i ->
                Box(Modifier.size(width = 14.dp, height = 5.dp).clip(RoundedCornerShape(3.dp))
                    .background(if (i < level) accent else ElectroColors.SurfaceElevated))
            }
            Spacer(Modifier.width(4.dp))
            Text(if (active) "$level/$SEAT_LEVELS" else "выкл", style = ElectroType.Caption,
                color = if (active) accent else ElectroColors.TextMuted)
        }
    }
}

/** Слайдер уровня 0..3 с подписью N/3 — всплывает под выбранным креслом. */
@Composable
private fun LevelSlider(level: Int, accent: Color, onChange: (Int) -> Unit) {
    Spacer(Modifier.height(Space.x2))
    Surface(color = ElectroColors.SurfaceElevated, shape = Radius.Pill, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = Space.x4, vertical = Space.x2),
            verticalAlignment = Alignment.CenterVertically) {
            Slider(
                value = level.toFloat(), onValueChange = { onChange(it.toInt()) },
                valueRange = 0f..SEAT_LEVELS.toFloat(), steps = SEAT_LEVELS - 1,
                colors = SliderDefaults.colors(
                    thumbColor = accent, activeTrackColor = accent,
                    inactiveTrackColor = ElectroColors.SurfaceRaised,
                ),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(Space.x3))
            Text("$level/$SEAT_LEVELS", style = ElectroType.Title, color = ElectroColors.TextPrimary)
        }
    }
}


// ---------- горизонтальная полоска температуры (LO 18° … HI 32°) ----------

/**
 * Полоска уставки: слева LO (18°), справа HI (32°), заливка — горизонтальный
 * градиент от морозно-синего (Info) к огненно-оранжевому (Warn). Тянется пальцем;
 * значение запоминается локально (onSet) и НЕ уходит на машину сразу — уставка
 * применяется при включении климата. Заблокировано (климат включён) — полоска
 * тусклая и жесты не ловятся.
 */
@Composable
private fun TemperatureBar(temp: Int, cabin: Double?, locked: Boolean = false, onSet: (Int) -> Unit) {
    var dragTemp by remember { mutableStateOf<Int?>(null) }
    val shown = dragTemp ?: temp
    val frac = (shown - Cmd.TEMP_MIN).toFloat() / (Cmd.TEMP_MAX - Cmd.TEMP_MIN)
    val palette = ElectroColors
    val alpha = if (locked) 0.4f else 1f

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(Cmd.tempLabel(shown), style = ElectroType.Display, color = palette.TextPrimary)
        Text(
            cabin?.let { "В салоне ${it.asTemp()}°" } ?: "В салоне —",
            style = ElectroType.Caption, color = palette.TextMuted,
        )
        Spacer(Modifier.height(Space.x3))

        Box(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .pointerInput(locked) {
                    if (locked) return@pointerInput
                    detectTapGestures { p -> onSet(tempAtX(p.x, size.width)) }
                }
                .pointerInput(locked) {
                    if (locked) return@pointerInput
                    detectDragGestures(
                        onDragStart = { p -> dragTemp = tempAtX(p.x, size.width) },
                        onDrag = { change, _ ->
                            dragTemp = tempAtX(change.position.x, size.width)
                            change.consume()
                        },
                        onDragEnd = { dragTemp?.let(onSet); dragTemp = null },
                        onDragCancel = { dragTemp = null },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxWidth().height(28.dp)) {
                val r = size.height / 2f
                val track = Brush.horizontalGradient(
                    listOf(palette.Info.copy(alpha = alpha), palette.Warn.copy(alpha = alpha)),
                )
                drawRoundRect(track, cornerRadius = CornerRadius(r, r))
                val knobX = (frac * size.width).coerceIn(r, size.width - r)
                val c = Offset(knobX, size.height / 2f)
                drawCircle(palette.Background, r + 3.dp.toPx(), c)
                drawCircle(if (locked) palette.TextDisabled else palette.TextPrimary, r - 2.dp.toPx(), c)
            }
        }

        Spacer(Modifier.height(Space.x1))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("LO · 18°", style = ElectroType.Label, color = palette.Info.copy(alpha = alpha))
            Text("32° · HI", style = ElectroType.Label, color = palette.Warn.copy(alpha = alpha))
        }
    }
}

/** Температура по X касания вдоль полоски (0 = LO слева, ширина = HI справа). */
private fun tempAtX(x: Float, width: Int): Int {
    val frac = (x / width.toFloat()).coerceIn(0f, 1f)
    return Cmd.TEMP_MIN + (frac * (Cmd.TEMP_MAX - Cmd.TEMP_MIN)).roundToInt()
}
