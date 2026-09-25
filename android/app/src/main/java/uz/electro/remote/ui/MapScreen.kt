package uz.electro.remote.ui

import uz.electro.remote.i18n.S
import uz.electro.remote.ui.components.Lx
import android.content.Intent
import androidx.compose.ui.unit.sp
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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

    // Маршрут: всегда показываем выбор из всех навигаторов, установленных на телефоне
    // (не «приложение по умолчанию»). Известные (NAV_APPS) открываются своей ссылкой с
    // построением маршрута, остальные — обработчики geo: (объявлены в <queries> манифеста).
    var chooseApp by remember { mutableStateOf(false) }
    val installedNavs = remember { installedNavigators(ctx) }
    fun openIn(app: NavApp) {
        val point = loc ?: return
        runCatching {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(app.uri(point.lat, point.lon))).setPackage(app.pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
    fun openRoute() {
        val point = loc ?: return
        if (installedNavs.isNotEmpty()) chooseApp = true
        else runCatching {
            val coords = "%.6f,%.6f".format(Locale.US, point.lat, point.lon)
            val uri = Uri.parse("geo:$coords?q=$coords(Leapmotor)")
            ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW, uri), S("Открыть в…")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
    if (chooseApp) AlertDialog(
        onDismissRequest = { chooseApp = false },
        containerColor = ElectroColors.SurfaceElevated,
        title = { Text(S("Открыть в…"), color = ElectroColors.TextPrimary) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                installedNavs.forEach { app ->
                    Row(
                        Modifier.fillMaxWidth().clip(Radius.Sm).clickable { chooseApp = false; openIn(app) }.padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        app.icon?.let { Image(it, null, Modifier.size(32.dp)); Spacer(Modifier.width(Space.x3)) }
                        Text(app.name, color = ElectroColors.TextPrimary, fontSize = 16.sp)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { chooseApp = false }) { Text(S("Отмена"), color = ElectroColors.TextSecondary) } },
    )

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

            // Живая карта: Mapbox — как MapKit на iOS. Тянется и зумится пальцем,
            // метка — машина; тап по метке открывает маршрут.
            Box(
                Modifier.fillMaxWidth().weight(1f).padding(horizontal = Space.x4)
                    .clip(Radius.Lg).background(ElectroColors.Surface),
            ) {
                CarMap(loc.lat, loc.lon, onMarkerTap = { openRoute() }, modifier = Modifier.fillMaxSize())
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

/**
 * Карта Mapbox: центр и метка — машина; при смене координат метка переезжает, камера
 * следует. Стиль — светлый или тёмный по теме. Ключ — строка mapbox_access_token
 * (из android/mapbox.properties); без ключа карта пустая, и мы говорим об этом.
 */
@Composable
private fun CarMap(lat: Double, lon: Double, onMarkerTap: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val dark = isAppDarkTheme()   // стиль карты идёт за темой приложения, а не системы
    val token = remember { ctx.getString(uz.electro.remote.R.string.mapbox_access_token) }
    if (token.isBlank()) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(S("Карта не настроена: нет ключа Mapbox"), style = ElectroType.Caption,
                color = ElectroColors.TextMuted, textAlign = TextAlign.Center,
                modifier = Modifier.padding(Space.x4))
        }
        return
    }
    remember(token) { com.mapbox.common.MapboxOptions.accessToken = token; true }
    val point = com.mapbox.geojson.Point.fromLngLat(lon, lat)
    val viewport = com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState {
        setCameraOptions { center(point); zoom(16.0); pitch(0.0); bearing(0.0) }
    }
    com.mapbox.maps.extension.compose.MapboxMap(
        modifier = modifier,
        mapViewportState = viewport,
        style = {
            com.mapbox.maps.extension.compose.style.MapStyle(
                style = if (dark) com.mapbox.maps.Style.DARK else com.mapbox.maps.Style.STANDARD,
            )
        },
    ) {
        val pin = com.mapbox.maps.extension.compose.annotation.rememberIconImage(
            key = "car-pin", painter = androidx.compose.ui.res.painterResource(uz.electro.remote.R.drawable.ic_map_pin),
        )
        com.mapbox.maps.extension.compose.annotation.generated.PointAnnotation(
            point = point,
            onClick = { onMarkerTap(); true },
        ) {
            iconImage = pin
            iconAnchor = com.mapbox.maps.extension.style.layers.properties.generated.IconAnchor.BOTTOM
        }
    }
    LaunchedEffect(lat, lon) {
        viewport.easeTo(
            com.mapbox.maps.dsl.cameraOptions { center(point) },
            com.mapbox.maps.plugin.animation.MapAnimationOptions.mapAnimationOptions { duration(600) },
        )
    }
}


/** Навигаторы, которые умеем открывать на точке машины (пакеты объявлены в <queries> манифеста). */
private data class NavApp(val name: String, val pkg: String, val icon: ImageBitmap? = null,
                          val uri: (Double, Double) -> String)

/**
 * Все навигаторы на телефоне: сначала известные (с маршрутом), потом любые обработчики
 * geo:-ссылок, которых нет в списке (Maps.me, Organic Maps, Petal, штатные карты и т.п.).
 */
private fun installedNavigators(ctx: android.content.Context): List<NavApp> {
    val pm = ctx.packageManager
    fun icon(pkg: String): ImageBitmap? = runCatching {
        val d = pm.getApplicationIcon(pkg)
        val bmp = android.graphics.Bitmap.createBitmap(96, 96, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp); d.setBounds(0, 0, 96, 96); d.draw(c); bmp.asImageBitmap()
    }.getOrNull()
    val known = NAV_APPS.filter { runCatching { pm.getPackageInfo(it.pkg, 0) }.isSuccess }
        .map { it.copy(name = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(it.pkg, 0)).toString() }.getOrDefault(it.name), icon = icon(it.pkg)) }
    val probe = Intent(Intent.ACTION_VIEW, Uri.parse("geo:41.311,69.240?q=41.311,69.240"))
    val others = runCatching { pm.queryIntentActivities(probe, 0) }.getOrDefault(emptyList())
        .map { it.activityInfo.packageName }.distinct()
        .filter { p -> p != ctx.packageName && known.none { it.pkg == p } }
        .map { p ->
            NavApp(runCatching { pm.getApplicationLabel(pm.getApplicationInfo(p, 0)).toString() }.getOrDefault(p), p, icon(p)) { la, lo ->
                "geo:$la,$lo?q=$la,$lo(Leapmotor)" }
        }
    return known + others
}
private val NAV_APPS = listOf(
    NavApp("Google Maps", "com.google.android.apps.maps") { la, lo -> "google.navigation:q=$la,$lo" },
    NavApp("Яндекс Навигатор", "ru.yandex.yandexnavi") { la, lo -> "yandexnavi://build_route_on_map?lat_to=$la&lon_to=$lo" },
    NavApp("Яндекс Карты", "ru.yandex.yandexmaps") { la, lo -> "yandexmaps://maps.yandex.ru/?rtext=~$la,$lo&rtt=auto" },
    NavApp("2ГИС", "ru.dublgis.dgismobile") { la, lo -> "dgis://2gis.ru/routeSearch/rsType/car/to/$lo,$la" },
    NavApp("Waze", "com.waze") { la, lo -> "waze://?ll=$la,$lo&navigate=yes" },
)
