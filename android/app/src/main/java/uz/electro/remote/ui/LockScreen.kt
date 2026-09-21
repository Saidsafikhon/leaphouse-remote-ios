package uz.electro.remote.ui

import uz.electro.remote.i18n.S
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uz.electro.remote.ui.components.ElectroButton
import uz.electro.remote.ui.components.BrandLockup
import uz.electro.remote.ui.theme.ElectroColors
import uz.electro.remote.ui.theme.Space

/**
 * Экран блокировки: системный диалог (отпечаток / лицо / код телефона)
 * показывается сам при открытии; кнопка — повторить, если отменили.
 */
@Composable
fun LockScreen(onPrompt: ((Boolean) -> Unit) -> Unit, onUnlocked: () -> Unit) {
    var failed by remember { mutableStateOf(false) }
    val ask = { onPrompt { ok -> if (ok) onUnlocked() else failed = true } }
    LaunchedEffect(Unit) { ask() }

    Column(
        Modifier.fillMaxSize().background(ElectroColors.Background).padding(Space.x6),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        BrandLockup(markSize = 44.dp)
        Spacer(Modifier.height(Space.x6))
        Text(
            if (failed) S("Не удалось подтвердить. Попробуйте ещё раз.") else S("Подтвердите, что это вы"),
            color = if (failed) ElectroColors.Danger else ElectroColors.TextSecondary,
            fontSize = 15.sp, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.weight(1f))
        ElectroButton(text = S("Разблокировать"), modifier = Modifier.fillMaxWidth()) { failed = false; ask() }
        Spacer(Modifier.height(Space.x6))
    }
}
