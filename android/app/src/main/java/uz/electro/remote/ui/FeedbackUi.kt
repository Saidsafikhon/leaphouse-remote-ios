package uz.electro.remote.ui

import uz.electro.remote.i18n.S
import android.net.Uri
import android.os.Build
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uz.electro.remote.ui.theme.ElectroColors

/** Виды отзыва — те же коды, что ждёт сервер (`POST /api/v1/feedback`). */
private val FEEDBACK_KINDS = listOf(
    "bug" to S("Ошибка"),
    "idea" to S("Идея"),
    "other" to S("Другое"),
)

/** Сколько файлов можно приложить — совпадает с сервером. */
private const val MAX_ATTACHMENTS = 3

/**
 * Диалог «Отзыв»: вид + свободный текст + до трёх вложений (скриншоты или
 * записи экрана из галереи). Уходит в админку, ответа в приложении не будет —
 * для этого есть «Помощь». Тестовая функция на время обкатки.
 *
 * [onSend] получает (kind, text, files) и колбэк с результатом: null —
 * отправлено, иначе причина отказа человеческим языком.
 */
@Composable
fun FeedbackDialog(
    onSend: (kind: String, text: String, files: List<Uri>, done: (String?) -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    var kind by remember { mutableStateOf("bug") }
    var text by remember { mutableStateOf("") }
    var files by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var sent by remember { mutableStateOf(false) }

    // Системный выбор фото/видео: без разрешений на хранилище, на любом Android.
    // Скриншот и запись экрана человек делает штатными средствами телефона,
    // здесь только выбирает их.
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_ATTACHMENTS)
    ) { picked ->
        if (picked.isNotEmpty()) files = (files + picked).distinct().take(MAX_ATTACHMENTS)
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = ElectroColors.SurfaceElevated,
        tonalElevation = 0.dp,
        title = { Text(if (sent) S("Спасибо!") else S("Отзыв"), color = ElectroColors.TextPrimary) },
        text = {
            if (sent) {
                Text(S("Отзыв отправлен. Мы читаем каждый."),
                    color = ElectroColors.TextSecondary, fontSize = 14.sp)
                return@AlertDialog
            }
            Column {
                Text(S("Что не так, чего не хватает или что было бы удобнее — напишите, это уйдёт разработчикам."),
                    color = ElectroColors.TextSecondary, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FEEDBACK_KINDS.forEach { (code, label) ->
                        FilterChip(
                            selected = kind == code,
                            onClick = { kind = code },
                            label = { Text(label, fontSize = 12.sp, maxLines = 1, softWrap = false) },
                            modifier = Modifier.weight(1f),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ElectroColors.Accent,
                                selectedLabelColor = ElectroColors.OnAccent,
                                labelColor = ElectroColors.TextSecondary,
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true, selected = kind == code,
                                borderColor = ElectroColors.Outline,
                                selectedBorderColor = ElectroColors.Accent,
                            ),
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = text, onValueChange = { if (it.length <= 2000) text = it },
                    minLines = 4, maxLines = 8,
                    placeholder = { Text(S("Например: климат включился сам в 8:10, машина C10"), fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = ElectroColors.TextPrimary,
                        unfocusedTextColor = ElectroColors.TextPrimary,
                        focusedBorderColor = ElectroColors.Accent,
                        unfocusedBorderColor = ElectroColors.Outline,
                        cursorColor = ElectroColors.Accent,
                        focusedPlaceholderColor = ElectroColors.TextMuted,
                        unfocusedPlaceholderColor = ElectroColors.TextMuted,
                    ),
                )
                Spacer(Modifier.height(10.dp))
                AttachmentsRow(
                    files = files, enabled = !busy,
                    onAdd = {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                    },
                    onRemove = { uri -> files = files - uri },
                )
                Text(
                    if (files.isEmpty()) S("Можно приложить скриншот или запись экрана (до {0}, по 40 МБ).", MAX_ATTACHMENTS)
                    else S("Нажмите на файл, чтобы убрать."),
                    color = ElectroColors.TextMuted, fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
                error?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, color = ElectroColors.Danger, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            if (sent) {
                TextButton(onClick = onDismiss) { Text(S("Закрыть"), color = ElectroColors.Accent) }
            } else {
                TextButton(
                    enabled = !busy && text.trim().length >= 3,
                    onClick = {
                        busy = true; error = null
                        onSend(kind, text.trim(), files) { failure ->
                            busy = false
                            if (failure == null) sent = true else error = failure
                        }
                    },
                ) {
                    if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp,
                        color = ElectroColors.Accent)
                    else Text(S("Отправить"), color = ElectroColors.Accent)
                }
            }
        },
        dismissButton = {
            if (!sent) TextButton(enabled = !busy, onClick = onDismiss) {
                Text(S("Отмена"), color = ElectroColors.TextSecondary)
            }
        },
    )
}

/** Ряд миниатюр выбранных файлов и кнопка «добавить». */
@Composable
private fun AttachmentsRow(
    files: List<Uri>, enabled: Boolean, onAdd: () -> Unit, onRemove: (Uri) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        files.forEach { uri -> AttachmentThumb(uri, enabled) { onRemove(uri) } }
        if (files.size < MAX_ATTACHMENTS) {
            Box(
                Modifier.size(64.dp).clip(RoundedCornerShape(10.dp))
                    .border(1.dp, ElectroColors.Outline, RoundedCornerShape(10.dp))
                    .clickable(enabled = enabled, onClick = onAdd),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.AddCircleOutline, S("Приложить файл"),
                    tint = ElectroColors.Accent, modifier = Modifier.size(26.dp))
            }
        }
    }
}

