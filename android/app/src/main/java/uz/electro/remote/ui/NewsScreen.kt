package uz.electro.remote.ui

import uz.electro.remote.i18n.S
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
 * Лента из админки: уведомления, новости, важное. Непрочитанное — с точкой,
 * тап отмечает прочитанным, «Прочитать всё» гасит бейдж.
 */
@Composable
fun NewsScreen(
    items: List<NewsItemDto>,
    read: Set<String>,
    onRead: (NewsItemDto) -> Unit,
    onReadAll: () -> Unit,
    onBack: () -> Unit,
) {
    var filter by remember { mutableStateOf("all") }
    var selected by remember { mutableStateOf<NewsItemDto?>(null) }
    val filters = listOf("all" to S("Все"), "info" to S("Уведомления"), "news" to S("Новости"), "alert" to S("Важное"))

    // Запись на весь экран — вместо ленты, с «назад» и «закрыть».
    selected?.let { n ->
        androidx.activity.compose.BackHandler { selected = null }
        NewsDetailScreen(n, onClose = { selected = null })
        return
    }
    val shown = items.filter { filter == "all" || it.kind == filter }
    val unread = items.count { it.id !in read }

    ScreenScaffold(S("Новости"), onBack) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            filters.forEach { (k, label) ->
                val on = filter == k
                Surface(
                    color = ElectroColors.SurfaceElevated, shape = Radius.Sm,
                    border = if (on) androidx.compose.foundation.BorderStroke(1.dp, ElectroColors.Accent) else null,
                    modifier = Modifier.height(ControlSize.Chip).clip(Radius.Sm).clickable { filter = k },
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
        shown.forEach { n ->
            val isRead = n.id in read
            val (icon, tint) = when (n.kind) {
                "alert" -> Lx.Warning to ElectroColors.Warn
                "news" -> Lx.Campaign to ElectroColors.Info
                else -> Lx.Notifications to ElectroColors.Accent
            }
            Surface(
                color = ElectroColors.Surface, shape = Radius.Md,
                border = if (isRead) null else androidx.compose.foundation.BorderStroke(1.dp, ElectroColors.Accent.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth().clip(Radius.Md).clickable { onRead(n); selected = n },
            ) {
                Row(Modifier.padding(Space.x4), verticalAlignment = Alignment.Top) {
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).background(tint.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp)) }
                    Spacer(Modifier.width(Space.x3))
                    Column(Modifier.weight(1f)) {
                        Text(n.title, style = ElectroType.Body, color = ElectroColors.TextPrimary)
                        if (n.body.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(n.body, style = ElectroType.Caption, color = ElectroColors.TextSecondary)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            newsDate(n.created_at) + if (isRead) "" else S(" · не прочитано"),
                            style = ElectroType.Unit, color = if (isRead) ElectroColors.TextMuted else ElectroColors.Accent,
                        )
                    }
                    if (!isRead) {
                        Spacer(Modifier.width(Space.x2))
                        Box(Modifier.padding(top = 6.dp).size(8.dp).clip(CircleShape).background(ElectroColors.Accent))
                    }
                }
            }
        }
    }
}

/** Одна запись на весь экран: заголовок, тип, дата, полный текст. */
@Composable
fun NewsDetailScreen(n: NewsItemDto, onClose: () -> Unit) {
    val (icon, tint) = when (n.kind) {
        "alert" -> Lx.Warning to ElectroColors.Warn
        "news" -> Lx.Campaign to ElectroColors.Info
        else -> Lx.Notifications to ElectroColors.Accent
    }
    val kindTitle = when (n.kind) { "alert" -> S("Важное"); "news" -> S("Новость"); else -> S("Уведомление") }
    Column(Modifier.fillMaxSize().background(ElectroColors.Background)) {
        Row(Modifier.fillMaxWidth().padding(Space.x4), verticalAlignment = Alignment.CenterVertically) {
            Icon(Lx.ArrowBack, S("Назад"), tint = ElectroColors.TextPrimary,
                modifier = Modifier.size(26.dp).clickable(onClick = onClose))
            Spacer(Modifier.width(Space.x3))
            Text(kindTitle, style = ElectroType.Headline, color = ElectroColors.TextPrimary, modifier = Modifier.weight(1f))
            Icon(Lx.Close, S("Закрыть"), tint = ElectroColors.TextSecondary,
                modifier = Modifier.size(24.dp).clickable(onClick = onClose))
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = Space.x5).padding(bottom = Space.x6),
            verticalArrangement = Arrangement.spacedBy(Space.x4),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.x3)) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(tint.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
                }
                Text(newsDate(n.created_at), style = ElectroType.Caption, color = ElectroColors.TextMuted)
            }
            Text(n.title, style = ElectroType.Title, color = ElectroColors.TextPrimary)
            if (n.body.isNotBlank()) Text(n.body, style = ElectroType.Body, color = ElectroColors.TextSecondary)
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
    SimpleDateFormat("d MMMM, HH:mm", Locale("ru")).apply { timeZone = TimeZone.getDefault() }.format(d)
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
