package com.vueo.tv.update

import android.view.KeyEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vueo.tv.BuildConfig
import com.vueo.tv.ui.TvDesign
import kotlinx.coroutines.launch

private val PromptShape = RoundedCornerShape(24.dp)
private val ActionShape = RoundedCornerShape(14.dp)

@Composable
fun TvUpdatePrompt(
    release: TvUpdateRelease,
    onLater: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val laterRequester = remember { FocusRequester() }
    val updateRequester = remember { FocusRequester() }

    var downloading by remember(release.versionCode) { mutableStateOf(false) }
    var progress by remember(release.versionCode) { mutableIntStateOf(0) }
    var status by remember(release.versionCode) { mutableStateOf<String?>(null) }

    val canDownload = release.downloadUrl != null

    LaunchedEffect(release.versionCode, canDownload) {
        if (canDownload) updateRequester.requestFocus() else laterRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = .74f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 560.dp, max = 680.dp)
                .clip(PromptShape)
                .background(TvDesign.SurfaceRaised.copy(alpha = .98f))
                .border(1.dp, TvDesign.White.copy(alpha = .14f), PromptShape)
                .padding(horizontal = 34.dp, vertical = 30.dp),
        ) {
            TextLine(
                text = "UPDATE AVAILABLE",
                sizeSp = 13,
                weight = FontWeight.SemiBold,
                color = TvDesign.Muted,
                letterSpacingSp = 1.4f,
            )

            Spacer(Modifier.height(10.dp))

            TextLine(
                text = release.title,
                sizeSp = 29,
                weight = FontWeight.SemiBold,
                color = TvDesign.White,
                maxLines = 2,
            )

            Spacer(Modifier.height(8.dp))

            TextLine(
                text = "${BuildConfig.VERSION_NAME}  →  ${release.versionName}",
                sizeSp = 16,
                weight = FontWeight.Medium,
                color = TvDesign.Muted,
            )

            Spacer(Modifier.height(24.dp))

            TextLine(
                text = "What’s new",
                sizeSp = 18,
                weight = FontWeight.SemiBold,
                color = TvDesign.White,
            )

            Spacer(Modifier.height(10.dp))

            val notes = release.changelog.take(4)
            if (notes.isEmpty()) {
                TextLine(
                    text = "Latest VUEO TV development build is ready to install.",
                    sizeSp = 16,
                    weight = FontWeight.Normal,
                    color = TvDesign.Muted,
                    maxLines = 3,
                )
            } else {
                notes.forEach { note ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                    ) {
                        TextLine(
                            text = "•",
                            sizeSp = 16,
                            weight = FontWeight.Bold,
                            color = TvDesign.White,
                        )
                        Spacer(Modifier.width(10.dp))
                        TextLine(
                            text = note,
                            sizeSp = 16,
                            weight = FontWeight.Normal,
                            color = TvDesign.Muted,
                            modifier = Modifier.weight(1f),
                            maxLines = 2,
                        )
                    }
                    Spacer(Modifier.height(7.dp))
                }
            }

            status?.let { message ->
                Spacer(Modifier.height(10.dp))
                TextLine(
                    text = message,
                    sizeSp = 14,
                    weight = FontWeight.Medium,
                    color = TvDesign.White.copy(alpha = .86f),
                    maxLines = 2,
                )
            }

            Spacer(Modifier.height(28.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TvUpdateAction(
                    label = "Later",
                    requester = laterRequester,
                    enabled = !downloading,
                    onClick = onLater,
                    onBack = onLater,
                    left = if (canDownload) updateRequester else laterRequester,
                    right = if (canDownload) updateRequester else laterRequester,
                    up = laterRequester,
                    down = laterRequester,
                    primary = false,
                )

                Spacer(Modifier.width(12.dp))

                TvUpdateAction(
                    label = when {
                        downloading -> "$progress%"
                        canDownload -> "Update"
                        else -> "Unavailable"
                    },
                    requester = updateRequester,
                    enabled = canDownload,
                    onClick = {
                        if (!downloading) {
                            if (TvUpdateManager.needsInstallPermission(context)) {
                                status = "Allow installs for VUEO, then return and choose Update again."
                                TvUpdateManager.openInstallPermissionSettings(context.applicationContext)
                            } else {
                                downloading = true
                                progress = 0
                                status = "Downloading verified update…"
                                scope.launch {
                                    TvUpdateManager
                                        .downloadAndInstall(
                                            context = context.applicationContext,
                                            release = release,
                                            onProgress = { progress = it },
                                        )
                                        .onSuccess {
                                            status = "Android installer opened."
                                        }
                                        .onFailure { failure ->
                                            status = failure.message ?: "Unable to install update."
                                        }
                                    downloading = false
                                }
                            }
                        }
                    },
                    onBack = { if (!downloading) onLater() },
                    left = laterRequester,
                    right = laterRequester,
                    up = updateRequester,
                    down = updateRequester,
                    primary = true,
                )
            }

            Spacer(Modifier.height(16.dp))

            TextLine(
                text = "Android will ask for final confirmation before installation.",
                sizeSp = 12,
                weight = FontWeight.Normal,
                color = TvDesign.Dim,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun TvUpdateAction(
    label: String,
    requester: FocusRequester,
    enabled: Boolean,
    onClick: () -> Unit,
    onBack: () -> Unit,
    left: FocusRequester,
    right: FocusRequester,
    up: FocusRequester,
    down: FocusRequester,
    primary: Boolean,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.035f else 1f,
        animationSpec = tween(if (focused) 125 else 95),
        label = "updateActionScale",
    )

    val background = when {
        !enabled -> TvDesign.White.copy(alpha = .05f)
        focused -> TvDesign.White.copy(alpha = if (primary) .22f else .16f)
        primary -> TvDesign.White.copy(alpha = .11f)
        else -> TvDesign.White.copy(alpha = .055f)
    }

    val border = when {
        focused -> TvDesign.White.copy(alpha = .86f)
        primary && enabled -> TvDesign.White.copy(alpha = .16f)
        else -> TvDesign.White.copy(alpha = .10f)
    }

    Box(
        modifier = Modifier
            .focusRequester(requester)
            .focusProperties {
                this.left = left
                this.right = right
                this.up = up
                this.down = down
            }
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                val keyCode = event.nativeKeyEvent.keyCode
                when {
                    keyCode == KeyEvent.KEYCODE_BACK -> {
                        if (event.type == KeyEventType.KeyUp && enabled) onBack()
                        true
                    }

                    keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                        keyCode == KeyEvent.KEYCODE_ENTER ||
                        keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                        if (event.type == KeyEventType.KeyUp && enabled) onClick()
                        true
                    }

                    else -> false
                }
            }
            .scale(scale)
            .clip(ActionShape)
            .background(background)
            .border(1.dp, border, ActionShape)
            .focusable(enabled)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        TextLine(
            text = label,
            sizeSp = 15,
            weight = if (primary) FontWeight.SemiBold else FontWeight.Medium,
            color = if (enabled) TvDesign.White else TvDesign.Dim,
            maxLines = 1,
        )
    }
}

@Composable
private fun TextLine(
    text: String,
    sizeSp: Int,
    weight: FontWeight,
    color: Color,
    modifier: Modifier = Modifier,
    letterSpacingSp: Float = 0f,
    maxLines: Int = Int.MAX_VALUE,
) {
    androidx.compose.material3.Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = sizeSp.sp,
        fontWeight = weight,
        letterSpacing = letterSpacingSp.sp,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}
