package com.vueo.shared.core.diagnostics

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Debug
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.provider.MediaStore
import android.view.Choreographer
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Opt-in performance recorder shared by TV and Mobile.
 *
 * OFF means no frame probe, no sampler and no event buffering. ON merely arms the
 * feature; Start Recording is what attaches the frame probe and periodic sampler.
 * RuntimeDiagnostics forwards its already-existing breadcrumbs here, so provider,
 * player and page activity can be correlated without duplicating instrumentation.
 */
object PerformanceDiagnostics {
    private const val PREFS = "vueo_performance_diagnostics"
    private const val KEY_ENABLED = "enabled"
    private const val MAX_EVENTS = 5_000
    private const val SYSTEM_SAMPLE_MS = 1_500L
    private const val PSS_SAMPLE_MS = 5_000L
    private const val JANK_THRESHOLD_MS = 48L

    enum class Tab(val label: String) {
        HOME("Home"),
        SEARCH("Search"),
        DETAILS("Details"),
        PLAYER("Player"),
        SOURCES("Sources"),
        EPISODES("Episodes"),
        LIBRARY("Library"),
        SETTINGS("Settings"),
        PROVIDER("Provider"),
        SYSTEM("System"),
        OTHER("Other"),
        FULL("Full"),
    }

    data class SaveResult(
        val displayName: String,
        val location: String,
    )

    private data class Event(
        val timestampMs: Long,
        val category: Tab,
        val page: Tab,
        val line: String,
    )

