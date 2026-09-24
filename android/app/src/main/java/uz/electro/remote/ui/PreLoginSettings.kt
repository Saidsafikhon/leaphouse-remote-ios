package uz.electro.remote.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uz.electro.remote.BuildConfig
import uz.electro.remote.i18n.S
import uz.electro.remote.ui.components.LangPicker
import uz.electro.remote.ui.components.SectionCard
import uz.electro.remote.ui.theme.ElectroColors
import uz.electro.remote.ui.theme.Space

/** Круглая кнопка с шестерёнкой рядом с «?» на экране входа. */
@Composable
fun SettingsFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick = onClick, shape = CircleShape, color = ElectroColors.SurfaceElevated, modifier = modifier.size(44.dp)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Settings, contentDescription = S("Настройки"), tint = ElectroColors.TextSecondary, modifier = Modifier.size(22.dp))
        }
    }
}

/** Настройки, доступные до входа: язык, оформление, защита входа. Остальное — после входа. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreLoginSettingsSheet(onClose: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onClose, containerColor = ElectroColors.Background) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Space.x4).padding(bottom = Space.x8),
            verticalArrangement = Arrangement.spacedBy(Space.x4),
        ) {
            Text(S("Настройки"), color = ElectroColors.TextPrimary, fontSize = 20.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            SectionCard(S("Язык")) { LangPicker() }
            ThemeSection()
            LockSection()
            Text(S("Версия {0}", BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")"), color = ElectroColors.TextMuted, fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}
