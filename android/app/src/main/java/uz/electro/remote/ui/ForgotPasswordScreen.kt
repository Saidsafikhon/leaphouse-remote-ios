package uz.electro.remote.ui

import uz.electro.remote.i18n.S
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.launch
import uz.electro.remote.CarViewModel
import uz.electro.remote.ui.theme.Space

/**
 * «Забыли пароль». Писем сервер не шлёт — почтового канала у установки нет,
 * поэтому заявка уходит в админку: мастер выдаёт новый пароль и сообщает его
 * по оставленному контакту.
 *
 * Ответ сервера одинаков для заведённой и незаведённой почты, поэтому экран
 * не говорит «такого аккаунта нет»: иначе форма работает проверкой чужих
 * адресов.
 */
@Composable
fun ForgotPasswordScreen(vm: CarViewModel, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf(vm.settings.email.orEmpty()) }
    var contact by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var sent by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    AuthScaffold(
        title = S("Забыли пароль"),
        subtitle = S("Заявка уйдёт мастеру: он выдаст новый пароль и сообщит его вам."),
        action = when {
            busy -> S("Отправляем…")
            sent -> S("Вернуться ко входу")
            else -> S("Отправить заявку")
        },
        busy = busy,
        enabled = sent || email.isNotBlank(),
        onBack = onBack,
        error = error,
        note = if (sent) {
            S("Заявка принята. Мастер сбросит пароль и сообщит новый — если такая учётная запись существует.")
        } else null,
        onAction = {
            if (sent) { onBack(); return@AuthScaffold }
            busy = true; error = null
            scope.launch {
                vm.requestPasswordReset(email.trim(), contact.trim())
                    .onSuccess { sent = true }
                    .onFailure { error = vm.authError(it, S("Не получилось отправить заявку")) }
                busy = false
            }
        },
    ) {
        Field("EMAIL", email, { email = it }, KeyboardType.Email)
        Spacer(Modifier.height(Space.x4))
        Field(S("ТЕЛЕФОН ИЛИ TELEGRAM"), contact, { contact = it }, KeyboardType.Text)
        Spacer(Modifier.height(Space.x1))
        Hint(S("Куда сообщить новый пароль. Необязательно, но так быстрее."))
    }
}
