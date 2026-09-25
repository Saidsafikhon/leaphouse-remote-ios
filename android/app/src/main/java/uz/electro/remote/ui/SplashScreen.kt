package uz.electro.remote.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uz.electro.remote.R
import uz.electro.remote.i18n.S
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min

/**
 * Экран загрузки по макету Figma «loading-dark-theme / loading-light-theme»: иконка
 * приложения в трёх пульсирующих кольцах с зелёным свечением, «LeapRemote», «EV CONTROL
 * SYSTEM», «Заряжается...» и полоса прогресса. Таймлайн макета (5 с, петля) сжат до
 * одного прохода [TOTAL_MS]: вход элементов как в макете, полоса заполняется до конца,
 * затем экран плавно уходит и зовёт [onDone].
 */
private val Outfit = FontFamily(
    Font(R.font.outfit_light, FontWeight.Light),
    Font(R.font.outfit_medium, FontWeight.Medium),
    Font(R.font.outfit_bold, FontWeight.Bold),
)
private val Green = Color(0xFF3E8A2E)
private val GreenText = Color(0xFF2E8A37)

private const val TOTAL_MS = 2600L
private const val FADE_MS = 350L

// --- кривые из макета ---
/** cubic-bezier(0.16, 1, 0.3, 1) — «expo out», быстрый вход с мягкой остановкой */
private fun expoOut(x: Float) = if (x >= 1f) 1f else 1f - Math.pow(2.0, -10.0 * x).toFloat()
/** пружина с небольшим перелётом (linear(...) из макета) */
private fun springOut(x: Float): Float {
    if (x <= 0f) return 0f; if (x >= 1f) return 1f
    val c = 1.9f; val c3 = c + 1f
    return 1f + c3 * Math.pow((x - 1).toDouble(), 3.0).toFloat() + c * Math.pow((x - 1).toDouble(), 2.0).toFloat()
}
private fun easeInOut(x: Float) = (0.5f - 0.5f * cos(PI * x.coerceIn(0f, 1f))).toFloat()
/** доля отрезка [a, b] (мс) в момент t */
private fun seg(t: Long, a: Long, b: Long) = ((t - a).toFloat() / (b - a)).coerceIn(0f, 1f)
/** пульсация между lo и hi с периодом p после старта s */
private fun pulse(t: Long, s: Long, p: Long, lo: Float, hi: Float): Float {
    if (t <= s) return hi
    val k = ((t - s) % p).toFloat() / p
    return lo + (hi - lo) * (0.5f + 0.5f * cos(2 * PI * k)).toFloat()
}

