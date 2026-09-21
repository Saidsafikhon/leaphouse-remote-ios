package uz.electro.remote.ui.components

import uz.electro.remote.i18n.S
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import uz.electro.remote.ui.theme.*

/**
 * Библиотека контролов Electro Remote — код-двойник страницы Components
 * в Figma-файле «Electro Remote — Design System».
 *
 * Оформление снято с обложки: почти чёрные плитки без заливки-подсветки,
 * тонкие иконки, обычные (не полужирные) подписи. Активное состояние —
 * бирюзовая обводка и бирюзовые иконка с подписью, а не заливка.
 *
 * До этого в экранах жили пять разных кнопок (Tile 150×88, NumTile 46,
 * Mode 64, MiniBtn 38, Circle 52) со своими радиусами и цветами. Здесь их три:
 * [ControlTile], [ControlChip], [RoundAction] — и одна [ElectroButton].
 */

/** Состояние контрола. Active — подтверждено машиной, Pending — команда в пути. */
enum class ControlState { Default, Active, Pending, Disabled }

@Composable
fun ControlTile(
    label: String,
    icon: ImageVector,
    state: ControlState = ControlState.Default,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val ink = when (state) {
        ControlState.Active -> ElectroColors.Accent
        ControlState.Pending -> ElectroColors.TextSecondary
        ControlState.Disabled -> ElectroColors.TextDisabled
        ControlState.Default -> ElectroColors.TextPrimary
    }
    val textColor = when (state) {
        ControlState.Active -> ElectroColors.Accent
        ControlState.Disabled -> ElectroColors.TextDisabled
        else -> ElectroColors.TextSecondary
    }
    val borderColor = when (state) {
        ControlState.Active -> ElectroColors.Accent
        ControlState.Disabled -> ElectroColors.Outline
        else -> Color.Transparent
    }

    Surface(
        // на уровень выше фона: иначе внутри карточки (Surface) плитка не видна
        color = if (state == ControlState.Disabled) ElectroColors.Surface else ElectroColors.SurfaceElevated,
        shape = Radius.Md,
        modifier = modifier
            .height(ControlSize.Tile)
            .border(1.dp, borderColor, Radius.Md)
            .clickable(
                enabled = state != ControlState.Disabled && state != ControlState.Pending,
                onClick = onClick,
            ),
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = Space.x1, vertical = Space.x3),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (state == ControlState.Pending) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = ink,
                    strokeWidth = 1.5.dp,
                )
            } else {
                Icon(icon, null, tint = ink, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.height(Space.x2))
            Text(
                label,
                style = ElectroType.Label,
                color = textColor,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}

/** Компактный выбор значения: уровень обогрева, скорость вентилятора, ±. */
@Composable
fun ControlChip(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val ink = when {
        !enabled -> ElectroColors.TextDisabled
        selected -> ElectroColors.Accent
        else -> ElectroColors.TextPrimary
    }
    Surface(
        color = if (enabled) ElectroColors.SurfaceElevated else ElectroColors.Surface,
        shape = Radius.Sm,
        modifier = modifier
            .height(ControlSize.Chip)
            .border(1.dp, if (selected) ElectroColors.Accent else Color.Transparent, Radius.Sm)
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text, style = ElectroType.Body, color = ink)
        }
    }
}

/** Круглая кнопка ± возле крупного значения — как в карточке климата на обложке. */
@Composable
fun RoundAction(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val ink = when {
        !enabled -> ElectroColors.TextDisabled
        active -> ElectroColors.Accent
        else -> ElectroColors.TextPrimary
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            color = ElectroColors.SurfaceElevated,
            shape = CircleShape,
            modifier = Modifier
                .size(ControlSize.Round)
                .border(1.dp, if (active) ElectroColors.Accent else Color.Transparent, CircleShape)
                .clickable(enabled = enabled, onClick = onClick),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = ink, modifier = Modifier.size(22.dp))
            }
        }
        if (label.isNotBlank()) {
            Spacer(Modifier.height(Space.x2))
            Text(label, style = ElectroType.Caption, color = ElectroColors.TextSecondary)
        }
    }
}

enum class ButtonStyle { Primary, Secondary, Ghost, Danger }

