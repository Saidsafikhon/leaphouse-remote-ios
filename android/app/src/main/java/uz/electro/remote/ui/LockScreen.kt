package uz.electro.remote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backspace
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import uz.electro.remote.security.AppLock
import uz.electro.remote.ui.components.BrandLockup
import uz.electro.remote.ui.theme.ElectroColors
import uz.electro.remote.ui.theme.Space

const val PIN_LENGTH = 4

/**
 * Экран блокировки: четыре точки, цифровая клавиатура и, если включено,
 * кнопка биометрии (системный диалог показывается сам при открытии).
 */
@Composable
fun LockScreen(lock: AppLock, onBiometric: ((Boolean) -> Unit) -> Unit, onUnlocked: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var cooldown by remember { mutableIntStateOf(lock.cooldownSec()) }
    val bio = lock.biometricEnabled && lock.biometricAvailable()

    // биометрия — сразу, как только экран появился
    LaunchedEffect(Unit) { if (bio && cooldown == 0) onBiometric { ok -> if (ok) onUnlocked() } }
    LaunchedEffect(cooldown) { if (cooldown > 0) { delay(1000); cooldown = lock.cooldownSec() } }
    LaunchedEffect(pin) {
        if (pin.length == PIN_LENGTH) {
            if (lock.check(pin)) onUnlocked()
            else { error = "Неверный код"; delay(350); pin = ""; cooldown = lock.cooldownSec() }
        }
    }

    Column(
        Modifier.fillMaxSize().background(ElectroColors.Background).padding(Space.x6),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(0.8f))
        BrandLockup(markSize = 40.dp)
        Spacer(Modifier.height(Space.x8))
        Text(
            when {
                cooldown > 0 -> "Подождите $cooldown с"
                error != null -> error!!
                else -> "Введите код"
            },
            color = if (error != null || cooldown > 0) ElectroColors.Danger else ElectroColors.TextSecondary,
            fontSize = 15.sp,
        )
        Spacer(Modifier.height(Space.x5))
        PinDots(pin.length, error != null)
        Spacer(Modifier.weight(1f))
        PinPad(
            enabled = cooldown == 0,
            onDigit = { d -> if (pin.length < PIN_LENGTH) { error = null; pin += d } },
            onBackspace = { pin = pin.dropLast(1) },
            biometric = if (bio) ({ onBiometric { ok -> if (ok) onUnlocked() } }) else null,
        )
        Spacer(Modifier.height(Space.x6))
    }
}

@Composable
fun PinDots(filled: Int, error: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        repeat(PIN_LENGTH) { i ->
            Box(
                Modifier.size(16.dp).clip(CircleShape)
                    .background(
                        when {
                            error -> ElectroColors.Danger
                            i < filled -> ElectroColors.Accent
                            else -> ElectroColors.SurfaceElevated
                        }
                    )
                    .border(1.dp, if (i < filled || error) androidx.compose.ui.graphics.Color.Transparent else ElectroColors.Outline, CircleShape),
            )
        }
    }
}

/** Клавиатура 3×4: цифры, слева биометрия (или пусто), справа стереть. */
@Composable
fun PinPad(enabled: Boolean, onDigit: (String) -> Unit, onBackspace: () -> Unit, biometric: (() -> Unit)?) {
    val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("bio", "0", "back"))
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                row.forEach { key ->
                    when (key) {
                        "bio" -> Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                            if (biometric != null) Icon(
                                Icons.Outlined.Fingerprint, "Биометрия", tint = ElectroColors.Accent,
                                modifier = Modifier.size(34.dp).clip(CircleShape).clickable(enabled = enabled, onClick = biometric).padding(2.dp),
                            )
                        }
                        "back" -> Box(
                            Modifier.size(72.dp).clip(CircleShape).clickable(enabled = enabled, onClick = onBackspace),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Outlined.Backspace, "Стереть", tint = ElectroColors.TextSecondary, modifier = Modifier.size(26.dp)) }
                        else -> Box(
                            Modifier.size(72.dp).clip(CircleShape).background(ElectroColors.Surface)
                                .clickable(enabled = enabled) { onDigit(key) },
                            contentAlignment = Alignment.Center,
                        ) { Text(key, color = ElectroColors.TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Medium) }
                    }
                }
            }
        }
    }
}

/**
 * Диалог задания кода: ввести, повторить. [verifyFirst] — сперва спросить
 * текущий код (смена или отключение).
 */
@Composable
fun PinSetupDialog(lock: AppLock, verifyFirst: Boolean, title: String, verifyOnly: Boolean = false, onDone: (String?) -> Unit, onDismiss: () -> Unit) {
    var stage by remember { mutableIntStateOf(if (verifyFirst) 0 else 1) } // 0 текущий, 1 новый, 2 повтор
    var pin by remember { mutableStateOf("") }
    var first by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(pin) {
        if (pin.length != PIN_LENGTH) return@LaunchedEffect
        when (stage) {
            0 -> if (lock.check(pin)) { if (verifyOnly) onDone(null) else { stage = 1; pin = "" } } else { error = "Неверный код"; delay(350); pin = "" }
            1 -> { first = pin; pin = ""; stage = 2 }
            2 -> if (pin == first) onDone(pin) else { error = "Коды не совпадают"; delay(350); pin = ""; first = ""; stage = 1 }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ElectroColors.SurfaceElevated, tonalElevation = 0.dp,
        title = { Text(title, color = ElectroColors.TextPrimary) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    error ?: when (stage) { 0 -> "Введите текущий код"; 1 -> "Придумайте код из 4 цифр"; else -> "Повторите код" },
                    color = if (error != null) ElectroColors.Danger else ElectroColors.TextSecondary, fontSize = 14.sp,
                )
                Spacer(Modifier.height(Space.x4))
                PinDots(pin.length, error != null)
                Spacer(Modifier.height(Space.x5))
                PinPad(enabled = true, onDigit = { d -> if (pin.length < PIN_LENGTH) { error = null; pin += d } },
                    onBackspace = { pin = pin.dropLast(1) }, biometric = null)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена", color = ElectroColors.TextSecondary) } },
    )
}
