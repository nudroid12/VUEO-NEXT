package com.vueo.shared.core.trakt

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** A token pair is encrypted and committed in one preference value, including rotated refresh tokens. */
internal class TraktAccountStore(private val context: Context) {
    val prefs = context.applicationContext.getSharedPreferences("vueo_trakt", Context.MODE_PRIVATE)
    fun key(profile: String, name: String) = "profile:$profile:$name"
    fun connected(profile: String) = prefs.contains(key(profile, "session"))
    @Synchronized private fun secret(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("vueo_trakt_tokens", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("vueo_trakt_tokens", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun read(profile: String): JSONObject? {
        val value = prefs.getString(key(profile, "session"), null) ?: return null
        return try {
            val parts = value.split('.', limit = 2)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secret(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
            JSONObject(String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8))
        } catch (_: Exception) {
            prefs.edit().remove(key(profile, "session")).putString(key(profile, "status"), "Sign in again to connect Trakt.").apply()
            null
        }
    }
    fun save(profile: String, tokens: JSONObject, publishCloud: Boolean = true) {
        check(tokens.optString("access_token").isNotBlank() && tokens.optString("refresh_token").isNotBlank())
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secret())
        val value = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + "." +
            Base64.encodeToString(cipher.doFinal(tokens.toString().toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        check(prefs.edit().putString(key(profile, "session"), value).commit()) { "Unable to save Trakt session" }
        if (publishCloud) com.vueo.shared.core.sync.VueoSyncService.get(context).queueTraktSession(profile, tokens)
    }
    fun clear(profile: String, publishCloud: Boolean = true) {
        val identity = prefs.getString(key(profile, "account_id"), null)
        val editor = prefs.edit()
        val localOptions = setOf("discovery", "sync_favorites", "sync_history", "sync_playback", "activity_scrobble")
        prefs.all.keys.filter { raw -> raw.startsWith("profile:$profile:") &&
            (publishCloud || raw.removePrefix("profile:$profile:") !in localOptions)
        }.forEach { editor.remove(it) }
        if (identity != null) editor.putString(key(profile, "account_id"), identity)
        editor.commit()
        if (publishCloud) com.vueo.shared.core.sync.VueoSyncService.get(context).queueTraktSession(profile, null)
    }
    fun enabled(profile: String, name: String, default: Boolean = true) = prefs.getBoolean(key(profile, name), default)
}