    private val installed = AtomicBoolean(false)
    private val enabled = AtomicBoolean(false)
    private val recording = AtomicBoolean(false)
    private val sessionSequence = AtomicLong(0L)
    private val droppedEvents = AtomicLong(0L)
    private val activeScans = AtomicInteger(0)
    private val activeProviders = AtomicInteger(0)
    private val activeQuickJs = AtomicInteger(0)
    private val currentScreen = AtomicReference("UNKNOWN")
    private val currentPage = AtomicReference(Tab.OTHER)
    private val eventsLock = Any()
    private val events = ArrayDeque<Event>(MAX_EVENTS)
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private val timestampFormat = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue(): SimpleDateFormat =
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    }

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var sampler: ScheduledExecutorService? = null

    @Volatile
    private var lastFrameNs: Long = 0L

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!recording.get()) {
                lastFrameNs = 0L
                return
            }
            val previous = lastFrameNs
            lastFrameNs = frameTimeNanos
            if (previous > 0L) {
                val gapMs = (frameTimeNanos - previous) / 1_000_000L
                if (gapMs >= JANK_THRESHOLD_MS) {
                    appendEvent(
                        category = currentPage.get(),
                        message = "FRAME_JANK gap=${gapMs}ms screen=${safe(currentScreen.get(), 100)}",
                    )
                }
            }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    fun install(context: Context) {
        appContext = context.applicationContext
        if (!installed.compareAndSet(false, true)) return
        enabled.set(
            context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_ENABLED, false)
        )
    }

    fun isEnabled(context: Context): Boolean {
        install(context)
        return enabled.get()
    }

    fun isRecording(): Boolean = recording.get()

    fun isCollecting(): Boolean = enabled.get() && recording.get()

    fun setEnabled(context: Context, value: Boolean) {
        install(context)
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, value)
            .apply()
        enabled.set(value)
        if (!value) stopRecording()
    }

    fun startRecording(context: Context) {
        install(context)
        if (!enabled.get() || !recording.compareAndSet(false, true)) return
        activeScans.set(0)
        activeProviders.set(0)
        activeQuickJs.set(0)
        val session = sessionSequence.incrementAndGet()
        appendEvent(Tab.SYSTEM, "RECORDING_START session=$session")
        startSampler()
        mainHandler.post {
            if (recording.get()) {
                lastFrameNs = 0L
                Choreographer.getInstance().removeFrameCallback(frameCallback)
                Choreographer.getInstance().postFrameCallback(frameCallback)
            }
        }
    }

    fun stopRecording() {
        if (!recording.compareAndSet(true, false)) return
        appendEvent(Tab.SYSTEM, "RECORDING_STOP")
        sampler?.shutdownNow()
        sampler = null
        mainHandler.post {
            Choreographer.getInstance().removeFrameCallback(frameCallback)
            lastFrameNs = 0L
        }
    }

    fun observeScreen(label: String) {
        val safeLabel = label.take(140)
        currentScreen.set(safeLabel)
        currentPage.set(pageFor(safeLabel))
    }

    /** Called by RuntimeDiagnostics before its own crash-log enable check. */
    fun captureRuntimeEvent(message: String) {
        if (!enabled.get() || !recording.get()) return
        val trimmed = message.trim()
        when {
            trimmed.startsWith("SCAN_START ") -> activeScans.incrementAndGet()
            trimmed.startsWith("SCAN_END ") -> activeScans.updateAndGet { (it - 1).coerceAtLeast(0) }
            trimmed.startsWith("PROVIDER_START ") -> activeProviders.incrementAndGet()
            trimmed.startsWith("PROVIDER_END ") -> activeProviders.updateAndGet { (it - 1).coerceAtLeast(0) }
            trimmed.startsWith("QJS_PHASE ") && " phase=QJS_CREATE_BEGIN " in " $trimmed " -> activeQuickJs.incrementAndGet()
            trimmed.startsWith("QJS_PHASE ") && (" phase=QJS_CLOSE " in " $trimmed " || " phase=QJS_ABORT " in " $trimmed ") ->
                activeQuickJs.updateAndGet { (it - 1).coerceAtLeast(0) }
        }
        if (trimmed.startsWith("SCREEN ")) {
            val label = trimmed.removePrefix("SCREEN ").take(140)
            observeScreen(label)
            appendEvent(currentPage.get(), "SCREEN $label")
            return
        }
        appendEvent(categoryFor(trimmed), trimmed)
    }

    fun export(tab: Tab): String {
        val snapshot = snapshot(tab)
        return buildString {
            appendLine("VUEO Performance Diagnostics — ${tab.label}")
            appendLine("Enabled: ${enabled.get()} | Recording: ${recording.get()} | Events: ${eventCount()} | Dropped: ${droppedEvents.get()}")
            appendLine("Active: scans=${activeScans.get()} providers=${activeProviders.get()} quickJs=${activeQuickJs.get()}")
            appendLine("Current screen: ${currentScreen.get()}")
            appendLine("Sampling: system=${SYSTEM_SAMPLE_MS}ms | jank threshold=${JANK_THRESHOLD_MS}ms | disk writes during recording=none")
            appendLine()
            if (snapshot.isEmpty()) {
                appendLine("No events recorded for this tab.")
            } else {
                snapshot.forEach { appendLine(it.line) }
            }
        }
    }

    fun clear() {
        synchronized(eventsLock) { events.clear() }
        droppedEvents.set(0L)
    }

    fun saveBundle(context: Context): SaveResult {
        install(context)
        val displayName = "VUEO_Performance_${SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())}.zip"
        val metadata = buildMetadata(context)

        if (Build.VERSION.SDK_INT >= 29) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/VUEO")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = requireNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)) {
                "Unable to create performance log in Downloads"
            }
            try {
                resolver.openOutputStream(uri, "w")!!.use { output -> writeZip(output, metadata) }
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                return SaveResult(displayName, "Downloads/VUEO")
            } catch (error: Throwable) {
                runCatching { resolver.delete(uri, null, null) }
                throw error
            }
        }

        val root = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
        val directory = File(root, "VUEO").apply { mkdirs() }
        val destination = File(directory, displayName)
        destination.outputStream().buffered().use { output -> writeZip(output, metadata) }
        return SaveResult(displayName, destination.parentFile?.absolutePath ?: directory.absolutePath)
    }

    private fun startSampler() {
        sampler?.shutdownNow()
        val executor = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "vueo-performance-sampler").apply { isDaemon = true }
        }
        sampler = executor
        var previousWall = SystemClock.elapsedRealtime()
        var previousCpu = Process.getElapsedCpuTime()
        var lastPssAt = 0L
        var cachedPssMb = 0L
        executor.scheduleWithFixedDelay({
            if (!recording.get()) return@scheduleWithFixedDelay
            runCatching {
                val nowWall = SystemClock.elapsedRealtime()
                val nowCpu = Process.getElapsedCpuTime()
                val wallDelta = (nowWall - previousWall).coerceAtLeast(1L)
                val cpuDelta = (nowCpu - previousCpu).coerceAtLeast(0L)
                previousWall = nowWall
                previousCpu = nowCpu
                val cpuPercent = (cpuDelta * 100.0) / wallDelta.toDouble()
                val runtime = Runtime.getRuntime()
                val javaMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024L * 1024L)
                val javaMaxMb = runtime.maxMemory() / (1024L * 1024L)
                val nativeMb = Debug.getNativeHeapAllocatedSize() / (1024L * 1024L)
                if (nowWall - lastPssAt >= PSS_SAMPLE_MS || lastPssAt == 0L) {
                    cachedPssMb = Debug.getPss() / 1024L
                    lastPssAt = nowWall
                }
                val pssMb = cachedPssMb
                val rssMb = readRssKb() / 1024L
                appendEvent(
                    Tab.SYSTEM,
                    "SYSTEM_SAMPLE cpu=${String.format(Locale.US, "%.1f", cpuPercent)}% " +
                        "java=$javaMb/$javaMaxMb MB native=${nativeMb}MB pss=${pssMb}MB rss=${rssMb}MB " +
                        "threads~${Thread.activeCount()} activeScans=${activeScans.get()} " +
                        "activeProviders=${activeProviders.get()} activeQuickJs=${activeQuickJs.get()} " +
                        "screen=${safe(currentScreen.get(), 100)}"
                )
            }
        }, 0L, SYSTEM_SAMPLE_MS, TimeUnit.MILLISECONDS)
    }

    private fun appendEvent(category: Tab, message: String) {
        if (!recording.get() && !message.startsWith("RECORDING_STOP")) return
        val page = currentPage.get()
        val stamp = timestampFormat.get()!!.format(Date())
        val line = "$stamp | [${category.label.uppercase(Locale.US)}] ${safe(message, 900)}"
        val event = Event(System.currentTimeMillis(), category, page, line)
        synchronized(eventsLock) {
            if (events.size >= MAX_EVENTS) {
                events.removeFirst()
                droppedEvents.incrementAndGet()
            }
            events.addLast(event)
        }
    }

    private fun snapshot(tab: Tab): List<Event> = synchronized(eventsLock) {
        if (tab == Tab.FULL) return@synchronized events.toList()
        events.filter { event ->
            event.category == tab || (isPageTab(tab) && event.page == tab)
        }
    }

    private fun eventCount(): Int = synchronized(eventsLock) { events.size }

    private fun categoryFor(message: String): Tab {
        val upper = message.uppercase(Locale.US)
        return when {
            upper.startsWith("PLAYER_EVENT") && ("SOURCE" in upper || "SERVER" in upper) -> Tab.SOURCES
            upper.startsWith("PLAYER_EVENT") && "EPISODE" in upper -> Tab.EPISODES
            upper.startsWith("PLAYER_EVENT") || upper.startsWith("PLAYBACK_") -> Tab.PLAYER
            upper.startsWith("SCAN_") || upper.startsWith("PROVIDER_") || upper.startsWith("QJS_") ||
                upper.startsWith("DISCOVERY_TRACE") -> Tab.PROVIDER
            upper.startsWith("UI_STALL") || upper.startsWith("SYSTEM_") -> Tab.SYSTEM
            else -> currentPage.get()
        }
    }

    private fun pageFor(label: String): Tab {
        val upper = label.uppercase(Locale.US)
        return when {
            "SOURCES" in upper -> Tab.SOURCES
            "EPISODES" in upper -> Tab.EPISODES
            "PLAYER" in upper -> Tab.PLAYER
            "DETAIL" in upper -> Tab.DETAILS
            "SEARCH" in upper -> Tab.SEARCH
            "LIBRARY" in upper || "MY LIST" in upper -> Tab.LIBRARY
            "SETTING" in upper || "PLUGIN" in upper || "ADDON" in upper -> Tab.SETTINGS
            "HOME" in upper -> Tab.HOME
            else -> Tab.OTHER
        }
    }

    private fun isPageTab(tab: Tab): Boolean = tab in setOf(
        Tab.HOME, Tab.SEARCH, Tab.DETAILS, Tab.PLAYER, Tab.SOURCES,
        Tab.EPISODES, Tab.LIBRARY, Tab.SETTINGS, Tab.OTHER,
    )

    private fun writeZip(output: OutputStream, metadata: String) {
        ZipOutputStream(output.buffered()).use { zip ->
            zipText(zip, "metadata.txt", metadata)
            Tab.entries.forEach { tab ->
                val name = if (tab == Tab.FULL) "full.log" else "${tab.name.lowercase(Locale.US)}.log"
                zipText(zip, name, export(tab))
            }
        }
    }

    private fun zipText(zip: ZipOutputStream, name: String, text: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(text.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun buildMetadata(context: Context): String {
        val packageInfo = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull()
        return buildString {
            appendLine("VUEO Performance Diagnostic Metadata")
            appendLine("Package: ${context.packageName}")
            appendLine("Version: ${packageInfo?.versionName ?: "unknown"}")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Enabled: ${enabled.get()}")
            appendLine("Recording: ${recording.get()}")
            appendLine("Events: ${eventCount()}")
            appendLine("Dropped: ${droppedEvents.get()}")
            appendLine("Active scans/providers/quickJs: ${activeScans.get()}/${activeProviders.get()}/${activeQuickJs.get()}")
            appendLine("Current screen: ${currentScreen.get()}")
            appendLine("System sample interval: ${SYSTEM_SAMPLE_MS}ms")
            appendLine("PSS probe interval: ${PSS_SAMPLE_MS}ms")
            appendLine("Jank threshold: ${JANK_THRESHOLD_MS}ms")
            appendLine("Runtime disk writes while recording: none")
        }
    }

    private fun readRssKb(): Long = runCatching {
        File("/proc/self/status").useLines { lines ->
            lines.firstOrNull { it.startsWith("VmRSS:") }
                ?.substringAfter(':')
                ?.trim()
                ?.substringBefore(' ')
                ?.toLongOrNull()
                ?: 0L
        }
    }.getOrDefault(0L)

    private fun safe(value: String, max: Int): String = value
        .replace('\n', ' ')
        .replace('\r', ' ')
        .take(max)
}
