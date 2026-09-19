package uz.electro.remote.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import uz.electro.remote.R
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

// Шрифт кита — Inter. Пока файлы не подключены, используем системный sans-serif.
// Заменить на FontFamily(Font(R.font.inter_*)) после добавления шрифтов в res/font.
private val AppFont = FontFamily.SansSerif

/** Фирменный шрифт LeapRemote (Figma «Brand»): словесный знак и крупные величины. */
val BrandFont = FontFamily(
    Font(R.font.unbounded_light, FontWeight.Light),
    Font(R.font.unbounded_extrabold, FontWeight.ExtraBold),
)

/**
 * Шкала из 8 ступеней — та же, что текстовыми стилями в Figma.
 *
 * Начертания взяты с обложки: крупное набрано лёгким (Light/Normal), жирное
 * осталось только у мелких служебных подписей. Раньше почти всё было Bold.
 */
object ElectroType {
    /** Модель автомобиля, температура — одна крупная величина на экран. */
    val Display = TextStyle(fontFamily = BrandFont, fontWeight = FontWeight.Light, fontSize = 32.sp, lineHeight = 40.sp)
    val Title = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Light, fontSize = 26.sp, lineHeight = 32.sp)
    val Headline = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Normal, fontSize = 19.sp, lineHeight = 25.sp)
    /** Значение в полосе состояния: «520 км», «85 %». */
    val Value = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 20.sp)
    val Body = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 19.sp)
    /** Подпись контрола — на обложке она обычная, не полужирная. */
    val Label = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 15.sp)
    val Caption = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp)
    /** Марка над моделью, заголовки секций — единственное, что набрано капсом. */
    val Overline = TextStyle(
        fontFamily = AppFont, fontWeight = FontWeight.Bold, fontSize = 11.sp,
        lineHeight = 14.sp, letterSpacing = 0.10.em,
    )
    /** Единица измерения рядом с числом. */
    val Unit = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 14.sp)
}

val AppTypography = Typography(
    headlineLarge = ElectroType.Display,
    headlineMedium = ElectroType.Title,
    titleLarge = ElectroType.Headline,
    titleMedium = ElectroType.Value,
    bodyLarge = ElectroType.Body,
    bodyMedium = ElectroType.Label,
    labelSmall = ElectroType.Caption,
)
