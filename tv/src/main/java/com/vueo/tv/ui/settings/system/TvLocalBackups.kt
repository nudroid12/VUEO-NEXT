package com.vueo.tv.settings

import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.BaseColumns
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.window.DialogProperties
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class TvLocalBackup(val name: String, val uri: Uri)

internal object TvLocalBackups {
    private val downloadPath = "${Environment.DIRECTORY_DOWNLOADS}/VUEO/"

    val locationLabel: String
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) "Downloads/VUEO" else "VUEO local storage"

    fun fileName(input: String): String? {
        val value = input.trim()
        if (value.isBlank() || value.startsWith('.') ||
            value.any { it.isISOControl() || it in "\\/:\"*?<>|" }
        ) return null
        val normalized = if (value.endsWith(".json", ignoreCase = true)) value else "$value.json"
        return normalized.takeIf { it.length <= 80 }
    }

    suspend fun save(context: Context, name: String, folder: Uri?, includeCredentials: Boolean): TvLocalBackup =
        withContext(Dispatchers.IO) {
            require(fileName(name) == name) { "Enter a valid backup file name." }
            if (folder != null) {
                val parent = DocumentsContract.buildDocumentUriUsingTree(folder, DocumentsContract.getTreeDocumentId(folder))
                val resolver = context.contentResolver
                val uri = DocumentsContract.createDocument(resolver, parent, "application/json", name)
                    ?: error("Unable to create a backup in the selected folder.")
                var saved = false
                try {
                    VueoBackupManager.exportToUri(context, uri, includeCredentials)
                    saved = true
                    return@withContext TvLocalBackup(displayName(context, uri, name), uri)
                } finally {
                    if (!saved) runCatching { DocumentsContract.deleteDocument(resolver, uri) }
                }
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                val directory = localDirectory(context)
                check(directory.isDirectory || directory.mkdirs()) { "Unable to open VUEO local storage." }
                val temporary = File.createTempFile("backup-", ".tmp", directory)
                try {
                    VueoBackupManager.exportToUri(context, Uri.fromFile(temporary), includeCredentials)
                    val file = File(directory, name)
                    check(file.createNewFile()) { "A backup with that name already exists. Choose another name." }
                    if (!temporary.renameTo(file)) {
                        file.delete()
                        error("Unable to finish saving the backup.")
                    }
                    TvLocalBackup(file.name, Uri.fromFile(file))
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
                    TvLocalBackup(displayName(context, uri, name), uri)
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
                arrayOf(downloadPath, context.packageName, "%.json"), "$id DESC",
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

    private fun displayName(context: Context, uri: Uri, fallback: String): String =
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        }.getOrNull() ?: fallback

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
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(TvLocalBackups.locationLabel)
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 230.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(files, key = { it.uri.toString() }) { file ->
                        TextButton(
                            onClick = { onPick(file) },
                            modifier = if (file == files.first()) Modifier.focusRequester(first) else Modifier,
                        ) { Text(if (file == files.first()) "Latest • ${file.name}" else file.name) }
                    }
                }
            }
            }
            LaunchedEffect(files.isEmpty()) { if (files.isNotEmpty()) first.requestFocus() }
        },
        confirmButton = { TextButton(onClick = onBrowse) { Text("Browse files") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
internal fun TvBackupResultDialog(title: String, message: String, onDismiss: () -> Unit) {
    val close = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.focusRequester(close)) { Text("OK") }
            LaunchedEffect(Unit) { close.requestFocus() }
        },
    )
}

@Composable
internal fun TvSaveBackupDialog(
    includeCredentials: Boolean,
    onSave: (String, Uri?, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(backupFileName()) }
    var folder by remember { mutableStateOf<Uri?>(null) }
    var destination by remember { mutableStateOf(TvLocalBackups.locationLabel) }
    var notice by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val saveFocus = remember { FocusRequester() }
    val normalizedName = TvLocalBackups.fileName(name)
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            folder = uri
            val id = DocumentsContract.getTreeDocumentId(uri)
            destination = if (uri.authority == "com.android.externalstorage.documents") {
                val volume = id.substringBefore(':').let { if (it == "primary") "Internal storage" else it }
                listOf(volume, id.substringAfter(':', "").trim('/')).filter { it.isNotBlank() }.joinToString("/")
            } else {
                val document = DocumentsContract.buildDocumentUriUsingTree(uri, id)
                runCatching {
                    context.contentResolver.query(document, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                        if (it.moveToFirst()) it.getString(0) else null
                    }
                }.getOrNull() ?: "Selected folder"
            }
            notice = null
        } else {
            notice = "No folder selected. You can save to $destination."
        }
    }

    LaunchedEffect(editing) {
        if (editing) {
            withFrameNanos { }
            keyboard?.show()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(.55f),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = { Text("Save to") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("File name") },
                    singleLine = true,
                    readOnly = !editing,
                    isError = normalizedName == null,
                    supportingText = if (normalizedName == null) {
                        { Text("Enter a valid file name, up to 80 characters.") }
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                        .onFocusChanged { if (!it.isFocused) editing = false }
                        .onPreviewKeyEvent { event ->
                            if (event.key != Key.DirectionCenter && (editing || event.key != Key.Enter && event.key != Key.NumPadEnter)) {
                                return@onPreviewKeyEvent false
                            }
                            if (event.type == KeyEventType.KeyUp) {
                                if (editing) keyboard?.show() else editing = true
                            }
                            true
                        },
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Destination")
                    Text(destination)
                }
                if (includeCredentials) Text("API keys will be included.")
                notice?.let { Text(it) }
            }
        },
        dismissButton = {
            TextButton(onClick = {
                try {
                    folderPicker.launch(folder)
                } catch (_: ActivityNotFoundException) {
                    notice = "This TV has no folder picker. You can save to $destination."
                }
            }) { Text("Browse Folder") }
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        confirmButton = {
            TextButton(
                enabled = normalizedName != null,
                modifier = Modifier.focusRequester(saveFocus),
                onClick = {
                    if (normalizedName != null) {
                        keyboard?.hide()
                        onSave(normalizedName, folder, destination)
                    }
                },
            ) { Text("Save") }
            LaunchedEffect(Unit) { saveFocus.requestFocus() }
        },
    )
}

@Composable
internal fun TvBackupProgressDialog(message: String) {
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text(message) },
        text = { CircularProgressIndicator() },
        confirmButton = {},
    )
}
