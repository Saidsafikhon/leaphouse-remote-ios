package uz.electro.remote.ui

import uz.electro.remote.i18n.S
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uz.electro.remote.data.SupportDto
import uz.electro.remote.ui.theme.ElectroColors

/** Круглая кнопка «Помощь» — кругляш с вопросом внутри. */
@Composable
fun HelpFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = ElectroColors.SurfaceElevated,
        modifier = modifier.size(44.dp),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("?", color = ElectroColors.Accent, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * Диалог «Помощь» — те же контакты поддержки, что на голове
 * (`GET /api/v1/agent/support`). Контакты кликабельны: набор, чат, профиль, сайт.
 */
@Composable
fun HelpDialog(support: SupportDto?, onFeedback: (() -> Unit)? = null, onDismiss: () -> Unit) {
    val uri = LocalUriHandler.current
    val s = support
    val any = s != null && (s.phone.isNotBlank() || s.telegram.isNotBlank() ||
        s.instagram.isNotBlank() || s.site.isNotBlank())
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ElectroColors.SurfaceElevated,
        tonalElevation = 0.dp,
        title = { Text(S("Помощь"), color = ElectroColors.TextPrimary) },
        text = {
            Column {
                if (!any) {
                    Text(S("Контакты поддержки пока не заданы."),
                        color = ElectroColors.TextMuted, fontSize = 13.sp)
                } else {
                    Text(S("Свяжитесь с поддержкой удобным способом:"),
                        color = ElectroColors.TextSecondary, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    if (s!!.phone.isNotBlank())
                        HelpLine(S("Телефон"), s.phone) {
                            runCatching { uri.openUri("tel:" + s.phone.filter { it == '+' || it.isDigit() }) }
                        }
                    if (s.telegram.isNotBlank())
                        HelpLine("Telegram", s.telegram) { runCatching { uri.openUri(tgLink(s.telegram)) } }
                    if (s.instagram.isNotBlank())
                        HelpLine("Instagram", s.instagram) { runCatching { uri.openUri(igLink(s.instagram)) } }
                    if (s.site.isNotBlank())
                        HelpLine(S("Сайт"), s.site) { runCatching { uri.openUri(webLink(s.site)) } }
                }
                if (onFeedback != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(S("Нашли ошибку или есть идея — напишите нам прямо отсюда."),
                        color = ElectroColors.TextMuted, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(S("Закрыть"), color = ElectroColors.Accent) }
        },
        dismissButton = onFeedback?.let {
            { TextButton(onClick = { onDismiss(); it() }) { Text(S("Оставить отзыв"), color = ElectroColors.Accent) } }
        },
    )
}

@Composable
private fun HelpLine(label: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = ElectroColors.TextMuted, fontSize = 13.sp, modifier = Modifier.width(96.dp))
        Text(value, color = ElectroColors.Accent, fontSize = 15.sp, modifier = Modifier.weight(1f))
    }
}

internal fun tgLink(v: String): String =
    if (v.startsWith("http")) v
    else "https://t.me/" + v.trim().removePrefix("@").removePrefix("https://t.me/")

internal fun igLink(v: String): String =
    if (v.startsWith("http")) v else "https://instagram.com/" + v.trim().removePrefix("@")

internal fun webLink(v: String): String =
    if (v.startsWith("http")) v else "https://" + v.trim()
