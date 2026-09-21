package uz.electro.remote.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import uz.electro.remote.R

/**
 * Иконки в стиле iOS-версии: набор Lucide (ISC), тонкие обводочные глифы —
 * визуально то же, что SF Symbols на iPhone. Имена сохранены от прежних
 * Material-иконок, чтобы замена по экранам была построчной.
 */
object Lx {
    val Warning: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.lx_triangle_alert)
    val Whatshot: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.lx_flame)
    val Delete: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.lx_trash)
    val Notifications: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.lx_bell)
    val LockOpen: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.lx_lock_open)
    val Add: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.lx_plus)
    val AcUnit: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.lx_snowflake)
    val ArrowBack: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.lx_arrow_left)
    val ArrowForward: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.lx_arrow_right)
    val VisibilityOff: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.lx_eye_off)
    val Visibility: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.lx_eye)
    val Schedule: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.lx_clock)
    val Remove: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.lx_minus)
    val RecordVoiceOver: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.lx_audio_lines)
    val MyLocation: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.lx_locate)
    val Map: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.lx_map)
    val Lock: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.lx_lock)
    val ExpandMore: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.lx_chevron_down)
    val ExpandLess: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.lx_chevron_up)
    val CloudOff: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.lx_cloud_off)
    val Close: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.lx_x)
    val Campaign: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.lx_newspaper)
    val Air: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.lx_wind)
    val Shield: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.lx_shield)
    val Settings: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.lx_settings)
    val Refresh: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.lx_refresh_cw)
    val QrCode2: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.lx_qr_code)
    val PowerSettingsNew: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.lx_power)
    val PlayArrow: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.lx_play)
    val Loop: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.lx_refresh_ccw)
    val Info: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.lx_info)
    val Home: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.lx_house)
    val Edit: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.lx_pencil)
    val DirectionsCar: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.lx_car)
    val Directions: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.lx_route)
    val Dashboard: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.lx_layout_grid)
    val CheckCircle: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.lx_circle_check)
    val AirlineSeatReclineNormal: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.lx_car_seat)
    val AddCircleOutline: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.lx_circle_plus)
}
