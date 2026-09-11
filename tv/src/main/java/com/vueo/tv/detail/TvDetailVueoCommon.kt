package com.vueo.tv.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vueo.shared.core.media.MediaItem
import com.vueo.tv.ui.TvDesign
import com.vueo.tv.ui.TvNetworkImage

internal val VueoDetailHorizontalPadding = 48.dp
internal val VueoDetailHeroHeight = 540.dp
internal val VueoDetailCardShape = RoundedCornerShape(12.dp)

internal object VueoDetailFocusMemory {
    var mediaKey: String? = null
    var selectedSeason: Int? = null
    var episodeId: String? = null

    fun resetFor(mediaKey: String) {
        this.mediaKey = mediaKey
        selectedSeason = null
        episodeId = null
    }
}

@Composable
internal fun VueoDetailBackdrop(
    item: MediaItem,
    imageAlpha: Float,
    scrimAlpha: Float,
) {
    Box(Modifier.fillMaxSize()) {
        TvNetworkImage(
            url = item.background ?: item.poster,
            contentDescription = item.name,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = imageAlpha },
            contentScale = ContentScale.Crop,
            fallback = TvDesign.Black,
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = scrimAlpha }
                .background(
                    Brush.horizontalGradient(
                        0f to TvDesign.Black.copy(alpha = .99f),
                        .18f to TvDesign.Black.copy(alpha = .94f),
                        .42f to TvDesign.Black.copy(alpha = .70f),
                        .68f to TvDesign.Black.copy(alpha = .22f),
                        1f to Color.Transparent,
                    )
                )
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        .56f to Color.Transparent,
                        .78f to TvDesign.Black.copy(alpha = .30f),
                        1f to TvDesign.Black.copy(alpha = .94f),
                    )
                ),
        )
    }
}

@Composable
internal fun VueoDetailSectionTitle(title: String) {
    Text(
        text = title,
        color = TvDesign.White,
        fontSize = 19.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(horizontal = VueoDetailHorizontalPadding),
    )
}

@Composable
internal fun VueoDetailMessage(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = VueoDetailHorizontalPadding, vertical = 8.dp)
            .background(TvDesign.Surface.copy(alpha = .84f), VueoDetailCardShape)
            .border(1.dp, TvDesign.White.copy(alpha = .08f), VueoDetailCardShape)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = message,
            color = TvDesign.White.copy(alpha = .60f),
            fontSize = 12.sp,
        )
    }
}

internal fun vueoDetailRuntime(minutes: Int): String {
    val hours = minutes / 60
    val rest = minutes % 60
    return when {
        hours <= 0 -> "${minutes}m"
        rest == 0 -> "${hours}h"
        else -> "${hours}h ${rest}m"
    }
}
