package com.vueo.app.core.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.vueo.app.BuildConfig
import com.vueo.app.core.stremio.SimpleHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class VueoUpdateRelease(
    val versionCode: Int,
    val versionName: String,
    val title: String,
    val changelog: List<String>,
    val downloadUrl: String?,
    val sha256: String?,
    val publishedAt: String?,
) {
    fun isNewerThanCurrent(): Boolean =
        versionCode > BuildConfig.VERSION_CODE

    fun toJson(): JSONObject =
        JSONObject()
            .put("versionCode", versionCode)
            .put("versionName", versionName)
            .put("title", title)
            .put(
                "changelog",
                JSONArray(changelog),
            )
            .put(
                "downloadUrl",
                downloadUrl,
            )
            .put("sha256", sha256)
            .put(
                "publishedAt",
                publishedAt,
            )

    companion object {
        fun fromJson(
            json: JSONObject,
        ): VueoUpdateRelease? {
            val code =
                json.optInt(
                    "versionCode",
                    -1,
                )
            val name =
                json.optString(
                    "versionName"
                )
                    .takeIf {
                        it.isNotBlank()
                    }
                    ?: return null

            if (code <= 0) {
                return null
            }

            return VueoUpdateRelease(
                versionCode = code,
                versionName = name,
                title =
                    json.optString(
                        "title",
                        "VUEO $name",
                    ),
                changelog =
                    json.optJSONArray(
                        "changelog"
                    )
                        .toStringList(),
                downloadUrl =
                    json.optHttpsUrl(
                        "downloadUrl"
                    ),
                sha256 =
                    json.optString(
                        "sha256"
                    )
                        .trim()
                        .lowercase()
                        .takeIf {
                            it.matches(
                                Regex(
                                    "[0-9a-f]{64}"
                                )
                            )
                        },
                publishedAt =
                    json.optString(
                        "publishedAt"
                    )
                        .takeIf {
                            it.isNotBlank()
                        },
            )
        }
    }
}

data class VueoUpdateCheckResult(
    val release: VueoUpdateRelease?,
    val checkedAtEpochMs: Long,
    val fromCache: Boolean,
    val error: String? = null,
)

class VueoUpdateStore(
    context: Context,
) {
    private val prefs =
        context.applicationContext
            .getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE,
            )

    fun latestRelease():
        VueoUpdateRelease? {
        val raw =
            prefs.getString(
                KEY_RELEASE_JSON,
                null,
            )
                ?: return null

        return runCatching {
            VueoUpdateRelease
                .fromJson(
                    JSONObject(raw)
                )
        }.getOrNull()
    }

    fun lastCheckedAt(): Long =
        prefs.getLong(
            KEY_LAST_CHECKED_AT,
            0L,
        )

    fun lastError(): String? =
        prefs.getString(
            KEY_LAST_ERROR,
            null,
        )
            ?.takeIf {
                it.isNotBlank()
            }

    fun saveSuccess(
        release: VueoUpdateRelease,
        checkedAtEpochMs: Long,
    ) {
        prefs.edit()
            .putString(
                KEY_RELEASE_JSON,
                release
                    .toJson()
                    .toString(),
            )
            .putLong(
                KEY_LAST_CHECKED_AT,
                checkedAtEpochMs,
            )
            .remove(
                KEY_LAST_ERROR
            )
            .apply()
    }

    fun saveFailure(
        error: String,
        checkedAtEpochMs: Long,
    ) {
        prefs.edit()
            .putLong(
                KEY_LAST_CHECKED_AT,
                checkedAtEpochMs,
            )
            .putString(
                KEY_LAST_ERROR,
                error.take(240),
            )
            .apply()
    }

    companion object {
        private const val PREFS_NAME =
            "vueo_update_state"
        private const val KEY_RELEASE_JSON =
            "latest_release_json"
        private const val KEY_LAST_CHECKED_AT =
            "last_checked_at"
        private const val KEY_LAST_ERROR =
            "last_error"
    }
}

