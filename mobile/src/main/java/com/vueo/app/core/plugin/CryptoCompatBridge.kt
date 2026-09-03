package com.vueo.app.core.plugin

import android.util.Base64
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Native primitives used by the QuickJS CryptoJS-compatible wrapper.
 *
 * This is a compatibility API for provider scripts, not an API for storing
 * user secrets. Provider inputs and outputs stay in memory during discovery.
 */
object CryptoCompatBridge {
    private val secureRandom = SecureRandom()

    fun execute(requestJson: String): String =
        runCatching {
            val request = JSONObject(requestJson)

            when (request.getString("op")) {
                "hash" -> hash(request)
                "hmac" -> hmac(request)
                "decrypt" -> decrypt(request)
                "encrypt" -> encrypt(request)
                "random" -> random(request)
                else -> error(
                    "Unknown crypto operation: " +
                        request.optString("op")
                )
            }
        }.getOrElse { error ->
            JSONObject().put(
                "error",
                error.message
                    ?: error::class.java.simpleName,
            )
        }.toString()

    private fun hash(request: JSONObject): JSONObject {
        val algorithm = when (
            request.getString("algorithm")
                .uppercase()
        ) {
            "MD5" -> "MD5"
            "SHA1", "SHA-1" -> "SHA-1"
            "SHA256", "SHA-256" -> "SHA-256"
            "SHA384", "SHA-384" -> "SHA-384"
            "SHA512", "SHA-512" -> "SHA-512"
            else -> error(
                "Unsupported hash algorithm."
            )
        }

        val digest = MessageDigest
            .getInstance(algorithm)
            .digest(
                decode(
                    request.getString("data")
                )
            )

        return bytesResponse(digest)
    }

    private fun hmac(request: JSONObject): JSONObject {
        val algorithm = when (
            request.getString("algorithm")
                .uppercase()
        ) {
            "MD5" -> "HmacMD5"
            "SHA1", "SHA-1" -> "HmacSHA1"
            "SHA256", "SHA-256" -> "HmacSHA256"
            "SHA384", "SHA-384" -> "HmacSHA384"
            "SHA512", "SHA-512" -> "HmacSHA512"
            else -> error(
                "Unsupported HMAC algorithm."
            )
        }

        val mac = Mac.getInstance(algorithm)
        mac.init(
            SecretKeySpec(
                decode(
                    request.getString("key")
                ),
                algorithm,
            )
        )

        return bytesResponse(
            mac.doFinal(
                decode(
                    request.getString("data")
                )
            )
        )
    }

    private fun decrypt(request: JSONObject): JSONObject {
        val algorithm =
            request.getString("algorithm")
                .uppercase()

        val key = decode(
            request.getString("key")
        )

        val iv = decode(
            request.optString("iv")
        )

        val ciphertext = decode(
            request.getString("data")
        )

        val transformation =
            when (algorithm) {
                "AES" ->
                    "AES/CBC/PKCS5Padding"
                "TRIPLEDES", "3DES", "DESEDE" ->
                    "DESede/CBC/PKCS5Padding"
                else ->
                    error(
                        "Unsupported cipher."
                    )
            }

        val keyAlgorithm =
            if (algorithm == "AES") {
                "AES"
            } else {
                "DESede"
            }

        val normalizedKey =
            if (keyAlgorithm == "DESede") {
                normalizeTripleDesKey(key)
            } else {
                key
            }

        val cipher = Cipher.getInstance(
            transformation
        )

        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(
                normalizedKey,
                keyAlgorithm,
            ),
            IvParameterSpec(iv),
        )

        return bytesResponse(
            cipher.doFinal(ciphertext)
        )
    }

    private fun encrypt(request: JSONObject): JSONObject {
        val algorithm =
            request.getString("algorithm")
                .uppercase()

        val key = decode(
            request.getString("key")
        )

        val iv = decode(
            request.optString("iv")
        )

        val plaintext = decode(
            request.getString("data")
        )

        val transformation =
            when (algorithm) {
                "AES" ->
                    "AES/CBC/PKCS5Padding"
                "TRIPLEDES", "3DES", "DESEDE" ->
                    "DESede/CBC/PKCS5Padding"
                else ->
                    error(
                        "Unsupported cipher."
                    )
            }

        val keyAlgorithm =
            if (algorithm == "AES") {
                "AES"
            } else {
                "DESede"
            }

        val normalizedKey =
            if (keyAlgorithm == "DESede") {
                normalizeTripleDesKey(key)
            } else {
                key
            }

        val cipher = Cipher.getInstance(
            transformation
        )

        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(
                normalizedKey,
                keyAlgorithm,
            ),
            IvParameterSpec(iv),
        )

        return bytesResponse(
            cipher.doFinal(plaintext)
        )
    }

    private fun random(request: JSONObject): JSONObject {
        val count = request.optInt(
            "count",
            16,
        ).coerceIn(0, 4096)

        val bytes = ByteArray(count)
        secureRandom.nextBytes(bytes)

        return bytesResponse(bytes)
    }

    private fun normalizeTripleDesKey(
        key: ByteArray,
    ): ByteArray =
        when (key.size) {
            24 -> key
            16 ->
                key + key.copyOfRange(0, 8)
            else ->
                error(
                    "TripleDES key must be 16 or 24 bytes."
                )
        }

    private fun bytesResponse(
        bytes: ByteArray,
    ): JSONObject =
        JSONObject().put(
            "data",
            Base64.encodeToString(
                bytes,
                Base64.NO_WRAP,
            ),
        )

    private fun decode(value: String): ByteArray {
        if (value.isBlank()) {
            return ByteArray(0)
        }

        return Base64.decode(
            value,
            Base64.DEFAULT,
        )
    }
}
