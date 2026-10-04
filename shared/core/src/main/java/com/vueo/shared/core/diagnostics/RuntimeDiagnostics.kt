package com.vueo.shared.core.diagnostics

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

object RuntimeDiagnostics {
    private const val FILE_NAME = "vueo_runtime_diagnostics.log"
    private const val ROTATE_AT_BYTES = 512 * 1024L
    private const val KEEP_BYTES = 256 * 1024
    // Provider main-thread risk threshold; independent of the live ANR watchdog.
    private const val STALL_THRESHOLD_MS = 350L

    private val installed = AtomicBoolean(false)
    private val scanSequence = AtomicLong(0L)
    private val activeScans = AtomicInteger(0)
    private val activeProviders = AtomicInteger(0)
    private val writer = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "vueo-runtime-diagnostics").apply { isDaemon = true }
    }
    private val fileLock = Any()
    private val scanStates = ConcurrentHashMap<Long, ScanState>()
    private val resumedActivities = AtomicInteger(0)
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    @Volatile
    private var appContext: Context? = null

    data class ProviderToken internal constructor(
        val scanId: Long,
        val providerName: String,
        val startedNs: Long,
        val heapStartBytes: Long,
        val startedOnMainThread: Boolean,
    )

    private data class ScanState(
        val startedNs: Long,
        val startedOnMainThread: Boolean,
        val activeProviders: AtomicInteger = AtomicInteger(0),
        val maxConcurrentProviders: AtomicInteger = AtomicInteger(0),
    )

    fun install(context: Context) {
        appContext = context.applicationContext
        if (!installed.compareAndSet(false, true)) return

        runCatching { CrashReportStore.startSession(context.applicationContext) }
        installCrashHandler()
        startStallWatchdog(context.applicationContext)
        record(
            "SESSION_START android=${Build.VERSION.SDK_INT} " +
                "device=${safeToken(Build.MANUFACTURER)}_${safeToken(Build.MODEL)} " +
                memoryLabel()
        )
    }

    fun recordScreen(label: String) {
        CrashReportStore.screen(label)
        record("SCREEN ${safeText(label, 120)}")
    }

    fun beginSourceScan(requestLabel: String, targetProviders: Int): Long {
        val id = scanSequence.incrementAndGet()
        val onMain = Looper.myLooper() == Looper.getMainLooper()
        scanStates[id] = ScanState(
            startedNs = System.nanoTime(),
            startedOnMainThread = onMain,
        )
        val scans = activeScans.incrementAndGet()
        record(
            "SCAN_START id=$id request=${safeText(requestLabel, 120)} " +
                "targets=$targetProviders activeScans=$scans thread=${threadLabel()} " +
                "mainThread=$onMain ${memoryLabel()}"
        )
        return id
    }

    fun finishSourceScan(
        scanId: Long,
        streams: Int,
        completedProviders: Int,
        outcome: String = "complete",
    ) {
        val state = scanStates.remove(scanId)
        val scans = activeScans.updateAndGet { current -> (current - 1).coerceAtLeast(0) }
        if (state == null) return

        val elapsedMs = elapsedMs(state.startedNs)
        record(
            "SCAN_END id=$scanId outcome=${safeToken(outcome)} elapsed=${elapsedMs}ms " +
                "streams=$streams providers=$completedProviders " +
                "maxConcurrentProviders=${state.maxConcurrentProviders.get()} " +
                "activeScans=$scans thread=${threadLabel()} ${memoryLabel()}"
        )

    }

    fun recordDiscoveryTrace(
        scanId: Long,
        stage: String,
        details: String,
        providerName: String? = null,
    ) {
        val providerPart = providerName
            ?.takeIf { it.isNotBlank() }
            ?.let { " provider=${safeText(it, 80)}" }
            .orEmpty()

        record(
            "DISCOVERY_TRACE scan=$scanId stage=${safeToken(stage)}$providerPart " +
                safeText(details, 360)
        )
    }

    fun recordSubtitleLayout(
        platform: String,
        positionMs: Long,
        incomingCues: Int,
        displayedCues: Int,
        unpositionedTextCues: Int,
    ) {
        record(
            "SUBTITLE_LAYOUT platform=${safeToken(platform)} positionMs=$positionMs " +
                "incoming=$incomingCues displayed=$displayedCues " +
                "unpositionedText=$unpositionedTextCues path=managed-view"
        )
    }

    fun recordPlaybackError(
        platform: String, provider: String, server: String, url: String?, mimeType: String?,
        errorCode: Int, errorName: String, positionMs: Long, state: Int, error: Throwable,
    ) {
        // Omit paths too: signed credentials may live in path segments, not just query parameters.
        fun redact(value: String): String = CrashRecoveryPolicy.sanitize(value)
            .replace(Regex("https?://[^\\s<>\"']+")) { match ->
                val host = runCatching { java.net.URI(match.value).host }.getOrNull()
                "https://${host ?: "redacted"}/<redacted>"
            }
        val endpoint = runCatching { java.net.URI(url.orEmpty()).host }.getOrNull().orEmpty()
        record("PLAYBACK_ERROR platform=${safeToken(platform)} provider=${safeText(redact(provider), 120)} " +
            "server=${safeText(redact(server), 120)} host=${safeText(endpoint, 120)} mime=${safeText(mimeType.orEmpty(), 80)} " +
            "code=$errorCode name=${safeToken(errorName)} positionMs=$positionMs state=$state")
        val seen = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Throwable, Boolean>())
        var cause: Throwable? = error
        var depth = 0
        while (cause != null && depth < 8 && seen.add(cause)) {
            val current = cause
            record("PLAYBACK_CAUSE depth=$depth class=${current.javaClass.name} " +
                "message=${safeText(redact(current.message.orEmpty()), 700)} " +
                "location=${safeText(current.stackTrace.firstOrNull()?.toString().orEmpty(), 180)}")
            cause = current.cause
            depth++
        }
    }

    fun failSourceScan(scanId: Long, error: Throwable, completedProviders: Int) {
        record(
            "SCAN_ERROR id=$scanId type=${safeToken(error::class.java.simpleName)} " +
                "message=${safeText(error.message.orEmpty(), 180)} providers=$completedProviders"
        )
        finishSourceScan(
            scanId = scanId,
            streams = 0,
            completedProviders = completedProviders,
            outcome = "failed",
        )
    }

    fun beginProvider(scanId: Long, providerName: String): ProviderToken {
        val globalActive = activeProviders.incrementAndGet()
        val state = scanStates[scanId]
        val scanActive = state?.activeProviders?.incrementAndGet() ?: 0
        state?.maxConcurrentProviders?.updateMax(scanActive)
        val onMain = Looper.myLooper() == Looper.getMainLooper()
        val token = ProviderToken(
            scanId = scanId,
            providerName = providerName,
            startedNs = System.nanoTime(),
            heapStartBytes = heapUsedBytes(),
            startedOnMainThread = onMain,
        )
        record(
            "PROVIDER_START scan=$scanId provider=${safeText(providerName, 80)} " +
                "activeProviders=$scanActive globalActiveProviders=$globalActive " +
                "thread=${threadLabel()} mainThread=$onMain"
        )
        return token
    }

    fun finishProvider(
        token: ProviderToken,
        status: String,
        streamCount: Int,
        errorType: String? = null,
    ) {
        val elapsedMs = elapsedMs(token.startedNs)
        val heapDeltaMb = (heapUsedBytes() - token.heapStartBytes) / (1024.0 * 1024.0)
        val globalActive = activeProviders.updateAndGet { current -> (current - 1).coerceAtLeast(0) }
        val scanActive = scanStates[token.scanId]
            ?.activeProviders
            ?.updateAndGet { current -> (current - 1).coerceAtLeast(0) }
            ?: 0
        val errorSuffix = errorType
            ?.takeIf { it.isNotBlank() }
            ?.let { " errorType=${safeToken(it)}" }
            .orEmpty()
        record(
            "PROVIDER_END scan=${token.scanId} provider=${safeText(token.providerName, 80)} " +
                "status=${safeToken(status)} elapsed=${elapsedMs}ms streams=$streamCount " +
                "heapDelta=${String.format(Locale.US, "%.1f", heapDeltaMb)}MB " +
                "startedOnMainThread=${token.startedOnMainThread} activeProviders=$scanActive " +
                "globalActiveProviders=$globalActive$errorSuffix"
        )
        if (token.startedOnMainThread && elapsedMs >= STALL_THRESHOLD_MS) {
            record(
                "UI_STALL_RISK scan=${token.scanId} provider=${safeText(token.providerName, 80)} " +
                    "providerRanOnMainThread=true elapsed=${elapsedMs}ms"
            )
        }
    }

    fun export(context: Context): String {
        install(context)
        flushWriter()
        val body = synchronized(fileLock) {
            runCatching { logFile().takeIf(File::exists)?.readText().orEmpty() }.getOrDefault("")
        }
        val packageInfo = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull()

        return buildString {
            appendLine("VUEO Performance / Crash Diagnostic Log")
            appendLine("Package: ${context.packageName}")
            appendLine("Version: ${packageInfo?.versionName ?: "unknown"}")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Current: ${memoryLabel()}")
            appendLine()
            if (body.isBlank()) {
                appendLine("No runtime diagnostics captured yet.")
                appendLine("Open a title and run source discovery, then reopen this log.")
            } else {
                append(body.takeLast(400_000))
            }
        }
    }

    fun clear(context: Context) {
        install(context)
        flushWriter()
        synchronized(fileLock) {
            runCatching { logFile().writeText("") }
        }
        record("DIAGNOSTICS_CLEARED")
    }

    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { appContext?.let { CrashReportStore.capture(it, thread, throwable) } }
            runCatching {
                val stack = throwable.stackTrace
                    .take(36)
                    .joinToString(" | ") { frame ->
                        "${frame.className}.${frame.methodName}:${frame.lineNumber}"
                    }
                appendSync(
                    timestamped(
                        "CRASH thread=${safeText(thread.name, 60)} " +
                            "type=${safeToken(throwable::class.java.simpleName)} " +
                            "message=${safeText(throwable.message.orEmpty(), 220)} ${memoryLabel()} " +
                            "stack=${safeText(stack, 5000)}"
                    )
                )
            }
            if (previous != null) previous.uncaughtException(thread, throwable)
            else {
                android.os.Process.killProcess(android.os.Process.myPid())
                kotlin.system.exitProcess(10)
            }
        }
    }

    // Runs independently of the main looper: captures stacks while it is stalled.
    private fun startStallWatchdog(context: Context) {
        (context as? Application)?.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) { resumedActivities.incrementAndGet() }
            override fun onActivityPaused(activity: Activity) { resumedActivities.updateAndGet { maxOf(0, it - 1) } }
            override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
        val pendingSince = AtomicLong(0L)
        val probe = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "vueo-anr-watchdog").apply { isDaemon = true }
        }
        var samples = 0
        probe.scheduleWithFixedDelay({
            runCatching {
                if (resumedActivities.get() == 0) {
                    pendingSince.set(0L)
                    samples = 0
                    return@runCatching
                }
                val now = SystemClock.uptimeMillis()
                val since = pendingSince.get()
                if (since == 0L) {
                    samples = 0
                    if (pendingSince.compareAndSet(0L, now)) {
                        mainHandler.post { pendingSince.compareAndSet(now, 0L) }
                    }
                } else if (now - since >= 2_000L && samples < 3) {
                    samples++
                    val stack = Looper.getMainLooper().thread.stackTrace.take(80)
                        .joinToString("\n") { "  at $it" }
                    val evidence = "UI_STALL_LIVE delay=${now - since}ms sample=$samples " +
                        "activeScans=${activeScans.get()} activeProviders=${activeProviders.get()} ${memoryLabel()}\n$stack"
                    CrashReportStore.stall(timestamped(evidence))
                    record(evidence)
                }
            }
        }, 1_000L, 1_000L, TimeUnit.MILLISECONDS)
    }

    private fun record(message: String) {
        val line = timestamped(message)
        CrashReportStore.note(line)
        writer.execute {
            appendSync(line)
            appContext?.let { CrashReportStore.updateSystemSummary(it, line, force = message.startsWith("SCREEN ")) }
        }
    }

    private fun appendSync(line: String) {
        val context = appContext ?: return
        synchronized(fileLock) {
            runCatching {
                val file = File(context.noBackupFilesDir, FILE_NAME)
                file.appendText(line + "\n")
                if (file.length() > ROTATE_AT_BYTES) {
                    val bytes = file.readBytes()
                    val tail = bytes.copyOfRange((bytes.size - KEEP_BYTES).coerceAtLeast(0), bytes.size)
                    file.writeBytes(tail)
                }
            }
        }
    }

    private fun logFile(): File {
        val context = requireNotNull(appContext) { "RuntimeDiagnostics is not installed" }
        return File(context.noBackupFilesDir, FILE_NAME)
    }

    private fun flushWriter() {
        runCatching { writer.submit { }.get(800, TimeUnit.MILLISECONDS) }
    }

    private fun timestamped(message: String): String {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        return "$stamp | $message"
    }

    private fun memoryLabel(): String {
        val runtime = Runtime.getRuntime()
        val used = runtime.totalMemory() - runtime.freeMemory()
        return "heap=${mb(used)}MB/${mb(runtime.maxMemory())}MB"
    }

    private fun heapUsedBytes(): Long {
        val runtime = Runtime.getRuntime()
        return runtime.totalMemory() - runtime.freeMemory()
    }

    private fun mb(bytes: Long): Long = bytes / (1024L * 1024L)

    private fun elapsedMs(startedNs: Long): Long =
        (System.nanoTime() - startedNs) / 1_000_000L

    private fun threadLabel(): String = safeText(Thread.currentThread().name, 60)

    private fun safeToken(value: String): String =
        value.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80)

    private fun safeText(value: String, max: Int): String =
        value
            .replace('\n', ' ')
            .replace('\r', ' ')
            .replace(Regex("(?i)(authorization|cookie|token|api[_-]?key)\\s*[:=]\\s*[^\\s,;]+")) {
                "${it.groupValues[1]}=<redacted>"
            }
            .take(max)

    private fun AtomicInteger.updateMax(candidate: Int) {
        while (true) {
            val current = get()
            if (candidate <= current || compareAndSet(current, candidate)) return
        }
    }
}
