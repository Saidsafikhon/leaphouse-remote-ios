package uz.electro.remote

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import java.security.MessageDigest
import kotlin.system.exitProcess

/**
 * Anti-tamper: сверяет SHA-256 подписи установленного APK с эталоном из
 * BuildConfig.EXPECTED_SIG. Если APK переподписан (распаковали, изменили,
 * подписали своим ключом) — отпечаток не совпадёт и приложение завершится.
 *
 * EXPECTED_SIG пуст → проверка выключена (debug и несконфигурированные сборки).
 * Несколько отпечатков — через запятую (раздача файлом + Google Play).
 * Эталон брать из release-keystore:  keytool -list -v -keystore ... | grep SHA256
 * и подставлять через ./gradlew -PexpectedSig=... (см. app/build.gradle).
 */
internal object SignatureGuard {

    private const val TAG = "SignatureGuard"

    fun enforce(context: Context) {
        // Список через запятую: ключ раздачи файлом и ключ Google Play (см. app/build.gradle).
        val expected = uz.electro.remote.BuildConfig.EXPECTED_SIG.split(',')
            .map { it.trim() }.filter { it.isNotEmpty() }
        if (expected.isEmpty()) return                 // проверка отключена
        val actual = currentSignatureSha256(context)
        if (actual.none { a -> expected.any { it.equals(a, ignoreCase = true) } }) {
            Log.e(TAG, "signature mismatch: refusing to run")
            exitProcess(10)
        }
    }

    /** Все SHA-256 сертификатов подписи APK (обычно один). */
    private fun currentSignatureSha256(context: Context): Set<String> = runCatching {
        val pm = context.packageManager
        val pkg = context.packageName
        val certs: Array<android.content.pm.Signature> =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val info = pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES)
                val s = info.signingInfo ?: return emptySet()
                if (s.hasMultipleSigners()) s.apkContentsSigners else s.signingCertificateHistory
            } else {
                @Suppress("DEPRECATION", "PackageManagerGetSignatures")
                pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES).signatures
            } ?: return emptySet()
        val md = MessageDigest.getInstance("SHA-256")
        certs.mapNotNull { it }.map { sig ->
            md.reset()
            md.digest(sig.toByteArray()).joinToString("") { "%02X".format(it) }
        }.toSet()
    }.getOrElse { emptySet() }
}
