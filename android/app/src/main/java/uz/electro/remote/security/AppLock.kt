package uz.electro.remote.security

import uz.electro.remote.i18n.S
import android.app.KeyguardManager
import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Защита входа в приложение системной блокировкой телефона: отпечаток / лицо,
 * а запасной путь — PIN, рисунок или пароль экрана блокировки. Своего кода
 * нет: хранить и проверять нечего, всё делает система (`BiometricPrompt` с
 * `DEVICE_CREDENTIAL`, на старых Android — `KeyguardManager`).
 */
class AppLock(ctx: Context) {
    private val sp = ctx.getSharedPreferences("electro", Context.MODE_PRIVATE)
    private val app = ctx.applicationContext

    /** Включена ли блокировка при входе. Старый 4-значный код (до 0.44) переносится в «включено». */
    var enabled: Boolean
        get() = sp.getBoolean(K_ENABLED, !sp.getString("lock_pin_hash", null).isNullOrEmpty())
        set(v) { sp.edit().putBoolean(K_ENABLED, v).remove("lock_pin_hash").remove("lock_pin_salt").apply() }

    /** Предлагали ли уже включить после входа; «не сейчас» запоминаем, чтобы не докучать. */
    var offerDeclined: Boolean
        get() = sp.getBoolean(K_OFFER_DECLINED, false)
        set(v) { sp.edit().putBoolean(K_OFFER_DECLINED, v).apply() }

    /**
     * Есть ли на телефоне чем защищать: биометрия или хотя бы блокировка экрана.
     * Без блокировки экрана системе нечего спросить — тумблер недоступен.
     */
    fun available(): Boolean {
        val bm = BiometricManager.from(app).canAuthenticate(AUTHENTICATORS)
        if (bm == BiometricManager.BIOMETRIC_SUCCESS) return true
        return (app.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager)?.isDeviceSecure == true
    }

    /** Есть ли биометрия (для подписи в настройках). */
    fun biometricAvailable(): Boolean =
        BiometricManager.from(app).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS

    /** Системный диалог: биометрия, при отказе/отсутствии — код или рисунок телефона. */
    fun prompt(activity: FragmentActivity, onResult: (Boolean) -> Unit) {
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onResult(true)
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onResult(false)
            })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("LeapRemote")
            .setSubtitle(S("Подтвердите, что это вы"))
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()
        prompt.authenticate(info)
    }

    private companion object {
        const val K_ENABLED = "lock_system"
        const val K_OFFER_DECLINED = "lock_offer_declined"
        // WEAK + DEVICE_CREDENTIAL — единственная комбинация, которую библиотека
        // умеет на всех API (на 28–29 через KeyguardManager)
        const val AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
    }
}
