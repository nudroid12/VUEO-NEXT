package com.vueo.tv.home

import com.vueo.tv.ui.motion.*
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.ScaleFactor
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vueo.tv.ui.TvDesign
import com.vueo.tv.ui.TvNetworkImage
import com.vueo.tv.ui.tvSidebarContentStartPadding
import com.vueo.tv.ui.motion.TvMotion

/**
 * Modern Home hero scene.
 *
 * Important: the fades live inside the hero-media bounds, matching Vueo's
 * Modern Home composition. This avoids dimming the entire Home surface and
 * gives the rows a clean black field beneath the artwork.
 */
@OptIn(androidx.compose.animation.ExperimentalAnimationApi::class)
@Composable
internal fun TvModernHomeHero(
    scene: TvHomeHeroScene?,
    heroHeight: Dp,
    rowsViewportHeight: Dp,
    stableHeroHeight: Dp,
    stableRowsViewportHeight: Dp,
    modifier: Modifier = Modifier,
) {
    val contentStartPadding = tvSidebarContentStartPadding(MODERN_HOME_CONTENT_START_PADDING)
    val density = LocalDensity.current
    val visibleHeroHeightPx = with(density) { heroHeight.toPx() }
    val stableHeroHeightPx = with(density) { stableHeroHeight.toPx() }
    // Keep decoding at fixed bounds, but draw the same crop as a genuinely
    // resizing viewport. Uniform scaling preserves the image's aspect ratio.
    val resizingHeroCrop = remember(visibleHeroHeightPx) {
        object : ContentScale {
            override fun computeScaleFactor(srcSize: Size, dstSize: Size): ScaleFactor {
                if (srcSize.width <= 0f || srcSize.height <= 0f) return ScaleFactor(1f, 1f)
                val scale = maxOf(dstSize.width / srcSize.width, visibleHeroHeightPx / srcSize.height)
                return ScaleFactor(scale, scale)
            }
        }
    }
    val heroImageTranslationPx = (visibleHeroHeightPx - stableHeroHeightPx) / 2f
    val copyTranslationPx = with(density) { (stableRowsViewportHeight - rowsViewportHeight).toPx() }
    // Both planes use one transition clock; same-title enrichment updates do not
    // restart the fade. Geometry changes never change the image decode target.
    var readyScene by remember { mutableStateOf<TvHomeHeroScene?>(null) }
    LaunchedEffect(scene) {
        if (readyScene == null || readyScene?.entry?.key == scene?.entry?.key) readyScene = scene
    }
    val sceneTransition = updateTransition(readyScene ?: scene, label = "homeHeroScene")
    val mediaClip = remember(visibleHeroHeightPx) {
        object : Shape {
            override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: androidx.compose.ui.unit.Density): Outline {
                return Outline.Rectangle(
                    androidx.compose.ui.geometry.Rect(0f, 0f, size.width, visibleHeroHeightPx.coerceIn(0f, size.height)),
                )
            }
        }
    }
    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 56.dp)
                .fillMaxWidth(MODERN_HOME_HERO_MEDIA_WIDTH_FRACTION)
                .height(stableHeroHeight),
        ) {
            // Warm the incoming image at exactly the displayed decode size. Keep
            // the outgoing scene visible until this cancellable load completes.
            val incoming = scene
            if (incoming != null && readyScene != null && incoming.entry.key != readyScene?.entry?.key) {
                key(incoming.entry.key) {
                    TvNetworkImage(
                        url = incoming.entry.media.background ?: incoming.entry.media.poster,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0f },
                        highPriority = true,
                        fadeEnabled = false,
                        onLoadResult = { readyScene = incoming },
                    )
                }
            }
            sceneTransition.Crossfade(
                animationSpec = tvTunedSpec(TvMotionGroup.HERO, true, tween(
                    durationMillis = 400,
                    easing = TvMotion.EaseOut,
                )),
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    clip = true
                    shape = mediaClip
                },
                contentKey = { it?.entry?.key },
            ) { displayedEntry ->
                val media = displayedEntry?.entry?.media
                TvNetworkImage(
                    highPriority = true,
                    fadeEnabled = false,
                    url = media?.background ?: media?.poster,
                    contentDescription = media?.name,
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        translationY = heroImageTranslationPx
                    },
                    contentScale = resizingHeroCrop,
                    fallback = TvDesign.Black,
                )
            }

            HeroMediaGradient(modifier = Modifier.fillMaxWidth().height(heroHeight))
        }

        sceneTransition.Crossfade(
            animationSpec = tvTunedSpec(TvMotionGroup.HERO, true, tween(
                durationMillis = 400,
                easing = TvMotion.EaseOut,
            )),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(
                    start = contentStartPadding,
                    end = 48.dp,
                    bottom = stableRowsViewportHeight + 16.dp,
                )
                .fillMaxWidth(MODERN_HOME_HERO_TEXT_WIDTH_FRACTION)
                .graphicsLayer { translationY = copyTranslationPx },
            contentKey = { it?.entry?.key },
        ) { focusedEntry ->
            if (focusedEntry != null) {
                HeroCopy(scene = focusedEntry)
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
            // Vueo Modern Home only fades the leading ~45% of the hero-media
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
private fun HeroCopy(scene: TvHomeHeroScene) {
    val entry = scene.entry
    val media = entry.media
    val logo = scene.artwork?.logo
    var logoLoadFailed by remember(logo) { mutableStateOf(false) }

    Column(
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(Modifier.height(100.dp).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
            if (!logo.isNullOrBlank() && !logoLoadFailed) {
                TvNetworkImage(
                    url = logo,
                    contentDescription = media.name,
                    modifier = Modifier.width(220.dp).height(100.dp),
                    contentScale = ContentScale.Fit,
                    fallback = Color.Transparent,
                    highPriority = true,
                    alignment = Alignment.CenterStart,
                    onLoadResult = { success -> logoLoadFailed = !success },
                )
            } else {
                androidx.compose.material3.Text(
                    text = media.name,
                    color = TvDesign.White,
                    fontSize = 36.sp,
                    lineHeight = 39.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

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
