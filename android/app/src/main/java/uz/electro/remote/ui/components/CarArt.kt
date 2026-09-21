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
        "jade-green" to Paint("jade-green", "Глазурно-зелёный", Color(0xFF2E5B45)),
        "galaxy-silver" to Paint("galaxy-silver", "Серебристый", Color(0xFFB8BCC2)),
        "glacier-blue" to Paint("glacier-blue", "Ледниковый голубой", Color(0xFFB6CDE0)),
        "walden-green" to Paint("walden-green", "Тёмно-зелёный", Color(0xFF2F4A3C)),
        "seaweed-green" to Paint("seaweed-green", "Зелёный", Color(0xFF3D6B4A)),
        "light-white" to Paint("light-white", "Белый", Color(0xFFF2F3F5)),
        "sakura-pink" to Paint("sakura-pink", "Сакура розовый", Color(0xFFE8C4C8)),
        "acorn-brown" to Paint("acorn-brown", "Ореховый коричневый", Color(0xFF6E5A4E)),
        "berry-blue" to Paint("berry-blue", "Ягодный синий", Color(0xFF3E5A8A)),
        "star-purple" to Paint("star-purple", "Звёздный фиолетовый", Color(0xFF5A4A6E)),
        "tundra-grey" to Paint("tundra-grey", "Тундра-серый", Color(0xFFC9C6C0)),
        "sky-grey" to Paint("sky-grey", "Небесно-серый", Color(0xFF4C5157)),
        "dawn-purple" to Paint("dawn-purple", "Рассветный фиолетовый", Color(0xFF4A3550)),
        "starry-night-blue" to Paint("starry-night-blue", "Звёздная ночь (синий)", Color(0xFF4A5F85)),
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
            "tundra-grey" to R.drawable.car_c10_tundra_grey,
            "jade-green" to R.drawable.car_c10_jade_green,
        ),
        "C11" to mapOf(
            "pearl-white" to R.drawable.car_c11_pearl_white,
            "metallic-black" to R.drawable.car_c11_metallic_black,
            "canopy-gray" to R.drawable.car_c11_canopy_gray,
            "galaxy-silver" to R.drawable.car_c11_galaxy_silver,
            "walden-green" to R.drawable.car_c11_walden_green,
            "light-white" to R.drawable.car_c11_light_white,
        ),
        "C01" to mapOf(
            "metallic-black" to R.drawable.car_c01_metallic_black,
            "galaxy-silver" to R.drawable.car_c01_galaxy_silver,
            "walden-green" to R.drawable.car_c01_walden_green,
        ),
        "B10" to mapOf(
            "pearl-white" to R.drawable.car_b10_pearl_white,
            "metallic-black" to R.drawable.car_b10_metallic_black,
            "tundra-grey" to R.drawable.car_b10_tundra_grey,
            "galaxy-silver" to R.drawable.car_b10_galaxy_silver,
            "starry-night-blue" to R.drawable.car_b10_starry_night_blue,
            "dawn-purple" to R.drawable.car_b10_dawn_purple,
            "sakura-pink" to R.drawable.car_b10_sakura_pink,
        ),
        "A10" to mapOf(
            "seaweed-green" to R.drawable.car_a10_seaweed_green,
            "galaxy-silver" to R.drawable.car_a10_galaxy_silver,
            "tundra-grey" to R.drawable.car_a10_tundra_grey,
            "berry-blue" to R.drawable.car_a10_berry_blue,
            "acorn-brown" to R.drawable.car_a10_acorn_brown,
            "star-purple" to R.drawable.car_a10_star_purple,
        ),
        "D19" to mapOf(
            "metallic-black" to R.drawable.car_d19_metallic_black,
            "pearl-white" to R.drawable.car_d19_pearl_white,
            "sky-grey" to R.drawable.car_d19_sky_grey,
            "jade-green" to R.drawable.car_d19_jade_green,
        ),
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
