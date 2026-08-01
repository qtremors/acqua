package dev.qtremors.acqua.data.session

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class SavedInstagramSession(
    val cookies: String,
    val userAgent: String
)

class InstagramSessionStore(context: Context) {
    private val appContext = context.applicationContext
    private val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private val KEY_ALIAS = "acqua_login_session_key"
    private val PREFS_NAME = "acqua_secure_sessions"
    private val SESSION_KEY = "instagram_session"
    private val LEGACY_PREFS_NAME = "acqua_prefs"
    private val LEGACY_COOKIES_KEY = "acqua_instagram_cookies"
    private val LEGACY_USER_AGENT_KEY = "acqua_instagram_ua"
    private val TRANSFORMATION = "AES/GCM/NoPadding"

    fun save(session: SavedInstagramSession) {
        require(session.cookies.isNotBlank()) { "Cannot save an empty login session" }

        val payload = JSONObject()
            .put("cookies", session.cookies)
            .put("userAgent", session.userAgent)
            .toString()
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        }
        val encrypted = cipher.doFinal(payload.toByteArray(Charsets.UTF_8))
        val encoded = listOf(cipher.iv, encrypted).joinToString(".") {
            Base64.encodeToString(it, Base64.NO_WRAP)
        }

        val saved = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(SESSION_KEY, encoded)
            .commit()
        check(saved) { "Could not persist the encrypted login session" }
        removeLegacyPlaintextSession()
    }

    fun load(): SavedInstagramSession? {
        val encrypted = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(SESSION_KEY, null)

        if (encrypted == null) return migrateLegacySession()

        return runCatching {
            val parts = encrypted.split('.', limit = 2)
            require(parts.size == 2) { "Invalid encrypted session payload" }
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val ciphertext = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            }
            val json = JSONObject(String(cipher.doFinal(ciphertext), Charsets.UTF_8))
            SavedInstagramSession(
                cookies = json.getString("cookies"),
                userAgent = json.optString("userAgent")
            ).takeIf { it.cookies.isNotBlank() }
        }.getOrElse {
            clear()
            null
        }
    }

    fun clear() {
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            remove(SESSION_KEY)
        }
        removeLegacyPlaintextSession()
    }

    private fun migrateLegacySession(): SavedInstagramSession? {
        val prefs = appContext.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
        val cookies = prefs.getString(LEGACY_COOKIES_KEY, null).orEmpty()
        if (cookies.isBlank()) return null

        val session = SavedInstagramSession(
            cookies = cookies,
            userAgent = prefs.getString(LEGACY_USER_AGENT_KEY, null).orEmpty()
        )
        return runCatching {
            save(session)
            session
        }.getOrNull()
    }

    private fun removeLegacyPlaintextSession() {
        appContext.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE).edit {
            remove(LEGACY_COOKIES_KEY)
            remove(LEGACY_USER_AGENT_KEY)
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
            generateKey()
        }
    }
}