@Composable
fun ElectroButton(
    text: String,
    modifier: Modifier = Modifier,
    style: ButtonStyle = ButtonStyle.Primary,
    enabled: Boolean = true,
    loading: Boolean = false,
    onClick: () -> Unit,
) {
    val bg = when {
        !enabled -> ElectroColors.Surface
        style == ButtonStyle.Primary -> ElectroColors.Accent
        style == ButtonStyle.Secondary -> ElectroColors.SurfaceElevated
        else -> Color.Transparent
    }
    val ink = when {
        !enabled -> ElectroColors.TextDisabled
        style == ButtonStyle.Primary -> ElectroColors.OnAccent
        style == ButtonStyle.Secondary -> ElectroColors.TextPrimary
        style == ButtonStyle.Ghost -> ElectroColors.TextSecondary
        else -> ElectroColors.Danger
    }
    val border = when {
        !enabled -> ElectroColors.Outline
        style == ButtonStyle.Danger -> ElectroColors.Danger
        style == ButtonStyle.Primary -> Color.Transparent
        else -> ElectroColors.Outline
    }
    Surface(
        color = bg,
        shape = Radius.Sm,
        modifier = modifier
            .height(ControlSize.Button)
            .border(1.dp, border, Radius.Sm)
            .clickable(enabled = enabled && !loading, onClick = onClick),
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = Space.x5),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(16.dp), color = ink, strokeWidth = 1.5.dp)
                Spacer(Modifier.width(Space.x2))
            }
            Text(if (loading) S("Отправка…") else text, style = ElectroType.Body, color = ink)
        }
    }
}

/** Состояние связи и исход команды. */
enum class BadgeKind { Online, Cloud, Offline, Success, Unsafe, Failed, Timeout, Unconfirmed, Info }

@Composable
@ReadOnlyComposable
private fun BadgeKind.ink(): Color = when (this) {
    BadgeKind.Online, BadgeKind.Success -> ElectroColors.Ok
    BadgeKind.Cloud, BadgeKind.Unconfirmed -> ElectroColors.Warn
    BadgeKind.Offline -> ElectroColors.TextMuted
    BadgeKind.Unsafe, BadgeKind.Failed, BadgeKind.Timeout -> ElectroColors.Danger
    BadgeKind.Info -> ElectroColors.Info
}

@Composable
@ReadOnlyComposable
private fun BadgeKind.tint(): Color = when (this) {
    BadgeKind.Online, BadgeKind.Success -> ElectroColors.OkTint
    BadgeKind.Cloud, BadgeKind.Unconfirmed -> ElectroColors.WarnTint
    BadgeKind.Offline -> ElectroColors.SurfaceElevated
    BadgeKind.Unsafe, BadgeKind.Failed, BadgeKind.Timeout -> ElectroColors.DangerTint
    BadgeKind.Info -> ElectroColors.InfoTint
}

@Composable
fun StatusBadge(kind: BadgeKind, text: String, modifier: Modifier = Modifier) {
    Surface(color = kind.tint(), shape = Radius.Pill, modifier = modifier) {
        Row(
            Modifier.padding(horizontal = Space.x3, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (kind == BadgeKind.Online || kind == BadgeKind.Cloud || kind == BadgeKind.Offline) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(kind.ink()))
                Spacer(Modifier.width(6.dp))
            }
            Text(text, style = ElectroType.Overline, color = kind.ink())
        }
    }
}

/**
 * Полоса состояния с обложки: слева охрана с иконкой в цветном квадрате,
 * справа запас хода и заряд — числом с полоской, единица набрана мельче.
 */
@Composable
fun StatusStrip(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    accent: Color = ElectroColors.Ok,
    accentTint: Color = ElectroColors.OkTint,
    metrics: List<Metric> = emptyList(),
) {
    Surface(color = ElectroColors.Surface, shape = Radius.Md, modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = Space.x3, vertical = Space.x3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(accentTint),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(Space.x3))
            Column(Modifier.weight(1f)) {
                Text(title, style = ElectroType.Body, color = ElectroColors.TextPrimary)
                Text(subtitle, style = ElectroType.Caption, color = ElectroColors.TextMuted)
            }
            if (metrics.isNotEmpty()) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    metrics.forEach { MetricRow(it) }
                }
            }
        }
    }
}

/** Число с единицей и полоской заполнения. [fraction] в null — полоски нет. */
data class Metric(val value: String, val unit: String, val fraction: Float? = null)

@Composable
private fun MetricRow(metric: Metric) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        metric.fraction?.let { f ->
            Box(
                Modifier.width(28.dp).height(8.dp).clip(RoundedCornerShape(3.dp))
                    .background(ElectroColors.SurfaceElevated),
            ) {
                Box(
                    Modifier.fillMaxWidth(f.coerceIn(0f, 1f)).fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp)).background(ElectroColors.Accent),
                )
            }
            Spacer(Modifier.width(Space.x2))
        }
        Text(metric.value, style = ElectroType.Value, color = ElectroColors.Accent)
        Spacer(Modifier.width(3.dp))
        Text(metric.unit, style = ElectroType.Unit, color = ElectroColors.TextMuted)
    }
}

