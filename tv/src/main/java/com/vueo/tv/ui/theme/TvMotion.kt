package com.vueo.tv.ui.motion

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color

/**
 * VUEO TV motion language.
 *
 * Full-screen motion is deliberately lightweight: near-sequential fade-through
 * plus a tiny incoming-only depth cue. This avoids two complex TV surfaces
 * scaling at the same time, which can read as a hitch even when navigation is
 * otherwise responsive.
 */
internal object TvMotion {
    const val FOCUS_IN_MS = 120
    const val FOCUS_OUT_MS = 90
    const val QUICK_MS = 110
    const val ELEMENT_MS = 160
    const val PANEL_IN_MS = 200
    const val PANEL_OUT_MS = 125
    const val SCREEN_IN_MS = 180
    const val SCREEN_OUT_MS = 96
    const val BACKDROP_MS = 270

    val EaseOut = CubicBezierEasing(0.22f, 0.61f, 0.36f, 1f)
    val EaseIn = CubicBezierEasing(0.40f, 0f, 1f, 1f)
    val EaseInOut = CubicBezierEasing(0.40f, 0f, 0.20f, 1f)
}

internal fun tvScreenFadeThrough(
    enterDurationMillis: Int = TvMotion.SCREEN_IN_MS,
    exitDurationMillis: Int = TvMotion.SCREEN_OUT_MS,
    enterDelayMillis: Int = 72,
    initialScale: Float = 0.996f,
    @Suppress("UNUSED_PARAMETER") targetScale: Float = 0.992f,
): ContentTransform =
    (
        fadeIn(
            animationSpec = tween(
                durationMillis = enterDurationMillis,
                delayMillis = enterDelayMillis,
                easing = TvMotion.EaseOut,
            ),
        ) +
            scaleIn(
                initialScale = initialScale,
                animationSpec = tween(
                    durationMillis = enterDurationMillis,
                    delayMillis = (enterDelayMillis - 8).coerceAtLeast(0),
                    easing = TvMotion.EaseOut,
                ),
            )
    ) togetherWith
        fadeOut(
            animationSpec = tween(
                durationMillis = exitDurationMillis,
                easing = TvMotion.EaseIn,
            ),
        )

internal fun tvImmediateCut(): ContentTransform =
    EnterTransition.None togetherWith ExitTransition.None

/** Player workspace uses fade only so video never appears to zoom. */
internal fun tvPlayerFadeThrough(
    enterDurationMillis: Int = 190,
    exitDurationMillis: Int = 105,
    enterDelayMillis: Int = 15,
): ContentTransform =
    fadeIn(
        animationSpec = tween(
            durationMillis = enterDurationMillis,
            delayMillis = enterDelayMillis,
            easing = TvMotion.EaseOut,
        ),
    ) togetherWith
        fadeOut(
            animationSpec = tween(
                durationMillis = exitDurationMillis,
                easing = TvMotion.EaseInOut,
            ),
        )

internal fun tvPanelEnter(): EnterTransition =
    fadeIn(
        animationSpec = tween(
            durationMillis = TvMotion.PANEL_IN_MS,
            delayMillis = 8,
            easing = TvMotion.EaseOut,
        ),
    ) +
        scaleIn(
            initialScale = 0.985f,
            animationSpec = tween(
                durationMillis = TvMotion.PANEL_IN_MS,
                delayMillis = 8,
                easing = TvMotion.EaseOut,
            ),
        )

internal fun tvPanelExit(): ExitTransition =
    fadeOut(
        animationSpec = tween(
            durationMillis = TvMotion.PANEL_OUT_MS,
            easing = TvMotion.EaseInOut,
        ),
    ) +
        scaleOut(
            targetScale = 0.992f,
            animationSpec = tween(
                durationMillis = TvMotion.PANEL_OUT_MS,
                easing = TvMotion.EaseInOut,
            ),
        )

internal fun tvFocusSpec(focused: Boolean = true): FiniteAnimationSpec<Float> =
    tween(
        durationMillis = if (focused) TvMotion.FOCUS_IN_MS else TvMotion.FOCUS_OUT_MS,
        easing = TvMotion.EaseOut,
    )

internal fun tvFocusColorSpec(focused: Boolean = true): FiniteAnimationSpec<Color> =
    tween(
        durationMillis = if (focused) TvMotion.FOCUS_IN_MS else TvMotion.FOCUS_OUT_MS,
        easing = TvMotion.EaseOut,
    )
