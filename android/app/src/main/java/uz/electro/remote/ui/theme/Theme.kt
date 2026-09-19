package uz.electro.remote.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Маппинг токенов Electro на роли Material3.
private fun schemeOf(p: ElectroPalette, dark: Boolean): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary          = p.Accent,
        onPrimary        = p.OnAccent,
        primaryContainer = p.AccentSoft,
        error            = p.Danger,
        onError          = p.OnAccent,
        tertiary         = p.Warn,
        background       = p.Background,
        onBackground     = p.TextPrimary,
        surface          = p.Surface,
        onSurface        = p.TextPrimary,
        surfaceVariant   = p.SurfaceElevated,
        onSurfaceVariant = p.TextSecondary,
        outline          = p.Outline,
    )
}

/**
 * Тему выбирает не приложение, а устройство: системная «Тёмная тема», в том
 * числе включённая по расписанию от заката до рассвета, — тогда утром экран
 * светлый, вечером тёмный. Своего переключателя нет намеренно: рубильник рядом
 * с системным означал бы, что человек однажды выставит его и перестанет
 * понимать, почему темнеет телефон, а приложение — нет.
 */
/** Оформление: за системой, всегда светлое или всегда тёмное. Выбор в настройках. */
enum class ThemeMode { AUTO, LIGHT, DARK }

/** Текущий выбор темы — observable, чтобы экран перекрасился сразу, без перезапуска. */
object ThemePref {
    val mode = androidx.compose.runtime.mutableStateOf(ThemeMode.AUTO)

    fun load(ctx: android.content.Context) {
        val v = ctx.getSharedPreferences("electro", android.content.Context.MODE_PRIVATE).getString("themeMode", null)
        mode.value = runCatching { ThemeMode.valueOf(v ?: "AUTO") }.getOrDefault(ThemeMode.AUTO)
    }

    fun set(ctx: android.content.Context, m: ThemeMode) {
        mode.value = m
        ctx.getSharedPreferences("electro", android.content.Context.MODE_PRIVATE).edit().putString("themeMode", m.name).apply()
    }
}

@Composable
fun ElectroTheme(content: @Composable () -> Unit) {
    val dark = when (ThemePref.mode.value) {
        ThemeMode.AUTO -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val palette = if (dark) ElectroDarkColors else ElectroLightColors

    // Значки статусной строки рисует система, и на светлом фоне белые пропадают
    // совсем — их цвет переключаем вместе с палитрой.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = palette.Background.toArgb()
            window.navigationBarColor = palette.Background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    CompositionLocalProvider(LocalElectroColors provides palette) {
        MaterialTheme(
            colorScheme = schemeOf(palette, dark),
            typography = AppTypography,
            content = content,
        )
    }
}
