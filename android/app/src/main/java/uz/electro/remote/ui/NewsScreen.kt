package uz.electro.remote.ui

import uz.electro.remote.i18n.S
import uz.electro.remote.ui.components.PullRefresh
import uz.electro.remote.ui.components.Lx
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Close
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uz.electro.remote.data.NewsItemDto
import uz.electro.remote.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Лента: карточки с обложкой (как в макете Figma «Новости»), разделы — обновления,
 * инструкции, события, новости (в т.ч. автоновости из RSS с пометкой источника).
 * Непрочитанное — бейдж NEW; тап открывает запись, «Подробнее» — ссылку источника.
 */
@Composable
fun NewsScreen(
    items: List<NewsItemDto>,
    read: Set<String>,
    onRead: (NewsItemDto) -> Unit,
    onReadAll: () -> Unit,
    onBack: () -> Unit,
    onRefresh: (() -> Unit)? = null,
) {
    var filter by remember { mutableStateOf("all") }
    var selected by remember { mutableStateOf<NewsItemDto?>(null) }
    val filters = listOf(
        "all" to S("Все"), "update" to S("Обновления"), "guide" to S("Инструкции"),
        "event" to S("События"), "news" to S("Новости"), "alert" to S("Важное"),
    )

    // Запись на весь экран — вместо ленты, с «назад» и «закрыть».
    selected?.let { n ->
        androidx.activity.compose.BackHandler { selected = null }
        NewsDetailScreen(n, onClose = { selected = null })
        return
    }
    val shown = items.filter { n ->
        when (filter) {
            "all" -> true
            "alert" -> n.kind == "alert"
            "news" -> n.category == "news" || (n.category.isBlank() && n.kind == "news")
            else -> n.category == filter
        }
    }
    val unread = items.count { it.source == null && it.id !in read }

    PullRefresh(onRefresh) {
    ScreenScaffold(S("Новости"), onBack) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            filters.forEach { (k, label) ->
                val on = filter == k
                Surface(
                    color = if (on) ElectroColors.Accent.copy(alpha = 0.14f) else ElectroColors.SurfaceElevated, shape = Radius.Pill,
                    border = if (on) androidx.compose.foundation.BorderStroke(1.dp, ElectroColors.Accent) else null,
                    modifier = Modifier.height(ControlSize.Chip).clip(Radius.Pill).clickable { filter = k },
                ) {
                    Box(Modifier.padding(horizontal = Space.x4).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        Text(label, style = ElectroType.Body, color = if (on) ElectroColors.Accent else ElectroColors.TextPrimary, maxLines = 1)
                    }
                }
            }
        }
        if (unread > 0) {
            uz.electro.remote.ui.components.ElectroButton(
                S("Прочитать всё ({0})", unread), Modifier.fillMaxWidth(),
                style = uz.electro.remote.ui.components.ButtonStyle.Secondary, onClick = onReadAll,
            )
        }
        if (shown.isEmpty()) {
            EmptyNote(if (items.isEmpty()) S("Пока ничего нет. Здесь появятся новости и уведомления от оператора.") else S("В этом разделе пусто."))
        }
        // NEW — только у новостей оператора; RSS-новости показываются тихо
        shown.forEach { n -> NewsCard(n, isRead = n.source != null || n.id in read, onOpen = { onRead(n); selected = n }) }
    }
    }
}

/** Подпись раздела карточки: категория, а без неё — тип записи. */
private fun categoryLabel(n: NewsItemDto): String = when (n.category) {
    "update" -> S("Обновление ПО"); "guide" -> S("Инструкция"); "event" -> S("Событие"); "news" -> S("Новость")
    else -> when (n.kind) { "alert" -> S("Важное"); "news" -> S("Новость"); else -> S("Уведомление") }
}

