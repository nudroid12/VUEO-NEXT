package com.vueo.shared.core.plugin

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

/** JSON transports embedded NULs safely across the QuickJS String binding. */
internal object BinaryCompatBridge {
    private fun unpack(jsonString: String): String =
        JSONArray("[$jsonString]").getString(0)

    fun encodeBinary(jsonString: String): String {
        val value = unpack(jsonString)
        val bytes = ByteArray(value.length) { index ->
            (value[index].code and 0xFF).toByte()
        }
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun decodeBinary(base64: String): String {
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        return JSONObject.quote(buildString(bytes.size) {
            bytes.forEach { append((it.toInt() and 0xFF).toChar()) }
        })
    }

    fun encodeUtf8(jsonString: String): String =
        Base64.encodeToString(unpack(jsonString).toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

    fun decodeUtf8(base64: String): String =
        JSONObject.quote(String(Base64.decode(base64, Base64.DEFAULT), Charsets.UTF_8))
}
