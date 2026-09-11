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
import com.vueo.shared.core.media.StreamTransportPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.net.URI
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume


private fun JSONArray?.toVueoStringList(): List<String> {
    if (this == null) return emptyList()
    return (0 until length())
        .mapNotNull { index ->
            optString(index).trim().takeIf { it.isNotBlank() }
        }
}

private fun JSONArray?.toVueoLongList(): List<Long> {
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
    private val webViewConcurrency = Semaphore(1)

    suspend fun resolveJson(requestJson: String): String {
        val request = runCatching {
            ResolveRequest.parse(requestJson)
        }.getOrElse { error ->
            return errorJson(
                error.message ?: "Invalid WebView resolver request."
            )
        }

        val validationError = withContext(Dispatchers.IO) {
            runCatching {
                PluginHttp.requirePublicHttpUrl(
                    url = request.url,
                    allowHttp = true,
                )
            }.exceptionOrNull()
        }
        if (validationError != null) {
            return errorJson(
                validationError.message
                    ?: "WebView resolver blocked a local/private URL."
            )
        }

        return webViewConcurrency.withPermit {
            withContext(Dispatchers.Main.immediate) {
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
                                        stream.streamType?.let { put("type", it) }
                                        stream.mimeType?.let { put("mimeType", it) }
                                        put("headers", JSONObject(stream.headers))
                                    }
                                )
                            }
                        },
                    )
                }.toString()
            }
        }
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
            val safeHosts = ConcurrentHashMap<String, Boolean>()
            var finishScheduled = false
            var destroyed = false

            fun isAllowedWebUrl(rawUrl: String?): Boolean {
                val value = rawUrl?.trim().orEmpty()
                if (value.isBlank()) return false
                val uri = runCatching { URI(value) }.getOrNull() ?: return false
                val scheme = uri.scheme?.lowercase()
                if (scheme != "http" && scheme != "https") return false
                if (PluginHttp.isClearlyLocalHttpUrl(value)) return false
                val host = uri.host?.lowercase()?.takeIf { it.isNotBlank() }
                    ?: return false
                return safeHosts.getOrPut(host) {
                    PluginHttp.isPublicHttpUrlBlocking(
                        url = value,
                        allowHttp = true,
                    )
                }
            }

            fun finalizedPlaybackHeaders(
                stream: CapturedStream,
            ): Map<String, String> {
                val headers = stream.headers
                    .filterKeys { key ->
                        key.lowercase() !in BLOCKED_PLAYBACK_HEADERS
                    }
                    .toMutableMap()

                val refreshedCookie = runCatching {
                    CookieManager.getInstance().getCookie(stream.url)
                }.getOrNull().orEmpty()

                if (refreshedCookie.isNotBlank()) {
                    headers["Cookie"] = refreshedCookie
                }

                headers["User-Agent"] =
                    headers["User-Agent"] ?: request.userAgent
                headers["Accept"] =
                    headers["Accept"] ?: "*/*"
                headers["Referer"] =
                    headers["Referer"]
                        ?: request.referer.ifBlank { request.url }

                return headers
            }

            fun transportScore(url: String): Int {
                val value = url.lowercase()
                return when {
                    value.contains(".m3u8") -> 30
                    value.contains(".mp4") -> 20
                    value.contains(".m4v") -> 20
                    value.contains("/sora/") -> 10
                    else -> 0
                }
            }

            fun sortedResult(): List<CapturedStream> =
                synchronized(streams) {
                    streams.values
                        .distinctBy { it.url }
                        .map { stream ->
                            stream.copy(
                                headers = finalizedPlaybackHeaders(stream),
                            )
                        }
                        .sortedWith(
                            compareByDescending<CapturedStream> {
                                transportScore(it.url)
                            }.thenByDescending {
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
                forcePlayable: Boolean = false,
                streamType: String? = null,
                mimeType: String? = null,
            ) {
                val fixedUrl = rawUrl
                    ?.trim()
                    ?.toAbsoluteUrl(request.url)
                    ?.takeIf { forcePlayable || request.isStreamUrl(it) }
                    ?.takeIf(::isAllowedWebUrl)
                    ?: return

                val normalizedMimeType = StreamTransportPolicy.playbackMimeType(
                    url = fixedUrl,
                    streamType = streamType,
                    mimeType = mimeType,
                )

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
                            streamType = streamType
                                ?.trim()
                                ?.takeIf { it.isNotBlank() },
                            mimeType = normalizedMimeType,
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
                                streamType = parts[2],
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

                    clean.startsWith("VUEO_CANDIDATE|") -> {
                        val parts = clean.split("|", limit = 3)
                        if (parts.size >= 3) {
                            val file = parts[2]
                            addStream(
                                label = guessLabel(file),
                                rawUrl = file,
                                headers = request.defaultHeaders(),
                            )
                        }
                    }

                    clean.startsWith("VUEO_RESPONSE|") -> {
                        val parts = clean.split("|", limit = 4)
                        if (parts.size >= 4) {
                            val mime = parts[1]
                            val length = parts[2].toLongOrNull() ?: -1L
                            val file = parts[3]
                            if (request.isPlayableResponse(file, mime, length)) {
                                addStream(
                                    label = guessLabel(file),
                                    rawUrl = file,
                                    headers = request.defaultHeaders(),
                                    forcePlayable = true,
                                    mimeType = mime,
                                )
                            }
                        }
                    }
                }
            }

            fun injectRuntimeHook(view: WebView?) {
                if (!request.directLoad) return
                runCatching {
                    val hookScript = HOOK_JS
                        .substringAfter("<script>")
                        .substringBeforeLast("</script>")
                    view?.evaluateJavascript(hookScript, null)
                }
            }

            fun clickWebView() {
                if (streams.isNotEmpty()) return

                if (request.directLoad) {
                    runCatching {
                        val interactionTextsJson =
                            JSONArray(request.interactionTexts).toString()
                        val searchTextJson =
                            JSONObject.quote(request.searchText)

                        webView.evaluateJavascript(
                            """
                            (function() {
                              try {
                                var wanted = $interactionTextsJson;
                                var searchText = $searchTextJson;

                                function norm(value) {
                                  return String(value || '')
                                    .toLowerCase()
                                    .replace(/\s+/g, ' ')
                                    .trim();
                                }

                                function visible(el) {
                                  if (!el) return false;
                                  var style = window.getComputedStyle(el);
                                  if (!style) return true;
                                  if (
                                    style.display === 'none' ||
                                    style.visibility === 'hidden' ||
                                    Number(style.opacity || '1') === 0
                                  ) return false;
                                  var rect = el.getBoundingClientRect();
                                  return rect.width > 0 && rect.height > 0;
                                }

                                function descriptor(el) {
                                  return norm(
                                    (el.innerText || el.textContent || '') + ' ' +
                                    (el.getAttribute('aria-label') || '') + ' ' +
                                    (el.getAttribute('title') || '') + ' ' +
                                    (el.getAttribute('placeholder') || '') + ' ' +
                                    (el.className || '')
                                  );
                                }

                                function clickElement(el) {
                                  if (!el) return false;
                                  try {
                                    el.scrollIntoView({
                                      block: 'center',
                                      inline: 'center'
                                    });
                                  } catch(e) {}

                                  try {
                                    el.dispatchEvent(
                                      new MouseEvent('mousedown', {
                                        bubbles: true,
                                        cancelable: true,
                                        view: window
                                      })
                                    );
                                    el.dispatchEvent(
                                      new MouseEvent('mouseup', {
                                        bubbles: true,
                                        cancelable: true,
                                        view: window
                                      })
                                    );
                                    el.click();
                                    return true;
                                  } catch(e) {
                                    return false;
                                  }
                                }

                                function setInputValue(input, value) {
                                  if (!input) return false;

                                  try {
                                    var proto =
                                      input.tagName &&
                                      input.tagName.toLowerCase() === 'textarea'
                                        ? window.HTMLTextAreaElement.prototype
                                        : window.HTMLInputElement.prototype;

                                    var descriptor =
                                      Object.getOwnPropertyDescriptor(
                                        proto,
                                        'value'
                                      );

                                    if (descriptor && descriptor.set) {
                                      descriptor.set.call(input, value);
                                    } else {
                                      input.value = value;
                                    }

                                    input.dispatchEvent(
                                      new Event('input', {
                                        bubbles: true
                                      })
                                    );
                                    input.dispatchEvent(
                                      new Event('change', {
                                        bubbles: true
                                      })
                                    );
                                    input.dispatchEvent(
                                      new KeyboardEvent('keydown', {
                                        key: 'Enter',
                                        code: 'Enter',
                                        keyCode: 13,
                                        which: 13,
                                        bubbles: true
                                      })
                                    );
                                    input.dispatchEvent(
                                      new KeyboardEvent('keyup', {
                                        key: 'Enter',
                                        code: 'Enter',
                                        keyCode: 13,
                                        which: 13,
                                        bubbles: true
                                      })
                                    );

                                    if (input.form) {
                                      try {
                                        if (input.form.requestSubmit) {
                                          input.form.requestSubmit();
                                        }
                                      } catch(e) {}
                                    }

                                    return true;
                                  } catch(e) {
                                    return false;
                                  }
                                }

                                var searchNorm = norm(searchText);

                                /*
                                 * Stage 1:
                                 * If a card/link for the requested title is already
                                 * visible, click it directly. This also handles search
                                 * results rendered after a previous interaction tick.
                                 */
                                if (searchNorm) {
                                  var titleLinks =
                                    document.querySelectorAll(
                                      'a[href],button,[role="button"]'
                                    );

                                  for (
                                    var t = 0;
                                    t < titleLinks.length;
                                    t++
                                  ) {
                                    var titleEl = titleLinks[t];
                                    if (!visible(titleEl)) continue;

                                    var titleDesc = descriptor(titleEl);
                                    if (
                                      titleDesc &&
                                      titleDesc.indexOf(searchNorm) !== -1
                                    ) {
                                      if (clickElement(titleEl)) {
                                        return 'title-click';
                                      }
                                    }
                                  }
                                }

                                /*
                                 * Stage 2:
                                 * Open CineMode's search UI if it is collapsed.
                                 */
                                if (searchNorm) {
                                  var searchButtons =
                                    document.querySelectorAll(
                                      'button,a,[role="button"]'
                                    );

                                  for (
                                    var s = 0;
                                    s < searchButtons.length;
                                    s++
                                  ) {
                                    var searchButton = searchButtons[s];
                                    if (!visible(searchButton)) continue;

                                    var searchDesc =
                                      descriptor(searchButton);

                                    if (
                                      searchDesc === 'search' ||
                                      searchDesc.indexOf(' search ') !== -1 ||
                                      searchDesc.indexOf('search button') !== -1 ||
                                      searchDesc.indexOf('open search') !== -1
                                    ) {
                                      clickElement(searchButton);
                                      break;
                                    }
                                  }

                                  /*
                                   * Stage 3:
                                   * Fill any available search input. React controlled
                                   * inputs need the native value setter plus input and
                                   * change events.
                                   */
                                  var searchInputs =
                                    document.querySelectorAll(
                                      'input[type="search"],' +
                                      'input[placeholder*="search" i],' +
                                      'input[aria-label*="search" i],' +
                                      'input[name*="search" i],' +
                                      'input[name="q"]'
                                    );

                                  for (
                                    var q = 0;
                                    q < searchInputs.length;
                                    q++
                                  ) {
                                    var input = searchInputs[q];
                                    if (!visible(input)) continue;

                                    var current = norm(input.value);
                                    if (current !== searchNorm) {
                                      setInputValue(input, searchText);
                                      return 'search-fill';
                                    }
                                  }
                                }

                                /*
                                 * Stage 4:
                                 * Once on the detail/player page, interact with Watch,
                                 * Play, Continue, Skip and Close controls.
                                 */
                                var nodes = Array.prototype.slice.call(
                                  document.querySelectorAll(
                                    'button,a,[role="button"],' +
                                    '[class*="play" i],[class*="watch" i],' +
                                    '[class*="skip" i],[class*="close" i]'
                                  )
                                );

                                var best = null;
                                var bestScore = 9999;

                                for (
                                  var n = 0;
                                  n < nodes.length;
                                  n++
                                ) {
                                  var el = nodes[n];
                                  if (!visible(el)) continue;

                                  var desc = descriptor(el);
                                  if (!desc) continue;

                                  for (
                                    var i = 0;
                                    i < wanted.length;
                                    i++
                                  ) {
                                    var needle = norm(wanted[i]);
                                    if (!needle) continue;

                                    if (
                                      desc === needle ||
                                      desc.indexOf(needle) !== -1
                                    ) {
                                      if (i < bestScore) {
                                        best = el;
                                        bestScore = i;
                                      }
                                      break;
                                    }
                                  }
                                }

                                if (best) {
                                  clickElement(best);
                                }

                                var videos =
                                  document.querySelectorAll('video');

                                for (
                                  var v = 0;
                                  v < videos.length;
                                  v++
                                ) {
                                  try {
                                    videos[v].muted = true;
                                    videos[v].playsInline = true;

                                    var p = videos[v].play();
                                    if (p && p.catch) {
                                      p.catch(function(){});
                                    }
                                  } catch(e) {}
                                }

                                var common = [
                                  '.vjs-big-play-button',
                                  '.jw-icon-display',
                                  'button[aria-label*="play" i]',
                                  '[data-testid*="play" i]'
                                ];

                                for (
                                  var c = 0;
                                  c < common.length;
                                  c++
                                ) {
                                  var control =
                                    document.querySelector(common[c]);

                                  if (
                                    !control ||
                                    !visible(control)
                                  ) continue;

                                  clickElement(control);
                                  break;
                                }

                                return 'interaction';
                              } catch(e) {
                                return 'interaction-error';
                              }
                            })();
                            """.trimIndent(),
                            null,
                        )
                    }
                }

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
                    allowFileAccess = false
                    allowContentAccess = false
                    allowFileAccessFromFileURLs = false
                    allowUniversalAccessFromFileURLs = false
                    mediaPlaybackRequiresUserGesture = false
                    loadsImagesAutomatically = true
                    javaScriptCanOpenWindowsAutomatically = !request.suppressPopups
                    setSupportMultipleWindows(false)
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    userAgentString = request.userAgent
                }

                val targetHost = runCatching {
                    URI(request.url).host.orEmpty().lowercase()
                }.getOrDefault("")

                webView.webChromeClient = WebChromeClient()
                webView.webViewClient = object : WebViewClient() {
                    override fun onPageStarted(
                        view: WebView?,
                        url: String?,
                        favicon: Bitmap?,
                    ) {
                        injectRuntimeHook(view)
                    }

                    override fun onPageCommitVisible(
                        view: WebView?,
                        url: String?,
                    ) {
                        injectRuntimeHook(view)
                    }

                    override fun onPageFinished(
                        view: WebView?,
                        url: String?,
                    ) {
                        injectRuntimeHook(view)
                    }

                    override fun shouldInterceptRequest(
                        view: WebView?,
                        webRequest: WebResourceRequest?,
                    ): WebResourceResponse? {
                        val requestUrl = webRequest?.url?.toString().orEmpty()

                        if (
                            requestUrl.startsWith("http://", ignoreCase = true) ||
                            requestUrl.startsWith("https://", ignoreCase = true)
                        ) {
                            if (!isAllowedWebUrl(requestUrl)) {
                                return blockedWebResponse()
                            }
                        }

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

                            /*
                             * Capture must be passive by default. Older builds
                             * returned an empty response here, which stopped
                             * JS/MSE players after their first source/API hit
                             * and prevented the downstream manifest request from
                             * ever being revealed.
                             */
                            if (request.blockMatchedRequests) {
                                return WebResourceResponse(
                                    "video/mp4",
                                    "UTF-8",
                                    ByteArrayInputStream(ByteArray(0)),
                                )
                            }
                        }

                        return super.shouldInterceptRequest(view, webRequest)
                    }

                    @Deprecated("Deprecated in Android")
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        url: String?,
                    ): Boolean {
                        val targetUrl = url.orEmpty()
                        if (
                            targetUrl.startsWith("http", ignoreCase = true) &&
                            PluginHttp.isClearlyLocalHttpUrl(targetUrl)
                        ) return true
                        if (!request.lockMainFrameHost) return false
                        val host = runCatching {
                            URI(targetUrl).host.orEmpty().lowercase()
                        }.getOrDefault("")
                        return targetHost.isNotBlank() &&
                            host.isNotBlank() &&
                            host != targetHost
                    }

                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        webRequest: WebResourceRequest?,
                    ): Boolean {
                        if (webRequest?.isForMainFrame != true) return false
                        val targetUrl = webRequest.url?.toString().orEmpty()
                        if (
                            targetUrl.startsWith("http", ignoreCase = true) &&
                            PluginHttp.isClearlyLocalHttpUrl(targetUrl)
                        ) return true
                        if (!request.lockMainFrameHost) return false

                        val host = runCatching {
                            webRequest.url?.host.orEmpty().lowercase()
                        }.getOrDefault("")

                        return targetHost.isNotBlank() &&
                            host.isNotBlank() &&
                            host != targetHost
                    }
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

                if (request.directLoad) {
                    val initialHeaders = mutableMapOf<String, String>()
                    if (request.referer.isNotBlank()) {
                        initialHeaders["Referer"] = request.referer
                    }
                    webView.loadUrl(request.url, initialHeaders)
                } else {
                    webView.loadDataWithBaseURL(
                        request.referer.ifBlank { request.url },
                        wrapper,
                        "text/html",
                        "UTF-8",
                        null,
                    )
                }

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
        val streamType: String? = null,
        val mimeType: String? = null,
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
        val directLoad: Boolean,
        val interactionTexts: List<String>,
        val searchText: String,
        val suppressPopups: Boolean,
        val lockMainFrameHost: Boolean,
        val viewportWidth: Int,
        val viewportHeight: Int,
        val clickX: Float,
        val clickY: Float,
        val blockMatchedRequests: Boolean,
    ) {
        fun isStreamUrl(rawUrl: String?): Boolean {
            val value = rawUrl?.lowercase().orEmpty()
            if (value.isBlank()) return false
            if (blockedParts.any(value::contains)) return false

            // Known final transports always stay eligible, even when a
            // provider supplies extra match probes for an API/player endpoint.
            if (DEFAULT_MATCH_PARTS.any(value::contains)) return true

            // Source-discovery endpoints are probes, not playable streams.
            // Treating them as final used to schedule an early WebView finish.
            if (DEFAULT_DISCOVERY_PARTS.any(value::contains)) return false

            return matchParts.any(value::contains)
        }

        fun isPlayableResponse(
            rawUrl: String?,
            contentType: String?,
            contentLength: Long,
        ): Boolean {
            val value = rawUrl?.lowercase().orEmpty()
            if (value.isBlank()) return false
            if (blockedParts.any(value::contains)) return false
            if (looksLikeMediaSegment(value)) return false
            if (isStreamUrl(value)) return true

            val mime = contentType?.lowercase().orEmpty()
            return when {
                mime.contains("application/vnd.apple.mpegurl") -> true
                mime.contains("application/x-mpegurl") -> true
                mime.contains("application/dash+xml") -> true
                mime.startsWith("video/mp4") -> contentLength >= MIN_DIRECT_VIDEO_BYTES
                mime.startsWith("video/webm") -> contentLength >= MIN_DIRECT_VIDEO_BYTES
                mime.startsWith("video/x-m4v") -> contentLength >= MIN_DIRECT_VIDEO_BYTES
                else -> false
            }
        }

        private fun looksLikeMediaSegment(value: String): Boolean =
            SEGMENT_PARTS.any(value::contains)

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

                val matchParts = (
                    DEFAULT_MATCH_PARTS +
                        json.optJSONArray("match")
                            .toVueoStringList()
                            .map { it.lowercase() }
                ).distinct()

                val blockedParts = json.optJSONArray("blocked")
                    .toVueoStringList()
                    .map { it.lowercase() }
                    .ifEmpty { DEFAULT_BLOCKED_PARTS }

                val clickDelays = json.optJSONArray("clickDelaysMs")
                    .toVueoLongList()
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
                    directLoad = json.optBoolean("directLoad", false),
                    interactionTexts = json.optJSONArray("interactionTexts")
                        .toVueoStringList()
                        .map { it.lowercase() }
                        .ifEmpty { DEFAULT_INTERACTION_TEXTS },
                    searchText = json.optString("searchText").trim(),
                    suppressPopups = json.optBoolean("suppressPopups", false),
                    lockMainFrameHost = json.optBoolean("lockMainFrameHost", false),
                    viewportWidth = viewportWidth,
                    viewportHeight = viewportHeight,
                    clickX = json.optDouble("clickX", viewportWidth / 2.0)
                        .toFloat(),
                    clickY = json.optDouble("clickY", viewportHeight / 2.0)
                        .toFloat(),
                    blockMatchedRequests = json.optBoolean(
                        "blockMatchedRequests",
                        false,
                    ),
                )
            }
        }
    }

    private fun injectIntoAbyssPage(
        pageUrl: String,
        referer: String,
        userAgent: String,
    ): WebResourceResponse {
        val response = PluginHttp.client.newCall(
            Request.Builder()
                .url(pageUrl)
                .header("User-Agent", userAgent)
                .header("Referer", referer)
                .header(
                    "Accept",
                    "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                )
                .header(
                    "Accept-Language",
                    "ms-MY,ms;q=0.9,en-US;q=0.8,en;q=0.7",
                )
                .header("Accept-Encoding", "identity")
                .build()
        ).execute()

        val html = response.use { safeResponse ->
            if (!safeResponse.isSuccessful) {
                error("HTTP ${safeResponse.code} while resolving embedded page.")
            }

            safeResponse.headers("Set-Cookie").forEach { cookie ->
                runCatching {
                    CookieManager.getInstance().setCookie(pageUrl, cookie)
                }
            }
            runCatching { CookieManager.getInstance().flush() }
            safeResponse.body.string()
        }

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

    private fun blockedWebResponse(): WebResourceResponse =
        WebResourceResponse(
            "text/plain",
            "UTF-8",
            ByteArrayInputStream(ByteArray(0)),
        )

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


    companion object {
        private const val BRIDGE_NAME = "vueoCapture"
        private const val DEFAULT_WEBVIEW_TIMEOUT_MS = 10_000L
        private const val MIN_WEBVIEW_TIMEOUT_MS = 1_000L
        private const val MAX_WEBVIEW_TIMEOUT_MS = 12_000L

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

        private val DEFAULT_INTERACTION_TEXTS = listOf(
            "watch now",
            "start watching",
            "watch",
            "play now",
            "play",
            "continue",
            "skip ad",
            "skip",
            "close ad",
            "close",
        )

        private val DEFAULT_MATCH_PARTS = listOf(
            "/sora/",
            ".m3u8",
            ".mpd",
            ".mp4",
            ".m4v",
            ".webm",
            ".mkv",
        )

        private val DEFAULT_DISCOVERY_PARTS = listOf(
            "/backend_/sources/",
            "/backend/sources/",
            "/api/source/",
            "/api/sources/",
            "/sources/berkas",
            "/sources/valstrax",
            "/sources/burat",
            "/sources/zinogre",
            "/sources/daedalus",
        )

        private val SEGMENT_PARTS = listOf(
            ".m4s",
            ".cmfv",
            ".cmfa",
            ".ts?",
            "/segment/",
            "/segments/",
            "/chunk/",
            "/chunks/",
            "seg-",
            "segment-",
            "fragment-",
            "init.mp4",
        )

        private const val MIN_DIRECT_VIDEO_BYTES = 512L * 1024L

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

        private val BLOCKED_PLAYBACK_HEADERS = setOf(
            "host",
            "connection",
            "accept-encoding",
            "range",
            "origin",
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
    try {
      if (typeof url === "object" && url.url) url = url.url;
    } catch(e) {}
    url = String(url || "");
    if (url.indexOf("//") === 0) return "https:" + url;
    try { return new URL(url, document.baseURI).href; } catch(e) {}
    return url;
  }

  function normalizeText(value) {
    return String(value || "")
      .replace(/\\u002f/ig, "/")
      .replace(/\\\//g, "/")
      .replace(/&amp;/g, "&");
  }

  function emitUrls(tag, value) {
    try {
      var text = normalizeText(value);
      if (!text || text.length > 262144) return;
      var matcher = /(https?:\/\/[^\s"'<>\\]+|\/\/[^\s"'<>\\]+)/ig;
      var count = 0;
      var match;
      while ((match = matcher.exec(text)) && count < 32) {
        cap("VUEO_CANDIDATE|" + String(tag || "scan") + "|" + abs(match[1]));
        count++;
      }
    } catch(e) {}
  }

  function scanValue(value, tag, depth, seen) {
    try {
      if (depth > 4 || value == null) return;
      if (typeof value === "string") {
        emitUrls(tag, value);
        return;
      }
      if (typeof value !== "object") return;
      seen = seen || (typeof WeakSet !== "undefined" ? new WeakSet() : null);
      if (seen) {
        if (seen.has(value)) return;
        seen.add(value);
      }
      if (Array.isArray(value)) {
        for (var i = 0; i < value.length && i < 64; i++) {
          scanValue(value[i], tag, depth + 1, seen);
        }
        return;
      }
      var keys = Object.keys(value);
      for (var k = 0; k < keys.length && k < 64; k++) {
        scanValue(value[keys[k]], tag, depth + 1, seen);
      }
    } catch(e) {}
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

      var videos = document.querySelectorAll("video,source");
      for (var v = 0; v < videos.length; v++) {
        var src = videos[v].currentSrc || videos[v].src || videos[v].getAttribute("src") || "";
        if (src && String(src).indexOf("blob:") !== 0) {
          cap("VUEO_VIDEO|" + abs(src));
        }
      }
    } catch(e) {}
  }

  function inspectResponse(response) {
    try {
      if (!response) return;
      var type = "";
      var length = "-1";
      try { type = response.headers.get("content-type") || ""; } catch(e) {}
      try { length = response.headers.get("content-length") || "-1"; } catch(e) {}
      var responseUrl = abs(response.url || "");
      if (responseUrl) {
        cap("VUEO_RESPONSE|" + type + "|" + length + "|" + responseUrl);
      }

      var lower = String(type || "").toLowerCase();
      var numericLength = parseInt(length || "-1", 10);
      var textLike =
        lower.indexOf("json") !== -1 ||
        lower.indexOf("text/") === 0 ||
        lower.indexOf("javascript") !== -1 ||
        lower.indexOf("xml") !== -1 ||
        lower === "";

      if (textLike && (numericLength < 0 || numericLength <= 524288)) {
        response.clone().text().then(function(text) {
          emitUrls("fetch-response", text);
        }).catch(function(){});
      }
    } catch(e) {}
  }

  try {
    var oldFetch = window.fetch;
    if (oldFetch) {
      window.fetch = function() {
        var requestUrl = "";
        try { requestUrl = abs(arguments[0]); } catch(e) {}
        if (requestUrl) cap("VUEO_FETCH|" + requestUrl);
        return oldFetch.apply(this, arguments).then(function(response) {
          inspectResponse(response);
          return response;
        });
      };
    }
  } catch(e) {}

  try {
    var oldOpen = XMLHttpRequest.prototype.open;
    var oldSend = XMLHttpRequest.prototype.send;
    XMLHttpRequest.prototype.open = function(method, requestUrl) {
      try {
        this.__vueoRequestUrl = abs(requestUrl);
        cap("VUEO_XHR|" + this.__vueoRequestUrl);
      } catch(e) {}
      return oldOpen.apply(this, arguments);
    };
    XMLHttpRequest.prototype.send = function() {
      try {
        if (!this.__vueoCaptureBound) {
          this.__vueoCaptureBound = true;
          this.addEventListener("load", function() {
            try {
              var responseUrl = abs(this.responseURL || this.__vueoRequestUrl || "");
              var type = this.getResponseHeader("content-type") || "";
              var length = this.getResponseHeader("content-length") || "-1";
              if (responseUrl) {
                cap("VUEO_RESPONSE|" + type + "|" + length + "|" + responseUrl);
              }
              var lower = String(type || "").toLowerCase();
              var textLike =
                lower.indexOf("json") !== -1 ||
                lower.indexOf("text/") === 0 ||
                lower.indexOf("javascript") !== -1 ||
                lower.indexOf("xml") !== -1 ||
                lower === "";
              if (textLike && (!this.responseType || this.responseType === "text")) {
                emitUrls("xhr-response", this.responseText || "");
              }
            } catch(e) {}
          });
        }
      } catch(e) {}
      return oldSend.apply(this, arguments);
    };
  } catch(e) {}

  try {
    var oldJsonParse = JSON.parse;
    JSON.parse = function(text, reviver) {
      var result = oldJsonParse.apply(this, arguments);
      try {
        setTimeout(function() {
          scanValue(result, "json-parse", 0, null);
        }, 0);
      } catch(e) {}
      return result;
    };
  } catch(e) {}

  try {
    var oldAtob = window.atob;
    if (oldAtob) {
      window.atob = function(value) {
        var decoded = oldAtob.apply(this, arguments);
        try {
          if (decoded && decoded.length <= 262144) emitUrls("atob", decoded);
        } catch(e) {}
        return decoded;
      };
    }
  } catch(e) {}

  try {
    if (window.crypto && window.crypto.subtle && window.crypto.subtle.decrypt) {
      var oldDecrypt = window.crypto.subtle.decrypt.bind(window.crypto.subtle);
      window.crypto.subtle.decrypt = function() {
        return oldDecrypt.apply(this, arguments).then(function(result) {
          try {
            if (result && result.byteLength && result.byteLength <= 524288) {
              var decoded = new TextDecoder("utf-8").decode(result);
              emitUrls("webcrypto-decrypt", decoded);
            }
          } catch(e) {}
          return result;
        });
      };
    }
  } catch(e) {}

  try {
    var oldSetAttribute = Element.prototype.setAttribute;
    Element.prototype.setAttribute = function(name, value) {
      try {
        if (String(name || "").toLowerCase() === "src") {
          var tag = String(this.tagName || "").toLowerCase();
          if (tag === "video" || tag === "source") {
            var mediaUrl = abs(value);
            if (mediaUrl && mediaUrl.indexOf("blob:") !== 0) {
              cap("VUEO_VIDEO|" + mediaUrl);
            }
          }
        }
      } catch(e) {}
      return oldSetAttribute.apply(this, arguments);
    };
  } catch(e) {}

  try {
    var observer = new MutationObserver(function() {
      inspectPlayer();
    });
    observer.observe(document.documentElement || document, {
      subtree: true,
      childList: true,
      attributes: true,
      attributeFilter: ["src"]
    });
  } catch(e) {}

  try {
    if (window.URL && window.URL.createObjectURL) {
      var oldCreateObjectURL = window.URL.createObjectURL.bind(window.URL);
      window.URL.createObjectURL = function(value) {
        var objectUrl = oldCreateObjectURL(value);
        try {
          var type = value && value.type ? String(value.type) : "";
          var size = value && typeof value.size === "number" ? value.size : -1;
          cap("VUEO_BLOB|" + type + "|" + size + "|" + objectUrl);
        } catch(e) {}
        return objectUrl;
      };
    }
  } catch(e) {}

  try {
    if (window.MediaSource && window.MediaSource.prototype.addSourceBuffer) {
      var oldAddSourceBuffer = window.MediaSource.prototype.addSourceBuffer;
      window.MediaSource.prototype.addSourceBuffer = function(mime) {
        try { cap("VUEO_MSE|" + String(mime || "")); } catch(e) {}
        return oldAddSourceBuffer.apply(this, arguments);
      };
    }
  } catch(e) {}

  inspectPlayer();
  setTimeout(inspectPlayer, 250);
  setTimeout(inspectPlayer, 600);
  setTimeout(inspectPlayer, 1000);
  setTimeout(inspectPlayer, 1800);
  setInterval(inspectPlayer, 900);
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