@Composable
private fun NewsCard(n: NewsItemDto, isRead: Boolean, onOpen: () -> Unit) {
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    val accent = if (n.kind == "alert") ElectroColors.Warn else ElectroColors.Accent
    val hasImage = !n.image_url.isNullOrBlank()
    Surface(
        color = ElectroColors.Surface, shape = Radius.Md,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isRead) ElectroColors.Outline else accent.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth().clip(Radius.Md).clickable(onClick = onOpen),
    ) {
        Column(Modifier.padding(Space.x3)) {
            if (hasImage) {
                Box(Modifier.fillMaxWidth().height(170.dp).clip(Radius.Sm).background(ElectroColors.SurfaceElevated)) {
                    coil.compose.AsyncImage(
                        model = n.image_url, contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    // источник / раздел — тегом на обложке, NEW — справа
                    val tag = n.source ?: categoryLabel(n)
                    Box(Modifier.align(Alignment.TopStart).padding(10.dp).clip(Radius.Sm)
                        .background(ElectroColors.Background.copy(alpha = 0.85f)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Text(tag.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = ElectroColors.TextPrimary, letterSpacing = 0.5.sp)
                    }
                    if (!isRead) Box(Modifier.align(Alignment.TopEnd).padding(10.dp).clip(Radius.Pill)
                        .background(ElectroColors.Accent).padding(horizontal = 8.dp, vertical = 3.dp)) {
                        Text("NEW", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = ElectroColors.OnAccent)
                    }
                }
                Spacer(Modifier.height(Space.x3))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(categoryLabel(n).uppercase(), fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                    color = ElectroColors.TextSecondary, letterSpacing = 0.6.sp, modifier = Modifier.weight(1f))
                if (!hasImage && !isRead) {
                    Box(Modifier.clip(Radius.Pill).background(ElectroColors.Accent).padding(horizontal = 7.dp, vertical = 2.dp)) {
                        Text("NEW", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = ElectroColors.OnAccent)
                    }
                    Spacer(Modifier.width(Space.x2))
                }
                Text(newsDate(n.created_at), style = ElectroType.Unit, color = ElectroColors.TextMuted)
            }
            Spacer(Modifier.height(6.dp))
            Text(n.title, style = ElectroType.Body, fontWeight = FontWeight.SemiBold, color = ElectroColors.TextPrimary,
                maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            if (!hasImage && n.body.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(n.body, style = ElectroType.Caption, color = ElectroColors.TextSecondary, maxLines = 3,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(Space.x3))
            Row(verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { val l = n.link; if (!l.isNullOrBlank()) runCatching { uriHandler.openUri(l) } else onOpen() }) {
                Text(S("Подробнее"), style = ElectroType.Body, color = accent, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Icon(Lx.ArrowForward, null, tint = accent, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** Одна запись на весь экран: обложка, раздел, дата, полный текст, ссылка на источник. */
@Composable
fun NewsDetailScreen(n: NewsItemDto, onClose: () -> Unit) {
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    Column(Modifier.fillMaxSize().background(ElectroColors.Background)) {
        Row(Modifier.fillMaxWidth().padding(Space.x4), verticalAlignment = Alignment.CenterVertically) {
            Icon(Lx.ArrowBack, S("Назад"), tint = ElectroColors.TextPrimary,
                modifier = Modifier.size(26.dp).clickable(onClick = onClose))
            Spacer(Modifier.width(Space.x3))
            Text(categoryLabel(n), style = ElectroType.Headline, color = ElectroColors.TextPrimary, modifier = Modifier.weight(1f))
            Icon(Lx.Close, S("Закрыть"), tint = ElectroColors.TextSecondary,
                modifier = Modifier.size(24.dp).clickable(onClick = onClose))
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = Space.x5).padding(bottom = Space.x6),
            verticalArrangement = Arrangement.spacedBy(Space.x4),
        ) {
            if (!n.image_url.isNullOrBlank()) {
                coil.compose.AsyncImage(
                    model = n.image_url, contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(200.dp).clip(Radius.Md).background(ElectroColors.SurfaceElevated),
                )
            }
            Text(listOfNotNull(n.source, newsDate(n.created_at)).joinToString(" · "), style = ElectroType.Caption, color = ElectroColors.TextMuted)
            Text(n.title, style = ElectroType.Title, color = ElectroColors.TextPrimary)
            if (n.body.isNotBlank()) Text(n.body, style = ElectroType.Body, color = ElectroColors.TextSecondary)
        }
        val link = n.link
        if (!link.isNullOrBlank()) {
            uz.electro.remote.ui.components.ElectroButton(
                S("Открыть источник"), Modifier.fillMaxWidth().padding(horizontal = Space.x5).padding(bottom = Space.x2),
                onClick = { runCatching { uriHandler.openUri(link) } },
            )
        }
        uz.electro.remote.ui.components.ElectroButton(
            S("Закрыть"), Modifier.fillMaxWidth().padding(horizontal = Space.x5).padding(bottom = Space.x4),
            style = uz.electro.remote.ui.components.ButtonStyle.Secondary, onClick = onClose,
        )
    }
}

private fun newsDate(iso: String): String = runCatching {
    val clean = iso.replace(Regex("[.][0-9]+"), "")     // без дробных секунд
    val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
    val d = parser.parse(clean) ?: return ""
    SimpleDateFormat("d MMMM, HH:mm", Locale(uz.electro.remote.i18n.Lang.current)).apply { timeZone = TimeZone.getDefault() }.format(d)
}.getOrDefault("")

/** Колокольчик «Новости» с бейджем непрочитанных. */
@Composable
fun NewsBell(unread: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier) {
        Surface(
            onClick = onClick, shape = CircleShape, color = ElectroColors.SurfaceElevated,
            modifier = Modifier.size(44.dp),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    Lx.Notifications, null,
                    tint = if (unread > 0) ElectroColors.Accent else ElectroColors.TextSecondary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        if (unread > 0) {
            Box(
                Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-2).dp)
                    .height(16.dp).clip(Radius.Pill).background(ElectroColors.Accent)
                    .padding(horizontal = 5.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (unread > 9) "9+" else "$unread", color = ElectroColors.OnAccent,
                    fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
