package com.vueo.shared.core.diagnostics

import android.app.ActivityManager
import android.app.Application
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File

/** Private on-device crash evidence, separate from the rotating diagnostic log. */
class VueoDiagnosticApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        RuntimeDiagnostics.install(this)
    }
}

object CrashReportStore {
    private const val PREFS = "vueo_crash_recovery"
    private const val FILE = "last_crash.json"
    private val lock = Any()
    private val breadcrumbs = java.util.ArrayDeque<String>()
    private var currentStartedMs = 0L
    private var previousStartedMs = 0L
    private var previousVersion = "unknown"
    private var currentVersion = "unknown"
    private var lastSystemSummaryNs = 0L
    private var lastScreen = "Startup"
    private var lastStall = "No live stall stack captured."

    internal fun startSession(context: Context) = synchronized(lock) {
        if (currentStartedMs != 0L) return@synchronized
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        previousStartedMs = prefs.getLong("session_started", 0L)
        previousVersion = prefs.getString("session_version", "unknown") ?: "unknown"
        val evidence = File(context.noBackupFilesDir, "anr_evidence.json")
        val previousEvidence = File(context.noBackupFilesDir, "anr_evidence_previous.json")
        previousEvidence.delete()
        if (evidence.exists()) evidence.renameTo(previousEvidence)
        currentStartedMs = System.currentTimeMillis()
        currentVersion = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "unknown"
        prefs.edit().putLong("session_started", currentStartedMs)
            .putString("session_version", currentVersion).commit()
    }

    internal fun screen(label: String) = synchronized(lock) {
        lastScreen = sanitize(label).take(120)
    }

    internal fun note(message: String) = synchronized(lock) {
        if (breadcrumbs.size == 48) breadcrumbs.removeFirst()
        breadcrumbs.addLast(sanitize(message).take(700))
    }

    internal fun stall(stack: String) = synchronized(lock) {
        lastStall = sanitize(stack).take(16_000)
    }

    private fun persistEvidence(context: Context) {
        val snapshot = synchronized(lock) {
            JSONObject().put("session", currentStartedMs).put("timestamp", System.currentTimeMillis())
                .put("screen", lastScreen).put("timeline", breadcrumbs.joinToString("\n"))
                .put("stall", lastStall)
        }
        val file = AtomicFile(File(context.noBackupFilesDir, "anr_evidence.json"))
        val output = file.startWrite()
        try {
            output.write(snapshot.toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(output)
        } catch (error: Throwable) {
            file.failWrite(output)
            throw error
        }
    }

    // Invoked by the existing diagnostic writer, never for each event on the UI thread.
    internal fun updateSystemSummary(context: Context, message: String, force: Boolean = false) {
        val now = System.nanoTime()
        synchronized(lock) {
            if (!force && now - lastSystemSummaryNs < 500_000_000L) return
            lastSystemSummaryNs = now
        }
        runCatching { persistEvidence(context) }
        if (Build.VERSION.SDK_INT < 30) return
        runCatching {
            val state = synchronized(lock) { "Screen: $lastScreen | ${sanitize(message)}" }
            val bytes = state.toByteArray(Charsets.UTF_8).take(128).toByteArray()
            (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)
                ?.setProcessStateSummary(bytes)
        }
    }

    internal fun capture(context: Context, thread: Thread, throwable: Throwable) = synchronized(lock) {
        val chain = generateSequence(throwable) { it.cause }.take(6).toList()
        val root = chain.last()
        val summary = when {
            chain.any { it is OutOfMemoryError } -> "App ran out of memory."
            else -> "${root.javaClass.simpleName}: ${sanitize(root.message.orEmpty().take(1200)).take(240)}"
        }
        val details = buildString {
            appendLine("VUEO Crash Report")
            appendLine("Package: ${context.packageName}")
            appendLine("Version: $currentVersion")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Thread: ${thread.name.take(120)}")
            appendLine("Screen: $lastScreen")
            appendLine("Last activity (observed events; not a proven cause):")
            breadcrumbs.forEach { appendLine(it) }
            appendLine("Code location: ${root.stackTrace.firstOrNull() ?: "Unavailable"}")
            appendLine("Exception chain (root cause first):")
            chain.asReversed().forEach { error ->
                appendLine("${error.javaClass.name}: ${error.message.orEmpty().take(1200)}")
                error.stackTrace.take(32).forEach { appendLine("  at ${it.toString().take(400)}") }
            }
        }
        write(context, AppCrashReport(System.currentTimeMillis(), summary, sanitize(details).take(24_000)))
    }

    /** Call off the UI thread. Legacy installs do not report old historical exits. */
    fun pending(context: Context): AppCrashReport? {
        val dismissed = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong("dismissed_through", 0L)
        val local = CrashRecoveryPolicy.pendingLocal(synchronized(lock) { read(context) }, dismissed, currentStartedMs)
        val system = if (Build.VERSION.SDK_INT >= 30 && previousStartedMs > 0L) {
            runCatching { systemReport(context, dismissed) }.getOrNull()
        } else null
        val chosen = CrashRecoveryPolicy.choose(local, system)
        if (chosen != null && chosen != local) runCatching { synchronized(lock) { write(context, chosen) } }
        return chosen
    }

    /** Persist dismissal before clearing the displayed report. Back follows the same path. */
    fun dismiss(context: Context, report: AppCrashReport): Boolean = synchronized(lock) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val cutoff = maxOf(report.timestampMs, currentStartedMs, prefs.getLong("dismissed_through", 0L))
        val saved = prefs.edit().putLong("dismissed_through", cutoff).commit()
        if (saved) {
            val stored = read(context)
            if (stored == null || stored.timestampMs <= cutoff) reportFile(context).delete()
        }
        saved
    }