/** Заголовок секции: на обложке он обычным текстом, а не капсом. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = ElectroType.Headline.copy(fontSize = ElectroType.Body.fontSize),
        color = ElectroColors.TextSecondary, modifier = modifier)
}

@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(color = ElectroColors.Surface, shape = Radius.Md, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(Space.x4), verticalArrangement = Arrangement.spacedBy(Space.x3)) {
            Row(
                Modifier.fillMaxWidth().let { m -> if (onAction != null) m.clickable(onClick = onAction) else m },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = ElectroType.Body, color = ElectroColors.TextSecondary,
                    modifier = Modifier.weight(1f))
                if (action != null) Text(action, style = ElectroType.Body, color = ElectroColors.Accent)
            }
            content()
        }
    }
}

/**
 * Свёрнутый раздел. Раньше климат, окна, свет и сиденья лежали на главном
 * экране одной лентой — восемь секций подряд.
 */
@Composable
fun FoldSection(
    icon: ImageVector,
    title: String,
    subtitle: String,
    expanded: Boolean,
    modifier: Modifier = Modifier,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(color = ElectroColors.Surface, shape = Radius.Md, modifier = modifier.fillMaxWidth()) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(Space.x4),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, null, tint = if (expanded) ElectroColors.Accent else ElectroColors.TextSecondary,
                    modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(Space.x3))
                Column(Modifier.weight(1f)) {
                    Text(title, style = ElectroType.Body, color = ElectroColors.TextPrimary)
                    Text(subtitle, style = ElectroType.Caption, color = ElectroColors.TextMuted)
                }
                Icon(
                    if (expanded) Lx.ExpandLess else Lx.ExpandMore,
                    null, tint = ElectroColors.TextMuted, modifier = Modifier.size(22.dp),
                )
            }
            if (expanded) {
                Column(
                    Modifier.padding(start = Space.x4, end = Space.x4, bottom = Space.x4),
                    verticalArrangement = Arrangement.spacedBy(Space.x3),
                    content = content,
                )
            }
        }
    }
}

/** Снекбар результата команды: заголовок — что произошло, подпись — с чем именно. */
@Composable
fun ElectroToast(kind: BadgeKind, title: String, message: String?, modifier: Modifier = Modifier) {
    val icon = when (kind) {
        BadgeKind.Success -> Lx.CheckCircle
        BadgeKind.Unsafe, BadgeKind.Failed -> Lx.Warning
        BadgeKind.Timeout, BadgeKind.Unconfirmed -> Lx.Schedule
        BadgeKind.Offline -> Lx.CloudOff
        else -> Lx.Info
    }
    Surface(
        color = ElectroColors.SurfaceElevated,
        shape = Radius.Md,
        modifier = modifier.fillMaxWidth().border(1.dp, kind.ink(), Radius.Md),
    ) {
        Row(
            Modifier.padding(horizontal = Space.x4, vertical = Space.x3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(kind.tint()),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = kind.ink(), modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(Space.x3))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = ElectroType.Body,
                    color = if (kind == BadgeKind.Success || kind == BadgeKind.Info)
                        ElectroColors.TextPrimary else kind.ink(),
                )
                if (!message.isNullOrBlank()) {
                    Text(message, style = ElectroType.Caption, color = ElectroColors.TextSecondary)
                }
            }
        }
    }
}

/**
 * Подтверждение опасного действия — открыть двери, багажник.
 * Общий на все экраны: до этого копия жила приватно в «Управлении».
 */
@Composable
fun ElectroDialog(
    icon: ImageVector,
    accent: Color,
    title: String,
    msg: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    //: у сообщения выбора нет — там «Отмена» рядом с «Понятно» предлагала бы
    //: отменить то, чего не произошло
    dismissText: String? = S("Отмена"),
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ElectroColors.SurfaceElevated,
        // Material3 иначе подмешивает акцент в фон диалога через tonal elevation
        tonalElevation = 0.dp,
        shape = Radius.Lg,
        icon = { Icon(icon, null, tint = accent, modifier = Modifier.size(34.dp)) },
        title = { Text(title, style = ElectroType.Headline, color = ElectroColors.TextPrimary) },
        text = { Text(msg, style = ElectroType.Body, color = ElectroColors.TextSecondary) },
        confirmButton = { ElectroButton(confirmText, style = ButtonStyle.Primary, onClick = onConfirm) },
        dismissButton = dismissText?.let {
            { ElectroButton(it, style = ButtonStyle.Ghost, onClick = onDismiss) }
        },
    )
}
