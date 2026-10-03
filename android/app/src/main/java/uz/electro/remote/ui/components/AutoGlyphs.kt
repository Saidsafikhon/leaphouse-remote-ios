package uz.electro.remote.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Автомобильные HVAC-глифы, которых нет в Material Icons — по образцу авто-набора
 * (Car Equipment Symbols): обдув/обогрев лобового и заднего стекла, обогрев зеркал.
 * Обводочный стиль (round), тонируются через Icon(tint=…), как обычные иконки.
 * viewport 24×24.
 */
private fun autoGlyph(nm: String, body: PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(nm, 24.dp, 24.dp, 24f, 24f).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.7f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = body,
        )
    }.build()

/**
 * Три волнистые стрелки тепла, поднимающиеся вверх ВНУТРИ стекла — как у
 * `windshield.*.and.heat.waves` в SF Symbols на iPhone.
 */
private fun PathBuilder.heatArrows(xs: List<Float>, top: Float, bottom: Float) {
    for (x in xs) {
        val mid = (top + bottom) / 2f
        moveTo(x, bottom)
        curveTo(x - 1.3f, bottom - 1.0f, x + 1.3f, mid + 1.0f, x, mid)
        curveTo(x - 1.3f, mid - 1.0f, x + 1.3f, top + 1.0f, x, top)
        // наконечник стрелки
        moveTo(x - 1.4f, top + 1.4f); lineTo(x, top); lineTo(x + 1.4f, top + 1.4f)
    }
}

/** Обдув/обогрев ЛОБОВОГО — стекло-трапеция, внутри три стрелки тепла. */
val IconWindshieldDefrost: ImageVector by lazy {
    autoGlyph("WindshieldDefrost") {
        moveTo(5f, 5f)
        lineTo(19f, 5f)
        curveTo(20f, 5f, 20.6f, 5.6f, 20.4f, 6.5f)
        lineTo(18.6f, 16.5f)
        curveTo(18.4f, 17.4f, 17.8f, 18f, 16.8f, 18f)
        lineTo(7.2f, 18f)
        curveTo(6.2f, 18f, 5.6f, 17.4f, 5.4f, 16.5f)
        lineTo(3.6f, 6.5f)
        curveTo(3.4f, 5.6f, 4f, 5f, 5f, 5f)
        close()
        heatArrows(listOf(8.5f, 12f, 15.5f), 8.2f, 15.2f)
    }
}

/** Обогрев СТЁКОЛ (заднее) — скруглённый прямоугольник, внутри три стрелки тепла. */
val IconRearDefrost: ImageVector by lazy {
    autoGlyph("RearDefrost") {
        moveTo(6f, 5.5f)
        lineTo(18f, 5.5f)
        curveTo(19.4f, 5.5f, 20.5f, 6.6f, 20.5f, 8f)
        lineTo(20.5f, 15f)
        curveTo(20.5f, 16.4f, 19.4f, 17.5f, 18f, 17.5f)
        lineTo(6f, 17.5f)
        curveTo(4.6f, 17.5f, 3.5f, 16.4f, 3.5f, 15f)
        lineTo(3.5f, 8f)
        curveTo(3.5f, 6.6f, 4.6f, 5.5f, 6f, 5.5f)
        close()
        heatArrows(listOf(8.5f, 12f, 15.5f), 8f, 15f)
    }
}

/** Обогрев боковых ЗЕРКАЛ — корпус зеркала на ножке, внутри стрелки тепла. */
val IconMirrorHeat: ImageVector by lazy {
    autoGlyph("MirrorHeat") {
        moveTo(6.5f, 5f)
        lineTo(17.5f, 5f)
        curveTo(19.5f, 5f, 20.5f, 6f, 20.2f, 8f)
        lineTo(19.2f, 13.5f)
        curveTo(19f, 15f, 18f, 15.8f, 16.5f, 15.8f)
        lineTo(7f, 15.8f)
        curveTo(5.2f, 15.8f, 4f, 14.8f, 4f, 13f)
        lineTo(4f, 7.5f)
        curveTo(4f, 6f, 5f, 5f, 6.5f, 5f)
        close()
        // ножка
        moveTo(11f, 15.8f); lineTo(11f, 19.5f); lineTo(15.5f, 19.5f)
        heatArrows(listOf(8.6f, 12f, 15.4f), 7.4f, 13.4f)
    }
}

/**
 * Боковое стекло двери: передняя кромка сильно скошена до самого низа, задняя
 * прямая — силуэт окна машины, а не «коробка» скачивания.
 */
private fun PathBuilder.sideWindow() {
    moveTo(2.5f, 18.5f)
    lineTo(10.5f, 5f)
    lineTo(19.5f, 5f)
    curveTo(20.3f, 5f, 21f, 5.7f, 21f, 6.5f)
    lineTo(21f, 18.5f)
    close()
}

/** Опустить стёкла — окно со стрелкой вниз. */
val IconWindowDown: ImageVector by lazy {
    autoGlyph("WindowDown") {
        sideWindow()
        moveTo(15.5f, 8.5f); lineTo(15.5f, 15f)
        moveTo(12.9f, 12.4f); lineTo(15.5f, 15f); lineTo(18.1f, 12.4f)
    }
}

/** Поднять стёкла — окно со стрелкой вверх. */
val IconWindowUp: ImageVector by lazy {
    autoGlyph("WindowUp") {
        sideWindow()
        moveTo(15.5f, 15f); lineTo(15.5f, 8.5f)
        moveTo(12.9f, 11.1f); lineTo(15.5f, 8.5f); lineTo(18.1f, 11.1f)
    }
}
