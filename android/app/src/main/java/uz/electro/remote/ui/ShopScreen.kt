package uz.electro.remote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uz.electro.remote.data.ProductDto
import uz.electro.remote.i18n.S
import uz.electro.remote.ui.components.PullRefresh
import uz.electro.remote.ui.components.ButtonStyle
import uz.electro.remote.ui.components.ElectroButton
import uz.electro.remote.ui.components.Lx
import uz.electro.remote.ui.theme.*

/**
 * Магазин аксессуаров и доп. оборудования: витрина карточками, карточка товара с
 * описанием и заявкой (телефон + комментарий). Оплаты в приложении нет — менеджер
 * перезванивает. Без входа витрину видно, заказать — только после входа.
 */
@Composable
fun ShopScreen(
    products: List<ProductDto>,
    loggedIn: Boolean,
    phoneHint: String,
    model: String?,
    onOrder: (productId: String, qty: Int, phone: String, comment: String, done: (String?) -> Unit) -> Unit,
    onBack: () -> Unit,
    onRefresh: (() -> Unit)? = null,
) {
    var category by remember { mutableStateOf("all") }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<ProductDto?>(null) }
    val categories = listOf(
        "all" to S("Все"), "accessory" to S("Аксессуары"), "equipment" to S("Оборудование"),
        "care" to S("Уход"), "tuning" to S("Тюнинг"), "other" to S("Другое"),
    )
    selected?.let { p ->
        androidx.activity.compose.BackHandler { selected = null }
        ProductScreen(p, loggedIn, phoneHint, onOrder, onClose = { selected = null })
        return
    }
    // сначала товары для своей модели, потом общие, потом остальные
    val m = (model ?: "").uppercase()
    val q = query.trim().lowercase()
    val shown = products
        .filter { category == "all" || it.category == category }
        .filter { q.isEmpty() || it.title.lowercase().contains(q) || it.description.lowercase().contains(q) || it.models.lowercase().contains(q) }
        .sortedBy { p -> when { p.models.isBlank() -> 1; m.isNotBlank() && p.models.uppercase().contains(m) -> 0; else -> 2 } }

    PullRefresh(onRefresh) {
    ScreenScaffold(S("Магазин"), onBack) {
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            placeholder = { Text(S("Поиск товаров"), color = ElectroColors.TextMuted) },
            leadingIcon = { Icon(Lx.Search, null, tint = ElectroColors.TextMuted, modifier = Modifier.size(20.dp)) },
            trailingIcon = if (query.isNotEmpty()) ({ Icon(Lx.Close, null, tint = ElectroColors.TextMuted,
                modifier = Modifier.size(20.dp).clickable { query = "" }) }) else null,
            shape = Radius.Md, modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ElectroColors.Accent, unfocusedBorderColor = ElectroColors.Outline,
                focusedTextColor = ElectroColors.TextPrimary, unfocusedTextColor = ElectroColors.TextPrimary,
                cursorColor = ElectroColors.Accent,
            ),
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            categories.forEach { (k, label) ->
                val on = category == k
                Surface(
                    color = if (on) ElectroColors.Accent.copy(alpha = 0.14f) else ElectroColors.SurfaceElevated, shape = Radius.Pill,
                    border = if (on) androidx.compose.foundation.BorderStroke(1.dp, ElectroColors.Accent) else null,
                    modifier = Modifier.height(ControlSize.Chip).clip(Radius.Pill).clickable { category = k },
                ) {
                    Box(Modifier.padding(horizontal = Space.x4).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        Text(label, style = ElectroType.Body, color = if (on) ElectroColors.Accent else ElectroColors.TextPrimary, maxLines = 1)
                    }
                }
            }
        }
        if (shown.isEmpty()) EmptyNote(when { products.isEmpty() -> S("Товары скоро появятся."); q.isNotEmpty() -> S("Ничего не найдено"); else -> S("В этом разделе пусто.") })
        // сетка 2 в ряд
        shown.chunked(2).forEach { row ->
            // карточки в ряду одной высоты: строка старой цены зарезервирована всегда
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(Space.x3)) {
                row.forEach { p -> ProductCard(p, Modifier.weight(1f).fillMaxHeight()) { selected = p } }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
    }
}

fun formatPrice(price: Long, currency: String): String {
    val n = "%,d".format(price).replace(',', ' ')
    return if (currency == "USD") "$$n" else S("{0} сум", n)
}

@Composable
private fun ProductCard(p: ProductDto, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        color = ElectroColors.Surface, shape = Radius.Md,
        border = androidx.compose.foundation.BorderStroke(1.dp, ElectroColors.Outline),
        modifier = modifier.clip(Radius.Md).clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(Space.x3)) {
            Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(Radius.Sm).background(ElectroColors.SurfaceElevated), contentAlignment = Alignment.Center) {
                if (!p.image_url.isNullOrBlank()) coil.compose.AsyncImage(
                    model = p.image_url, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                ) else Icon(Lx.ShoppingBag, null, tint = ElectroColors.TextMuted, modifier = Modifier.size(36.dp))
                if (p.old_price != null && p.old_price > p.price) Box(
                    Modifier.align(Alignment.TopStart).padding(8.dp).clip(Radius.Pill).background(ElectroColors.Accent)
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                ) { Text("-${100 - (p.price * 100 / p.old_price)}%", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = ElectroColors.OnAccent) }
            }
            Spacer(Modifier.height(Space.x2))
            Text(p.title, style = ElectroType.Body, fontWeight = FontWeight.SemiBold, color = ElectroColors.TextPrimary,
                maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(formatPrice(p.price, p.currency), style = ElectroType.Body, fontWeight = FontWeight.Bold, color = ElectroColors.Accent)
            // строка старой цены есть всегда (пустая, если скидки нет) — иначе карточки разной высоты
            val discount = p.old_price != null && p.old_price > p.price
            Text(if (discount) formatPrice(p.old_price!!, p.currency) else " ", style = ElectroType.Unit, color = ElectroColors.TextMuted,
                textDecoration = if (discount) TextDecoration.LineThrough else TextDecoration.None, maxLines = 1)
        }
    }
}