@Composable
fun SplashScreen(dark: Boolean, onDone: () -> Unit) {
    var t by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        val start = withFrameMillis { it }
        while (t < TOTAL_MS + FADE_MS) { t = withFrameMillis { it } - start }
        onDone()
    }
    val bg = if (dark) Color(0xFF0B0E14) else Color(0xFFF4F6F8)
    val title = if (dark) Color.White else Color(0xFF12161A)
    val sub = if (dark) Color(0xFF8E9AA8) else Color(0xFF5C6470)
    val charging = if (dark) Color(0xFF99A699) else Color(0xFF666666)
    val track = if (dark) Color(0xFF263326) else Color(0xFFE5EBE5)
    val glowA = if (dark) 0.6f else 0.35f
    val ringA = if (dark) floatArrayOf(0.5f, 0.3f, 0.15f) else floatArrayOf(0.4f, 0.2f, 0.08f)

    Box(Modifier.fillMaxSize().background(bg).alpha(1f - seg(t, TOTAL_MS, TOTAL_MS + FADE_MS))) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(bottom = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(0.9f))
            // --- иконка в кольцах ---
            Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
                // кольца: tight 150 / medium 180 / wide 220 dp — входят с перелётом и дышат
                data class Ring(val d: Float, val w: Float, val a: Float, val start: Long, val from: Float, val amp: Float)
                val rings = listOf(
                    Ring(150f, 2f, ringA[0], 500, 0.6f, 0.08f),
                    Ring(180f, 1.5f, ringA[1], 600, 0.5f, 0.12f),
                    Ring(220f, 1f, ringA[2], 800, 0.4f, 0.15f),
                )
                Canvas(Modifier.fillMaxSize()) {
                    rings.forEach { r ->
                        val k = seg(t, r.start, r.start + 500)
                        val scale = if (k < 1f) r.from + (1f - r.from) * springOut(k)
                            else pulse(t, r.start + 500, 1800, 1f - r.amp / 2, 1f + r.amp / 2)
                        val op = expoOut(k) * (if (k >= 1f) pulse(t, r.start + 500, 1800, 0.45f, 1f) else 1f)
                        drawCircle(Green.copy(alpha = r.a * op), radius = r.d / 2f * density * scale, style = Stroke(width = r.w * density))
                    }
                }
                // иконка 120 dp со свечением
                val kLogo = seg(t, 0, 700)
                val logoScale = if (kLogo < 1f) 0.3f + 0.7f * springOut(kLogo) else pulse(t, 700, 2000, 1f, 1.04f)
                val glow = (if (kLogo >= 1f) pulse(t, 700, 2000, 25f, 40f) else 5f + 25f * expoOut(kLogo))
                Box(
                    Modifier.size(120.dp)
                        .graphicsLayer { scaleX = logoScale; scaleY = logoScale; alpha = expoOut(seg(t, 0, 500)) }
                        .drawBehind {
                            val g = glow * density
                            drawRoundRect(
                                brush = Brush.radialGradient(
                                    listOf(Green.copy(alpha = glowA), Green.copy(alpha = glowA * 0.35f), Color.Transparent),
                                    center = Offset(size.width / 2, size.height / 2 + 4 * density),
                                    radius = size.width / 2 + g,
                                ),
                                topLeft = Offset(-g, -g + 4 * density), size = Size(size.width + 2 * g, size.height + 2 * g),
                                cornerRadius = CornerRadius(28 * density + g),
                            )
                        },
                ) {
                    Image(painterResource(R.drawable.splash_logo), null, Modifier.fillMaxSize().clip(RoundedCornerShape(28.dp)))
                }
            }
            // --- названия ---
            val kT = expoOut(seg(t, 500, 1000))
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = title)) { append("Leap") }
                    withStyle(SpanStyle(fontWeight = FontWeight.Light, color = GreenText)) { append("Remote") }
                },
                fontFamily = Outfit, fontSize = 36.sp,
                modifier = Modifier.graphicsLayer { alpha = kT; translationY = (1f - kT) * 20 * density },
            )
            Spacer(Modifier.height(8.dp))
            val kS = expoOut(seg(t, 700, 1200))
            Text(
                "EV CONTROL SYSTEM", fontFamily = Outfit, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = sub,
                modifier = Modifier.graphicsLayer { alpha = kS; translationY = (1f - kS) * 15 * density },
            )
            Spacer(Modifier.weight(1f))
            // --- «Заряжается...» и полоса ---
            Column(Modifier.fillMaxWidth().height(80.dp).padding(horizontal = 60.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                val kC = expoOut(seg(t, 800, 1200))
                Text(S("Заряжается..."), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = charging, letterSpacing = 1.5.sp,
                    modifier = Modifier.graphicsLayer { alpha = kC; translationY = (1f - kC) * 10 * density })
                Spacer(Modifier.height(16.dp))
                val fill = easeInOut(seg(t, 800, TOTAL_MS - 200))
                Box(Modifier.fillMaxWidth().height(6.dp).alpha(expoOut(seg(t, 700, 1000)))
                    .clip(RoundedCornerShape(3.dp)).background(track)) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(min(1f, fill)).clip(RoundedCornerShape(3.dp)).background(Green))
                }
            }
            Spacer(Modifier.weight(0.75f))
        }
    }
}
