package com.vueo.shared.core.sync

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import android.util.JsonReader
import org.json.JSONObject
import java.io.File
import java.io.InputStreamReader
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Device-only key; encrypted sessions/outbox live outside Android backup. */
internal class SyncSecureFile(context: Context, name: String) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "vueo-sync-$name.bin"))
    private fun secret(): SecretKey = synchronized(KEY_LOCK) {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey) ?: KeyGenerator.getInstance("AES", "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    @Synchronized fun read(): JSONObject? {
        if (!file.baseFile.exists()) return null
        val bytes = file.openRead().use { it.readBytes() }
        require(bytes.size > 12) { "Invalid encrypted sync state" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secret(), GCMParameterSpec(128, bytes.copyOfRange(0,12)))
        return JSONObject(String(cipher.doFinal(bytes.copyOfRange(12,bytes.size)), Charsets.UTF_8))
    }
    @Synchronized fun write(value: JSONObject) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secret())
        val bytes = cipher.iv + cipher.doFinal(value.toString().toByteArray(Charsets.UTF_8))
        val stream = file.startWrite()
        try { stream.write(bytes); file.finishWrite(stream) }
        catch (error: Exception) { file.failWrite(stream); throw error }
    }
    @Synchronized fun readStreaming(consume: (JsonReader)->Unit) {
        if(!file.baseFile.exists()) return
        file.openRead().use { input ->
            val iv=ByteArray(12)
            var offset=0
            while(offset<iv.size) {
                val count=input.read(iv,offset,iv.size-offset)
                require(count>0) { "Invalid encrypted sync state" }
                offset+=count
            }
            val cipher=Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE,secret(),GCMParameterSpec(128,iv))
            JsonReader(InputStreamReader(CipherInputStream(input,cipher),Charsets.UTF_8)).use { reader ->
                consume(reader)
                // Reading EOF verifies the GCM authentication tag before migration is committed.
                check(reader.peek()==android.util.JsonToken.END_DOCUMENT)
            }
        }
    }
    @Synchronized fun delete() = file.delete()
    companion object { private const val ALIAS = "vueo_sync_device_v1"; private val KEY_LOCK = Any() }
}

/** Record encryption prevents one pending entity from requiring serialization of all account data. */
internal class SyncRecordCipher {
    private fun key(): SecretKey = synchronized(KEY_LOCK) {
        cached ?: run {
            val store=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val value=(store.getKey(ALIAS,null) as? SecretKey) ?: KeyGenerator.getInstance("AES","AndroidKeyStore").apply {
                init(KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            }.generateKey()
            cached=value; value
        }
    }
    fun encrypt(bytes: ByteArray): ByteArray {
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE,key())
        return cipher.iv+cipher.doFinal(bytes)
    }
    fun decrypt(bytes: ByteArray): ByteArray {
        require(bytes.size>12)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,bytes.copyOfRange(0,12)))
        return cipher.doFinal(bytes.copyOfRange(12,bytes.size))
    }
    companion object {
        private const val ALIAS="vueo_sync_records_v1"
        private val KEY_LOCK=Any()
        private var cached: SecretKey?=null
    }
}
