package com.vueo.shared.core.player

import com.vueo.shared.core.language.LanguagePolicy
import com.vueo.shared.core.media.SubtitleTrack

/** Metadata-only decision. Never touches a subtitle endpoint. */
object AutomaticSubtitlePolicy {
    fun isAi(name: String?, id: String?): Boolean =
        name.orEmpty().contains("ai", true) || id.orEmpty().contains("ai", true)
    fun isAi(track: SubtitleTrack): Boolean = isAi(track.name, track.id)
    fun <T> choose(tracks: List<T>, languages: List<String?>, languageOf: (T) -> String?, eligible: (T) -> Boolean): T? {
        val candidates = tracks.filter(eligible)
        languages.mapNotNull { LanguagePolicy.canonicalCode(it)?.takeUnless { code -> code == "und" } }.distinct().forEach { language ->
            candidates.firstOrNull { LanguagePolicy.canonicalCode(languageOf(it)) == language }?.let { return it }
        }
        return null
    }
}
