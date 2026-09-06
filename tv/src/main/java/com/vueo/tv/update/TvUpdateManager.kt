package com.vueo.tv.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.vueo.tv.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class TvUpdateRelease(
    val versionCode: Int,
    val versionName: String,
    val title: String,
    val changelog: List<String>,
    val downloadUrl: String?,
    val sha256: String?,
    val publishedAt: String?,
) {
    fun isNewerThanCurrent(): Boolean = versionCode > BuildConfig.VERSION_CODE
}

data class TvUpdateCheckResult(
    val release: TvUpdateRelease?,
    val checkedAtEpochMs: Long,
    val error: String? = null,
)

private class TvUpdateStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("vueo_tv_update_state", Context.MODE_PRIVATE)

    fun latestRelease(): TvUpdateRelease? {
        val raw = prefs.getString("latest_release_json", null) ?: return null
        return runCatching { parseRelease(JSONObject(raw)) }.getOrNull()
    }

    fun lastCheckedAt(): Long = prefs.getLong("last_checked_at", 0L)
    fun lastError(): String? = prefs.getString("last_error", null)?.takeIf(String::isNotBlank)

    fun saveSuccess(release: TvUpdateRelease, checkedAt: Long) {
        prefs.edit()
            .putString("latest_release_json", release.toJson().toString())
            .putLong("last_checked_at", checkedAt)
            .remove("last_error")
            .apply()
    }

    fun saveFailure(error: String, checkedAt: Long) {
        prefs.edit().putLong("last_checked_at", checkedAt).putString("last_error", error.take(240)).apply()
    }
}

object TvUpdateManager {
    const val DEFAULT_MANIFEST_URL =
        "https://github.com/nudroid12/VUEO-NEXT/releases/download/vueo-dev/tv-update.json"

    private const val CHECK_INTERVAL_MS = 60L * 60L * 1000L

    suspend fun check(context: Context, force: Boolean = false): TvUpdateCheckResult = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val store = TvUpdateStore(appContext)
        val now = System.currentTimeMillis()
        if (!force && store.lastCheckedAt() > 0L && now - store.lastCheckedAt() < CHECK_INTERVAL_MS) {
            return@withContext TvUpdateCheckResult(store.latestRelease(), store.lastCheckedAt(), store.lastError())
        }

        runCatching {
            val separator = if (DEFAULT_MANIFEST_URL.contains("?")) "&" else "?"
            val connection = URL(DEFAULT_MANIFEST_URL + separator + "t=$now").openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("User-Agent", "VUEO-TV/${BuildConfig.VERSION_NAME}")
            val raw = try {
                require(connection.responseCode in 200..299) { "Update check failed with HTTP ${connection.responseCode}." }
                connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } finally {
                connection.disconnect()
            }
            val root = JSONObject(raw)
            require(root.optInt("schemaVersion", -1) == 1) { "Unsupported update feed schema." }
            val release = parseRelease(root) ?: error("Update feed is missing release information.")
            store.saveSuccess(release, now)
            TvUpdateCheckResult(release, now)
        }.getOrElse { failure ->
            val message = failure.message ?: "Unable to check for updates."
            store.saveFailure(message, now)
            TvUpdateCheckResult(store.latestRelease(), now, message)
        }
    }

    fun needsInstallPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    suspend fun downloadAndInstall(
        context: Context,
        release: TvUpdateRelease,
        onProgress: (Int) -> Unit = {},
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val appContext = context.applicationContext
            if (needsInstallPermission(appContext)) error("Allow VUEO to install unknown apps first.")
            val url = release.downloadUrl ?: error("This update does not include an APK download.")
            val target = File(File(appContext.cacheDir, "updates").apply { mkdirs() }, "VUEO-TV-update.apk")
            download(url, target, onProgress)
            verifySha256(target, release.sha256)
            val info = appContext.packageManager.getPackageArchiveInfo(target.absolutePath, 0)
                ?: error("Downloaded file is not a valid Android APK.")
            require(info.packageName == appContext.packageName) {
                "Downloaded APK is for ${info.packageName}, not ${appContext.packageName}."
            }
            withContext(Dispatchers.Main) {
                val uri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.vueo.updates", target)
                appContext.startActivity(
                    Intent(Intent.ACTION_VIEW)
                        .setDataAndType(uri, "application/vnd.android.package-archive")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }

    private suspend fun download(url: String, target: File, onProgress: (Int) -> Unit) {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 15_000
        connection.readTimeout = 60_000
        connection.setRequestProperty("User-Agent", "VUEO-TV/${BuildConfig.VERSION_NAME}")
        try {
            require(connection.responseCode in 200..299) { "APK download failed with HTTP ${connection.responseCode}." }
            val total = connection.contentLengthLong
            var downloaded = 0L
            var last = -1
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
                            if (progress != last) {
                                last = progress
                                withContext(Dispatchers.Main) { onProgress(progress) }
                            }
                        }
                    }
                }
            }
            withContext(Dispatchers.Main) { onProgress(100) }
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
        require(actual.equals(expected, ignoreCase = true)) { "Downloaded APK failed integrity verification." }
    }
}

private fun parseRelease(json: JSONObject): TvUpdateRelease? {
    val versionCode = json.optInt("versionCode", -1)
    val versionName = json.optString("versionName").takeIf(String::isNotBlank) ?: return null
    if (versionCode <= 0) return null
    return TvUpdateRelease(
        versionCode = versionCode,
        versionName = versionName,
        title = json.optString("title", "VUEO $versionName"),
        changelog = json.optJSONArray("changelog").toStringList(),
        downloadUrl = json.optString("downloadUrl").trim().takeIf { it.startsWith("https://") },
        sha256 = json.optString("sha256").trim().lowercase().takeIf { it.matches(Regex("[0-9a-f]{64}")) },
        publishedAt = json.optString("publishedAt").takeIf(String::isNotBlank),
    )
}

private fun TvUpdateRelease.toJson(): JSONObject = JSONObject()
    .put("versionCode", versionCode)
    .put("versionName", versionName)
    .put("title", title)
    .put("changelog", JSONArray(changelog))
    .put("downloadUrl", downloadUrl)
    .put("sha256", sha256)
    .put("publishedAt", publishedAt)

private fun JSONArray?.toStringList(): List<String> = buildList {
    val array = this@toStringList ?: return@buildList
    for (index in 0 until array.length()) {
        array.optString(index).takeIf(String::isNotBlank)?.let(::add)
    }
}
