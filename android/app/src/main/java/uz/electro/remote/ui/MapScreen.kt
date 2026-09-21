package uz.electro.remote.ui

import uz.electro.remote.i18n.S
import uz.electro.remote.ui.components.Lx
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uz.electro.remote.data.GeoPoint
import uz.electro.remote.ui.theme.*
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Карта с локацией машины. Кусок карты вокруг авто — статичная картинка (один запрос,
 * грузится только при смене позиции; не тянется на каждый опрос GPS). Кнопка «Маршрут»
 * ведёт в выбранное приложение карт. Координаты и курс — с головы (её GPS = положение авто).
 */
@Composable
fun MapScreen(loc: GeoPoint?) {
    val ctx = LocalContext.current

    // Открыть выбор установленных приложений карт (chooser) на точке авто.
    fun openRoute() {
        val point = loc ?: return
        runCatching {
            val coords = "%.6f,%.6f".format(Locale.US, point.lat, point.lon)
            val uri = Uri.parse("geo:$coords?q=$coords(Leapmotor C16)")
            val chooser = Intent.createChooser(Intent(Intent.ACTION_VIEW, uri), S("Открыть в…"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(chooser)
        }
    }

    Column(Modifier.fillMaxSize().background(ElectroColors.Background)) {
        Row(Modifier.fillMaxWidth().padding(Space.x4), verticalAlignment = Alignment.CenterVertically) {
            Text(S("Карта"), style = ElectroType.Headline, color = ElectroColors.TextPrimary)
            Spacer(Modifier.width(Space.x2))
            Text("· Leapmotor C16", style = ElectroType.Body, color = ElectroColors.TextMuted)
        }

        // Раньше здесь стоял ранний return из composable-лямбды: он оставлял
        // группы Compose незакрытыми и ронял приложение при открытии вкладки
        // без координат. Ветки должны быть if/else, а не выход из лямбды.
        if (loc == null) {
            EmptyLocation()
        } else {
            // строка координат + курс
            Surface(color = ElectroColors.Surface, shape = Radius.Md,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Space.x4)) {
                Row(Modifier.padding(Space.x3), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Lx.MyLocation, null, tint = ElectroColors.Accent,
                        modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(Space.x3))
                    Column(Modifier.weight(1f)) {
                        Text(S("Положение автомобиля"), style = ElectroType.Caption,
                            color = ElectroColors.TextMuted)
                        Text("%.5f, %.5f".format(Locale.US, loc.lat, loc.lon),
                            style = ElectroType.Body, color = ElectroColors.TextPrimary)
                        loc.bearing?.let { b ->
                            Text(S("Курс: {0} {1}°", compass(b), b.toInt()),
                                style = ElectroType.Caption, color = ElectroColors.TextMuted)
                        }
                    }
                }
            }
            Spacer(Modifier.height(Space.x3))

            // Кусок карты — статичная картинка Yandex Static Maps (без ключа), грузится
            // только при заметном смещении (округление до ~11 м).
            val key = "%.4f,%.4f".format(Locale.US, loc.lat, loc.lon)
            val mapUrl = remember(key) {
                val ll = "%.6f,%.6f".format(Locale.US, loc.lon, loc.lat)
                "https://static-maps.yandex.ru/1.x/?ll=$ll&z=16&size=650,450&l=map&pt=$ll,pm2rdm"
            }
            val bmp by produceState<ImageBitmap?>(initialValue = null, key1 = mapUrl) {
                value = withContext(Dispatchers.IO) {
                    runCatching {
                        val conn = (URL(mapUrl).openConnection() as HttpURLConnection).apply {
                            connectTimeout = 8000; readTimeout = 8000
                        }
                        conn.inputStream.use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
                    }.getOrNull()
                }
            }

            Box(
                Modifier.fillMaxWidth().weight(1f).padding(horizontal = Space.x4)
                    .clip(Radius.Lg).background(ElectroColors.Surface)
                    .clickable { openRoute() },
                contentAlignment = Alignment.Center,
            ) {
                val img = bmp
                if (img != null) {
                    Image(img, S("Карта"), Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Lx.Map, null, tint = ElectroColors.TextMuted,
                            modifier = Modifier.size(40.dp))
                        Spacer(Modifier.height(Space.x3))
                        Text(S("Загрузка карты…"), style = ElectroType.Caption,
                            color = ElectroColors.TextMuted, textAlign = TextAlign.Center)
                    }
                }
            }
            Spacer(Modifier.height(Space.x3))

            MapBtn(S("Маршрут"), Lx.Directions,
                Modifier.fillMaxWidth().padding(horizontal = Space.x4)) { openRoute() }
            Spacer(Modifier.height(Space.x4))
        }
    }
}

@Composable
private fun ColumnScope.EmptyLocation() {
    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Lx.MyLocation, null, tint = ElectroColors.TextMuted,
                modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(Space.x3))
            Text(S("Нет координат"), style = ElectroType.Body, color = ElectroColors.TextSecondary)
            Spacer(Modifier.height(Space.x1))
            Text(
                S("Положение приходит с головы. Разбудите машину и дождитесь связи."),
                style = ElectroType.Caption, color = ElectroColors.TextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = Space.x6),
            )
        }
    }
}

private fun compass(bearing: Double): String {
    val dirs = listOf(S("С"), S("СВ"), S("В"), S("ЮВ"), S("Ю"), S("ЮЗ"), S("З"), S("СЗ"))
    val normalized = ((bearing % 360) + 360) % 360
    return dirs[((normalized + 22.5) / 45).toInt() % 8]
}

@Composable
private fun MapBtn(text: String, icon: ImageVector, mod: Modifier, onClick: () -> Unit) {
    Surface(color = ElectroColors.Accent, shape = Radius.Sm,
        modifier = mod.height(ControlSize.Button).clickable(onClick = onClick)) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = ElectroColors.OnAccent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(Space.x2))
            Text(text, style = ElectroType.Body, color = ElectroColors.OnAccent)
        }
    }
}
