package com.vueo.tv.ui.theme

import com.vueo.tv.ui.motion.TvMotionGroup
import com.vueo.tv.ui.motion.tvTunedSpec
import com.vueo.tv.ui.motion.TvMotion
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics

private class TabMarkerState {
    var host by mutableStateOf<Rect?>(null)
    var selected by mutableStateOf<Rect?>(null)
}
private val LocalTabMarker = staticCompositionLocalOf<TabMarkerState?> { null }

/** One shared underline per strip; it follows selected labels without changing layout or focus. */
@Composable
internal fun TvTabMarkerHost(content: @Composable () -> Unit) {
    val state = remember { TabMarkerState() }
    val host = state.host
    val selected = state.selected
    val ready = host != null && selected != null
    var initialized by remember { mutableStateOf(false) }
    SideEffect { if (ready) initialized = true }
    val markerWidth = (selected?.width ?: 0f) * .65f
    val spec = if (initialized) tvTunedSpec(
        TvMotionGroup.TABS, true, tween<Float>(160, easing = TvMotion.EaseOut),
    ) else tween<Float>(0)
    val x by animateFloatAsState(
        (selected?.left ?: 0f) - (host?.left ?: 0f) + ((selected?.width ?: 0f) - markerWidth) / 2f,
        spec, label = "tabMarkerX")
    val width by animateFloatAsState(markerWidth, spec, label = "tabMarkerWidth")
    val y = (selected?.bottom ?: 0f) - (host?.top ?: 0f) - 2f
    CompositionLocalProvider(LocalTabMarker provides state) {
        Box(Modifier.onGloballyPositioned { state.host = it.boundsInRoot() }
            .drawWithContent {
                drawContent()
                if (ready) drawLine(Color.White, Offset(x, y), Offset(x + width, y),
                    strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            }) { content() }
    }
}

/** Attach to the label/control, after any layout weight and before its padding. */
internal fun Modifier.tvTabMarker(selected: Boolean): Modifier = composed {
    val state = LocalTabMarker.current ?: return@composed this
    var bounds by remember { mutableStateOf<Rect?>(null) }
    SideEffect { if (selected && bounds != null) state.selected = bounds }
    this.semantics { this.selected = selected; role = Role.Tab }.onGloballyPositioned {
        bounds = it.boundsInRoot()
        if (selected) state.selected = bounds
    }
}
