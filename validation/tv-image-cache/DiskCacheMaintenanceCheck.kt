package com.vueo.tv.ui

import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files

private fun write(dir: File, name: String, size: Long, timestamp: Long): File =
    File(dir, name).apply {
        RandomAccessFile(this, "rw").use { it.setLength(size) }
        check(setLastModified(timestamp))
    }

private fun finalBytes(dir: File): Long =
    dir.listFiles().orEmpty().filter { it.isFile && !it.name.endsWith(".tmp") }
        .sumOf(File::length)

fun main() {
    val dir = Files.createTempDirectory("tv-image-cache-check").toFile()
    try {
        val maintenance = TvImageDiskCacheMaintenance(maxBytes = 12, intervalMs = 60)
        val oldest = write(dir, "oldest", 6, 1_000)
        val newer = write(dir, "newer", 6, 2_000)
        val orphan = write(dir, "orphan.tmp", 5, 3_000)
        maintenance.maintain(dir, nowMs = 0)
        check(!orphan.exists())
        check(oldest.exists() && newer.exists())

        val active = write(dir, "active.tmp", 8, 4_000)
        val latest = write(dir, "latest", 6, 5_000)
        maintenance.maintain(dir, nowMs = 10)
        check(finalBytes(dir) == 18L) // ordinary periodic scan is throttled
        maintenance.maintain(dir, nowMs = 61)
        check(!oldest.exists() && newer.exists() && latest.exists())
        check(active.exists() && finalBytes(dir) == 12L)

        // Every completed download forces the final-file budget check.
        repeat(100) { index ->
            write(dir, "new-$index", 6, 6_000L + index)
            maintenance.maintain(dir, nowMs = 62L + index, force = true)
            check(finalBytes(dir) <= 12L)
            check(active.exists())
        }
        check(File(dir, "new-99").exists())
        check(File(dir, "new-98").exists())
        check(!File(dir, "new-97").exists())

        // File-size accounting also uses the actual production limit.
        val production = File(dir, "production").apply { mkdirs() }
        val realLimit = TvImageDiskCacheMaintenance()
        repeat(7) { i -> write(production, "poster-$i", 16L*1024*1024, 1_000L + i) }
        realLimit.maintain(production, nowMs = 0, force = true)
        check(finalBytes(production) == 96L*1024*1024)
        check(!File(production, "poster-0").exists())
        check(File(production, "poster-6").exists())
        println("PASS: startup orphan cleanup, active temp preservation, periodic LRU, forced cap (100 downloads), production 96 MiB cap")
    } finally {
        dir.deleteRecursively()
    }
}
