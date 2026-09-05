package com.vueo.shared.core.plugin

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.Collections
import kotlin.coroutines.resume

/**
 * Native WebView fallback for JavaScript providers whose upstream players only
 * reveal media URLs after JavaScript execution or user interaction.
 *
 * The page receives a capture-only JavaScript interface. It cannot call app
 * actions, read app data or invoke arbitrary native code.
 */
internal class PluginWebViewResolver(
    context: Context,
) {
    private val appContext = context.applicationContext

    suspend fun resolveJson(requestJson: String): String =
        withContext(Dispatchers.Main.immediate) {
            val request = runCatching {
                ResolveRequest.parse(requestJson)
            }.getOrElse { error ->
                return@withContext errorJson(
                    error.message ?: "Invalid WebView resolver request."
                )
            }

            val streams = resolve(request)
            JSONObject().apply {
                put(
                    "streams",
                    JSONArray().apply {
                        streams.forEach { stream ->
                            put(
                                JSONObject().apply {
                                    put("label", stream.label)
                                    put("url", stream.url)
                                    put("headers", JSONObject(stream.headers))
                                }
                            )
                        }
                    },
                )
            }.toString()
        }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    private suspend fun resolve(
        request: ResolveRequest,
    ): List<CapturedStream> =
        suspendCancellableCoroutine { continuation ->
            val handler = Handler(Looper.getMainLooper())
            val webView = WebView(appContext)
            val streams = Collections.synchronizedMap(
                linkedMapOf<String, CapturedStream>()
            )
            var finishScheduled = false
            var destroyed = false

            fun sortedResult(): List<CapturedStream> =
                synchronized(streams) {
                    streams.values
                        .distinctBy { it.url }
                        .sortedWith(
                            compareByDescending<CapturedStream> {
                                qualityScore(it.label, it.url)
                            }.thenBy { it.label }
                        )
                }

            fun safeDestroy() {
                if (destroyed) return
                destroyed = true
                runCatching {
                    handler.removeCallbacksAndMessages(null)
                    webView.stopLoading()
                    webView.loadUrl("about:blank")
                    webView.removeJavascriptInterface(BRIDGE_NAME)
                    webView.removeAllViews()
                    webView.destroy()
                }
            }

            fun finish() {
                handler.post {
                    if (continuation.isActive) {
                        val result = sortedResult()
                        safeDestroy()
                        continuation.resume(result)
                    } else {
                        safeDestroy()
                    }
                }
            }

            fun scheduleFinishSoon() {
                if (finishScheduled) return
                finishScheduled = true
                handler.postDelayed(
                    { finish() },
                    request.finishAfterFirstMs,
                )
            }

            fun addStream(
                label: String,
                rawUrl: String?,
                headers: Map<String, String>,
            ) {
                val fixedUrl = rawUrl
                    ?.trim()
                    ?.toAbsoluteUrl(request.url)
                    ?.takeIf { request.isStreamUrl(it) }
                    ?: return

                val fixedHeaders = headers.toMutableMap().apply {
                    putIfAbsent("User-Agent", request.userAgent)
                    putIfAbsent("Accept", "*/*")
                    putIfAbsent("Referer", request.referer.ifBlank { request.url })
                }

                var added = false
                synchronized(streams) {
                    if (!streams.containsKey(fixedUrl)) {
                        streams[fixedUrl] = CapturedStream(
                            label = label.trim().ifBlank { guessLabel(fixedUrl) },
                            url = fixedUrl,
                            headers = fixedHeaders,
                        )
                        added = true
                    }
                }

                if (added) scheduleFinishSoon()
            }

            fun handleBridgeCapture(value: String) {
                val clean = value.trim()
                if (clean.isBlank()) return

                when {
                    clean.startsWith("VUEO_SOURCE|") -> {
                        val parts = clean.split("|", limit = 4)
                        if (parts.size >= 4) {
                            addStream(
                                label = parts[1],
                                rawUrl = parts[3],
                                headers = request.defaultHeaders(),
                            )
                        }
                    }

                    clean.startsWith("VUEO_VIDEO|") -> {
                        val file = clean.removePrefix("VUEO_VIDEO|")
                        addStream(
                            label = guessLabel(file),
                            rawUrl = file,
                            headers = request.defaultHeaders(),
                        )
                    }

                    clean.startsWith("VUEO_FETCH|") ||
                        clean.startsWith("VUEO_XHR|") -> {
                        val file = clean.substringAfter('|')
                        if (request.isStreamUrl(file)) {
                            addStream(
                                label = guessLabel(file),
                                rawUrl = file,
                                headers = request.defaultHeaders(),
                            )
                        }
                    }
                }
            }

            fun clickWebView() {
                if (streams.isNotEmpty()) return

                runCatching {
                    val now = SystemClock.uptimeMillis()
                    val x = request.clickX
                    val y = request.clickY

                    webView.dispatchTouchEvent(
                        MotionEvent.obtain(
                            now,
                            now,
                            MotionEvent.ACTION_DOWN,
                            x,
                            y,
                            0,
                        )
                    )
                    webView.dispatchTouchEvent(
                        MotionEvent.obtain(
                            now,
                            now + 80L,
                            MotionEvent.ACTION_UP,
                            x,
                            y,
                            0,
                        )
                    )
                }
            }

            fun captureStream(requestInfo: WebResourceRequest?) {
                val requestUrl = requestInfo?.url?.toString()?.trim().orEmpty()
                if (!request.isStreamUrl(requestUrl)) return

                val headers = requestInfo?.requestHeaders
                    .orEmpty()
                    .toMutableMap()

                val cookie = runCatching {
                    CookieManager.getInstance().getCookie(requestUrl)
                }.getOrNull().orEmpty()

                if (cookie.isNotBlank()) headers["Cookie"] = cookie

                addStream(
                    label = guessLabel(requestUrl),
                    rawUrl = requestUrl,
                    headers = headers,
                )
            }

            continuation.invokeOnCancellation {
                handler.post { safeDestroy() }
            }

            runCatching {
                WebView.setWebContentsDebuggingEnabled(false)

                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                cookieManager.setAcceptThirdPartyCookies(webView, true)

                webView.addJavascriptInterface(
                    CaptureBridge(::handleBridgeCapture),
                    BRIDGE_NAME,
                )
                webView.layout(0, 0, request.viewportWidth, request.viewportHeight)

                webView.settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    loadsImagesAutomatically = true
                    javaScriptCanOpenWindowsAutomatically = true
                    setSupportMultipleWindows(false)
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    userAgentString = request.userAgent
                }

                webView.webChromeClient = WebChromeClient()
                webView.webViewClient = object : WebViewClient() {
                    override fun onPageStarted(
                        view: WebView?,
                        url: String?,
                        favicon: Bitmap?,
                    ) = Unit

                    override fun onPageFinished(
                        view: WebView?,
                        url: String?,
                    ) = Unit

                    override fun shouldInterceptRequest(
                        view: WebView?,
                        webRequest: WebResourceRequest?,
                    ): WebResourceResponse? {
                        val requestUrl = webRequest?.url?.toString().orEmpty()

                        if (
                            request.injectAbyssHook &&
                            shouldInjectAbyssPage(requestUrl)
                        ) {
                            return runCatching {
                                injectIntoAbyssPage(
                                    pageUrl = requestUrl,
                                    referer = request.referer.ifBlank { request.url },
                                    userAgent = request.userAgent,
                                )
                            }.getOrNull()
                        }

                        if (request.isStreamUrl(requestUrl)) {
                            captureStream(webRequest)
                            return WebResourceResponse(
                                "video/mp4",
                                "UTF-8",
                                ByteArrayInputStream(ByteArray(0)),
                            )
                        }

                        return super.shouldInterceptRequest(view, webRequest)
                    }

                    @Deprecated("Deprecated in Android")
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        url: String?,
                    ): Boolean = false

                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?,
                    ): Boolean = false
                }

                val wrapper = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <meta name="viewport" content="width=device-width, initial-scale=1.0">
                        <style>
                            html, body, iframe {
                                margin: 0;
                                padding: 0;
                                width: 100%;
                                height: 100%;
                                background: #000;
                                border: 0;
                                overflow: hidden;
                            }
                        </style>
                    </head>
                    <body>
                        <iframe
                            id="vueo_player_frame"
                            src="${htmlEscape(request.url)}"
                            allow="autoplay; fullscreen; encrypted-media; picture-in-picture"
                            allowfullscreen>
                        </iframe>
                    </body>
                    </html>
                """.trimIndent()

                webView.loadDataWithBaseURL(
                    request.referer.ifBlank { request.url },
                    wrapper,
                    "text/html",
                    "UTF-8",
                    null,
                )

                request.clickDelaysMs.forEach { clickDelay ->
                    if (clickDelay < request.timeoutMs) {
                        handler.postDelayed({ clickWebView() }, clickDelay)
                    }
                }

                handler.postDelayed({ finish() }, request.timeoutMs)
            }.onFailure {
                finish()
            }
        }

    private class CaptureBridge(
        private val onCapture: (String) -> Unit,
    ) {
        @JavascriptInterface
        fun capture(value: String?) {
            onCapture(value.orEmpty())
        }
    }

    private data class CapturedStream(
        val label: String,
        val url: String,
        val headers: Map<String, String>,
    )

    private data class ResolveRequest(
        val url: String,
        val referer: String,
        val timeoutMs: Long,
        val finishAfterFirstMs: Long,
        val clickDelaysMs: List<Long>,
        val matchParts: List<String>,
        val blockedParts: List<String>,
        val userAgent: String,
        val injectAbyssHook: Boolean,
        val viewportWidth: Int,
        val viewportHeight: Int,
        val clickX: Float,
        val clickY: Float,
    ) {
        fun isStreamUrl(rawUrl: String?): Boolean {
            val value = rawUrl?.lowercase().orEmpty()
            if (value.isBlank()) return false
            if (blockedParts.any(value::contains)) return false
            return matchParts.any(value::contains)
        }

        fun defaultHeaders(): Map<String, String> =
            mapOf(
                "User-Agent" to userAgent,
                "Accept" to "*/*",
                "Referer" to referer.ifBlank { url },
            )

        companion object {
            fun parse(raw: String): ResolveRequest {
                val json = JSONObject(raw)
                val url = json.optString("url").trim()
                require(url.startsWith("https://") || url.startsWith("http://")) {
                    "WebView resolver requires an HTTP(S) URL."
                }

                val timeoutMs = json.optLong("timeoutMs", DEFAULT_WEBVIEW_TIMEOUT_MS)
                    .coerceIn(MIN_WEBVIEW_TIMEOUT_MS, MAX_WEBVIEW_TIMEOUT_MS)

                val matchParts = json.optJSONArray("match")
                    .toStringList()
                    .map { it.lowercase() }
                    .ifEmpty { DEFAULT_MATCH_PARTS }

                val blockedParts = json.optJSONArray("blocked")
                    .toStringList()
                    .map { it.lowercase() }
                    .ifEmpty { DEFAULT_BLOCKED_PARTS }

                val clickDelays = json.optJSONArray("clickDelaysMs")
                    .toLongList()
                    .filter { it >= 0L }
                    .distinct()
                    .sorted()
                    .ifEmpty { DEFAULT_CLICK_DELAYS_MS }

                val viewportWidth = json.optInt("viewportWidth", 1080)
                    .coerceIn(320, 3840)
                val viewportHeight = json.optInt("viewportHeight", 1080)
                    .coerceIn(320, 2160)

                return ResolveRequest(
                    url = url,
                    referer = json.optString("referer").trim(),
                    timeoutMs = timeoutMs,
                    finishAfterFirstMs = json.optLong(
                        "finishAfterFirstMs",
                        1_200L,
                    ).coerceIn(100L, 5_000L),
                    clickDelaysMs = clickDelays,
                    matchParts = matchParts,
                    blockedParts = blockedParts,
                    userAgent = json.optString("userAgent")
                        .trim()
                        .ifBlank { DEFAULT_USER_AGENT },
                    injectAbyssHook = json.optBoolean("injectAbyssHook", true),
                    viewportWidth = viewportWidth,
                    viewportHeight = viewportHeight,
                    clickX = json.optDouble("clickX", viewportWidth / 2.0)
                        .toFloat(),
                    clickY = json.optDouble("clickY", viewportHeight / 2.0)
                        .toFloat(),
                )
            }
        }
    }

    private fun injectIntoAbyssPage(
        pageUrl: String,
        referer: String,
        userAgent: String,
    ): WebResourceResponse {
        val connection = URL(pageUrl).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", userAgent)
        connection.setRequestProperty("Referer", referer)
        connection.setRequestProperty(
            "Accept",
            "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        )
        connection.setRequestProperty(
            "Accept-Language",
            "ms-MY,ms;q=0.9,en-US;q=0.8,en;q=0.7",
        )
        connection.setRequestProperty("Accept-Encoding", "identity")
        connection.connectTimeout = 7_000
        connection.readTimeout = 7_000

        val html = connection.inputStream
            .bufferedReader()
            .use { it.readText() }

        connection.headerFields
            .filterKeys { it?.equals("Set-Cookie", true) == true }
            .values
            .flatten()
            .forEach { cookie ->
                runCatching {
                    CookieManager.getInstance().setCookie(pageUrl, cookie)
                }
            }
        runCatching { CookieManager.getInstance().flush() }
        connection.disconnect()

        val injected = if (html.contains("<head>", true)) {
            html.replaceFirst(
                Regex("<head>", RegexOption.IGNORE_CASE),
                "<head>$HOOK_JS",
            )
        } else {
            "$HOOK_JS$html"
        }

        return WebResourceResponse(
            "text/html",
            "UTF-8",
            ByteArrayInputStream(injected.toByteArray(Charsets.UTF_8)),
        ).apply {
            responseHeaders = mapOf("Access-Control-Allow-Origin" to "*")
        }
    }

    private fun shouldInjectAbyssPage(url: String): Boolean {
        val value = url.lowercase()
        return value.contains("abyss") &&
            (value.contains("?v=") || value.contains("&v="))
    }

    private fun qualityScore(label: String, url: String): Int {
        val value = "${label.lowercase()} ${url.lowercase()}"
        return when {
            value.contains("2160") -> 2160
            value.contains("1440") -> 1440
            value.contains("1080") -> 1080
            value.contains("720") || value.contains("/1421764806/") -> 720
            value.contains("480") -> 480
            value.contains("360") || value.contains("/677311756/") -> 360
            else -> 0
        }
    }

    private fun guessLabel(url: String): String {
        val value = url.lowercase()
        return when {
            value.contains("2160") -> "2160p"
            value.contains("1440") -> "1440p"
            value.contains("1080") -> "1080p"
            value.contains("/1421764806/") || value.contains("720") -> "720p"
            value.contains("480") -> "480p"
            value.contains("/677311756/") || value.contains("360") -> "360p"
            else -> "Auto"
        }
    }

    private fun String.toAbsoluteUrl(baseUrl: String): String {
        val value = trim()
        return when {
            value.startsWith("//") -> "https:$value"
            value.startsWith("http", true) -> value
            else -> runCatching {
                URI(baseUrl).resolve(value).toString()
            }.getOrDefault(value)
        }
    }

    private fun htmlEscape(value: String): String =
        value
            .replace("&", "&amp;")
            .replace("\"", "&quot;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return (0 until length())
            .mapNotNull { index ->
                optString(index).trim().takeIf { it.isNotBlank() }
            }
    }

    private fun JSONArray?.toLongList(): List<Long> {
        if (this == null) return emptyList()
        return (0 until length())
            .mapNotNull { index ->
                when (val value = opt(index)) {
                    is Number -> value.toLong()
                    is String -> value.toLongOrNull()
                    else -> null
                }
            }
    }

    companion object {
        private const val BRIDGE_NAME = "vueoCapture"
        private const val DEFAULT_WEBVIEW_TIMEOUT_MS = 14_000L
        private const val MIN_WEBVIEW_TIMEOUT_MS = 1_000L
        private const val MAX_WEBVIEW_TIMEOUT_MS = 18_000L

        private const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/138.0 Mobile Safari/537.36"

        private val DEFAULT_CLICK_DELAYS_MS = listOf(
            650L,
            1_300L,
            2_200L,
            3_400L,
            5_000L,
            7_000L,
            9_500L,
            12_000L,
        )

        private val DEFAULT_MATCH_PARTS = listOf(
            "/sora/",
            ".m3u8",
            ".mp4",
            ".m4v",
        )

        private val DEFAULT_BLOCKED_PARTS = listOf(
            "googlesyndication",
            "doubleclick.net",
            "google-analytics",
            "googletagmanager",
            "vast",
            "pixel.morphify",
            "decafeligiblyhad",
            "algiersreests",
            "morestamping",
        )

        private const val HOOK_JS = """
