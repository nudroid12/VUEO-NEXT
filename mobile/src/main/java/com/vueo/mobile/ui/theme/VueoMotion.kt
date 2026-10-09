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
 * Full-page navigation uses an overlapping handoff: the incoming surface starts
 * immediately while the outgoing surface is held fully visible for a short
 * moment before fading. That overlap is deliberate. It prevents the app
 * background from becoming visible between two expensive Compose trees, which
 * used to read as a black/dark flicker on poster-heavy screens.
 *
 * Only the incoming page gets a tiny depth movement. Scaling both full-screen
 * trees at the same time costs more GPU work and makes the handoff look like it
 * catches for a frame on slower devices.
 */
internal object VueoMotion {
    const val QUICK_MS = 120
    const val STANDARD_MS = 180
    const val SCREEN_MS = 190

    val EaseOut = CubicBezierEasing(0.22f, 0.61f, 0.36f, 1f)
    val EaseIn = CubicBezierEasing(0.40f, 0f, 1f, 1f)
    val EaseInOut = CubicBezierEasing(0.40f, 0f, 0.20f, 1f)
}

/** Forward/deeper full-screen navigation. No incoming dead-time. */
internal fun vueoScreenForwardTransition(): ContentTransform =
    (
        fadeIn(
            animationSpec = tween(
                durationMillis = 175,
                easing = VueoMotion.EaseOut,
            ),
        ) +
            scaleIn(
                initialScale = 0.990f,
                animationSpec = tween(
                    durationMillis = 195,
                    easing = VueoMotion.EaseOut,
                ),
            )
    ) togetherWith
        fadeOut(
            animationSpec = tween(
                durationMillis = 135,
                delayMillis = 42,
                easing = VueoMotion.EaseInOut,
            ),
        )

/**
 * Reverse/back navigation.
 *
 * The previous surface begins immediately from a very small "behind" scale,
 * while the outgoing page stays opaque briefly. This keeps the visual depth
 * cue without exposing the app background between frames.
 */
internal fun vueoScreenBackTransition(): ContentTransform =
    (
        fadeIn(
            animationSpec = tween(
                durationMillis = 155,
                easing = VueoMotion.EaseOut,
            ),
        ) +
            scaleIn(
                initialScale = 1.008f,
                animationSpec = tween(
                    durationMillis = 170,
                    easing = VueoMotion.EaseOut,
                ),
            )
    ) togetherWith
        fadeOut(
            animationSpec = tween(
                durationMillis = 118,
                delayMillis = 34,
                easing = VueoMotion.EaseInOut,
            ),
        )

/** Details -> root: reveal a fully opaque root beneath the outgoing page.
 * Avoid fading both text-heavy pages together or exposing a dark gap.
 */
internal fun vueoDetailsRootBackTransition(): ContentTransform =
    (scaleIn(
        initialScale = 1.004f,
        animationSpec = tween(durationMillis = 160, easing = VueoMotion.EaseOut),
    ) togetherWith fadeOut(
        animationSpec = tween(durationMillis = 100, easing = VueoMotion.EaseOut),
    )).apply {
        targetContentZIndex = -1f
    }

/** Root-tab changes are deliberately shorter and shallower than page pushes. */
internal fun vueoTabCrossTransition(): ContentTransform =
    (
        fadeIn(
            animationSpec = tween(
                durationMillis = 135,
                easing = VueoMotion.EaseOut,
            ),
        ) +
            scaleIn(
                initialScale = 0.996f,
                animationSpec = tween(
                    durationMillis = 150,
                    easing = VueoMotion.EaseOut,
                ),
            )
    ) togetherWith
        fadeOut(
            animationSpec = tween(
                durationMillis = 100,
                delayMillis = 30,
                easing = VueoMotion.EaseInOut,
            ),
        )

/**
 * Full-screen player handoff stays fade-only so the video surface never zooms.
 * The outgoing screen is held briefly while the player/root starts drawing.
 */
internal fun vueoPlayerRouteTransition(): ContentTransform =
    fadeIn(
        animationSpec = tween(
            durationMillis = 165,
            easing = VueoMotion.EaseOut,
        ),
    ) togetherWith
        fadeOut(
            animationSpec = tween(
                durationMillis = 110,
                delayMillis = 34,
                easing = VueoMotion.EaseInOut,
            ),
        )

/* Compatibility names used by nested VUEO screens. They now use the new
 * overlap model rather than the old delayed fade-through implementation. */
internal fun vueoFadeThrough(): ContentTransform = vueoScreenForwardTransition()

internal fun vueoDetailsBackFadeThrough(): ContentTransform = vueoScreenBackTransition()

internal fun vueoHomeReturnFadeThrough(): ContentTransform = vueoTabCrossTransition()

/** Small player overlays/feedback use fade only and keep their configurable timing. */
internal fun vueoPlayerFadeThrough(
    enterDurationMillis: Int = 200,
    exitDurationMillis: Int = 120,
    enterDelayMillis: Int = 0,
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
