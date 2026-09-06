package com.vueo.tv.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vueo.tv.ui.TvDesign
import com.vueo.tv.ui.TvNetworkImage

@Composable
internal fun TvModernHomeHero(
    entry: TvHomeEntry?,
    heroHeight: Dp,
    rowsViewportHeight: Dp,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Crossfade(
            targetState = entry,
            animationSpec = tween(durationMillis = 360),
            label = "modernHomeHeroMedia",
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 56.dp)
                .fillMaxWidth(MODERN_HOME_HERO_MEDIA_WIDTH_FRACTION)
                .height(heroHeight),
        ) { displayedEntry ->
            val media = displayedEntry?.media
            TvNetworkImage(
                url = media?.background ?: media?.poster,
                contentDescription = media?.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                fallback = TvDesign.Black,
            )
        }

        // Nuvio-style two-axis fade: a left reading field plus a clean handoff
        // from the hero image into the rows below.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to TvDesign.Black,
                        .22f to TvDesign.Black.copy(alpha = .90f),
                        .46f to TvDesign.Black.copy(alpha = .72f),
                        .72f to TvDesign.Black.copy(alpha = .22f),
                        1f to Color.Transparent,
                    )
                )
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        .58f to Color.Transparent,
                        .78f to TvDesign.Black.copy(alpha = .58f),
                        1f to TvDesign.Black,
                    )
                )
        )

        Crossfade(
            targetState = entry,
            animationSpec = tween(durationMillis = 180),
            label = "modernHomeHeroCopy",
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(
                    start = 52.dp,
                    end = 48.dp,
                    bottom = rowsViewportHeight + 16.dp,
                )
                .fillMaxWidth(MODERN_HOME_HERO_TEXT_WIDTH_FRACTION),
        ) { focusedEntry ->
            if (focusedEntry != null) {
                HeroCopy(entry = focusedEntry)
            }
        }
    }
}

@Composable
private fun HeroCopy(entry: TvHomeEntry) {
    val media = entry.media

    Column(
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        androidx.compose.material3.Text(
            text = media.name,
            color = TvDesign.White,
            fontSize = 36.sp,
            lineHeight = 39.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        val primaryMeta = media.heroPrimaryMeta()
        if (primaryMeta.isNotBlank()) {
            androidx.compose.material3.Text(
                text = primaryMeta,
                color = TvDesign.White.copy(alpha = .86f),
                fontSize = 13.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        val secondaryMeta = entry.heroSecondaryMeta()
        if (secondaryMeta.isNotBlank()) {
            androidx.compose.material3.Text(
                text = secondaryMeta,
                color = TvDesign.White.copy(alpha = .82f),
                fontSize = 13.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        media.description?.takeIf { it.isNotBlank() }?.let { description ->
            androidx.compose.material3.Text(
                text = description,
                color = TvDesign.White.copy(alpha = .78f),
                fontSize = 14.sp,
                lineHeight = 19.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
