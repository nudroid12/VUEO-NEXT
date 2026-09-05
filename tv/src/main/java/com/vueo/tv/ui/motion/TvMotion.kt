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
    const val QUICK_MS = 150
    const val FOCUS_MS = 170
    const val STANDARD_MS = 280
    const val SCREEN_MS = 340

    val EaseOut = CubicBezierEasing(0.22f, 0.61f, 0.36f, 1f)
    val EaseInOut = CubicBezierEasing(0.40f, 0f, 0.20f, 1f)
}

internal fun tvScreenFadeThrough(
    enterDurationMillis: Int = TvMotion.SCREEN_MS,
    exitDurationMillis: Int = TvMotion.QUICK_MS,
    enterDelayMillis: Int = 28,
    initialScale: Float = 0.988f,
    targetScale: Float = 0.994f,
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
    enterDurationMillis: Int = 240,
    exitDurationMillis: Int = 120,
    enterDelayMillis: Int = 50,
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
            durationMillis = TvMotion.STANDARD_MS,
            delayMillis = 20,
            easing = TvMotion.EaseOut,
        ),
    ) +
        scaleIn(
            initialScale = 0.992f,
            animationSpec = tween(
                durationMillis = TvMotion.STANDARD_MS,
                delayMillis = 20,
                easing = TvMotion.EaseOut,
            ),
        )

internal fun tvPanelExit(): ExitTransition =
    fadeOut(
        animationSpec = tween(
            durationMillis = TvMotion.QUICK_MS,
            easing = TvMotion.EaseInOut,
        ),
    ) +
        scaleOut(
            targetScale = 0.996f,
            animationSpec = tween(
                durationMillis = TvMotion.QUICK_MS,
                easing = TvMotion.EaseInOut,
            ),
        )

internal fun tvFocusSpec(): FiniteAnimationSpec<Float> =
    tween(
        durationMillis = TvMotion.FOCUS_MS,
        easing = TvMotion.EaseOut,
    )

internal fun tvFocusColorSpec(): FiniteAnimationSpec<Color> =
    tween(
        durationMillis = TvMotion.FOCUS_MS,
        easing = TvMotion.EaseOut,
    )
