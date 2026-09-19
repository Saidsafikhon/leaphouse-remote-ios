package uz.electro.remote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import uz.electro.remote.R
import uz.electro.remote.ui.theme.BrandFont
import uz.electro.remote.ui.theme.ElectroColors

/** Лайм иконки приложения (LeapRemote-play-icon-512) — на случай, если знак нужен вне темы. */
val BrandLime = Color(0xFFC5FB39)

/**
 * Знак LeapRemote: эмблема «L + машина + сигнал» из иконки приложения
 * (`drawable-nodpi/brand_glyph.png`, вырезана из 512-иконки, фон прозрачный)
 * на плашке цвета кнопок (Accent темы). [size] — сторона плашки.
 */
@Composable
fun BrandMark(
    size: Dp, modifier: Modifier = Modifier,
    // плашка — акцент темы, тот же зелёный, что у кнопок; глиф — цвет текста на акценте
    plate: Color = ElectroColors.Accent, ink: Color = ElectroColors.OnAccent,
) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.24f)).background(plate),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.brand_glyph),
            contentDescription = "LeapRemote", tint = ink,
            modifier = Modifier.size(size * 0.78f),
        )
    }
}

/**
 * Словесный знак «LeapRemote»: «Leap» жирным, «Remote» тонким, шрифт Unbounded —
 * как на странице Brand в Figma. Цвет один на оба слова, чтобы читалось как имя.
 */
@Composable
fun BrandWordmark(fontSize: TextUnit, color: Color = ElectroColors.TextPrimary, modifier: Modifier = Modifier) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)) { append("Leap") }
            withStyle(SpanStyle(fontWeight = FontWeight.Light)) { append("Remote") }
        },
        fontFamily = BrandFont, fontSize = fontSize, color = color,
        letterSpacing = (-0.03).em, maxLines = 1, softWrap = false,
        modifier = modifier,
    )
}

/** Знак + слово в строку: шапки экранов и логин. [markSize] задаёт масштаб. */
@Composable
fun BrandLockup(markSize: Dp, modifier: Modifier = Modifier, color: Color = ElectroColors.TextPrimary) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        BrandMark(markSize)
        Spacer(Modifier.width(markSize * 0.28f))
        BrandWordmark(fontSize = (markSize.value * 0.58f).sp, color = color)
    }
}
