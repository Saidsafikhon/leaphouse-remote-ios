package uz.electro.remote.ui

import uz.electro.remote.ui.components.Lx
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch
import uz.electro.remote.CarViewModel
import uz.electro.remote.ui.components.BadgeKind
import uz.electro.remote.ui.components.ButtonStyle
import uz.electro.remote.ui.components.ElectroButton
import uz.electro.remote.ui.components.ElectroToast
import uz.electro.remote.ui.theme.*

/**
 * Экран-ворота: пока у аккаунта нет машины, доступен только сканер QR.
 *
 * Регистрация доступа к машине не даёт — его открывает физический доступ к
 * экрану головы, где показан одноразовый код.
 */
@Composable
fun PairScreen(vm: CarViewModel, onPaired: () -> Unit) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val payload = result.contents ?: return@rememberLauncherForActivityResult
        busy = true; error = null; note = null
        scope.launch {
            vm.claimPairing(payload)
                .onSuccess { note = it; onPaired() }
                .onFailure { error = it.message ?: "Не удалось привязать машину" }
            busy = false
        }
    }

    Column(Modifier.fillMaxSize().background(ElectroColors.Background).padding(Space.x6)) {
        Text("Подключение авто", color = ElectroColors.TextPrimary, fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = Space.x2))

        Spacer(Modifier.weight(1f))

        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(132.dp).clip(Radius.Xl)
                    .background(ElectroColors.Surface)
                    .border(2.dp, ElectroColors.Accent, Radius.Xl),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Lx.QrCode2, null, tint = ElectroColors.Accent,
                    modifier = Modifier.size(72.dp))
            }

            Spacer(Modifier.height(Space.x6))
            Text("Подключите машину", color = ElectroColors.TextPrimary, fontSize = 22.sp,
                fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(Space.x2))
            Text("Откройте на экране машины «Привязать телефон» и отсканируйте QR",
                color = ElectroColors.TextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center)
        }

        if (error != null) {
            Spacer(Modifier.height(Space.x4))
            ElectroToast(BadgeKind.Failed, error!!, null)
        }
        if (note != null) {
            Spacer(Modifier.height(Space.x4))
            ElectroToast(BadgeKind.Success, note!!, null)
        }

        Spacer(Modifier.weight(1.4f))

        ElectroButton(
            text = if (busy) "Привязываем…" else "Сканировать QR машины",
            enabled = !busy,
            loading = busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            scanLauncher.launch(
                ScanOptions().setPrompt("Наведите на QR на экране машины")
                    .setBeepEnabled(false).setOrientationLocked(false)
            )
        }
        // Выход отсюда же: зашли не в тот аккаунт или QR пока негде взять —
        // иначе с этого экрана некуда деться, кроме как переустанавливать.
        Spacer(Modifier.height(Space.x2))
        ElectroButton(
            text = "Выйти из аккаунта",
            style = ButtonStyle.Ghost,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) { vm.logout() }
    }
}
