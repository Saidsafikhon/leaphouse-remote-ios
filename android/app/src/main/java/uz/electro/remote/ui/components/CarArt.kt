package uz.electro.remote.ui.components

import androidx.compose.ui.graphics.Color
import uz.electro.remote.R

/**
 * Рендеры машин по модели и цвету кузова — студийные вырезки с прозрачным фоном
 * (конфигуратор leapmotor.uz, `drawable-nodpi/car_<model>_<цвет>.webp`).
 *
 * Модель приходит с сервера (`VehicleDto.model`: C16, C10, …), цвет — локальная
 * настройка телефона: сервер цвет кузова не знает, а перекрашивать машину у всех
 * водителей ради одного было бы неверно.
 */
object CarArt {
    /** Цвет кузова: код как на сайте, подпись для настроек и образец для кружка. */
    class Paint(val code: String, val label: String, val swatch: Color)

    private val PAINTS = mapOf(
        "pearl-white" to Paint("pearl-white", "Жемчужно-белый", Color(0xFFE9EAEC)),
        "metallic-black" to Paint("metallic-black", "Чёрный металлик", Color(0xFF1B1D21)),
        "canopy-gray" to Paint("canopy-gray", "Серый", Color(0xFF8E9296)),
        "terra-grey" to Paint("terra-grey", "Терра-серый", Color(0xFF6A6D70)),
        "jade-green" to Paint("jade-green", "Нефритовый зелёный", Color(0xFF2E5B45)),
        "galaxy-silver" to Paint("galaxy-silver", "Серебристый", Color(0xFFB8BCC2)),
        "glacier-blue" to Paint("glacier-blue", "Ледниковый голубой", Color(0xFFB6CDE0)),
        "walden-green" to Paint("walden-green", "Тёмно-зелёный", Color(0xFF2F4A3C)),
        "seaweed-green" to Paint("seaweed-green", "Зелёный", Color(0xFF3D6B4A)),
    )

    /** Какие цвета есть у модели — ровно те, на которые есть рендер. */
    private val ART: Map<String, Map<String, Int>> = mapOf(
        "C16" to mapOf(
            "pearl-white" to R.drawable.car_c16_pearl_white,
            "metallic-black" to R.drawable.car_c16_metallic_black,
            "canopy-gray" to R.drawable.car_c16_canopy_gray,
            "jade-green" to R.drawable.car_c16_jade_green,
            "glacier-blue" to R.drawable.car_c16_glacier_blue,
        ),
        "C10" to mapOf(
            "pearl-white" to R.drawable.car_c10_pearl_white,
            "metallic-black" to R.drawable.car_c10_metallic_black,
            "canopy-gray" to R.drawable.car_c10_canopy_gray,
            "terra-grey" to R.drawable.car_c10_terra_grey,
            "jade-green" to R.drawable.car_c10_jade_green,
        ),
        "C11" to mapOf(
            "pearl-white" to R.drawable.car_c11_pearl_white,
            "metallic-black" to R.drawable.car_c11_metallic_black,
            "canopy-gray" to R.drawable.car_c11_canopy_gray,
            "galaxy-silver" to R.drawable.car_c11_galaxy_silver,
            "walden-green" to R.drawable.car_c11_walden_green,
        ),
        "C01" to mapOf(
            "metallic-black" to R.drawable.car_c01_metallic_black,
            "galaxy-silver" to R.drawable.car_c01_galaxy_silver,
            "walden-green" to R.drawable.car_c01_walden_green,
        ),
        "B10" to mapOf(
            "pearl-white" to R.drawable.car_b10_pearl_white,
            "metallic-black" to R.drawable.car_b10_metallic_black,
            "terra-grey" to R.drawable.car_b10_terra_grey,
            "glacier-blue" to R.drawable.car_b10_glacier_blue,
        ),
        "A10" to mapOf("seaweed-green" to R.drawable.car_a10_seaweed_green),
        "D19" to mapOf("metallic-black" to R.drawable.car_d19_metallic_black),
    )

    /** Нормализуем «Leapmotor C16», «c16 2025», «C16» → «C16». */
    private fun key(model: String?): String {
        val m = (model ?: "").uppercase()
        return ART.keys.firstOrNull { m.contains(it) } ?: "C16"
    }

    fun paints(model: String?): List<Paint> =
        ART.getValue(key(model)).keys.mapNotNull { PAINTS[it] }

    /** Рендер модели в выбранном цвете; нет такого цвета — первый доступный. */
    fun image(model: String?, paint: String?): Int {
        val colors = ART.getValue(key(model))
        return colors[paint] ?: colors["pearl-white"] ?: colors.values.first()
    }
}
