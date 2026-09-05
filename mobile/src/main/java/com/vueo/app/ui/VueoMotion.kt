package com.vueo.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * VUEO motion language.
 *
 * Motion should be felt as continuity rather than noticed as an effect:
 * restrained fade-through, very shallow depth scaling and no bounce.
 */
internal object VueoMotion {
    const val QUICK_MS = 180
    const val STANDARD_MS = 300
    const val SCREEN_MS = 360

    val EaseOut = CubicBezierEasing(0.22f, 0.61f, 0.36f, 1f)
    val EaseInOut = CubicBezierEasing(0.40f, 0f, 0.20f, 1f)
}

internal fun vueoFadeThrough(
    enterDurationMillis: Int = VueoMotion.SCREEN_MS,
    exitDurationMillis: Int = VueoMotion.QUICK_MS,
    enterDelayMillis: Int = 32,
    initialScale: Float = 0.985f,
    targetScale: Float = 0.992f,
): ContentTransform =
    (
        fadeIn(
            animationSpec = tween(
                durationMillis = enterDurationMillis,
                delayMillis = enterDelayMillis,
                easing = VueoMotion.EaseOut,
            ),
        ) +
            scaleIn(
                initialScale = initialScale,
                animationSpec = tween(
                    durationMillis = enterDurationMillis,
                    delayMillis = enterDelayMillis,
                    easing = VueoMotion.EaseOut,
                ),
            )
    ) togetherWith
        (
            fadeOut(
                animationSpec = tween(
                    durationMillis = exitDurationMillis,
                    easing = VueoMotion.EaseInOut,
                ),
            ) +
                scaleOut(
                    targetScale = targetScale,
                    animationSpec = tween(
                        durationMillis = exitDurationMillis,
                        easing = VueoMotion.EaseInOut,
                    ),
                )
        )

internal fun vueoPlayerFadeThrough(
    enterDurationMillis: Int = 260,
    exitDurationMillis: Int = 150,
    enterDelayMillis: Int = 70,
): ContentTransform =
    fadeIn(
        animationSpec = tween(
            durationMillis = enterDurationMillis,
            delayMillis = enterDelayMillis,
            easing = VueoMotion.EaseOut,
        ),
    ) togetherWith
        fadeOut(
            animationSpec = tween(
                durationMillis = exitDurationMillis,
                easing = VueoMotion.EaseInOut,
            ),
        )

internal fun vueoSoftEnter(
    durationMillis: Int = VueoMotion.STANDARD_MS,
    delayMillis: Int = 0,
    initialScale: Float = 0.96f,
): EnterTransition =
    fadeIn(
        animationSpec = tween(
            durationMillis = durationMillis,
            delayMillis = delayMillis,
            easing = VueoMotion.EaseOut,
        ),
    ) +
        scaleIn(
            initialScale = initialScale,
            animationSpec = tween(
                durationMillis = durationMillis,
                delayMillis = delayMillis,
                easing = VueoMotion.EaseOut,
            ),
        )

internal fun vueoSoftExit(
    durationMillis: Int = VueoMotion.QUICK_MS,
    targetScale: Float = 0.97f,
): ExitTransition =
    fadeOut(
        animationSpec = tween(
            durationMillis = durationMillis,
            easing = VueoMotion.EaseInOut,
        ),
    ) +
        scaleOut(
            targetScale = targetScale,
            animationSpec = tween(
                durationMillis = durationMillis,
                easing = VueoMotion.EaseInOut,
            ),
        )

/**
 * Keeps a full-screen player workspace composed long enough for both its
 * entrance and exit to finish. This avoids the abrupt window pop caused by
 * wrapping Dialog() in `if (visible)`.
 */
@Composable
internal fun VueoMotionDialogHost(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(
        usePlatformDefaultWidth = false,
        decorFitsSystemWindows = false,
    ),
    content: @Composable () -> Unit,
) {
    val visibility = remember {
        MutableTransitionState(false)
    }
    visibility.targetState = visible

    if (visibility.currentState || visibility.targetState) {
        Dialog(
            onDismissRequest = onDismissRequest,
            properties = properties,
        ) {
            AnimatedVisibility(
                visibleState = visibility,
                enter = vueoSoftEnter(
                    durationMillis = 230,
                    initialScale = 0.992f,
                ),
                exit = vueoSoftExit(
                    durationMillis = 150,
                    targetScale = 0.996f,
                ),
            ) {
                content()
            }
        }
    }
}

