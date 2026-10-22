package com.vueo.shared.core.player

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.vueo.shared.core.diagnostics.RuntimeDiagnostics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Opens the separately installed VLC app without forcing its decoder settings. */
class ExternalVlcActivity : Activity() {
    companion object {
        private const val URL = "vlc_source_url"
        private const val PLATFORM = "vlc_platform"
        private const val REQUEST_VLC = 71
        private const val VLC_PACKAGE = "org.videolan.vlc"
        fun intent(context: Context, url: String, positionMs: Long, platform: String): Intent =
            Intent(context, ExternalVlcActivity::class.java)
                .putExtra(URL, url)
                .putExtra(SoftwarePlaybackActivity.POSITION, positionMs.coerceAtLeast(0L))
                .putExtra(PLATFORM, platform)
    }

    private val scope = MainScope()
    @Volatile private var probeCall: Call? = null
    private lateinit var status: TextView
    private var launched = false
    private var foreground = false
    private var pending: Intent? = null
    private val url get() = intent.getStringExtra(URL).orEmpty()
    private val position get() = intent.getLongExtra(SoftwarePlaybackActivity.POSITION, 0L).coerceAtLeast(0L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED, Intent().putExtra(SoftwarePlaybackActivity.POSITION, position))
        status = TextView(this).apply {
            text = "Checking direct link for VLC…"
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setGravity(Gravity.CENTER)
            setBackgroundColor(Color.BLACK)
            setPadding(32, 24, 32, 24)
            addView(status)
            addView(Button(this@ExternalVlcActivity).apply { text = "Back"; setOnClickListener { finish() }; requestFocus() })
        })
        launched = savedInstanceState?.getBoolean("launched", false) ?: false
        if (launched) return
        val target = try {
            ExternalVlcPolicy.validateUrl(url)
            Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(url), "video/*")
                .setPackage(VLC_PACKAGE)
                .putExtra("position", position)
        } catch (_: Exception) {
            fail("Choose a direct HTTPS video source.", "invalid-url")
            return
        }
        if (target.resolveActivity(packageManager) == null) {
            fail("Install VLC for Android from Play Store, then try Open in VLC again.", "not-installed")
            return
        }
        scope.launch {
            try {
                val accepted = withContext(Dispatchers.IO) {
                    val client = OkHttpClient.Builder()
                        .connectTimeout(10L, TimeUnit.SECONDS)
                        .readTimeout(10L, TimeUnit.SECONDS)
                        .callTimeout(20L, TimeUnit.SECONDS)
                        .retryOnConnectionFailure(false)
                        .followSslRedirects(false)
                        .build()
                    // No provider UA/Referer/Auth/Cookie: VLC's ordinary VIEW intent
                    // cannot reliably forward them. Do not pretend they are sent.
                    val call = client.newCall(Request.Builder().url(url)
                        .header("Range", "bytes=0-31")
                        .header("Accept-Encoding", "identity").build())
                    probeCall = call
                    try {
                        currentCoroutineContext().ensureActive()
                        call.execute().use { response ->
                            val bytes = ByteArray(32)
                            var read = 0
                            response.body.byteStream().use { input ->
                                while (read < bytes.size) {
                                    val count = input.read(bytes, read, bytes.size - read)
                                    if (count < 0) break
                                    read += count
                                }
                            }
                            val mime = response.header("Content-Type").orEmpty()
                            val ok = response.request.url.isHttps && ExternalVlcPolicy.acceptsProbe(response.code, mime, bytes.copyOf(read))
                            log("EXTERNAL_VLC_PROBE", "http=${response.code} accepted=$ok providerHeadersSent=false bytes=$read")
                            ok
                        }
                    } finally {
                        probeCall = null
                        client.connectionPool.evictAll()
                        client.dispatcher.executorService.shutdown()
                    }
                }
                if (accepted) {
                    pending = target
                    launchPending()
                } else {
                    fail("VLC test unavailable: direct access check failed. The link may require provider headers, have expired, or use an unsupported format. This does not indicate a decoder failure.", "direct-access-rejected")
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                fail("Could not check this link for VLC. Refresh sources and try again. This does not indicate a decoder failure.", error.javaClass.simpleName)
            }
        }
    }

    private fun launchPending() {
        if (!foreground || launched || isFinishing) return
        val target = pending ?: return
        pending = null
        try {
            launched = true
            startActivityForResult(target, REQUEST_VLC)
            log("EXTERNAL_VLC_OPEN", "positionMs=$position settings=VLC-user-preferences")
        } catch (_: ActivityNotFoundException) {
            launched = false
            fail("VLC is unavailable. Install VLC for Android and try again.", "not-installed")
        } catch (_: SecurityException) {
            launched = false
            fail("Android could not open VLC. Check the installed VLC app.", "launch-denied")
        }
    }

    override fun onResume() { super.onResume(); foreground = true; launchPending() }
    override fun onPause() { foreground = false; super.onPause() }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_VLC) return
        val returnedUrl = data?.getStringExtra("extra_uri") ?: data?.dataString
        @Suppress("DEPRECATION")
        val returnedPosition = if (resultCode == RESULT_OK) {
            ExternalVlcPolicy.returnPosition(url, returnedUrl, data?.extras?.get("extra_position") as? Number)
        } else null
        log("EXTERNAL_VLC_RETURN", "result=$resultCode matchedPosition=${returnedPosition != null}")
        setResult(RESULT_OK, Intent().putExtra(SoftwarePlaybackActivity.POSITION, returnedPosition ?: position))
        finish()
    }

    private fun fail(message: String, reason: String) {
        if (isFinishing) return
        status.text = message
        log("EXTERNAL_VLC_UNAVAILABLE", "reason=$reason")
    }

    private fun log(event: String, details: String) {
        val source = java.security.MessageDigest.getInstance("SHA-256")
            .digest(url.toByteArray(Charsets.UTF_8)).take(8).joinToString("") { "%02x".format(it.toInt() and 255) }
        RuntimeDiagnostics.recordPlayerEvent(intent.getStringExtra(PLATFORM) ?: "Unknown", event, "source=$source $details")
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("launched", launched)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() { probeCall?.cancel(); scope.cancel(); super.onDestroy() }
}
