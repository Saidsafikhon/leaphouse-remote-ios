package uz.electro.remote.ui

import uz.electro.remote.i18n.S
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import uz.electro.remote.ui.components.ButtonStyle
import uz.electro.remote.ui.components.ElectroButton
import uz.electro.remote.ui.theme.ElectroColors
import uz.electro.remote.ui.theme.Space

/**
 * Ожидание списка машин.
 *
 * Пока сервер не ответил, неизвестно, есть ли у аккаунта машина, и показывать
 * «Подключите машину» рано. Но и крутить спиннер бесконечно нельзя: сервер
 * может быть недоступен — тогда человек должен видеть причину и кнопку, а не
 * гадать, завис ли телефон.
 */
@Composable
fun ParkGateScreen(note: String?, onRetry: () -> Unit, onLogout: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(ElectroColors.Background).padding(Space.x6),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Ветки только через if/else: ранний return из composable-лямбды
        // выпрыгивает мимо закрытия группы Compose, и композиция падает с
        // «Index -1 out of bounds» на первом же показе этого экрана.
        if (note == null) {
            CircularProgressIndicator(color = ElectroColors.Accent)
            Spacer(Modifier.height(Space.x4))
            Text(S("Спрашиваем сервер о ваших машинах…"),
                color = ElectroColors.TextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center)
        } else {
            Text(note, color = ElectroColors.TextPrimary, fontSize = 15.sp,
                textAlign = TextAlign.Center)
            Spacer(Modifier.height(Space.x6))
            ElectroButton(S("Повторить"), modifier = Modifier.fillMaxWidth(), onClick = onRetry)
            Spacer(Modifier.height(Space.x2))
            ElectroButton(S("Выйти из аккаунта"), style = ButtonStyle.Ghost,
                modifier = Modifier.fillMaxWidth(), onClick = onLogout)
        }
    }
}
