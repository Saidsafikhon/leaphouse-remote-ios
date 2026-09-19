package uz.electro.remote.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import uz.electro.remote.CarViewModel
import uz.electro.remote.ConnectPhase
import uz.electro.remote.ConnectStatus
import uz.electro.remote.R
import uz.electro.remote.ui.components.*
import uz.electro.remote.ui.components.BrandLockup
import uz.electro.remote.ui.components.CarArt
import uz.electro.remote.ui.theme.*

/**
 * Экран подключения — первое, что видно при запуске.
 *
 * Приложение не открывается само: T-BOX держит модем в спящем режиме, поэтому
 * по кнопке машину сперва будят через сервер — и только когда голова поднялась
 * и ответила, пускаем внутрь. Иначе первые нажатия уходили бы в пустоту.
 */
@Composable
fun ConnectScreen(vm: CarViewModel, status: ConnectStatus) {
    var showSettings by remember { mutableStateOf(false) }
    var showNews by remember { mutableStateOf(false) }
    val news by vm.news.collectAsState()
    val newsRead by vm.newsRead.collectAsState()
    val unreadNews by vm.unreadNews.collectAsState()

    // Путь к машине один — сервер, поэтому никаких разрешений спрашивать не за
    // что: SEND_SMS убран вместе с запасным каналом.
    fun start() {
        if (vm.wakeConfigured) vm.connect() else showSettings = true
    }

    // Настройки открываются полноэкранно ВМЕСТО экрана подключения. Раньше их
    // рисовали перед Column подключения — и Column перекрывал их сверху, отчего
    // кнопка «Настройки» будто «не реагировала» (экран открывался, но был не
    // виден). Здесь настройки — единственное, что рисуется, пока они открыты.
    if (showSettings) {
        androidx.compose.material3.Surface(
            color = ElectroColors.Background,
            modifier = Modifier.fillMaxSize(),
        ) { SettingsScreen(vm) { showSettings = false } }
        return
    }
    if (showNews) {
        LaunchedEffect(Unit) { vm.loadNews() }
        androidx.compose.material3.Surface(color = ElectroColors.Background, modifier = Modifier.fillMaxSize()) {
            NewsScreen(news, newsRead, onRead = { vm.markNewsRead(it.id) },
                onReadAll = { vm.markAllNewsRead() }, onBack = { showNews = false })
        }
        return
    }

    val chosen by vm.selectedVehicle.collectAsState()
    val support by vm.support.collectAsState()
    var showHelp by remember { mutableStateOf(false) }
    val loggedInForFeedback by vm.loggedIn.collectAsState()
    var showFeedback by remember { mutableStateOf(false) }
    // отзыв — только под аккаунтом: без ключа серверу его не принять
    if (showHelp) HelpDialog(support, onFeedback = if (loggedInForFeedback) ({ showFeedback = true }) else null) { showHelp = false }
    if (showFeedback) FeedbackDialog(
        onSend = { kind, text, files, done -> vm.sendFeedback(kind, text, files, done) },
        onDismiss = { showFeedback = false },
    )

    Column(
        Modifier.fillMaxSize().background(ElectroColors.Background)
            .padding(horizontal = Space.x5).padding(top = Space.x6, bottom = Space.x5),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Помощь доступна и до подключения — кнопка в правом верхнем углу.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            NewsBell(unreadNews, onClick = { showNews = true })
            Spacer(Modifier.width(Space.x2))
            HelpFab(onClick = { showHelp = true })
        }
        Spacer(Modifier.weight(1f))

        // тот же лозунг, что в шапке главного экрана: марка мелко, модель крупно
        BrandLockup(markSize = 26.dp)
        Spacer(Modifier.height(Space.x2))
        // Модель — выбранной машины: с несколькими машинами в парке подпись
        // «C16» над C01 вводила бы в заблуждение ровно там, где это опасно.
        Text(
            chosen?.model ?: "C16",
            style = ElectroType.Display,
            color = ElectroColors.TextPrimary,
        )
        chosen?.name?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(Space.x1))
            Text(it, style = ElectroType.Body, color = ElectroColors.TextMuted)
        }
        Spacer(Modifier.height(Space.x6))

        // Рендер — студийная вырезка с прозрачным фоном по модели и цвету кузова,
        // лежит на обычной карточке темы: никакой «вшитой» тёмной подложки.
        val paint by vm.paint.collectAsState()
        Surface(
            color = ElectroColors.SurfaceRaised,
            shape = Radius.Lg,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box {
                Image(
                    painterResource(CarArt.image(chosen?.model, paint)), null,
                    modifier = Modifier.fillMaxWidth().height(200.dp).padding(horizontal = Space.x2, vertical = Space.x2),
                    contentScale = ContentScale.Fit,
                )
                // статус подключения плашкой в углу hero — как на панели машины
                Box(Modifier.fillMaxWidth().padding(Space.x3), contentAlignment = Alignment.TopEnd) {
                    PhaseBadge(status)
                }
            }
        }

        Spacer(Modifier.height(Space.x5))
        val h = hint(status, vm.wakeConfigured)
        val failed = status.phase == ConnectPhase.Timeout || status.phase == ConnectPhase.Error
        Text(
            h,
            style = ElectroType.Body,
            color = if (failed) ElectroColors.Danger else ElectroColors.TextSecondary,
            textAlign = TextAlign.Center,
        )

        if (status.phase == ConnectPhase.Waiting) {
            Spacer(Modifier.height(Space.x4))
            LinearProgressIndicator(
                Modifier.fillMaxWidth().clip(Radius.Pill),
                color = ElectroColors.Accent,
                trackColor = ElectroColors.SurfaceElevated,
            )
        }

        Spacer(Modifier.weight(1f))

        when (status.phase) {
            ConnectPhase.Idle -> {
                ElectroButton("Подключиться", Modifier.fillMaxWidth()) { start() }
                Spacer(Modifier.height(Space.x2))
                ElectroButton(
                    "Найти машину", Modifier.fillMaxWidth(),
                    style = ButtonStyle.Secondary,
                ) { vm.findCar() }
            }

            ConnectPhase.Sending, ConnectPhase.Waiting -> {
                ElectroButton("Подключение", Modifier.fillMaxWidth(), loading = true) {}
            }

            ConnectPhase.Timeout -> {
                ElectroButton("Разбудить ещё раз", Modifier.fillMaxWidth()) { start() }
                Spacer(Modifier.height(Space.x2))
                ElectroButton(
                    "Всё равно открыть", Modifier.fillMaxWidth(),
                    style = ButtonStyle.Secondary,
                ) { vm.enterAnyway() }
            }

            ConnectPhase.Error -> {
                ElectroButton("Повторить", Modifier.fillMaxWidth()) { start() }
                Spacer(Modifier.height(Space.x2))
                ElectroButton(
                    "Всё равно открыть", Modifier.fillMaxWidth(),
                    style = ButtonStyle.Ghost,
                ) { vm.enterAnyway() }
            }

            ConnectPhase.Connected -> Unit
        }

        Spacer(Modifier.height(Space.x3))
        ElectroButton(
            if (vm.wakeConfigured) "Настройки" else "Настроить подключение",
            Modifier.fillMaxWidth(),
            style = ButtonStyle.Ghost,
        ) { showSettings = true }
        Spacer(Modifier.height(Space.x3))
        Text(appVersion(), style = ElectroType.Caption, color = ElectroColors.TextMuted)
        Spacer(Modifier.height(Space.x3))
    }
}

@Composable
private fun PhaseBadge(status: ConnectStatus) {
    when (status.phase) {
        ConnectPhase.Idle -> StatusBadge(BadgeKind.Offline, "СПИТ")
        ConnectPhase.Sending, ConnectPhase.Waiting -> StatusBadge(BadgeKind.Info, "ПОДКЛЮЧЕНИЕ")
        ConnectPhase.Timeout -> StatusBadge(BadgeKind.Failed, "ОШИБКА")
        ConnectPhase.Error -> StatusBadge(BadgeKind.Failed, "ОШИБКА")
        ConnectPhase.Connected -> StatusBadge(BadgeKind.Online, "НА СВЯЗИ")
    }
}

/** Коротко: без объяснений, что происходит под капотом — только статус. */
private fun hint(status: ConnectStatus, configured: Boolean): String = when {
    !configured && status.phase == ConnectPhase.Idle -> "Войдите на сервер в настройках."
    status.phase == ConnectPhase.Sending || status.phase == ConnectPhase.Waiting -> "Подключение…"
    status.phase == ConnectPhase.Timeout || status.phase == ConnectPhase.Error -> "Ошибка: подключение не удалось"
    else -> ""
}
