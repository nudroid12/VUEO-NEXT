package com.vueo.tv.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors

data class VueoTvUpdateRelease(
    val versionCode: Int,
    val versionName: String,
    val title: String,
    val changelog: List<String>,
    val downloadUrl: String,
    val sha256: String?,
) {
}

data class VueoTvUpdateCheckResult(
    val release: VueoTvUpdateRelease?,
    val error: String? = null,
)

object VueoTvUpdateManager {
    fun isNewerThanCurrent(
        context: Context,
        release: VueoTvUpdateRelease,
    ): Boolean {
        val packageInfo =
            context.packageManager.getPackageInfo(context.packageName, 0)
        val currentVersionCode =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
        return release.versionCode.toLong() > currentVersionCode
    }

    // VUEO_TV_DEV_RELEASE_MANIFEST
    const val DEFAULT_MANIFEST_URL =
        "https://github.com/nudroid12/VUEO-NEXT/releases/download/vueo-dev/tv-update.json"

    private const val MANIFEST_SCHEMA_VERSION = 1
    private const val CHECK_INTERVAL_MS = 60_000L
    private const val PREFS_NAME = "vueo_tv_update_state"
    private const val KEY_LAST_CHECKED = "last_checked"
    private const val UPDATE_DIR = "updates"
    private const val APK_NAME = "VUEOTV-update.apk"

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun check(
        context: Context,
        force: Boolean = false,
        callback: (VueoTvUpdateCheckResult) -> Unit,
    ) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val lastChecked = prefs.getLong(KEY_LAST_CHECKED, 0L)

        if (!force && lastChecked > 0L && now - lastChecked < CHECK_INTERVAL_MS) {
            callback(VueoTvUpdateCheckResult(release = null))
            return
        }

        executor.execute {
            val result =
                runCatching {
                    val separator = if (DEFAULT_MANIFEST_URL.contains("?")) "&" else "?"
                    val raw = getText("$DEFAULT_MANIFEST_URL${separator}t=$now")
                    val root = JSONObject(raw)

                    require(root.optInt("schemaVersion", -1) == MANIFEST_SCHEMA_VERSION) {
                        "Unsupported TV update feed."
                    }

                    val release = parseRelease(root)
                        ?: error("TV update feed is incomplete.")

                    prefs.edit().putLong(KEY_LAST_CHECKED, now).apply()
                    VueoTvUpdateCheckResult(release = release)
                }.getOrElse { failure ->
                    prefs.edit().putLong(KEY_LAST_CHECKED, now).apply()
                    VueoTvUpdateCheckResult(
                        release = null,
                        error = failure.message ?: "Unable to check for TV updates.",
                    )
                }

            mainHandler.post { callback(result) }
        }
    }

    fun needsInstallPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val packageIntent =
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        val fallback =
            Intent(Settings.ACTION_SECURITY_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        val target =
            if (packageIntent.resolveActivity(context.packageManager) != null) {
                packageIntent
            } else {
                fallback
            }

        context.startActivity(target)
    }

    fun downloadAndInstall(
        context: Context,
        release: VueoTvUpdateRelease,
        onProgress: (Int) -> Unit,
        callback: (Result<Unit>) -> Unit,
    ) {
        val appContext = context.applicationContext

        executor.execute {
            val result =
                runCatching {
                    require(!needsInstallPermission(appContext)) {
                        "Allow VUEO TV to install unknown apps first."
                    }

                    val updateDir =
                        File(appContext.cacheDir, UPDATE_DIR).apply { mkdirs() }
                    val apkFile = File(updateDir, APK_NAME)

                    downloadApk(
                        url = release.downloadUrl,
                        target = apkFile,
                        onProgress = onProgress,
                    )
                    verifySha256(apkFile, release.sha256)
                    verifyDownloadedPackage(appContext, apkFile)

                    mainHandler.post {
                        launchInstaller(appContext, apkFile)
                    }
                    Unit
                }

            mainHandler.post { callback(result) }
        }
    }

    private fun parseRelease(root: JSONObject): VueoTvUpdateRelease? {
        val versionCode = root.optInt("versionCode", -1)
        val versionName = root.optString("versionName").trim()
        val downloadUrl = root.optString("downloadUrl").trim()

        if (versionCode <= 0 || versionName.isBlank() || !downloadUrl.startsWith("https://")) {
            return null
        }

        val sha =
            root.optString("sha256")
                .trim()
                .lowercase()
                .takeIf { it.matches(Regex("[0-9a-f]{64}")) }

        return VueoTvUpdateRelease(
            versionCode = versionCode,
            versionName = versionName,
            title = root.optString("title", "VUEO TV $versionName"),
            changelog = root.optJSONArray("changelog").toStringList(),
            downloadUrl = downloadUrl,
            sha256 = sha,
        )
    }

    private fun getText(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 12_000
        connection.readTimeout = 20_000
        connection.setRequestProperty("User-Agent", "VUEO-TV-Updater")

        try {
            val code = connection.responseCode
            require(code in 200..299) { "TV update check failed with HTTP $code." }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun downloadApk(
        url: String,
        target: File,
        onProgress: (Int) -> Unit,
    ) {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 15_000
        connection.readTimeout = 90_000
        connection.setRequestProperty("User-Agent", "VUEO-TV-Updater")

        try {
            val code = connection.responseCode
            require(code in 200..299) { "TV APK download failed with HTTP $code." }

            val total = connection.contentLengthLong
            var downloaded = 0L
            var lastProgress = -1

            connection.inputStream.buffered().use { input ->
                target.outputStream().buffered().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read

                        if (total > 0L) {
                            val progress = (downloaded * 100L / total).toInt().coerceIn(0, 100)
                            if (progress != lastProgress) {
                                lastProgress = progress
                                mainHandler.post { onProgress(progress) }
                            }
                        }
                    }
                }
            }

            mainHandler.post { onProgress(100) }
        } finally {
            connection.disconnect()
        }
    }

    private fun verifySha256(file: File, expected: String?) {
        if (expected.isNullOrBlank()) return

        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }

        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        require(actual.equals(expected, ignoreCase = true)) {
            "Downloaded TV APK failed integrity verification."
        }
    }

    private fun verifyDownloadedPackage(context: Context, file: File) {
        val info = context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
            ?: error("Downloaded file is not a valid Android APK.")

        require(info.packageName == context.packageName) {
            "Downloaded APK is for ${info.packageName}, not ${context.packageName}."
        }
    }

    private fun launchInstaller(context: Context, apkFile: File) {
        val uri =
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.vueo.tv.updates",
                apkFile,
            )

        val intent =
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_ACTIVITY_NEW_TASK,
                )

        context.startActivity(intent)
    }
}

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return buildList {
        for (index in 0 until length()) {
            optString(index).takeIf { it.isNotBlank() }?.let(::add)
        }
    }
}
