package com.vueo.shared.core.detail

import com.vueo.shared.core.media.MediaItem
import com.vueo.shared.core.media.MediaPerson
import com.vueo.shared.core.media.MediaTypePolicy

object DetailPeoplePolicy {
    data class CreditLine(
        val label: String,
        val names: List<String>,
    )

    fun creditLines(media: MediaItem): List<CreditLine> = buildList {
        if (MediaTypePolicy.isSeries(media.type) && media.creators.isNotEmpty()) {
            add(CreditLine(label = "Creator", names = media.creators.take(3)))
        } else if (media.directors.isNotEmpty()) {
            add(CreditLine(label = "Director", names = media.directors.take(3)))
        }
        if (media.writers.isNotEmpty()) {
            add(CreditLine(label = "Writer", names = media.writers.take(3)))
        }
    }

    fun cast(media: MediaItem): List<MediaPerson> = media.cast.take(20)
}
