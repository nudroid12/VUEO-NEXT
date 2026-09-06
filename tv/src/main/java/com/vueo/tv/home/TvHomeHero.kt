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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vueo.tv.ui.TvDesign
import com.vueo.tv.ui.TvNetworkImage

/**
 * Modern Home hero scene.
 *
 * Important: the fades live inside the hero-media bounds, matching Nuvio's
 * Modern Home composition. This avoids dimming the entire Home surface and
 * gives the rows a clean black field beneath the artwork.
 */
@Composable
internal fun TvModernHomeHero(
    entry: TvHomeEntry?,
    heroHeight: Dp,
    rowsViewportHeight: Dp,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 56.dp)
                .fillMaxWidth(MODERN_HOME_HERO_MEDIA_WIDTH_FRACTION)
                .height(heroHeight),
        ) {
            Crossfade(
                targetState = entry,
                animationSpec = tween(durationMillis = 300),
                label = "modernHomeHeroMedia",
                modifier = Modifier.fillMaxSize(),
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

            HeroMediaGradient(modifier = Modifier.fillMaxSize())
        }

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
private fun HeroMediaGradient(modifier: Modifier = Modifier) {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val bg = TvDesign.Black

    Box(
        modifier = modifier.drawWithCache {
            // Nuvio Modern Home only fades the leading ~45% of the hero-media
            // plane. The rest of the artwork stays vivid.
            val horizontalFadeWidth = size.width * .45f
            val horizontalStops = arrayOf(
                0.0f to bg,
                .22f to bg.copy(alpha = .86f),
                .46f to bg.copy(alpha = .56f),
                .76f to bg.copy(alpha = .16f),
                1.0f to Color.Transparent,
            )
            val horizontal = if (isRtl) {
                Brush.horizontalGradient(
                    colorStops = horizontalStops,
                    startX = size.width,
                    endX = size.width - horizontalFadeWidth,
                )
            } else {
                Brush.horizontalGradient(
                    colorStops = horizontalStops,
                    startX = 0f,
                    endX = horizontalFadeWidth,
                )
            }

            // The bottom fade is also confined to the hero plane and starts
            // late, so the image keeps contrast until it hands off to rows.
            val bottomFadeStart = size.height * .82f
            val vertical = Brush.verticalGradient(
                colorStops = arrayOf(
                    0.0f to Color.Transparent,
                    .40f to bg.copy(alpha = .25f),
                    .75f to bg.copy(alpha = .65f),
                    1.0f to bg,
                ),
                startY = bottomFadeStart,
                endY = size.height,
            )

            onDrawBehind {
                val left = if (isRtl) size.width - horizontalFadeWidth else 0f
                drawRect(
                    brush = horizontal,
                    topLeft = Offset(left, 0f),
                    size = Size(horizontalFadeWidth, size.height),
                )
                drawRect(
                    brush = vertical,
                    topLeft = Offset(0f, bottomFadeStart),
                    size = Size(size.width, size.height - bottomFadeStart),
                )
            }
        },
    ) {}
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
