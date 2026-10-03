package com.vueo.tv.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vueo.tv.ui.TvDesign

/** Decorative placeholders have no focus nodes and do not block sidebar navigation. */
@Composable
internal fun TvHomeLoading(
    contentStartPadding: Dp,
    rowsViewportHeight: Dp,
) {
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
        Box(
            Modifier.align(Alignment.TopEnd).padding(top = 30.dp, end = 48.dp)
                .fillMaxWidth(.55f).height(maxHeight * .44f)
                .clip(RoundedCornerShape(18.dp)).background(TvDesign.Surface),
        )
        Column(
            Modifier.align(Alignment.BottomStart)
                .padding(start = contentStartPadding, bottom = rowsViewportHeight + 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Placeholder(Modifier.width(240.dp).height(64.dp))
            Placeholder(Modifier.width(210.dp).height(14.dp))
            Placeholder(Modifier.width(340.dp).height(14.dp))
            Placeholder(Modifier.width(300.dp).height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(Modifier.size(20.dp), color = TvDesign.White, strokeWidth = 2.dp)
                Text("Loading Home", color = TvDesign.Muted, fontSize = 16.sp)
            }
        }
        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().height(rowsViewportHeight)
                .padding(start = contentStartPadding, top = 12.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Placeholder(Modifier.width(150.dp).height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                repeat(4) {
                    Placeholder(Modifier.width(240.dp).height((rowsViewportHeight * .35f).coerceIn(90.dp, 155.dp)))
                }
            }
            Spacer(Modifier.height(2.dp))
            Placeholder(Modifier.width(120.dp).height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                repeat(6) {
                    Placeholder(Modifier.width(135.dp).height((rowsViewportHeight * .35f).coerceIn(90.dp, 160.dp)))
                }
            }
        }
    }
}

@Composable
private fun Placeholder(modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(10.dp)).background(TvDesign.SurfaceRaised.copy(alpha = .65f)))
}

@Composable
internal fun TvHomeLoadFailure(
    message: String,
    cachedContentVisible: Boolean,
    contentRequester: FocusRequester,
    onContentFocused: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val retryRequester = remember { FocusRequester() }
    var retryFocused by remember { mutableStateOf(false) }
    LaunchedEffect(message, cachedContentVisible) {
        if (!cachedContentVisible) {
            withFrameNanos { }
            runCatching { retryRequester.requestFocus() }
        }
    }
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(TvDesign.Surface.copy(alpha = .96f)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            if (cachedContentVisible) "Couldn't refresh Home" else "Couldn't load Home",
            color = TvDesign.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
        )
        Text(
            message.lineSequence().firstOrNull().orEmpty().take(180),
            color = TvDesign.Muted, fontSize = 14.sp, lineHeight = 19.sp,
            maxLines = 3, overflow = TextOverflow.Ellipsis,
        )
        Button(
            onClick = onRetry,
            modifier = Modifier.focusRequester(retryRequester)
                .focusProperties { if (cachedContentVisible) down = contentRequester }
                .onFocusChanged {
                    retryFocused = it.isFocused
                    if (it.isFocused) onContentFocused()
                },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (retryFocused) TvDesign.White else TvDesign.SurfaceRaised,
                contentColor = if (retryFocused) TvDesign.Black else TvDesign.White,
            ),
        ) {
            Text("Cuba semula")
        }
    }
}