object VueoUpdateManager {
    // VUEO_DEV_RELEASE_MANIFEST
    const val DEFAULT_MANIFEST_URL =
        "https://github.com/nudroid12/VUEO/releases/download/vueo-dev/update.json"

    private const val MANIFEST_SCHEMA_VERSION =
        1
    private const val AUTO_CHECK_INTERVAL_MS =
        60L * 60L * 1000L
    private const val UPDATE_DIR =
        "updates"
    private const val APK_NAME =
        "VUEO-update.apk"

    suspend fun check(
        context: Context,
        force: Boolean = false,
    ): VueoUpdateCheckResult {
        val appContext =
            context.applicationContext
        val store =
            VueoUpdateStore(appContext)
        val now =
            System.currentTimeMillis()
        val previous =
            store.latestRelease()
        val lastChecked =
            store.lastCheckedAt()

        if (
            !force &&
            lastChecked > 0L &&
            now - lastChecked <
                AUTO_CHECK_INTERVAL_MS
        ) {
            return VueoUpdateCheckResult(
                release = previous,
                checkedAtEpochMs =
                    lastChecked,
                fromCache = true,
                error =
                    store.lastError(),
            )
        }

        return runCatching {
            val separator =
                if (
                    DEFAULT_MANIFEST_URL
                        .contains("?")
                ) {
                    "&"
                } else {
                    "?"
                }

            val raw =
                SimpleHttp.getResilient(
                    DEFAULT_MANIFEST_URL +
                        separator +
                        "t=$now"
                )

            val root =
                JSONObject(raw)
            val schema =
                root.optInt(
                    "schemaVersion",
                    -1,
                )

            require(
                schema ==
                    MANIFEST_SCHEMA_VERSION
            ) {
                "Unsupported update feed schema $schema."
            }

            val release =
                VueoUpdateRelease
                    .fromJson(root)
                    ?: error(
                        "Update feed is missing release information."
                    )

            store.saveSuccess(
                release = release,
                checkedAtEpochMs = now,
            )

            VueoUpdateCheckResult(
                release = release,
                checkedAtEpochMs = now,
                fromCache = false,
            )
        }.getOrElse {
            failure ->
            val message =
                failure.message
                    ?: "Unable to check for updates."

            store.saveFailure(
                error = message,
                checkedAtEpochMs = now,
            )

            VueoUpdateCheckResult(
                release = previous,
                checkedAtEpochMs = now,
                fromCache = false,
                error = message,
            )
        }
    }

    fun needsInstallPermission(
        context: Context,
    ): Boolean =
        Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.O &&
            !context
                .packageManager
                .canRequestPackageInstalls()

    fun openInstallPermissionSettings(
        context: Context,
    ) {
        if (
            Build.VERSION.SDK_INT <
            Build.VERSION_CODES.O
        ) {
            return
        }

        val intent =
            Intent(
                Settings
                    .ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse(
                    "package:${context.packageName}"
                ),
            )
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                )

