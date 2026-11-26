package icu.xingguangcuican.meowcode.data.storage

import android.content.Context
import androidx.core.content.edit
import android.util.Base64
import icu.xingguangcuican.meowcode.data.oauth.OAuthSession
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Persists only what is needed to stay signed in: the session (tokens included) and
 * the on/off check for the user's theme preferences.
 *
 * Tokens are sealed with a hardware-backed AES-GCM key (Android Keystore) so a
 * backup-extracted file or a root-level read cannot yield a usable token. If the key
 * is lost (lock-screen reset, restore on another device) [readSession] returns null
 * and the user simply signs in again. Nothing here is a substitute for a proper
 * encrypted file store, but it is honest: no plaintext token ever touches disk.
 */
class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun readSession(): OAuthSession? = runCatching {
        val sealed = prefs.getString(KEY_SESSION, null) ?: return null
        val raw = JSONObject(unseal(sealed))
        OAuthSession(
            issuer = raw.getString("issuer"),
            clientId = raw.getString("client_id"),
            accessToken = raw.getString("access_token"),
            refreshToken = raw.optStringOrNull("refresh_token"),
            idToken = raw.optStringOrNull("id_token"),
            scope = raw.optStringOrNull("scope"),
            expiresAt = if (raw.has("expires_at")) raw.getLong("expires_at") else null,
        )
    }.getOrNull()

    fun writeSession(session: OAuthSession?) {
        if (session == null) {
            prefs.edit { remove(KEY_SESSION) }
            return
        }
        val json = JSONObject().apply {
            put("issuer", session.issuer)
            put("client_id", session.clientId)
            put("access_token", session.accessToken)
            session.refreshToken?.let { put("refresh_token", it) }
            session.idToken?.let { put("id_token", it) }
            session.scope?.let { put("scope", it) }
            session.expiresAt?.let { put("expires_at", it) }
        }
        prefs.edit { putString(KEY_SESSION, seal(json.toString())) }
    }

    /** Theme override: `null` means "follow the system". */
    fun readDarkTheme(): Boolean? = when (prefs.getString(KEY_DARK, null)) {
        "dark" -> true
        "light" -> false
        else -> null
    }

    fun writeDarkTheme(dark: Boolean?) {
        prefs.edit { putString(KEY_DARK, if (dark == null) null else if (dark) "dark" else "light") }
    }

    private fun seal(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val body = Base64.encodeToString(cipher.doFinal(plain.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        return "$iv:$body"
    }

    private fun unseal(sealed: String): String {
        val iv = Base64.decode(sealed.substringBefore(':'), Base64.NO_WRAP)
        val body = Base64.decode(sealed.substringAfter(':'), Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        return cipher.doFinal(body).toString(Charsets.UTF_8)
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance("AES", KEYSTORE)
        generator.init(android.security.keystore.KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
                android.security.keystore.KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
            .build())
        return generator.generateKey()
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) getString(key).ifBlank { null } else null

    private companion object {
        const val PREFS = "meowcode.session"
        const val KEY_SESSION = "sealed_session"
        const val KEY_DARK = "theme_dark"
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "meowcode.session.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
