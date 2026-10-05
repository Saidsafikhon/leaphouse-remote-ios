package uz.electro.remote.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import uz.electro.remote.data.EvonProduct
import uz.electro.remote.data.EvonProducts
import uz.electro.remote.i18n.Lang
import uz.electro.remote.i18n.S
import uz.electro.remote.ui.components.ButtonStyle
import uz.electro.remote.ui.components.ElectroButton
import uz.electro.remote.ui.components.Lx
import uz.electro.remote.ui.components.PullRefresh
import uz.electro.remote.ui.theme.*

/**
 * «Наши продукты» — другие программы EvOn. Список с сервера AppsMarket
 * (apps.evon.uz, platform=phone): сначала сохранённая копия, потом обновление.
 * Тап — открыть ссылку продукта во внешнем браузере; без ссылки карточка неактивна.
 */
@Composable
fun OurProductsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val lang = Lang.current
    val scope = rememberCoroutineScope()
    var list by remember(lang) { mutableStateOf(EvonProducts.cached(ctx, lang)) }
    var loading by remember(lang) { mutableStateOf(true) }
    var offline by remember(lang) { mutableStateOf(false) }

    // Обычный показ — из кэша (сервер не чаще раза в сутки); «потянуть вниз» — сверка сразу.
    suspend fun reload(force: Boolean) {
        loading = true
        runCatching { EvonProducts.refresh(ctx, lang, force) }
            .onSuccess { list = it; offline = false }
            .onFailure { offline = true }
        loading = false
    }
    LaunchedEffect(lang) { reload(force = false) }

    androidx.activity.compose.BackHandler(onBack = onBack)
    PullRefresh({ scope.launch { reload(force = true) } }) {
        LazyScreenScaffold(S("Наши продукты"), onBack) {
            item(key = "sub") {
                Text("EvOn", style = ElectroType.Caption, color = ElectroColors.TextMuted)
            }
            if (offline) item(key = "offline") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Lx.CloudOff, null, tint = ElectroColors.TextMuted, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(Space.x2))
                    Text(
                        if (list.isEmpty()) S("Нет связи — список продуктов не загрузился")
                        else S("Нет связи — показан сохранённый список"),
                        style = ElectroType.Caption, color = ElectroColors.TextMuted,
                    )
                }
            }
            if (list.isEmpty()) item(key = "empty") {
                if (loading) Box(Modifier.fillMaxWidth().padding(Space.x6), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(28.dp), color = ElectroColors.Accent, strokeWidth = 2.dp)
                } else if (!offline) EmptyNote(S("Список продуктов пока пуст."))
            }
            items(list.size, key = { list[it].id }) { i ->
                val p = list[i]
                EvonProductCard(p) {
                    runCatching {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(p.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }
            }
        }
    }
}

@Composable
private fun EvonProductCard(p: EvonProduct, onOpen: () -> Unit) {
    val canOpen = p.url.isNotBlank()
    Surface(
        color = ElectroColors.Surface, shape = Radius.Md,
        border = androidx.compose.foundation.BorderStroke(1.dp, ElectroColors.Outline),
        modifier = Modifier.fillMaxWidth().clip(Radius.Md).clickable(enabled = canOpen, onClick = onOpen),
    ) {
        Column(Modifier.padding(Space.x4), verticalArrangement = Arrangement.spacedBy(Space.x3)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(56.dp).clip(Radius.Sm).background(ElectroColors.SurfaceElevated),
                    contentAlignment = Alignment.Center,
                ) {
                    if (p.iconUrl != null) coil.compose.AsyncImage(
                        model = p.iconUrl, contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    ) else Icon(Lx.Dashboard, null, tint = ElectroColors.TextMuted, modifier = Modifier.size(26.dp))
                }
                Spacer(Modifier.width(Space.x3))
                Column(Modifier.weight(1f)) {
                    Text(p.title, style = ElectroType.Body, fontWeight = FontWeight.SemiBold,
                        color = ElectroColors.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (p.description.isNotBlank()) Text(p.description, style = ElectroType.Caption,
                        color = ElectroColors.TextSecondary, maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
            }
            ElectroButton(
                S("Открыть"), Modifier.fillMaxWidth(),
                style = ButtonStyle.Secondary, enabled = canOpen, onClick = onOpen,
            )
        }
    }
}
