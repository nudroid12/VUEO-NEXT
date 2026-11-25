package com.vueo.tv.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

@OptIn(ExperimentalFoundationApi::class)
private val PosterGridReveal = object : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        if (size <= 0f || containerSize <= 0f) return 0f
        val end = offset + size
        return when {
            // A card taller than the viewport may already cover it.
            offset <= 0f && end >= containerSize -> 0f
            offset < -1f -> offset
            end > containerSize + 1f -> minOf(offset, end - containerSize)
            else -> 0f
        }
    }
}

/** Reveal off-screen cards without re-aligning a row that is already visible. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TvPosterGridReveal(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalBringIntoViewSpec provides PosterGridReveal, content = content)
}
