package com.vueo.shared.core.extensions

/** Short user-facing failures without echoing configured URLs or credentials. */
object AddonManagementMessage {
    fun failure(error: Throwable): String = when (error) {
        is java.net.UnknownHostException -> "Addon server not found. Check the URL and connection."
        is java.net.SocketTimeoutException, is java.io.InterruptedIOException -> "Addon timed out. Try again."
        is javax.net.ssl.SSLException -> "Unable to verify the addon's HTTPS connection."
        is java.net.ConnectException -> "Unable to connect to the addon server."
        is org.json.JSONException -> "The URL did not return a valid addon manifest."
        is IllegalArgumentException -> "Check the addon URL and manifest. HTTP, HTTPS and Stremio URLs are supported."
        else -> "Unable to load addon. Check its URL or try again."
    }
}
