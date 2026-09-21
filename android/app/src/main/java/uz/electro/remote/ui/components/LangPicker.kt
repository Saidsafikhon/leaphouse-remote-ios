package uz.electro.remote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uz.electro.remote.i18n.Lang
import uz.electro.remote.ui.theme.ElectroColors

/**
 * Выбор языка — кнопка с текущим языком и выпадающий список RU / EN / UZ.
 * [compact] — короткая пилюля с кодом («RU ▾») для логина и экрана подключения,
 * иначе — строка с полным названием для настроек.
 */
@Composable
fun LangPicker(modifier: Modifier = Modifier, compact: Boolean = false) {
    val ctx = LocalContext.current
    val current = Lang.current
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            Modifier
                .clip(RoundedCornerShape(if (compact) 999.dp else 12.dp))
                .background(ElectroColors.SurfaceElevated)
                .clickable { open = true }
                .padding(horizontal = if (compact) 12.dp else 14.dp, vertical = if (compact) 7.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Language, null, tint = ElectroColors.TextSecondary, modifier = Modifier.size(if (compact) 15.dp else 18.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                if (compact) Lang.short(current) else Lang.title(current),
                fontSize = if (compact) 12.sp else 14.sp, fontWeight = FontWeight.SemiBold,
                color = ElectroColors.TextPrimary,
            )
            Spacer(Modifier.width(2.dp))
            Icon(Icons.Outlined.ExpandMore, null, tint = ElectroColors.TextSecondary, modifier = Modifier.size(16.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Lang.all.forEach { code ->
                val sel = code == current
                DropdownMenuItem(
                    text = {
                        Text(Lang.title(code), fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (sel) ElectroColors.Accent else ElectroColors.TextPrimary)
                    },
                    trailingIcon = { if (sel) Icon(Icons.Outlined.Check, null, tint = ElectroColors.Accent, modifier = Modifier.size(18.dp)) },
                    onClick = { Lang.set(ctx, code); open = false },
                )
            }
        }
    }
}