@Composable
private fun AttachmentThumb(uri: Uri, enabled: Boolean, onRemove: () -> Unit) {
    val ctx = LocalContext.current
    val isVideo = remember(uri) { ctx.contentResolver.getType(uri)?.startsWith("video/") == true }
    // миниатюра: сперва системная (loadThumbnail), если провайдер фото-пикера её
    // не отдаёт — декодируем сами с прореживанием (фото) или берём кадр (видео)
    val bitmap: ImageBitmap? by produceState<ImageBitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    ctx.contentResolver.loadThumbnail(uri, Size(192, 192), null)
                else null
            }.getOrNull()
                ?: runCatching { if (isVideo) videoFrame(ctx, uri) else sampledImage(ctx, uri) }.getOrNull()
        }?.asImageBitmap()
    }
    Box(
        Modifier.size(64.dp).clip(RoundedCornerShape(10.dp))
            .background(ElectroColors.Surface)
            .border(1.dp, ElectroColors.Outline, RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onRemove),
        contentAlignment = Alignment.Center,
    ) {
        bitmap?.let {
            Image(it, null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        if (isVideo) {
            Icon(Icons.Outlined.PlayCircleOutline, S("запись экрана"),
                tint = ElectroColors.OnAccent, modifier = Modifier.size(28.dp))
        } else if (bitmap == null) {
            Text("IMG", color = ElectroColors.TextMuted, fontSize = 11.sp)
        }
    }
}

/** Фото уменьшенным декодом: полный скриншот в память ради 64 dp не нужен. */
private fun sampledImage(ctx: android.content.Context, uri: Uri): android.graphics.Bitmap? {
    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    ctx.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (bounds.outWidth / sample > 384 || bounds.outHeight / sample > 384) sample *= 2
    val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
    return ctx.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, opts) }
}

/** Первый кадр записи экрана. */
private fun videoFrame(ctx: android.content.Context, uri: Uri): android.graphics.Bitmap? {
    val r = android.media.MediaMetadataRetriever()
    return try {
        r.setDataSource(ctx, uri)
        r.getFrameAtTime(0)?.let { android.graphics.Bitmap.createScaledBitmap(it, 192, 192 * it.height / it.width.coerceAtLeast(1), true) }
    } finally { runCatching { r.release() } }
}
