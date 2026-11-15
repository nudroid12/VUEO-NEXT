package com.vueo.tv.settings

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.BaseColumns
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.vueo.shared.core.storage.VueoBackupManager
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class TvLocalBackup(val name: String, val uri: Uri)

internal object TvLocalBackups {
    private val downloadPath = "${Environment.DIRECTORY_DOWNLOADS}/VUEO/"

    val locationLabel: String
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) "Downloads/VUEO" else "VUEO local storage"

    suspend fun save(context: Context, includeCredentials: Boolean): TvLocalBackup =
        withContext(Dispatchers.IO) {
            val name = "${backupFileName().removeSuffix(".json")}-${UUID.randomUUID().toString().take(8)}.json"
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                val directory = localDirectory(context)
                check(directory.isDirectory || directory.mkdirs()) { "Unable to open VUEO local storage." }
                val temporary = File.createTempFile("backup-", ".tmp", directory)
                try {
                    VueoBackupManager.exportToUri(context, Uri.fromFile(temporary), includeCredentials)
                    val file = File(directory, name)
                    check(temporary.renameTo(file)) { "Unable to finish saving the backup." }
                    TvLocalBackup(name, Uri.fromFile(file))
                } finally {
                    temporary.delete()
                }
            } else {
                val resolver = context.contentResolver
                val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, downloadPath)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val uri = resolver.insert(collection, values) ?: error("Unable to open Downloads.")
                var saved = false
                try {
                    VueoBackupManager.exportToUri(context, uri, includeCredentials)
                    val completed = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                    check(resolver.update(uri, completed, null, null) == 1) { "Unable to finish saving the backup." }
                    saved = true
                    TvLocalBackup(name, uri)
                } finally {
                    if (!saved) runCatching { resolver.delete(uri, null, null) }
                }
            }
        }

    suspend fun list(context: Context): List<TvLocalBackup> = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            localDirectory(context).listFiles().orEmpty()
                .filter { it.isFile && it.extension == "json" }
                .sortedByDescending { it.lastModified() }
                .map { TvLocalBackup(it.name, Uri.fromFile(it)) }
        } else {
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val id = BaseColumns._ID
            val name = MediaStore.MediaColumns.DISPLAY_NAME
            val path = MediaStore.MediaColumns.RELATIVE_PATH
            val owner = MediaStore.MediaColumns.OWNER_PACKAGE_NAME
            val pending = MediaStore.MediaColumns.IS_PENDING
            val cursor = context.contentResolver.query(
                collection, arrayOf(id, name), "$path = ? AND $owner = ? AND $pending = 0 AND $name LIKE ?",
                arrayOf(downloadPath, context.packageName, "VUEO-TV-backup-%.json"), "$id DESC",
            ) ?: error("Unable to read VUEO backups in Downloads.")
            cursor.use {
                buildList {
                    while (it.moveToNext()) {
                        add(TvLocalBackup(it.getString(1), ContentUris.withAppendedId(collection, it.getLong(0))))
                    }
                }
            }
        }
    }

    private fun localDirectory(context: Context): File = File(context.filesDir, "backups")
}

@Composable
internal fun TvRestoreBackupPicker(
    files: List<TvLocalBackup>,
    message: String?,
    onPick: (TvLocalBackup) -> Unit,
    onBrowse: () -> Unit,
    onDismiss: () -> Unit,
) {
    val first = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Restore Backup") },
        text = {
            if (files.isEmpty()) {
                Text(message ?: "No VUEO backups found in ${TvLocalBackups.locationLabel}.")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 230.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(files, key = { it.uri.toString() }) { file ->
                        TextButton(
                            onClick = { onPick(file) },
                            modifier = if (file == files.first()) Modifier.focusRequester(first) else Modifier,
                        ) { Text(file.name) }
                    }
                }
            }
            LaunchedEffect(files.isEmpty()) { if (files.isNotEmpty()) first.requestFocus() }
        },
        confirmButton = { TextButton(onClick = onBrowse) { Text("Browse files") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
