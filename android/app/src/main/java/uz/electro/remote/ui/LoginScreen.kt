package uz.electro.remote.ui

import uz.electro.remote.i18n.S
import uz.electro.remote.ui.components.Lx
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalUriHandler
import uz.electro.remote.data.Settings
import kotlinx.coroutines.launch
import uz.electro.remote.CarViewModel
import uz.electro.remote.ui.components.BadgeKind
import uz.electro.remote.ui.components.ElectroButton
import uz.electro.remote.ui.components.ElectroToast
import uz.electro.remote.ui.components.BrandLockup
import uz.electro.remote.ui.theme.*

/** Экран входа. Отсюда же уходят на регистрацию и восстановление пароля. */
@Composable
fun LoginScreen(
    vm: CarViewModel,
    onLoggedIn: () -> Unit,
    onRegister: () -> Unit,
    onForgot: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    // Почту помним между входами: меняется она куда реже, чем случается
    // повторный вход, а набирать её на телефоне долго.
    var email by remember { mutableStateOf(vm.settings.email.orEmpty()) }
    var pass by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    // Помощь (контакты поддержки) доступна и до входа
    val support by vm.support.collectAsState()
    var showHelp by remember { mutableStateOf(false) }
    if (showHelp) HelpDialog(support) { showHelp = false }
    // Новости «для всех» видны и до входа
    val news by vm.news.collectAsState()
    val newsRead by vm.newsRead.collectAsState()
    val unreadNews by vm.unreadNews.collectAsState()
    var showNews by remember { mutableStateOf(false) }
    var showShop by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.loadNews() }
    if (showShop) {
        LaunchedEffect(Unit) { vm.loadProducts() }
        val products by vm.products.collectAsState()
        androidx.compose.material3.Surface(color = ElectroColors.Background, modifier = Modifier.fillMaxSize()) {
            ShopScreen(products, loggedIn = false, phoneHint = "", model = null, onRefresh = { vm.loadProducts() },
                        onOrder = { id, qty, phone, comment, done -> vm.order(id, qty, phone, comment, done) }, onBack = { showShop = false })
        }
        return
    }
    if (showNews) {
        androidx.compose.material3.Surface(color = ElectroColors.Background, modifier = Modifier.fillMaxSize()) {
            NewsScreen(news, newsRead, onRefresh = { vm.loadNews(force = true) }, onRead = { vm.markNewsRead(it.id) },
                onReadAll = { vm.markAllNewsRead() }, onBack = { showNews = false })
        }
        return
    }

    Column(
        Modifier.fillMaxSize().background(ElectroColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.x6),
    ) {
        Spacer(Modifier.height(Space.x8))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            uz.electro.remote.ui.components.LangPicker(compact = true)
            Spacer(Modifier.width(Space.x2))
            ShopFab(onClick = { showShop = true })
            Spacer(Modifier.width(Space.x2))
            NewsBell(unreadNews, onClick = { showNews = true })
            Spacer(Modifier.width(Space.x2))
            HelpFab(onClick = { showHelp = true })
            Spacer(Modifier.width(Space.x2))
            SettingsFab(onClick = { showSettings = true })
        }
        if (showSettings) PreLoginSettingsSheet(onClose = { showSettings = false })
        Spacer(Modifier.height(Space.x6))

        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            BrandLockup(markSize = 44.dp)
        }
        Spacer(Modifier.height(Space.x1))
        Text(S("Управление вашим электромобилем"), color = ElectroColors.TextSecondary,
            fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())

        Spacer(Modifier.height(Space.x8))

        Field("EMAIL", email, { email = it }, KeyboardType.Email)
        Spacer(Modifier.height(Space.x4))
        Field(S("ПАРОЛЬ"), pass, { pass = it }, KeyboardType.Password,
            visual = if (show) VisualTransformation.None else PasswordVisualTransformation(),
            trailing = {
                IconButton(onClick = { show = !show }) {
                    Icon(if (show) Lx.VisibilityOff else Lx.Visibility,
                        null, tint = ElectroColors.TextSecondary)
                }
            })

        if (error != null) {
            Spacer(Modifier.height(Space.x3))
            ElectroToast(BadgeKind.Failed, error!!, null)
        }

        Spacer(Modifier.height(Space.x4))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(S("Забыли пароль?"), color = ElectroColors.TextSecondary, fontSize = 14.sp,
                modifier = Modifier.clickable(enabled = !busy) { onForgot() }
                    .padding(vertical = Space.x1))
            Spacer(Modifier.weight(1f))
            Text(S("Регистрация"), color = ElectroColors.Accent, fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(enabled = !busy) { onRegister() }
                    .padding(vertical = Space.x1))
        }

        Spacer(Modifier.height(Space.x8))

        ElectroButton(
            text = if (busy) S("Входим…") else S("Войти"),
            enabled = email.isNotBlank() && pass.isNotBlank(),
            loading = busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            busy = true; error = null
            scope.launch {
                vm.signIn(email.trim(), pass)
                    .onSuccess { onLoggedIn() }
                    .onFailure { error = vm.authError(it, S("Неверный email или пароль")) }
                busy = false
            }
        }

        Spacer(Modifier.height(Space.x6))
        Text(S("Политика конфиденциальности"), style = ElectroType.Caption, color = ElectroColors.TextMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().clickable { runCatching { uriHandler.openUri(Settings.PRIVACY_URL) } })
        Spacer(Modifier.height(Space.x4))
    }
}
