package com.vueo.tv.ui

import java.io.File

/**
 * Maintains the final image-file budget. The caller serializes maintenance,
 * final-file reads and publication with one disk mutex. Temporary downloads
 * are excluded after the initial orphan cleanup and keep their own size cap.
 */
internal class TvImageDiskCacheMaintenance(
    private val maxBytes: Long = 96L * 1024L * 1024L,
    private val intervalMs: Long = 60_000L,
) {
    private var prepared = false
    private var lastPruneMs: Long? = null

    fun maintain(directory: File, nowMs: Long, force: Boolean = false) {
        if (!prepared) {
            // Called before the first download, under the disk mutex.
            val initial = directory.listFiles() ?: return
            initial.filter { it.isFile && it.name.endsWith(".tmp") }
                .forEach { it.delete() }
            prepared = true
        }
        val last = lastPruneMs
        if (!force && last != null && nowMs - last in 0 until intervalMs) return
        val files = directory.listFiles()
            ?.filter { it.isFile && !it.name.endsWith(".tmp") }
            ?: return
        lastPruneMs = nowMs
        var bytes = files.sumOf(File::length)
        if (bytes <= maxBytes) return
        for (file in files.sortedWith(compareBy<File> { it.lastModified() }.thenBy { it.name })) {
            if (bytes <= maxBytes) break
            val length = file.length()
            if (file.delete()) bytes -= length
        }
    }
}
