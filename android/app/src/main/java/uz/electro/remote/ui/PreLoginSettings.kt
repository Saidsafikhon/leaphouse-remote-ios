package uz.electro.remote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uz.electro.remote.BuildConfig
import uz.electro.remote.data.Settings
import uz.electro.remote.i18n.S
import uz.electro.remote.ui.components.ButtonStyle
import uz.electro.remote.ui.components.ElectroButton
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

/** Настройки, доступные до входа: адрес сервера (для стендов и смены домена),
 *  язык и оформление. Всё остальное — после входа, в обычных настройках. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreLoginSettingsSheet(onClose: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val settings = remember { Settings(ctx) }
    var url by remember { mutableStateOf(settings.cloudUrl) }
    var note by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismissRequest = onClose, containerColor = ElectroColors.Background) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Space.x4).padding(bottom = Space.x8),
            verticalArrangement = Arrangement.spacedBy(Space.x4),
        ) {
            Text(S("Настройки"), color = ElectroColors.TextPrimary, fontSize = 20.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            SectionCard(S("Адрес сервера")) {
                OutlinedTextField(
                    value = url, onValueChange = { url = it }, singleLine = true,
                    placeholder = { Text("https://…", color = ElectroColors.TextMuted) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = ElectroColors.TextPrimary, unfocusedTextColor = ElectroColors.TextPrimary,
                        focusedBorderColor = ElectroColors.Accent, unfocusedBorderColor = ElectroColors.Outline,
                    ),
                )
                Text(S("Меняйте только по указанию поддержки. По умолчанию: {0}", Settings.DEFAULT_CLOUD_URL), color = ElectroColors.TextMuted, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                    ElectroButton(text = S("Сохранить"), modifier = Modifier.weight(1f)) {
                        val t = url.trim()
                        if (t.startsWith("http://") || t.startsWith("https://")) { settings.cloudUrl = t; url = settings.cloudUrl; note = S("Сохранено") }
                        else note = S("Адрес должен начинаться с http:// или https://")
                    }
                    ElectroButton(text = S("По умолчанию"), style = ButtonStyle.Secondary, modifier = Modifier.weight(1f)) {
                        settings.cloudUrl = ""; url = settings.cloudUrl; note = S("Сохранено")
                    }
                }
                note?.let { Text(it, color = ElectroColors.TextSecondary, fontSize = 12.sp) }
            }
            SectionCard(S("Язык")) { LangPicker() }
            ThemeSection()
            Text(S("Версия {0}", BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")"), color = ElectroColors.TextMuted, fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}
