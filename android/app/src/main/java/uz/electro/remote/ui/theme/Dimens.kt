package uz.electro.remote.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Шаг сетки. До этого отступы были 3/4/6/8/10/12/14/16/20/24 вразнобой. */
object Space {
    val x1 = 4.dp
    val x2 = 8.dp
    val x3 = 12.dp
    val x4 = 16.dp
    val x5 = 20.dp
    val x6 = 24.dp
    val x8 = 32.dp
}

/** Радиусы. Было пять произвольных (10/12/14/16/18) — стало четыре осмысленных. */
object Radius {
    val Sm = RoundedCornerShape(12.dp)
    val Md = RoundedCornerShape(16.dp)
    val Lg = RoundedCornerShape(20.dp)
    val Xl = RoundedCornerShape(28.dp)
    val Pill = RoundedCornerShape(999.dp)
}

/** Высоты контролов — раньше их было пять разных на одну и ту же роль. */
object ControlSize {
    val Tile = 88.dp
    /** Плитка быстрых кнопок на главной: восемь штук должны влезать без прокрутки. */
    val TileCompact = 72.dp
    val Chip = 44.dp
    val Round = 44.dp
    val Button = 48.dp
}
