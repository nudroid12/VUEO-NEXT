package com.vueo.shared.core.player

import android.content.Context
import android.graphics.Typeface

/** Bundled offline fonts; the system face remains the default. */
object SubtitleFonts {
    val ids = listOf("default", "arimo", "dejavu_sans", "atkinson", "noto_sans", "roboto")
    private val labels = listOf("Default", "Arimo", "DejaVu Sans", "Atkinson Hyperlegible", "Noto Sans", "Roboto")
    private val cache = mutableMapOf<String, Typeface>()
    fun normalize(id: String) = id.takeIf { it in ids } ?: "default"
    fun label(id: String) = labels[ids.indexOf(normalize(id))]
    fun next(id: String, direction: Int): String =
        ids[(ids.indexOf(normalize(id)) + direction + ids.size) % ids.size]
    @Synchronized
    fun resolve(context: Context, id: String, bold: Boolean): Typeface {
        val font = normalize(id)
        if (font == "default") return if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        val file = font + if (bold) "_bold" else ""
        return cache.getOrPut(file) {
            Typeface.createFromAsset(context.applicationContext.assets, "subtitle_fonts/$file.ttf")
        }
    }
}