<script>
(function() {
  if (window.__vueoHooked) return;
  window.__vueoHooked = true;

  function cap(value) {
    try {
      if (window.vueoCapture && window.vueoCapture.capture) {
        window.vueoCapture.capture(String(value));
      }
    } catch(e) {}
  }

  function abs(url) {
    if (!url) return "";
    url = String(url);
    if (url.indexOf("//") === 0) return "https:" + url;
    return url;
  }

  function sendSources(list) {
    try {
      if (!list || !list.length) return;
      for (var i = 0; i < list.length; i++) {
        var source = list[i] || {};
        var label = source.label || source.name || source.height || "Auto";
        var type = source.type || "";
        var file = source.file || source.url || "";
        if (file) {
          cap("VUEO_SOURCE|" + label + "|" + type + "|" + abs(file));
        }
      }
    } catch(e) {}
  }

  function inspectPlayer() {
    try {
      if (typeof window.jwplayer === "function") {
        var player = window.jwplayer();
        if (player) {
          if (player.getPlaylist) {
            var playlist = player.getPlaylist() || [];
            for (var i = 0; i < playlist.length; i++) {
              var item = playlist[i] || {};
              sendSources(item.sources);
              sendSources(item.allSources);
            }
          }
          if (player.getPlaylistItem) {
            var current = player.getPlaylistItem() || {};
            sendSources(current.sources);
            sendSources(current.allSources);
          }
          if (player.getConfig) {
            var config = player.getConfig() || {};
            sendSources(config.sources);
            if (config.playlist && config.playlist.length) {
              for (var c = 0; c < config.playlist.length; c++) {
                sendSources((config.playlist[c] || {}).sources);
                sendSources((config.playlist[c] || {}).allSources);
              }
            }
          }
        }
      }

      var videos = document.querySelectorAll("video");
      for (var v = 0; v < videos.length; v++) {
        var src = videos[v].currentSrc || videos[v].src || "";
        if (src) cap("VUEO_VIDEO|" + abs(src));
      }
    } catch(e) {}
  }

  try {
    var oldFetch = window.fetch;
    if (oldFetch) {
      window.fetch = function() {
        try { cap("VUEO_FETCH|" + abs(arguments[0])); } catch(e) {}
        return oldFetch.apply(this, arguments);
      };
    }
  } catch(e) {}

  try {
    var oldOpen = XMLHttpRequest.prototype.open;
    XMLHttpRequest.prototype.open = function(method, requestUrl) {
      try { cap("VUEO_XHR|" + abs(requestUrl)); } catch(e) {}
      return oldOpen.apply(this, arguments);
    };
  } catch(e) {}

  inspectPlayer();
  setTimeout(inspectPlayer, 300);
  setTimeout(inspectPlayer, 700);
  setTimeout(inspectPlayer, 1200);
  setTimeout(inspectPlayer, 2000);
  setInterval(inspectPlayer, 1000);
})();
</script>
        """

        private fun errorJson(message: String): String =
            JSONObject().apply {
                put("streams", JSONArray())
                put("error", message)
            }.toString()
    }
}
