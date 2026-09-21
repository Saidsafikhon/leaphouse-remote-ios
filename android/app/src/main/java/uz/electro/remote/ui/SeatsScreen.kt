package uz.electro.remote.ui

import uz.electro.remote.i18n.S
import uz.electro.remote.ui.components.Lx
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Whatshot
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
import androidx.compose.ui.unit.dp
import uz.electro.remote.data.Cmd
import uz.electro.remote.ui.components.SEAT_LEVELS
import uz.electro.remote.ui.components.nextSeatLevel
import uz.electro.remote.ui.theme.*

/**
 * Сиденья схемой салона — как на экране GWM: вид сверху, четыре места на своих
 * местах, у каждого слева обогрев, справа вентиляция.
 *
 * Место рисуется сверху вниз (спинка и подушка); нажатие на половину добавляет
 * ступень (0→1→2→3→0), обогрев греет тёплым, вентиляция — синим. Уровень видно
 * по точкам под иконкой, а не цифрой: на четыре места это тридцать две цифры,
 * а фигура говорит сама.
 */
@Composable
fun SeatsScreen(
    controls: Map<Int, String>,
    send: (Int, String) -> Unit,
    onBack: () -> Unit,
) {
    fun level(type: Int) = controls[type]?.toFloatOrNull()?.toInt()?.coerceIn(0, SEAT_LEVELS) ?: 0

    ScreenScaffold(S("Сиденья"), onBack) {
        Surface(color = ElectroColors.Surface, shape = Radius.Lg, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(Space.x4)) {
                // нос машины сверху — как в салоне вид сверху у GWM
                Text(S("▲ перёд"), style = ElectroType.Caption, color = ElectroColors.TextMuted,
                    modifier = Modifier.align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(Space.x3))

                CabinRow(
                    left = SeatSpec(S("Водитель"), Cmd.SEAT_HEAT_DRIVER, Cmd.SEAT_VENT_DRIVER),
                    right = SeatSpec(S("Пассажир"), Cmd.SEAT_HEAT_PASSENGER, Cmd.SEAT_VENT_PASSENGER),
                    level = ::level, send = send,
                )
                Spacer(Modifier.height(Space.x4))
                CabinRow(
                    left = SeatSpec(S("Заднее левое"), Cmd.SEAT_HEAT_REAR_L, Cmd.SEAT_VENT_REAR_L),
                    right = SeatSpec(S("Заднее правое"), Cmd.SEAT_HEAT_REAR_R, Cmd.SEAT_VENT_REAR_R),
                    level = ::level, send = send,
                )
            }
        }
        Text(
            S("Слева обогрев, справа вентиляция. Нажатие добавляет ступень, четвёртое — выключает."),
            style = ElectroType.Caption, color = ElectroColors.TextMuted,
        )
    }
}

private data class SeatSpec(val name: String, val heatType: Int, val ventType: Int)

@Composable
private fun CabinRow(
    left: SeatSpec, right: SeatSpec,
    level: (Int) -> Int, send: (Int, String) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.x4)) {
        listOf(left, right).forEach { seat ->
            SeatSchematic(
                name = seat.name,
                heat = level(seat.heatType),
                vent = level(seat.ventType),
                modifier = Modifier.weight(1f),
                onHeat = { send(seat.heatType, nextSeatLevel(level(seat.heatType)).toString()) },
                onVent = { send(seat.ventType, nextSeatLevel(level(seat.ventType)).toString()) },
            )
        }
    }
}

/** Одно место: фигура сиденья сверху + половины обогрева и вентиляции. */
@Composable
private fun SeatSchematic(
    name: String,
    heat: Int,
    vent: Int,
    modifier: Modifier = Modifier,
    onHeat: () -> Unit,
    onVent: () -> Unit,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(name, style = ElectroType.Label, color = ElectroColors.TextSecondary, maxLines = 1)
        Spacer(Modifier.height(Space.x2))
        Row(
            Modifier.height(112.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            // левая половина — обогрев, правая — вентиляция; каждая тапается
            SeatHalf(
                icon = Lx.Whatshot, level = heat, onColor = ElectroColors.Warn,
                side = Side.Left, modifier = Modifier.weight(1f).fillMaxHeight().clip(
                    RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp)
                ).clickable(onClick = onHeat),
            )
            SeatHalf(
                icon = Lx.Air, level = vent, onColor = ElectroColors.Info,
                side = Side.Right, modifier = Modifier.weight(1f).fillMaxHeight().clip(
                    RoundedCornerShape(topEnd = 18.dp, bottomEnd = 18.dp)
                ).clickable(onClick = onVent),
            )
        }
    }
}

private enum class Side { Left, Right }

@Composable
private fun SeatHalf(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    level: Int,
    onColor: Color,
    side: Side,
    modifier: Modifier = Modifier,
) {
    val active = level > 0
    val tint = if (active) onColor else ElectroColors.TextDisabled
    // палитру берём до Canvas: внутри draw-скоупа @Composable-геттеры недоступны
    val idle = ElectroColors.SurfaceElevated
    val bodyIdle = ElectroColors.SurfaceRaised
    Box(modifier.background(if (active) onColor.copy(alpha = 0.12f) else idle)) {
        // половина фигуры сиденья: спинка сверху, подушка снизу
        Canvas(Modifier.fillMaxSize().padding(Space.x2)) {
            val body = if (active) onColor.copy(alpha = 0.35f) else bodyIdle
            val full = size.width
            val leftPad = if (side == Side.Left) full * 0.18f else 0f
            val rightPad = if (side == Side.Right) full * 0.18f else 0f
            val x = leftPad
            val w = full - leftPad - rightPad
            // спинка (верхняя треть) и подушка (ниже) — как вид сверху
            drawRoundRect(
                color = body, topLeft = Offset(x, 0f),
                size = Size(w, size.height * 0.30f),
                cornerRadius = CornerRadius(10f, 10f),
            )
            drawRoundRect(
                color = body, topLeft = Offset(x, size.height * 0.36f),
                size = Size(w, size.height * 0.62f),
                cornerRadius = CornerRadius(14f, 14f),
            )
        }
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(6.dp))
            LevelDots(level, onColor)
        }
    }
}

/** Уровень 0..3 точками: заполнены снизу вверх, выключено — все тусклые. */
@Composable
private fun LevelDots(level: Int, onColor: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (i in 1..SEAT_LEVELS) {
            Box(
                Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(
                    if (i <= level) onColor else ElectroColors.TextDisabled.copy(alpha = 0.4f)
                )
            )
        }
    }
}
