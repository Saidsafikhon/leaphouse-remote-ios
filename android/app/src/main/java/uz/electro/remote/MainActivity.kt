package uz.electro.remote

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SignatureGuard.enforce(this)
        Push.ensureChannel(this)
        if (Build.VERSION.SDK_INT >= 33 && !Push.canPost(this)) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        Push.register(this)
        setContent {
            ElectroTheme {
                val vm: CarViewModel = viewModel()
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
                val owner = LocalLifecycleOwner.current
                DisposableEffect(owner, loggedIn) {
                    val obs = LifecycleEventObserver { _, e ->
                        if (e == Lifecycle.Event.ON_STOP && loggedIn && lock.enabled) locked.value = true
                    }
                    if (loggedIn && lock.enabled) locked.value = true
                    owner.lifecycle.addObserver(obs)
                    onDispose { owner.lifecycle.removeObserver(obs) }
                }
                if (locked.value && loggedIn && lock.enabled) {
                    LockScreen(
                        lock = lock,
                        onBiometric = { cb -> lock.promptBiometric(this@MainActivity, cb) },
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
