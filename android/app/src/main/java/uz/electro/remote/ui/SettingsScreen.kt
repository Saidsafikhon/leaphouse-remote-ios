package uz.electro.remote.ui

import uz.electro.remote.ui.components.Lx
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import uz.electro.remote.ui.components.CarArt
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch
import uz.electro.remote.BuildConfig
import uz.electro.remote.CarViewModel
import uz.electro.remote.data.Settings
import uz.electro.remote.data.VehicleDto
import uz.electro.remote.ui.components.ElectroDialog
import uz.electro.remote.ui.components.SectionCard
import uz.electro.remote.ui.theme.*

/** Строка версии для подвалов: «LeapRemote 0.38.0 (49)». */
fun appVersion(): String =
    "LeapRemote " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")"

/**
 * Полноэкранные настройки: вход в аккаунт, управление машинами (добавить,
 * выбрать, переименовать, отвязать), адрес сервера и выход.
 *
 * Переименование — локальное, только в этом телефоне: имя машины на сервере
 * общее для всего парка, и менять его водителю значило бы переименовать её у
 * всех. Отвязка снимает доступ у аккаунта, сама машина остаётся.
 */
@Composable
fun SettingsScreen(vm: CarViewModel, onClose: () -> Unit) {
    val settings = vm.settings
    val loggedIn by vm.loggedIn.collectAsState()
    val busy by vm.busy.collectAsState()
    val vehicles by vm.vehicles.collectAsState()
    val vehicleId by vm.vehicleId.collectAsState()
    val paint by vm.paint.collectAsState()
    val parkNote by vm.parkNote.collectAsState()

    LaunchedEffect(loggedIn) { if (loggedIn) vm.loadVehicles() }

    var email by remember { mutableStateOf(settings.email.orEmpty()) }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    var renaming by remember { mutableStateOf<VehicleDto?>(null) }
    var deleting by remember { mutableStateOf(false) }
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    var confirmDrop by remember { mutableStateOf<VehicleDto?>(null) }
    var showFeedback by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    var pairMsg by remember { mutableStateOf<String?>(null) }
    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val payload = result.contents ?: return@rememberLauncherForActivityResult
        pairMsg = null
        scope.launch {
            vm.claimPairing(payload)
                .onSuccess { pairMsg = "Машина привязана" }
                .onFailure { pairMsg = it.message ?: "Не удалось привязать машину" }
        }
    }

    ScreenScaffold("Настройки", onClose) {
        if (loggedIn) {
            SectionCard("Аккаунт") {
                Text("Вход выполнен: " + (settings.email ?: "—"),
                    color = ElectroColors.TextPrimary, fontSize = 14.sp)
                TextButton(onClick = { vm.logout() }, contentPadding = PaddingValues(0.dp)) {
                    Text("Выйти из аккаунта", color = ElectroColors.Danger)
                }
                TextButton(onClick = { deleting = true }, contentPadding = PaddingValues(0.dp)) {
                    Text("Удалить аккаунт", color = ElectroColors.TextMuted, fontSize = 12.sp)
                }
            }

            SectionCard("Мои машины", action = "Обновить", onAction = { vm.loadVehicles() }) {
                if (vehicles.isEmpty()) {
                    Text(parkNote ?: "Машин нет — добавьте по QR с экрана машины.",
                        color = ElectroColors.TextMuted, fontSize = 12.sp)
                }
                vehicles.forEach { vehicle ->
                    VehicleRow(
                        vehicle = vehicle,
                        nick = vm.vehicleNick(vehicle.vehicle_id),
                        selected = vehicle.vehicle_id == vehicleId,
                        onSelect = { vm.selectVehicle(vehicle.vehicle_id) },
                        onRename = { renaming = vehicle },
                        onDrop = { confirmDrop = vehicle },
                    )
                    // цвет кузова — только у выбранной машины, чтобы список не разрастался
                    if (vehicle.vehicle_id == vehicleId) {
                        PaintPicker(
                            model = vehicle.model,
                            // из StateFlow, а не из prefs напрямую: иначе галочка
                            // не переезжает, пока экран не пересоберут
                            current = paint,
                            onPick = { vm.setVehiclePaint(vehicle.vehicle_id, it) },
                        )
                    }
                }
                TextButton(
                    onClick = {
                        scanLauncher.launch(
                            ScanOptions().setPrompt("Наведите на QR на экране машины")
                                .setBeepEnabled(false).setOrientationLocked(false)
                        )
                    },
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Icon(Lx.AddCircleOutline, null, tint = ElectroColors.Accent,
                        modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Добавить машину", color = ElectroColors.Accent)
                }
                pairMsg?.let { Text(it, color = ElectroColors.TextSecondary, fontSize = 12.sp) }
            }
        } else {
            SectionCard("Вход") {
                OutlinedTextField(
                    value = email, onValueChange = { email = it }, singleLine = true,
                    label = { Text("Логин") }, modifier = Modifier.fillMaxWidth(), colors = fieldColors(),
                )
                OutlinedTextField(
                    value = password, onValueChange = { password = it }, singleLine = true,
                    label = { Text("Пароль") }, visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(), colors = fieldColors(),
                )
                error?.let { Text(it, color = ElectroColors.Danger, fontSize = 13.sp) }
                Button(
                    onClick = { error = null; vm.login(email.trim(), password) { failure -> error = failure } },
                    enabled = !busy && email.isNotBlank() && password.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectroColors.Accent, contentColor = ElectroColors.OnAccent),
                    shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(),
                ) { Text("Войти", fontWeight = FontWeight.Bold) }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Text("Политика конфиденциальности", style = ElectroType.Caption, color = ElectroColors.TextMuted,
                modifier = Modifier.clickable { runCatching { uriHandler.openUri(Settings.PRIVACY_URL) } })
            Spacer(Modifier.width(Space.x4))
            Text("Поддержка", style = ElectroType.Caption, color = ElectroColors.TextMuted,
                modifier = Modifier.clickable { runCatching { uriHandler.openUri(Settings.SUPPORT_URL) } })
        }
        ThemeSection()

        if (loggedIn) {
            LockSection()
        }

        if (loggedIn) {
            SectionCard("Отзыв") {
                Text("Замечания, идеи и предложения по приложению — разработчикам напрямую.",
                    color = ElectroColors.TextSecondary, fontSize = 13.sp)
                TextButton(onClick = { showFeedback = true }, contentPadding = PaddingValues(0.dp)) {
                    Text("Оставить отзыв", color = ElectroColors.Accent)
                }
            }
        }

        Text(appVersion(), style = ElectroType.Caption, color = ElectroColors.TextMuted,
            modifier = Modifier.fillMaxWidth().padding(top = Space.x1))
    }

    // --- удаление аккаунта: предупреждение, пароль, красная кнопка ---
    if (deleting) {
        var pass by remember { mutableStateOf("") }
        var err by remember { mutableStateOf<String?>(null) }
        var busyDel by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (!busyDel) deleting = false },
            containerColor = ElectroColors.SurfaceElevated, tonalElevation = 0.dp,
            title = { Text("Удалить аккаунт?", color = ElectroColors.TextPrimary) },
            text = {
                Column {
                    Text("Учётная запись, доступ к машинам, сцены и расписания будут удалены с сервера без возможности восстановления. Сами машины останутся в парке.",
                        color = ElectroColors.TextSecondary, fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(pass, { pass = it }, singleLine = true, label = { Text("Пароль для подтверждения") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                    err?.let { Spacer(Modifier.height(8.dp)); Text(it, color = ElectroColors.Danger, fontSize = 13.sp) }
                }
            },
            confirmButton = {
                TextButton(enabled = pass.length >= MIN_PASSWORD && !busyDel, onClick = {
                    busyDel = true; err = null
                    scope.launch {
                        vm.deleteAccount(pass)
                            .onSuccess { deleting = false }
                            .onFailure { err = it.message ?: "Не удалось удалить аккаунт" }
                        busyDel = false
                    }
                }) { Text(if (busyDel) "Удаляем…" else "Удалить навсегда", color = ElectroColors.Danger) }
            },
            dismissButton = {
                TextButton(enabled = !busyDel, onClick = { deleting = false }) { Text("Отмена", color = ElectroColors.TextSecondary) }
            },
        )
    }

    if (showFeedback) FeedbackDialog(
        onSend = { kind, text, files, done -> vm.sendFeedback(kind, text, files, done) },
        onDismiss = { showFeedback = false },
    )

    // --- переименование (локальное имя) ---
    renaming?.let { v ->
        var name by remember(v) { mutableStateOf(vm.vehicleNick(v.vehicle_id) ?: v.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            containerColor = ElectroColors.SurfaceElevated, tonalElevation = 0.dp,
            title = { Text("Имя машины", color = ElectroColors.TextPrimary) },
            text = {
                Column {
                    Text("Показывается только в этом телефоне.",
                        color = ElectroColors.TextMuted, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(name, { name = it }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.setVehicleNick(v.vehicle_id, name.takeIf { it.isNotBlank() && it != v.name })
                    renaming = null
                }) { Text("Сохранить", color = ElectroColors.Accent) }
            },
            dismissButton = {
                TextButton(onClick = {
                    vm.setVehicleNick(v.vehicle_id, null); renaming = null
                }) { Text("Сбросить", color = ElectroColors.TextSecondary) }
            },
        )
    }

    // --- подтверждение отвязки ---
    confirmDrop?.let { v ->
        ElectroDialog(
            Lx.Delete, ElectroColors.Danger,
            "Отвязать машину?",
            "«" + (vm.vehicleNick(v.vehicle_id) ?: v.name) + "» перестанет быть доступной этому " +
                "аккаунту. Сама машина останется — доступ вернёт новый QR с её экрана.",
            confirmText = "Отвязать",
            onConfirm = { vm.unlinkVehicle(v.vehicle_id); confirmDrop = null },
            onDismiss = { confirmDrop = null },
        )
    }
}

@Composable
private fun Field(label: String, hint: String, value: String, onValueChange: (String) -> Unit) {
    Column {
        Text(label, color = ElectroColors.TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(hint, color = ElectroColors.TextMuted, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(value = value, onValueChange = onValueChange, singleLine = true,
            modifier = Modifier.fillMaxWidth(), colors = fieldColors())
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = ElectroColors.TextPrimary,
    unfocusedTextColor = ElectroColors.TextPrimary,
    focusedBorderColor = ElectroColors.Accent,
    unfocusedBorderColor = ElectroColors.SurfaceElevated,
    focusedLabelColor = ElectroColors.Accent,
    unfocusedLabelColor = ElectroColors.TextSecondary,
    cursorColor = ElectroColors.Accent,
)

/** Оформление: авто (за системой), светлая, тёмная. Применяется сразу. */
@Composable
private fun ThemeSection() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val mode = uz.electro.remote.ui.theme.ThemePref.mode.value
    SectionCard("Оформление") {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(ElectroColors.SurfaceElevated).padding(4.dp)) {
            listOf(
                uz.electro.remote.ui.theme.ThemeMode.AUTO to "Авто",
                uz.electro.remote.ui.theme.ThemeMode.LIGHT to "Светлая",
                uz.electro.remote.ui.theme.ThemeMode.DARK to "Тёмная",
            ).forEach { (m, label) ->
                val sel = m == mode
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                        .background(if (sel) ElectroColors.Surface else androidx.compose.ui.graphics.Color.Transparent)
                        .clickable { uz.electro.remote.ui.theme.ThemePref.set(ctx, m) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, fontSize = 14.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (sel) ElectroColors.TextPrimary else ElectroColors.TextSecondary)
                }
            }
        }
        Text("Авто — как в системе телефона", color = ElectroColors.TextMuted, fontSize = 11.sp)
    }
}

/** Защита входа системной блокировкой телефона: отпечаток / лицо / код экрана. */
@Composable
private fun LockSection() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val activity = ctx as? androidx.fragment.app.FragmentActivity
    val lock = remember { uz.electro.remote.security.AppLock(ctx) }
    var enabled by remember { mutableStateOf(lock.enabled) }
    val available = remember { lock.available() }
    val bio = remember { lock.biometricAvailable() }

    SectionCard("Защита входа") {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Блокировка при входе", color = if (available) ElectroColors.TextPrimary else ElectroColors.TextMuted, fontSize = 14.sp)
                Text(
                    when {
                        !available -> "Сначала включите блокировку экрана в настройках телефона"
                        bio -> "Отпечаток или лицо, запасной путь — код экрана телефона"
                        else -> "Код, рисунок или пароль экрана телефона"
                    },
                    color = ElectroColors.TextMuted, fontSize = 11.sp,
                )
            }
            Switch(
                checked = enabled, enabled = available,
                onCheckedChange = { on ->
                    // и включение, и выключение — только после подтверждения системой
                    val act = activity ?: return@Switch
                    lock.prompt(act) { ok -> if (ok) { lock.enabled = on; enabled = on } }
                },
                colors = SwitchDefaults.colors(checkedTrackColor = ElectroColors.Accent, checkedThumbColor = ElectroColors.OnAccent),
            )
        }
    }
}