/** Карточка товара: фото, цена, описание, заявка. */
@Composable
private fun ProductScreen(
    p: ProductDto, loggedIn: Boolean, phoneHint: String,
    onOrder: (String, Int, String, String, (String?) -> Unit) -> Unit,
    onClose: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    var qty by remember { mutableIntStateOf(1) }
    var phone by remember { mutableStateOf(phoneHint) }
    var comment by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showForm by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(ElectroColors.Background)) {
        Row(Modifier.fillMaxWidth().padding(Space.x4), verticalAlignment = Alignment.CenterVertically) {
            Icon(Lx.ArrowBack, S("Назад"), tint = ElectroColors.TextPrimary, modifier = Modifier.size(26.dp).clickable(onClick = onClose))
            Spacer(Modifier.width(Space.x3))
            Text(S("Товар"), style = ElectroType.Headline, color = ElectroColors.TextPrimary, modifier = Modifier.weight(1f))
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = Space.x5).padding(bottom = Space.x6),
            verticalArrangement = Arrangement.spacedBy(Space.x4),
        ) {
            if (!p.image_url.isNullOrBlank()) coil.compose.AsyncImage(
                model = p.image_url, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(240.dp).clip(Radius.Md).background(ElectroColors.SurfaceElevated),
            )
            Text(p.title, style = ElectroType.Title, color = ElectroColors.TextPrimary)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                Text(formatPrice(p.price, p.currency), style = ElectroType.Headline, color = ElectroColors.Accent)
                if (p.old_price != null && p.old_price > p.price)
                    Text(formatPrice(p.old_price, p.currency), style = ElectroType.Caption, color = ElectroColors.TextMuted, textDecoration = TextDecoration.LineThrough)
            }
            if (p.models.isNotBlank()) Text(S("Подходит: {0}", p.models), style = ElectroType.Caption, color = ElectroColors.TextSecondary)
            if (p.description.isNotBlank()) Text(p.description, style = ElectroType.Body, color = ElectroColors.TextSecondary)
            if (!p.link.isNullOrBlank()) Text(S("Подробнее на сайте"), style = ElectroType.Body, color = ElectroColors.Accent,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { runCatching { uriHandler.openUri(p.link) } })

            if (sent) {
                uz.electro.remote.ui.components.ElectroToast(uz.electro.remote.ui.components.BadgeKind.Success,
                    S("Заявка отправлена"), S("Менеджер свяжется с вами по указанному телефону."))
            } else if (showForm) {
                Surface(color = ElectroColors.Surface, shape = Radius.Md, border = androidx.compose.foundation.BorderStroke(1.dp, ElectroColors.Outline)) {
                    Column(Modifier.padding(Space.x4), verticalArrangement = Arrangement.spacedBy(Space.x3)) {
                        Text(S("Заявка на покупку"), style = ElectroType.Body, fontWeight = FontWeight.SemiBold, color = ElectroColors.TextPrimary)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(S("Количество"), style = ElectroType.Body, color = ElectroColors.TextSecondary, modifier = Modifier.weight(1f))
                            QtyButton("−") { if (qty > 1) qty-- }
                            Text("$qty", style = ElectroType.Headline, color = ElectroColors.TextPrimary, modifier = Modifier.padding(horizontal = Space.x3))
                            QtyButton("+") { if (qty < 99) qty++ }
                        }
                        Field(S("ТЕЛЕФОН"), phone, { phone = it }, KeyboardType.Phone)
                        Text(S("КОММЕНТАРИЙ (необязательно)"), color = ElectroColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(value = comment, onValueChange = { comment = it }, minLines = 2, modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text))
                        error?.let { Text(it, color = ElectroColors.Danger, style = ElectroType.Caption) }
                        ElectroButton(if (busy) S("Отправляем…") else S("Отправить заявку"), Modifier.fillMaxWidth(), enabled = !busy && phone.trim().length >= 5) {
                            busy = true; error = null
                            onOrder(p.id, qty, phone.trim(), comment.trim()) { err -> busy = false; if (err == null) sent = true else error = err }
                        }
                    }
                }
            }
        }
        if (!sent && !showForm) {
            if (loggedIn) ElectroButton(S("Заказать"), Modifier.fillMaxWidth().padding(horizontal = Space.x5).padding(bottom = Space.x4)) { showForm = true }
            else Text(S("Войдите в аккаунт, чтобы оставить заявку"), style = ElectroType.Caption, color = ElectroColors.TextMuted,
                modifier = Modifier.fillMaxWidth().padding(Space.x5), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

@Composable
private fun QtyButton(label: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape, color = ElectroColors.SurfaceElevated, modifier = Modifier.size(36.dp)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(label, fontSize = 20.sp, color = ElectroColors.TextPrimary)
        }
    }
}

/** Кнопка «Магазин» — сумка в кругляше, рядом с колокольчиком. */
@Composable
fun ShopFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick = onClick, shape = CircleShape, color = ElectroColors.SurfaceElevated, modifier = modifier.size(44.dp)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(Lx.ShoppingBag, S("Магазин"), tint = ElectroColors.TextSecondary, modifier = Modifier.size(22.dp))
        }
    }
}
