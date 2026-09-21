package uz.electro.remote.ui

import uz.electro.remote.i18n.S
import uz.electro.remote.ui.components.Lx
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import kotlinx.coroutines.launch
import uz.electro.remote.CarViewModel
import uz.electro.remote.ui.theme.ElectroColors
import uz.electro.remote.ui.theme.Space

internal const val MIN_PASSWORD = 8

/**
 * Регистрация. Учётка общая с сайтом: тот же backend, та же таблица людей —
 * заведённым здесь логином входят и в веб-панель.
 *
 * Машину регистрация не открывает: её даёт только привязка по QR с экрана
 * головы, поэтому сразу после неё человек попадает на экран привязки.
 */
@Composable
fun RegisterScreen(vm: CarViewModel, onRegistered: () -> Unit, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    AuthScaffold(
        title = S("Регистрация"),
        subtitle = S("Один аккаунт для приложения и для сайта."),
        action = if (busy) S("Создаём…") else S("Создать аккаунт"),
        busy = busy,
        enabled = email.isNotBlank() && pass.length >= MIN_PASSWORD && repeat.isNotBlank(),
        onBack = onBack,
        error = error,
        onAction = {
            if (pass != repeat) { error = S("Пароли не совпадают"); return@AuthScaffold }
            busy = true; error = null
            scope.launch {
                vm.register(email.trim(), pass, name.trim(), phone.trim())
                    // Регистрация сразу отдаёт токен — второй раз входить не просим.
                    .onSuccess { onRegistered() }
                    .onFailure { error = vm.authError(it, S("Не получилось создать аккаунт")) }
                busy = false
            }
        },
    ) {
        Field("EMAIL", email, { email = it }, KeyboardType.Email)
        Spacer(Modifier.height(Space.x4))
        Field(S("ИМЯ (необязательно)"), name, { name = it }, KeyboardType.Text)
        Spacer(Modifier.height(Space.x4))
        Field(S("ТЕЛЕФОН (необязательно)"), phone, { phone = it }, KeyboardType.Phone)
        Spacer(Modifier.height(Space.x4))
        Field(S("ПАРОЛЬ"), pass, { pass = it }, KeyboardType.Password,
            visual = if (show) VisualTransformation.None else PasswordVisualTransformation(),
            trailing = {
                IconButton(onClick = { show = !show }) {
                    Icon(if (show) Lx.VisibilityOff else Lx.Visibility,
                        null, tint = ElectroColors.TextSecondary)
                }
            })
        Spacer(Modifier.height(Space.x1))
        Hint(S("Не короче {0} знаков", MIN_PASSWORD))
        Spacer(Modifier.height(Space.x4))
        Field(S("ПАРОЛЬ ЕЩЁ РАЗ"), repeat, { repeat = it }, KeyboardType.Password,
            visual = if (show) VisualTransformation.None else PasswordVisualTransformation())
    }
}
