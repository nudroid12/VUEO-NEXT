package com.vueo.mobile.ui

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
 * Full-page navigation prioritises frame continuity over visible effects:
 * the outgoing surface fades away first, then the incoming surface finishes
 * the handoff with only a tiny depth movement. Keeping full-page scale work
 * on the incoming surface only avoids the double-moving/ghosted frame that
 * is especially noticeable on poster-heavy screens.
 */
internal object VueoMotion {
    const val QUICK_MS = 140
    const val STANDARD_MS = 210
    const val SCREEN_MS = 240

    val EaseOut = CubicBezierEasing(0.22f, 0.61f, 0.36f, 1f)
    val EaseIn = CubicBezierEasing(0.40f, 0f, 1f, 1f)
    val EaseInOut = CubicBezierEasing(0.40f, 0f, 0.20f, 1f)
}

/**
 * Full-page navigation transition.
 *
 * This is intentionally a near-sequential fade-through rather than a cross
 * dissolve. The incoming surface starts only when the outgoing surface is
 * already faint, so two readable pages never sit on top of each other.
 * Outgoing full-screen scale was removed because scaling two complex Compose
 * trees at once can make the handoff look like it catches for a frame.
 */
internal fun vueoFadeThrough(): ContentTransform =
    (
        fadeIn(
            animationSpec = tween(
                durationMillis = 165,
                delayMillis = 72,
                easing = VueoMotion.EaseOut,
            ),
        ) +
            scaleIn(
                initialScale = 0.996f,
                animationSpec = tween(
                    durationMillis = 180,
                    delayMillis = 64,
                    easing = VueoMotion.EaseOut,
                ),
            )
    ) togetherWith
        fadeOut(
            animationSpec = tween(
                durationMillis = 96,
                easing = VueoMotion.EaseIn,
            ),
        )

/**
 * Home is one of the heaviest poster surfaces. Give the outgoing page a
 * slightly cleaner lead before Home starts drawing, while keeping the total
 * transition short enough that navigation still feels immediate.
 */
internal fun vueoHomeReturnFadeThrough(): ContentTransform =
    (
        fadeIn(
            animationSpec = tween(
                durationMillis = 160,
                delayMillis = 80,
                easing = VueoMotion.EaseOut,
            ),
        ) +
            scaleIn(
                initialScale = 0.997f,
                animationSpec = tween(
                    durationMillis = 175,
                    delayMillis = 72,
                    easing = VueoMotion.EaseOut,
                ),
            )
    ) togetherWith
        fadeOut(
            animationSpec = tween(
                durationMillis = 92,
                easing = VueoMotion.EaseIn,
            ),
        )

/** Player transitions remain fade-only so the video surface never zooms. */
internal fun vueoPlayerFadeThrough(
    enterDurationMillis: Int = 200,
    exitDurationMillis: Int = 120,
    enterDelayMillis: Int = 20,
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
    initialScale: Float = 0.985f,
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
    targetScale: Float = 0.992f,
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
