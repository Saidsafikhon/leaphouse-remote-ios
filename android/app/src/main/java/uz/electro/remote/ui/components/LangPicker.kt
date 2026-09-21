package uz.electro.remote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uz.electro.remote.i18n.Lang
import uz.electro.remote.ui.theme.ElectroColors

/**
 * Переключатель языка RU / EN / UZ.
 * [compact] — маленькая пилюля с кодами (для логина и экрана подключения),
 * иначе — сегментный ряд на всю ширину с полными названиями (настройки).
 */
@Composable
fun LangPicker(modifier: Modifier = Modifier, compact: Boolean = false) {
    val ctx = LocalContext.current
    val current = Lang.current
    Row(
        modifier
            .then(if (compact) Modifier else Modifier.fillMaxWidth())
            .clip(RoundedCornerShape(if (compact) 999.dp else 12.dp))
            .background(ElectroColors.SurfaceElevated)
            .padding(if (compact) 3.dp else 4.dp),
    ) {
        Lang.all.forEach { code ->
            val sel = code == current
            Box(
                Modifier
                    .then(if (compact) Modifier else Modifier.weight(1f))
                    .clip(RoundedCornerShape(if (compact) 999.dp else 10.dp))
                    .background(if (sel) ElectroColors.Surface else Color.Transparent)
                    .clickable { Lang.set(ctx, code) }
                    .padding(horizontal = if (compact) 12.dp else 0.dp, vertical = if (compact) 6.dp else 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (compact) Lang.short(code) else Lang.title(code),
                    fontSize = if (compact) 12.sp else 14.sp,
                    fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (sel) ElectroColors.TextPrimary else ElectroColors.TextSecondary,
                )
            }
        }
    }
}
