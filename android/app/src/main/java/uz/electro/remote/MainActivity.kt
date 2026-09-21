package uz.electro.remote

import uz.electro.remote.i18n.S
import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import uz.electro.remote.push.Push
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import uz.electro.remote.security.AppLock
import uz.electro.remote.ui.LockScreen
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import uz.electro.remote.ui.ForgotPasswordScreen
import uz.electro.remote.ui.LoginScreen
import uz.electro.remote.ui.PairScreen
import uz.electro.remote.ui.ParkGateScreen
import uz.electro.remote.ui.PhoneControlScreen
import uz.electro.remote.ui.RegisterScreen
import uz.electro.remote.ui.theme.ElectroTheme

class MainActivity : FragmentActivity() {

    /** Заблокировано ли приложение кодом; снимается кодом или биометрией. */
    private val locked = mutableStateOf(false)

    /** Где мы до того, как попали в приложение: вход, его ответвления и привязка. */
    private enum class Gate { LOGIN, REGISTER, FORGOT, LOADING, PAIR, READY }

    private val askNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private var newsReceiver: BroadcastReceiver? = null

    /** Действие из intent-а запуска; исполняется, когда пользователь вошёл. */
    private val pendingAction = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        VoiceActions.fromIntent(intent)?.let { pendingAction.value = it }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        VoiceActions.fromIntent(intent)?.let { pendingAction.value = it }
        SignatureGuard.enforce(this)
        uz.electro.remote.ui.theme.ThemePref.load(this)
        uz.electro.remote.i18n.Lang.load(this)
        Push.ensureChannel(this)
        if (Build.VERSION.SDK_INT >= 33 && !Push.canPost(this)) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        Push.register(this)
        setContent {
            ElectroTheme {
                val vm: CarViewModel = viewModel()
                // «Окей Google» / ярлык / ссылка leapremote://action/… — выполнить, как только вошли
                val voice by pendingAction.collectAsState()
                val loggedInNow by vm.loggedIn.collectAsState()
                androidx.compose.runtime.LaunchedEffect(voice, loggedInNow) {
                    val a = voice ?: return@LaunchedEffect
                    if (!loggedInNow) return@LaunchedEffect
                    pendingAction.value = null
                    vm.quickAction(a)
                }
                // пришёл push — перечитать ленту, пока приложение открыто
                androidx.compose.runtime.DisposableEffect(Unit) {
                    val r = object : BroadcastReceiver() {
                        override fun onReceive(c: Context?, i: Intent?) { vm.loadNews() }
                    }
                    if (Build.VERSION.SDK_INT >= 33) registerReceiver(r, IntentFilter(Push.ACTION_NEWS), Context.RECEIVER_NOT_EXPORTED)
                    else registerReceiver(r, IntentFilter(Push.ACTION_NEWS))
                    newsReceiver = r
                    onDispose { runCatching { unregisterReceiver(r) } }
                }
                val loggedIn by vm.loggedIn.collectAsState()
                val vehicles by vm.vehicles.collectAsState()
                val parkKnown by vm.parkKnown.collectAsState()
                val parkNote by vm.parkNote.collectAsState()

                var branch by remember { mutableStateOf<Gate?>(null) }

                // Ворота считаем из состояния, а не храним отдельно: сессия живёт
                // в настройках и переживает перезапуск, и держать рядом второй
                // источник правды — способ разойтись с ним.
                val gate = when {
                    !loggedIn -> branch.takeIf { it == Gate.REGISTER || it == Gate.FORGOT }
                        ?: Gate.LOGIN
                    // Пустой список до ответа сервера ещё ничего не значит —
                    // иначе «Подключите машину» мелькает на каждом входе.
                    !parkKnown -> Gate.LOADING
                    // Управление и побудка доступны только тем, у кого есть
                    // привязанная машина: регистрация доступа к машине не даёт.
                    vehicles.isEmpty() -> Gate.PAIR
                    else -> Gate.READY
                }

                // Выйдя из ответвления, не оставляем его висеть на следующий вход.
                LaunchedEffect(loggedIn) { if (loggedIn) branch = null }

                // Защита входа: код/биометрия при запуске и при каждом возврате
                // из фона. Только для вошедшего — экран логина сам себя защищает.
                val lock = remember { AppLock(this@MainActivity) }
                // если на телефоне сняли блокировку экрана — запирать нечем, не запираем
                val lockActive = lock.enabled && remember { lock.available() }
                val owner = LocalLifecycleOwner.current
                DisposableEffect(owner, loggedIn) {
                    val obs = LifecycleEventObserver { _, e ->
                        if (e == Lifecycle.Event.ON_STOP && loggedIn && lockActive) locked.value = true
                    }
                    if (loggedIn && lockActive) locked.value = true
                    owner.lifecycle.addObserver(obs)
                    onDispose { owner.lifecycle.removeObserver(obs) }
                }
                // После входа один раз предлагаем включить блокировку телефона
                // для входа; «Не сейчас» — больше не спрашиваем, есть в настройках.
                var offer by remember { mutableStateOf(false) }
                var wasLoggedIn by remember { mutableStateOf(loggedIn) }
                LaunchedEffect(loggedIn) {
                    if (loggedIn && !wasLoggedIn && !lock.enabled && !lock.offerDeclined && lock.available()) offer = true
                    wasLoggedIn = loggedIn
                }
                if (offer) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { offer = false; lock.offerDeclined = true },
                        containerColor = uz.electro.remote.ui.theme.ElectroColors.SurfaceElevated, tonalElevation = 0.dp,
                        title = { androidx.compose.material3.Text(S("Защитить вход?"), color = uz.electro.remote.ui.theme.ElectroColors.TextPrimary) },
                        text = { androidx.compose.material3.Text(S("При запуске и возврате в приложение будет запрашиваться блокировка телефона: отпечаток, лицо или код экрана. Можно включить позже в настройках."),
                            color = uz.electro.remote.ui.theme.ElectroColors.TextSecondary) },
                        confirmButton = { androidx.compose.material3.TextButton(onClick = {
                            offer = false
                            // включаем только после успешного подтверждения — иначе можно запереть самого себя
                            lock.prompt(this@MainActivity) { ok -> if (ok) lock.enabled = true else lock.offerDeclined = true }
                        }) { androidx.compose.material3.Text(S("Включить"), color = uz.electro.remote.ui.theme.ElectroColors.Accent) } },
                        dismissButton = { androidx.compose.material3.TextButton(onClick = { offer = false; lock.offerDeclined = true }) {
                            androidx.compose.material3.Text(S("Не сейчас"), color = uz.electro.remote.ui.theme.ElectroColors.TextSecondary) } },
                    )
                }

                if (locked.value && loggedIn && lockActive) {
                    LockScreen(
                        onPrompt = { cb -> lock.prompt(this@MainActivity, cb) },
                        onUnlocked = { locked.value = false },
                    )
                    return@ElectroTheme
                }

                when (gate) {
                    Gate.LOGIN -> LoginScreen(
                        vm = vm,
                        onLoggedIn = { branch = null },
                        onRegister = { branch = Gate.REGISTER },
                        onForgot = { branch = Gate.FORGOT },
                    )

                    Gate.REGISTER -> RegisterScreen(
                        vm = vm,
                        onRegistered = { branch = null },
                        onBack = { branch = null },
                    )

                    Gate.FORGOT -> ForgotPasswordScreen(vm = vm, onBack = { branch = null })

                    // Причину показываем только когда она уже есть: до первого
                    // ответа сервера это просто ожидание, а не отказ.
                    Gate.LOADING -> ParkGateScreen(
                        note = parkNote,
                        onRetry = { vm.loadVehicles() },
                        onLogout = { vm.logout() },
                    )

                    Gate.PAIR -> PairScreen(vm = vm, onPaired = { branch = null })

                    Gate.READY -> PhoneControlScreen(vm)
                }
            }
        }
    }
}