        context.startActivity(intent)
    }

    suspend fun downloadAndInstall(
        context: Context,
        release: VueoUpdateRelease,
        onProgress: (Int) -> Unit =
            {},
    ): Result<Unit> =
        withContext(
            Dispatchers.IO
        ) {
            runCatching {
                val appContext =
                    context.applicationContext

                if (
                    needsInstallPermission(
                        appContext
                    )
                ) {
                    error(
                        "Allow VUEO to install unknown apps first."
                    )
                }

                val downloadUrl =
                    release.downloadUrl
                        ?: error(
                            "This update does not include an APK download."
                        )

                val updateDir =
                    File(
                        appContext.cacheDir,
                        UPDATE_DIR,
                    ).apply {
                        mkdirs()
                    }

                val apkFile =
                    File(
                        updateDir,
                        APK_NAME,
                    )

                downloadApk(
                    url = downloadUrl,
                    target = apkFile,
                    onProgress =
                        onProgress,
                )

                verifySha256(
                    file = apkFile,
                    expected =
                        release.sha256,
                )

                withContext(
                    Dispatchers.Main
                ) {
                    launchInstaller(
                        context =
                            appContext,
                        apkFile =
                            apkFile,
                    )
                }
            }
        }

    private suspend fun downloadApk(
        url: String,
        target: File,
        onProgress: (Int) -> Unit,
    ) {
        val connection =
            URL(url)
                .openConnection()
                as HttpURLConnection

        connection
            .instanceFollowRedirects =
            true
        connection
            .connectTimeout =
            15_000
        connection
            .readTimeout =
            60_000
        connection
            .setRequestProperty(
                "User-Agent",
                "VUEO/${BuildConfig.VERSION_NAME}",
            )

        try {
            val responseCode =
                connection.responseCode

            require(
                responseCode in
                    200..299
            ) {
                "APK download failed with HTTP $responseCode."
            }

            val total =
                connection
                    .contentLengthLong
                    .coerceAtLeast(
                        -1L
                    )

            var downloaded = 0L
            var lastProgress = -1

            connection.inputStream
                .buffered()
                .use {
                    input ->
                    target.outputStream()
                        .buffered()
                        .use {
                            output ->
                            val buffer =
                                ByteArray(
                                    64 * 1024
                                )

                            while (true) {
                                val read =
                                    input.read(
                                        buffer
                                    )

                                if (read < 0) {
                                    break
                                }

                                output.write(
                                    buffer,
                                    0,
                                    read,
                                )

                                downloaded +=
                                    read

                                if (total > 0L) {
                                    val progress =
                                        (
                                            downloaded *
                                                100L /
                                                total
                                        )
                                            .toInt()
                                            .coerceIn(
                                                0,
                                                100,
                                            )

                                    if (
                                        progress !=
                                        lastProgress
                                    ) {
                                        lastProgress =
                                            progress

                                        withContext(
                                            Dispatchers.Main
                                        ) {
                                            onProgress(
                                                progress
                                            )
                                        }
                                    }
                                }
                            }
                        }
                }

            withContext(
                Dispatchers.Main
            ) {
                onProgress(100)
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun verifySha256(
        file: File,
        expected: String?,
    ) {
        if (
            expected.isNullOrBlank()
        ) {
            return
        }

        val digest =
            MessageDigest
                .getInstance(
                    "SHA-256"
                )

        file.inputStream()
            .buffered()
            .use {
                input ->
                val buffer =
                    ByteArray(
                        64 * 1024
                    )

                while (true) {
                    val read =
                        input.read(
                            buffer
                        )

                    if (read < 0) {
                        break
                    }

                    digest.update(
                        buffer,
                        0,
                        read,
                    )
                }
            }

        val actual =
            digest.digest()
                .joinToString(
                    ""
                ) {
                    byte ->
                    "%02x".format(
                        byte
                    )
                }

        require(
            actual.equals(
                expected,
                ignoreCase = true,
            )
        ) {
            "Downloaded APK failed integrity verification."
        }
    }

    private fun launchInstaller(
        context: Context,
        apkFile: File,
    ) {
        val uri =
            FileProvider
                .getUriForFile(
                    context,
                    "${context.packageName}.vueo.updates",
                    apkFile,
                )

        val intent =
            Intent(
                Intent.ACTION_VIEW
            )
                .setDataAndType(
                    uri,
                    "application/vnd.android.package-archive",
                )
                .addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_ACTIVITY_NEW_TASK
                )

        context.startActivity(
            intent
        )
    }
}

private fun JSONArray?.toStringList():
    List<String> {
    if (this == null) {
        return emptyList()
    }

    return buildList {
        for (
            index in
            0 until length()
        ) {
            optString(index)
                .takeIf {
                    it.isNotBlank()
                }
                ?.let(::add)
        }
    }
}

private fun JSONObject.optHttpsUrl(
    key: String,
): String? =
    optString(key)
        .trim()
        .takeIf {
            it.startsWith(
                "https://"
            )
        }
