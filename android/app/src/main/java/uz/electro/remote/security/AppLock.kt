package uz.electro.remote.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Защита входа в приложение: 4-значный код и биометрия (отпечаток / лицо).
 *
 * Код не хранится — только SHA-256 от соли и кода; соль случайная на телефон.
 * Биометрия — надстройка над кодом: без кода её включить нельзя, и отказ
 * системного диалога всегда оставляет запасной путь через код.
 */
class AppLock(ctx: Context) {
    private val sp = ctx.getSharedPreferences("electro", Context.MODE_PRIVATE)
    private val app = ctx.applicationContext

    /** Задан ли код. */
    val enabled: Boolean get() = !sp.getString(K_HASH, null).isNullOrEmpty()

    var biometricEnabled: Boolean
        get() = enabled && sp.getBoolean(K_BIO, false)
        set(v) { sp.edit().putBoolean(K_BIO, v).apply() }

    fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        sp.edit().putString(K_SALT, salt).putString(K_HASH, hash(salt, pin)).apply()
    }

    fun clear() { sp.edit().remove(K_HASH).remove(K_SALT).remove(K_BIO).remove(K_FAILS).remove(K_LOCK_UNTIL).apply() }

    fun check(pin: String): Boolean {
        val salt = sp.getString(K_SALT, null) ?: return false
        val ok = hash(salt, pin) == sp.getString(K_HASH, null)
        if (ok) sp.edit().remove(K_FAILS).remove(K_LOCK_UNTIL).apply()
        else {
            val fails = sp.getInt(K_FAILS, 0) + 1
            val e = sp.edit().putInt(K_FAILS, fails)
            // после пяти промахов — пауза 30 с, чтобы код нельзя было перебрать
            if (fails >= MAX_FAILS) e.putLong(K_LOCK_UNTIL, System.currentTimeMillis() + COOLDOWN_MS).putInt(K_FAILS, 0)
            e.apply()
        }
        return ok
    }

    /** Сколько секунд ещё ждать после серии промахов; 0 — можно вводить. */
    fun cooldownSec(): Int {
        val until = sp.getLong(K_LOCK_UNTIL, 0L)
        return ((until - System.currentTimeMillis()) / 1000).toInt().coerceAtLeast(0)
    }

    /** Есть ли на телефоне биометрия, которой можно пользоваться. */
    fun biometricAvailable(): Boolean =
        BiometricManager.from(app).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    /** Системный диалог биометрии; кнопка отказа — «Код». */
    fun promptBiometric(activity: FragmentActivity, onResult: (Boolean) -> Unit) {
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onResult(true)
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onResult(false)
            })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("LeapRemote")
            .setSubtitle("Подтвердите вход")
            .setNegativeButtonText("Код")
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()
        prompt.authenticate(info)
    }

    private fun hash(salt: String, pin: String): String =
        MessageDigest.getInstance("SHA-256").digest((salt + ":" + pin).toByteArray()).joinToString("") { "%02x".format(it) }

    private companion object {
        const val K_HASH = "lock_pin_hash"
        const val K_SALT = "lock_pin_salt"
        const val K_BIO = "lock_biometric"
        const val K_FAILS = "lock_fails"
        const val K_LOCK_UNTIL = "lock_until"
        const val MAX_FAILS = 5
        const val COOLDOWN_MS = 30_000L
        const val AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
    }
}
