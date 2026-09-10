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
 * Motion should feel cinematic and continuous rather than decorative:
 * short fade-throughs, very shallow depth scaling and no bounce.
 */
internal object TvMotion {
    const val FOCUS_IN_MS = 120
    const val FOCUS_OUT_MS = 90
    const val QUICK_MS = 110
    const val ELEMENT_MS = 160
    const val PANEL_IN_MS = 190
    const val PANEL_OUT_MS = 110
    const val SCREEN_IN_MS = 250
    const val SCREEN_OUT_MS = 130
    const val BACKDROP_MS = 270

    val EaseOut = CubicBezierEasing(0.22f, 0.61f, 0.36f, 1f)
    val EaseInOut = CubicBezierEasing(0.40f, 0f, 0.20f, 1f)
}

internal fun tvScreenFadeThrough(
    enterDurationMillis: Int = TvMotion.SCREEN_IN_MS,
    exitDurationMillis: Int = TvMotion.SCREEN_OUT_MS,
    enterDelayMillis: Int = 12,
    initialScale: Float = 0.992f,
    targetScale: Float = 0.996f,
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
                    delayMillis = enterDelayMillis,
                    easing = TvMotion.EaseOut,
                ),
            )
    ) togetherWith
        (
            fadeOut(
                animationSpec = tween(
                    durationMillis = exitDurationMillis,
                    easing = TvMotion.EaseInOut,
                ),
            ) +
                scaleOut(
                    targetScale = targetScale,
                    animationSpec = tween(
                        durationMillis = exitDurationMillis,
                        easing = TvMotion.EaseInOut,
                    ),
                )
        )


internal fun tvImmediateCut(): ContentTransform =
    EnterTransition.None togetherWith ExitTransition.None

/** Player workspace uses fade only so video never appears to zoom. */
internal fun tvPlayerFadeThrough(
    enterDurationMillis: Int = 180,
    exitDurationMillis: Int = 90,
    enterDelayMillis: Int = 0,
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
            initialScale = 0.996f,
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
            targetScale = 0.996f,
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
