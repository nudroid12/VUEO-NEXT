package com.vueo.shared.core.storage

import com.vueo.shared.core.media.MediaItem
import kotlinx.coroutines.flow.MutableStateFlow

data class TitleRating(val media: MediaItem, val value: Int)
object RatingChanges { val revision = MutableStateFlow(0L) }