    @android.annotation.TargetApi(30)
    private fun systemReport(context: Context, dismissed: Long): AppCrashReport? {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return null
        val exit = manager.getHistoricalProcessExitReasons(context.packageName, 0, 16)
            .filter { it.processName == context.packageName && it.timestamp >= previousStartedMs &&
                it.timestamp < currentStartedMs && it.timestamp > dismissed }
            .maxByOrNull { it.timestamp } ?: return null
        val foreground = exit.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
        val summary = when (exit.reason) {
            ApplicationExitInfo.REASON_CRASH -> "App closed because of an unhandled error."
            ApplicationExitInfo.REASON_CRASH_NATIVE -> "A native component crashed."
            ApplicationExitInfo.REASON_ANR -> "App stopped responding (ANR)."
            ApplicationExitInfo.REASON_LOW_MEMORY -> if (foreground) "Android closed the app because device memory was low." else return null
            ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> if (foreground) "Android closed the app because resource usage was excessive." else return null
            ApplicationExitInfo.REASON_UNKNOWN -> if (foreground) "App closed unexpectedly. The exact cause is unavailable." else return null
            else -> return null // Normal exit, force-stop, update and background process cleanup.
        }
        val details = buildString {
            appendLine("VUEO Crash Report (Android process exit record)")
            appendLine("Package: ${context.packageName}")
            appendLine("Previous session version: $previousVersion")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Reason: $summary (code ${exit.reason})")
            appendLine("Process: ${exit.processName}; pid=${exit.pid}; status=${exit.status}")
            appendLine("Memory: PSS=${exit.pss}KB; RSS=${exit.rss}KB")
            appendLine("System description: ${exit.description ?: "Unavailable"}")
            exit.processStateSummary?.let {
                appendLine("Last observed activity: ${String(it, Charsets.UTF_8)}")
            }
            appendLine("Previous-session evidence (observations, not proven causes):")
            appendLine(runCatching {
                val file = File(context.noBackupFilesDir, "anr_evidence_previous.json")
                if (!file.exists() || file.length() > 128_000) "Unavailable: no bounded previous-session evidence."
                else {
                    val evidence = JSONObject(file.readText())
                    if (evidence.optLong("session") != previousStartedMs ||
                        evidence.optLong("timestamp") > exit.timestamp) "Unavailable: evidence does not match this exit."
                    else "Screen: ${evidence.optString("screen")}\nTimeline (last 48 events, each capped at 700 characters):\n${evidence.optString("timeline")}\nLive stall snapshot (capped at 16000 characters):\n${evidence.optString("stall")}" 
                }
            }.getOrElse { "Unavailable: previous-session evidence read failed (${it.javaClass.simpleName})." })
            if (exit.reason == ApplicationExitInfo.REASON_ANR) {
                appendLine("Android ANR trace:")
                appendLine(runCatching {
                    exit.traceInputStream?.use { stream ->
                        val reader = stream.reader(Charsets.UTF_8)
                        val chars = CharArray(48_001)
                        var size = 0
                        while (size < chars.size) {
                            val count = reader.read(chars, size, chars.size - size)
                            if (count <= 0) break
                            size += count
                        }
                        String(chars, 0, minOf(size, 48_000)) +
                            if (size > 48_000) "\n[TRACE TRUNCATED: 48000-character limit]" else "\n[TRACE END]"
                    } ?: "Unavailable: Android supplied no trace."
                }.getOrElse { "Unavailable: trace read failed (${it.javaClass.simpleName})." })
            }
            appendLine("Snapshots can miss the blocking operation; a stack alone does not prove the root cause.")
        }
        return AppCrashReport(exit.timestamp, summary, sanitize(details).let { if (it.length > 110_000) it.take(110_000) + "\n[REPORT TRUNCATED]" else it })
    }

    private fun reportFile(context: Context) = AtomicFile(File(context.noBackupFilesDir, FILE))
    private fun read(context: Context): AppCrashReport? = runCatching {
        val file = reportFile(context)
        file.openRead().use { stream ->
            val bytes = ByteArray(1_000_001)
            var size = 0
            while (size < bytes.size) {
                val count = stream.read(bytes, size, bytes.size - size)
                if (count < 0) break
                size += count
            }
            if (size > 1_000_000) return@use null
            val json = JSONObject(String(bytes, 0, size, Charsets.UTF_8))
            AppCrashReport(json.getLong("timestamp"), json.getString("summary"), json.getString("details"))
        }
    }.getOrNull()

    private fun write(context: Context, report: AppCrashReport) {
        val file = reportFile(context)
        val output = file.startWrite()
        try {
            output.write(JSONObject().put("timestamp", report.timestampMs)
                .put("summary", report.summary).put("details", report.details).toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(output)
        } catch (error: Throwable) {
            file.failWrite(output)
            throw error
        }
    }

    internal fun sanitize(raw: String): String = CrashRecoveryPolicy.sanitize(raw)
}
