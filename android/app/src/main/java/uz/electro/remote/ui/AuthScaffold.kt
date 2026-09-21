package uz.electro.remote.ui

import uz.electro.remote.i18n.S
import uz.electro.remote.ui.components.Lx
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uz.electro.remote.ui.components.BadgeKind
import uz.electro.remote.ui.components.ElectroButton
import uz.electro.remote.ui.components.ElectroToast
import uz.electro.remote.ui.theme.*

/**
 * Общий каркас экранов входа: заголовок с «назад», форма, кнопка внизу.
 *
 * Экраны прокручиваются: на регистрации открытая клавиатура закрывает кнопку,
 * и без прокрутки до неё не добраться.
 */
@Composable
internal fun AuthScaffold(
    title: String,
    subtitle: String,
    action: String,
    busy: Boolean,
    enabled: Boolean,
    onBack: () -> Unit,
    onAction: () -> Unit,
    error: String? = null,
    note: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier.fillMaxSize().background(ElectroColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.x6),
    ) {
        Spacer(Modifier.height(Space.x8))
        Row(verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable(enabled = !busy) { onBack() }.padding(vertical = Space.x2)) {
            Icon(Lx.ArrowBack, null,
                tint = ElectroColors.TextSecondary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(Space.x2))
            Text(S("Назад"), color = ElectroColors.TextSecondary, fontSize = 14.sp)
        }

        Spacer(Modifier.height(Space.x5))
        Text(title, color = ElectroColors.TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(Space.x2))
        Text(subtitle, color = ElectroColors.TextSecondary, fontSize = 14.sp)

        Spacer(Modifier.height(Space.x6))
        content()

        if (error != null) {
            Spacer(Modifier.height(Space.x3))
            ElectroToast(BadgeKind.Failed, error, null)
        }
        if (note != null) {
            Spacer(Modifier.height(Space.x3))
            ElectroToast(BadgeKind.Success, note, null)
        }

        Spacer(Modifier.height(Space.x6))
        ElectroButton(
            text = action,
            enabled = enabled && !busy,
            loading = busy,
            modifier = Modifier.fillMaxWidth(),
            onClick = onAction,
        )

        Spacer(Modifier.height(Space.x8))
    }
}
