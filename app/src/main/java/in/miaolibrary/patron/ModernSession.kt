package `in`.miaolibrary.patron

import android.app.Activity
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Separate encrypted session store used by the redesigned UI. */
class ModernSession(private val activity: Activity) {
    private companion object {
        const val PREFS = "miao_library_modern_session"
        const val TOKEN = "token"
        const val IV = "iv"
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "miao_library_modern_session_key"
    }

    private fun key(): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        val existing = runCatching {
            val store = java.security.KeyStore.getInstance(KEYSTORE).apply { load(null) }
            (store.getKey(KEY_ALIAS, null) as? SecretKey)
        }.getOrNull()
        if (existing != null) return existing
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    fun saveToken(token: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(token.toByteArray(StandardCharsets.UTF_8))
        activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE).edit()
            .putString(TOKEN, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString(IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
    }

    fun readToken(): String? = runCatching {
        val prefs = activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE)
        val encoded = prefs.getString(TOKEN, null) ?: return null
        val iv = prefs.getString(IV, null) ?: return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            key(),
            GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP))
        )
        String(cipher.doFinal(Base64.decode(encoded, Base64.NO_WRAP)), StandardCharsets.UTF_8)
    }.getOrNull()

    fun clear() {
        activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE).edit().clear().apply()
    }
}
