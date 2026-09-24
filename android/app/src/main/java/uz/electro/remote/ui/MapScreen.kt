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

    // Маршрут: свой список установленных навигаторов (как на iOS); если их нет —
    // системный выбор по geo:-ссылке.
    var chooseApp by remember { mutableStateOf(false) }
    val installedNavs = remember { NAV_APPS.filter { runCatching { ctx.packageManager.getPackageInfo(it.pkg, 0) }.isSuccess } }
    fun openIn(app: NavApp) {
        val point = loc ?: return
        runCatching {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(app.uri(point.lat, point.lon))).setPackage(app.pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
    fun openRoute() {
        val point = loc ?: return
        when {
            installedNavs.size > 1 -> chooseApp = true
            installedNavs.size == 1 -> openIn(installedNavs[0])
            else -> runCatching {
                val coords = "%.6f,%.6f".format(Locale.US, point.lat, point.lon)
                val uri = Uri.parse("geo:$coords?q=$coords(Leapmotor C16)")
                ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW, uri), S("Открыть в…")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }
    if (chooseApp) AlertDialog(
        onDismissRequest = { chooseApp = false },
        containerColor = ElectroColors.SurfaceElevated,
        title = { Text(S("Открыть в…"), color = ElectroColors.TextPrimary) },
        text = {
            Column {
                installedNavs.forEach { app ->
                    Text(app.name, color = ElectroColors.TextPrimary, fontSize = 16.sp,
                        modifier = Modifier.fillMaxWidth().clickable { chooseApp = false; openIn(app) }.padding(vertical = 12.dp))
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

            // Живая карта: OpenStreetMap через osmdroid (без ключа) — как MapKit на iOS.
            // Тянется и зумится пальцем, метка — машина; тап по метке открывает маршрут.
            Box(
                Modifier.fillMaxWidth().weight(1f).padding(horizontal = Space.x4)
                    .clip(Radius.Lg).background(ElectroColors.Surface),
            ) {
                OsmMap(loc.lat, loc.lon, onMarkerTap = { openRoute() }, modifier = Modifier.fillMaxSize())
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

/** Карта OpenStreetMap (osmdroid): центр и метка — машина; при смене координат метка переезжает, карта следует. */
@Composable
private fun OsmMap(lat: Double, lon: Double, onMarkerTap: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    androidx.compose.ui.viewinterop.AndroidView(
        modifier = modifier,
        factory = {
            org.osmdroid.config.Configuration.getInstance().apply {
                userAgentValue = "LeapRemote/" + uz.electro.remote.BuildConfig.VERSION_NAME   // требование OSM
                osmdroidBasePath = java.io.File(ctx.cacheDir, "osm"); osmdroidTileCache = java.io.File(ctx.cacheDir, "osm/tiles")
            }
            org.osmdroid.views.MapView(ctx).apply {
                setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
                isTilesScaledToDpi = true
                minZoomLevel = 4.0; maxZoomLevel = 19.0
                controller.setZoom(16.5)
                controller.setCenter(org.osmdroid.util.GeoPoint(lat, lon))
                val m = org.osmdroid.views.overlay.Marker(this).apply {
                    id = "car"; position = org.osmdroid.util.GeoPoint(lat, lon)
                    setAnchor(org.osmdroid.views.overlay.Marker.ANCHOR_CENTER, org.osmdroid.views.overlay.Marker.ANCHOR_BOTTOM)
                    icon = androidx.core.content.ContextCompat.getDrawable(ctx, uz.electro.remote.R.drawable.ic_map_pin)
                    setOnMarkerClickListener { _, _ -> onMarkerTap(); true }
                }
                overlays.add(m)
                // тёмная тема — инвертируем тайлы, чтобы карта не светила белым
                if (dark) overlayManager.tilesOverlay.setColorFilter(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK.let {
                    android.graphics.ColorMatrixColorFilter(floatArrayOf(
                        -1f, 0f, 0f, 0f, 255f, 0f, -1f, 0f, 0f, 255f, 0f, 0f, -1f, 0f, 255f, 0f, 0f, 0f, 1f, 0f))
                })
            }
        },
        update = { map ->
            val p = org.osmdroid.util.GeoPoint(lat, lon)
            (map.overlays.firstOrNull { it is org.osmdroid.views.overlay.Marker && (it as org.osmdroid.views.overlay.Marker).id == "car" } as? org.osmdroid.views.overlay.Marker)?.let {
                if (it.position.distanceToAsDouble(p) > 5.0) { it.position = p; map.controller.animateTo(p) }
            }
            map.invalidate()
        },
        onRelease = { it.onDetach() },
    )
}


/** Навигаторы, которые умеем открывать на точке машины (пакеты объявлены в <queries> манифеста). */
private data class NavApp(val name: String, val pkg: String, val uri: (Double, Double) -> String)
private val NAV_APPS = listOf(
    NavApp("Google Maps", "com.google.android.apps.maps") { la, lo -> "google.navigation:q=$la,$lo" },
    NavApp("Яндекс Навигатор", "ru.yandex.yandexnavi") { la, lo -> "yandexnavi://build_route_on_map?lat_to=$la&lon_to=$lo" },
    NavApp("Яндекс Карты", "ru.yandex.yandexmaps") { la, lo -> "yandexmaps://maps.yandex.ru/?rtext=~$la,$lo&rtt=auto" },
    NavApp("2ГИС", "ru.dublgis.dgismobile") { la, lo -> "dgis://2gis.ru/routeSearch/rsType/car/to/$lo,$la" },
    NavApp("Waze", "com.waze") { la, lo -> "waze://?ll=$la,$lo&navigate=yes" },
)