/** Кружки цветов кузова, на которые есть рендер модели; выбранный — с обводкой акцентом. */
@Composable
private fun PaintPicker(model: String?, current: String?, onPick: (String) -> Unit) {
    val paints = CarArt.paints(model)
    if (paints.size < 2) return
    val chosen = current?.takeIf { c -> paints.any { it.code == c } } ?: paints.first().code
    Column(Modifier.fillMaxWidth().padding(start = 48.dp, top = 2.dp, bottom = 8.dp)) {
        // цветов может быть до 8 — переносим на вторую строку, а не режем
        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            paints.forEach { p ->
                val sel = p.code == chosen
                // выбранный — крупнее, с толстым акцентным кольцом и галочкой,
                // остальные — просто кружки с тонкой обводкой
                Box(
                    Modifier.size(if (sel) 34.dp else 26.dp)
                        .border(if (sel) 3.dp else 1.dp, if (sel) ElectroColors.Accent else ElectroColors.Outline, CircleShape)
                        .padding(if (sel) 5.dp else 3.dp).clip(CircleShape).background(p.swatch)
                        .clickable { onPick(p.code) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (sel) Text(
                        "✓", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        // галочка контрастная к кузову: на светлом — тёмная, на тёмном — белая
                        color = if (p.swatch.luminance() > 0.4f) Color(0xFF111111) else Color.White,
                    )
                }
            }
        }
        Text("Цвет кузова: " + paints.first { it.code == chosen }.label,
            color = ElectroColors.TextMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

/** Строка машины: выбор радиокнопкой, имя (локальное поверх серверного), правка и отвязка. */
@Composable
private fun VehicleRow(
    vehicle: VehicleDto,
    nick: String?,
    selected: Boolean,
    onSelect: () -> Unit,
    onRename: () -> Unit,
    onDrop: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(
            selected = selected, onClick = onSelect,
            colors = RadioButtonDefaults.colors(
                selectedColor = ElectroColors.Accent, unselectedColor = ElectroColors.TextMuted),
        )
        Column(Modifier.weight(1f).clickable(onClick = onSelect)) {
            Text(
                nick ?: vehicle.name,
                color = if (selected) ElectroColors.TextPrimary else ElectroColors.TextSecondary,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            )
            Text(
                listOfNotNull(vehicle.model, vehicle.vin.takeIf { it.isNotBlank() }).joinToString(" · "),
                color = ElectroColors.TextMuted, fontSize = 11.sp,
            )
        }
        IconButton(onClick = onRename) {
            Icon(Lx.Edit, "Переименовать", tint = ElectroColors.TextSecondary,
                modifier = Modifier.size(20.dp))
        }
        IconButton(onClick = onDrop) {
            Icon(Lx.Delete, "Отвязать", tint = ElectroColors.Danger,
                modifier = Modifier.size(20.dp))
        }
    }
}
