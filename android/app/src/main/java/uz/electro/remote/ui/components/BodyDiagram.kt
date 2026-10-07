package uz.electro.remote.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import uz.electro.remote.data.CarState
import uz.electro.remote.i18n.S
import uz.electro.remote.ui.theme.*

/**
 * Кузов: схема машины сверху (нос вверху) и статус замка.
 *
 * Открытая дверь рисуется красной и отведённой наружу, багажник и капот —
 * красной заливкой своей части. Данные — живые, от головы 4.61+ (у каждого
 * поколения машины свой источник, голова приводит их к одним полям). Если
 * машина о дверях не сообщает, карточка показывает только замок.
 */
@Composable
fun BodyStatusCard(car: CarState, modifier: Modifier = Modifier) {
    val open = car.openParts()
    val locked = car.locked
    Surface(color = ElectroColors.Surface, shape = Radius.Md, modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = Space.x3, vertical = Space.x3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (car.bodyKnown) {
                CarTopView(car, Modifier.width(64.dp).height(112.dp))
                Spacer(Modifier.width(Space.x4))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.x2)) {
                // замок
                val (lockText, lockColor, lockTint) = when (locked) {
                    true -> Triple(S("Закрыто"), ElectroColors.Ok, ElectroColors.OkTint)
                    false -> Triple(S("Открыто"), ElectroColors.Warn, ElectroColors.WarnTint)
                    null -> Triple(S("Замок: нет данных"), ElectroColors.TextMuted, ElectroColors.SurfaceElevated)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(lockTint),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(if (locked == true) Lx.Lock else Lx.LockOpen, null, tint = lockColor,
                            modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(Space.x2))
                    Text(lockText, style = ElectroType.Body, color = ElectroColors.TextPrimary)
                }
                // двери / багажник / капот
                when {
                    !car.bodyKnown -> Text(S("Двери: машина не сообщает"),
                        style = ElectroType.Caption, color = ElectroColors.TextMuted)
                    open.isEmpty() -> Text(if (car.hoodKnown) S("Двери, багажник и капот закрыты") else S("Двери и багажник закрыты"),
                        style = ElectroType.Caption, color = ElectroColors.TextMuted)
                    else -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(S("Не закрыто:"), style = ElectroType.Caption, color = ElectroColors.Danger)
                        open.forEach {
                            Text("• " + it.replaceFirstChar { c -> c.uppercase() },
                                style = ElectroType.Caption, color = ElectroColors.TextPrimary)
                        }
                    }
                }
            }
        }
    }
}

/** Машина сверху: корпус, капот, багажник, четыре двери. Открытое — красным. */
@Composable
fun CarTopView(car: CarState, modifier: Modifier = Modifier) {
    val body = ElectroColors.SurfaceElevated
    val line = ElectroColors.TextMuted
    val glass = ElectroColors.Outline
    val danger = ElectroColors.Danger
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val bodyW = w * 0.62f; val left = (w - bodyW) / 2f; val right = left + bodyW
        val top = h * 0.04f; val bottom = h * 0.96f
        val r = CornerRadius(bodyW * 0.32f)

        // корпус
        drawRoundRect(body, Offset(left, top), Size(bodyW, bottom - top), r)
        // капот (нос вверху) и багажник
        val hoodEnd = top + (bottom - top) * 0.24f
        val trunkStart = top + (bottom - top) * 0.80f
        if (car.hoodOpen) drawRoundRect(danger.copy(alpha = 0.85f), Offset(left, top), Size(bodyW, hoodEnd - top), r)
        if (car.trunkOpen == true) drawRoundRect(danger.copy(alpha = 0.85f), Offset(left, trunkStart),
            Size(bodyW, bottom - trunkStart), r)
        // стёкла: лобовое и заднее
        drawRoundRect(glass, Offset(left + bodyW * 0.14f, hoodEnd + 2f), Size(bodyW * 0.72f, (bottom - top) * 0.10f),
            CornerRadius(6f))
        drawRoundRect(glass, Offset(left + bodyW * 0.16f, trunkStart - (bottom - top) * 0.09f),
            Size(bodyW * 0.68f, (bottom - top) * 0.07f), CornerRadius(6f))
        // контур
        drawRoundRect(line, Offset(left, top), Size(bodyW, bottom - top), r, style = Stroke(2f))

        // двери: передние от конца капота, задние до начала багажника
        val doorLen = (trunkStart - hoodEnd) / 2f
        val frontY = hoodEnd + doorLen * 0.05f
        val rearY = hoodEnd + doorLen * 1.02f
        door(left, frontY, doorLen * 0.93f, leftSide = true, car.doors.frontLeft, line, danger)
        door(right, frontY, doorLen * 0.93f, leftSide = false, car.doors.frontRight, line, danger)
        door(left, rearY, doorLen * 0.93f, leftSide = true, car.doors.rearLeft, line, danger)
        door(right, rearY, doorLen * 0.93f, leftSide = false, car.doors.rearRight, line, danger)
    }
}

/** Дверь — отрезок по борту; открытая поворачивается наружу на петле спереди. */
private fun DrawScope.door(x: Float, y: Float, len: Float, leftSide: Boolean, open: Boolean, line: Color, danger: Color) {
    val stroke = if (open) 5f else 3f
    if (!open) {
        drawLine(line, Offset(x, y), Offset(x, y + len), strokeWidth = stroke)
        return
    }
    val angle = if (leftSide) 35f else -35f
    rotate(angle, pivot = Offset(x, y)) {
        drawLine(danger, Offset(x, y), Offset(x, y + len), strokeWidth = stroke)
    }
}

/** Передача не P: красная плашка над управлением — команды с телефона отключены. */
@Composable
fun GearLockBanner(modifier: Modifier = Modifier) {
    Surface(color = ElectroColors.DangerTint, shape = Radius.Md, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = Space.x4, vertical = Space.x3),
            verticalArrangement = Arrangement.spacedBy(Space.x1)) {
            Text(S("Машина не на паркинге"), style = ElectroType.Body, color = ElectroColors.Danger)
            Text(S("Управление с телефона отключено, пока передача не P."),
                style = ElectroType.Caption, color = ElectroColors.TextSecondary)
        }
    }
}
