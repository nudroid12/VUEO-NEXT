package com.vueo.shared.core.player

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import com.vueo.shared.core.diagnostics.RuntimeDiagnostics
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

/** Explicit, independent decoder test shared by Mobile and TV. Never auto-selected. */
class SoftwarePlaybackActivity : Activity() {
    companion object {
        const val POSITION = "software_position_ms"
        private const val URL = "software_source_url"
        private const val HEADERS = "software_source_headers"
        private const val PLATFORM = "software_platform"
        private const val LANGUAGE = "software_original_language"

        fun intent(context: Context, url: String, headers: Map<String, String>, positionMs: Long, platform: String, originalLanguage: String?): Intent =
            Intent(context, SoftwarePlaybackActivity::class.java)
                .putExtra(URL, url)
                .putExtra(HEADERS, Bundle().apply { headers.forEach { (k, v) -> putString(k, v) } })
                .putExtra(POSITION, positionMs.coerceAtLeast(0L))
                .putExtra(PLATFORM, platform)
                .putExtra(LANGUAGE, originalLanguage)
    }

    private var engine: LibVLC? = null
    private var player: MediaPlayer? = null
    private var media: Media? = null
    private lateinit var video: VLCVideoLayout
    private lateinit var chrome: LinearLayout
    private lateinit var status: TextView
    private lateinit var progress: SeekBar
    private lateinit var playButton: Button
    private val handler = Handler(Looper.getMainLooper())
    private var positionMs = 0L
    private var durationMs = 0L
    private var resumePlaying = true
    private var seeking = false
    private var firstPicture = false
    private var failed = false
    private var startedAt = 0L
    private var lastStatsAt = 0L
    private var timelineStarted = false
    private val audioManager by lazy { getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        if (change <= AudioManager.AUDIOFOCUS_LOSS) {
            resumePlaying = false
            player?.pause()
        }
    }
    private val sourceTag by lazy {
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(intent.getStringExtra(URL).orEmpty().toByteArray(Charsets.UTF_8)).take(8)
            .joinToString("") { "%02x".format(it.toInt() and 255) }
    }
    private val platform get() = intent.getStringExtra(PLATFORM) ?: "Unknown"
    private val tick = object : Runnable {
        override fun run() {
            val current = player ?: return
            snapshot()
            if (!seeking && durationMs > 0) progress.progress = ((positionMs.toDouble() / durationMs) * 1000).toInt()
            val stats = media?.stats
            if (!firstPicture && (stats?.displayedPictures ?: 0) > 0) {
                firstPicture = true
                log("SOFTWARE_FIRST_PICTURE", "elapsedMs=${SystemClock.elapsedRealtime() - startedAt} decoded=${stats?.decodedVideo} displayed=${stats?.displayedPictures}")
                status.text = "Software video • ${clock(positionMs)} / ${clock(durationMs)}"
            } else if (firstPicture && !failed) {
                status.text = "Software video • ${clock(positionMs)} / ${clock(durationMs)}"
            } else if (!failed && !resumePlaying) {
                status.text = "Software test • paused"
            } else if (!failed) {
                status.text = "Software test • waiting for video (${(SystemClock.elapsedRealtime() - startedAt) / 1000L}s)"
            }
            val now = SystemClock.elapsedRealtime()
            if (now - lastStatsAt >= 5000L) {
                lastStatsAt = now
                log("SOFTWARE_STATS", "positionMs=$positionMs decoded=${stats?.decodedVideo} displayed=${stats?.displayedPictures} lost=${stats?.lostPictures} playing=${current.isPlaying}")
            }
            playButton.text = if (current.isPlaying) "Pause" else "Play"
            handler.postDelayed(this, 500L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        volumeControlStream = AudioManager.STREAM_MUSIC
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or 4096
        positionMs = savedInstanceState?.getLong(POSITION) ?: intent.getLongExtra(POSITION, 0L)
        resumePlaying = savedInstanceState?.getBoolean("playing", true) ?: true
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        video = VLCVideoLayout(this)
        root.addView(video, FrameLayout.LayoutParams(-1, -1))
        chrome = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 8, 16, 8)
            setBackgroundColor(0xDD111111.toInt())
        }
        status = TextView(this).apply { text = "Software test • opening…"; setTextColor(Color.WHITE); textSize = 16f }
        chrome.addView(status)
        progress = SeekBar(this).apply { max = 1000 }
        progress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(seekBar: SeekBar) { seeking = true }
            override fun onStopTrackingTouch(seekBar: SeekBar) { seeking = false }
            override fun onProgressChanged(seekBar: SeekBar, value: Int, fromUser: Boolean) {
                if (fromUser && durationMs > 0L && player?.isSeekable == true) player?.setTime((durationMs * (value / 1000.0)).toLong())
            }
        })
        chrome.addView(progress)
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun button(label: String, action: () -> Unit): Button = Button(this).apply {
            text = label
            setOnClickListener { action() }
            buttons.addView(this)
        }
        playButton = button("Pause") { togglePlayback() }
        button("−10s") { seek(-10_000L) }
        button("+10s") { seek(10_000L) }
        button("Audio") { chooseTrack(false) }
        button("Subtitles") { chooseTrack(true) }
        button("Hide") { chrome.visibility = View.GONE }
        button("Back") { finish() }
        chrome.addView(HorizontalScrollView(this).apply { addView(buttons) })
        root.addView(chrome, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        root.setOnClickListener { chrome.visibility = if (chrome.visibility == View.VISIBLE) View.GONE else View.VISIBLE }
        setContentView(root)
        playButton.requestFocus()
    }

    override fun onStart() {
        super.onStart()
        startedAt = SystemClock.elapsedRealtime()
        lastStatsAt = startedAt
        firstPicture = false
        timelineStarted = false
        failed = false
        try {
            val url = intent.getStringExtra(URL).orEmpty()
            val bundle = intent.getBundleExtra(HEADERS)
            val headers = bundle?.keySet()?.associateWith { bundle.getString(it).orEmpty() }.orEmpty()
            val options = SoftwarePlaybackPolicy.mediaOptions(url, headers, positionMs)
            // Restrict the codec chain to VLC's software module. No MediaCodec fallback.
            val lib = LibVLC(this, arrayListOf("--no-video-title-show", "--codec=avcodec", "--no-audio-time-stretch"))
            engine = lib
            val current = MediaPlayer(lib)
            player = current
            current.attachViews(video, null, true, false)
            val input = Media(lib, Uri.parse(url))
            media = input
            input.setHWDecoderEnabled(false, false)
            input.addOption(":codec=avcodec")
            intent.getStringExtra(LANGUAGE)?.takeIf { it.matches(Regex("[A-Za-z]{2,3}")) }?.let {
                input.addOption(":audio-language=$it")
            }
            options.forEach(input::addOption)
            current.setEventListener { event ->
                when (event.type) {
                    MediaPlayer.Event.Playing -> {
                        status.text = "Software test • waiting for video…"
                        log("SOFTWARE_PLAYING", "positionMs=${current.time}")
                        for (i in 0 until input.trackCount) {
                            val track = input.getTrack(i)
                            if (track is org.videolan.libvlc.interfaces.IMedia.VideoTrack) {
                                log("SOFTWARE_FORMAT", "codec=${track.codec} profile=${track.profile} level=${track.level} width=${track.width} height=${track.height} fps=${track.frameRateNum}/${track.frameRateDen}")
                            }
                        }
                        if (!resumePlaying) current.pause()
                    }
                    MediaPlayer.Event.TimeChanged -> {
                        if (event.timeChanged > 0L || positionMs == 0L) timelineStarted = true
                    }
                    MediaPlayer.Event.EncounteredError -> fail("Software engine could not play this source.")
                    MediaPlayer.Event.EndReached -> {
                        snapshot()
                        resumePlaying = false
                        status.text = "Software test • ended"
                        log("SOFTWARE_ENDED", "positionMs=$positionMs")
                    }
                }
            }
            current.media = input
            log("SOFTWARE_START", "engine=LibVLC-3.7.7 decoder=avcodec hardware=false resumeMs=$positionMs headers=${headers.size}")
            if (!resumePlaying) {
                status.text = "Software test • paused"
            } else if (audioManager.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                current.play()
            } else {
                fail("Audio focus is unavailable.", "audio-focus")
            }
            handler.post(tick)
        } catch (error: Exception) {
            fail(if (error is IllegalArgumentException) error.message ?: "Invalid source." else "Software engine could not start.", error.javaClass.simpleName)
            releaseEngine()
        } catch (error: LinkageError) {
            fail("Software engine is unavailable in this build.", error.javaClass.simpleName)
            releaseEngine()
        }
    }

    private fun log(event: String, details: String) =
        RuntimeDiagnostics.recordPlayerEvent(platform, event, "source=$sourceTag $details")

    private fun fail(message: String, reason: String = "media-error") {
        failed = true
        resumePlaying = false
        player?.pause()
        status.text = "$message Press Back to return."
        chrome.visibility = View.VISIBLE
        log("SOFTWARE_ERROR", "reason=$reason positionMs=$positionMs elapsedMs=${SystemClock.elapsedRealtime() - startedAt} firstPicture=$firstPicture")
    }

    private fun snapshot() {
        player?.let { current ->
            if (!failed && timelineStarted && current.time >= 0L) positionMs = current.time
            if (current.length > 0L) durationMs = current.length
        }
        setResult(RESULT_OK, Intent().putExtra(POSITION, positionMs))
    }

    private fun togglePlayback() {
        player?.let { current ->
            if (current.isPlaying) { resumePlaying = false; current.pause() }
            else if (!failed && audioManager.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                resumePlaying = true
                current.play()
            }
        }
    }

    private fun seek(deltaMs: Long) {
        player?.takeIf { it.isSeekable }?.let { it.setTime(SoftwarePlaybackPolicy.seekTarget(it.time, deltaMs, it.length)) }
    }

    private fun chooseTrack(subtitles: Boolean) {
        val current = player ?: return
        val tracks = if (subtitles) current.spuTracks else current.audioTracks
        if (tracks.isNullOrEmpty()) return
        val selected = if (subtitles) current.spuTrack else current.audioTrack
        AlertDialog.Builder(this)
            .setTitle(if (subtitles) "Subtitles" else "Audio")
            .setSingleChoiceItems(tracks.map { it.name }.toTypedArray(), tracks.indexOfFirst { it.id == selected }) { dialog, index ->
                val next = tracks[index]
                if (subtitles) current.setSpuTrack(next.id) else current.setAudioTrack(next.id)
                log("SOFTWARE_TRACK", "kind=${if (subtitles) "text" else "audio"} id=${next.id}")
                dialog.dismiss()
            }.setNegativeButton("Cancel", null).show()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_SPACE -> { togglePlayback(); true }
        KeyEvent.KEYCODE_MEDIA_REWIND -> { seek(-10_000L); true }
        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { seek(10_000L); true }
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
            if (chrome.visibility != View.VISIBLE) { chrome.visibility = View.VISIBLE; playButton.requestFocus(); true }
            else super.onKeyDown(keyCode, event)
        }
        else -> super.onKeyDown(keyCode, event)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        snapshot()
        outState.putLong(POSITION, positionMs)
        outState.putBoolean("playing", resumePlaying)
        super.onSaveInstanceState(outState)
    }

    override fun finish() { snapshot(); super.finish() }

    override fun onStop() {
        snapshot()
        log("SOFTWARE_STOP", "positionMs=$positionMs firstPicture=$firstPicture")
        releaseEngine()
        super.onStop()
    }

    private fun releaseEngine() {
        handler.removeCallbacks(tick)
        audioManager.abandonAudioFocus(focusListener)
        player?.let { current ->
            current.setEventListener(null)
            runCatching { current.stop() }
            runCatching { current.detachViews() }
            runCatching { current.release() }
        }
        player = null
        media?.release()
        media = null
        engine?.release()
        engine = null
    }

    private fun clock(ms: Long): String {
        val seconds = ms.coerceAtLeast(0L) / 1000L
        return "%d:%02d:%02d".format(seconds / 3600L, (seconds / 60L) % 60L, seconds % 60L)
    }
}
