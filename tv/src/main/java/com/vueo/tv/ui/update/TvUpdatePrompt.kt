package com.vueo.tv.update

import com.vueo.tv.ui.motion.*
import android.view.KeyEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vueo.tv.R
import com.vueo.tv.ui.TvModalDialog
import com.vueo.tv.ui.TvDesign
import com.vueo.tv.ui.motion.TvMotion
import kotlinx.coroutines.launch

private val PromptShape = RoundedCornerShape(18.dp)
private val ActionShape = RoundedCornerShape(50)

private val CommitNote = Regex("Commit [0-9a-f]{7,40}", RegexOption.IGNORE_CASE)

private fun visibleUpdateNotes(changelog: List<String>): List<String> {
    fun noteKey(note: String) = note.trimEnd('.', '!', '?').trimEnd().lowercase()

    val notes = changelog.map(String::trim)
        .filterNot { note ->
            note.isBlank() ||
                note.startsWith("Latest successful ", ignoreCase = true) ||
                CommitNote.matches(note)
        }
        .distinctBy(::noteKey)

    val hasSystemSettingsNote = notes.any {
        noteKey(it) == "updated system settings and the about screen"
    }
    return notes.filterNot { hasSystemSettingsNote && noteKey(it) == "updated settings" }.take(3)
}

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
    var failed by remember(release.versionCode) { mutableStateOf(false) }

    val canDownload = release.downloadUrl != null
    val horizontalInset = 36.dp
    val notes = remember(release.changelog) { visibleUpdateNotes(release.changelog) }

    TvModalDialog(
        onDismissRequest = { if (!downloading) onLater() },
        initialFocus = if (canDownload) updateRequester else laterRequester,
        dismissOnBackPress = !downloading,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = .42f))
                .padding(horizontal = horizontalInset, vertical = 30.dp),
            contentAlignment = Alignment.BottomEnd,
        ) {
            Column(
                modifier = Modifier
                    .width(minOf(400.dp, maxWidth, (maxWidth + horizontalInset * 2f) * .45f))
                    .clip(PromptShape)
                    .background(Color(0xFF161B25))
                    .border(1.dp, TvDesign.White.copy(alpha = .14f), PromptShape)
                    .padding(horizontal = 22.dp, vertical = 14.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(
                        painter = painterResource(R.drawable.vueo_tv_icon_static),
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        TextLine(
                            text = "Update VUEO",
                            sizeSp = 22,
                            weight = FontWeight.SemiBold,
                            color = TvDesign.White,
                            maxLines = 1,
                        )
                        Spacer(Modifier.height(4.dp))
                        TextLine(
                            text = "New version · ${release.versionName}",
                            sizeSp = 13,
                            weight = FontWeight.Medium,
                            color = TvDesign.Muted,
                            maxLines = 2,
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                val showNotes = !downloading && status == null
                Box(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 66.dp),
                ) {
                    // Keep the note area's measured height during progress and status changes.
                    Column(
                        modifier = if (showNotes) Modifier.fillMaxWidth() else
                            Modifier.fillMaxWidth().alpha(0f).clearAndSetSemantics {},
                    ) {
                        if (notes.isNotEmpty()) {
                            TextLine("What’s new", 15, FontWeight.SemiBold, TvDesign.White)
                            notes.forEach { note ->
                                Spacer(Modifier.height(4.dp))
                                TextLine(note, 14, FontWeight.Normal, TvDesign.Muted, maxLines = 2)
                            }
                        } else {
                            TextLine(
                                if (canDownload) "Android will ask you to confirm installation."
                                else "This release has no downloadable APK.",
                                14, FontWeight.Normal, TvDesign.Muted, maxLines = 2,
                            )
                        }
                    }
                    if (!showNotes) {
                        Column(
                            Modifier.matchParentSize(),
                            verticalArrangement = Arrangement.Center,
                        ) {
                            if (downloading) {
                                TextLine("Downloading… $progress%", 13, FontWeight.Medium, TvDesign.White)
                                Spacer(Modifier.height(7.dp))
                                Box(
                                    Modifier.fillMaxWidth().height(4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(TvDesign.White.copy(alpha = .14f)),
                                ) {
                                    Box(
                                        Modifier.fillMaxWidth(progress.coerceIn(0, 100) / 100f)
                                            .height(4.dp).background(TvDesign.White),
                                    )
                                }
                            } else {
                                status?.let { message ->
                                    TextLine(message, 14, FontWeight.Medium,
                                        if (failed) Color(0xFFFFB4AB) else TvDesign.White, maxLines = 3)
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

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

                    Spacer(Modifier.width(8.dp))

                    TvUpdateAction(
                        label = when {
                            failed -> "Retry"
                            canDownload -> "Update"
                            else -> "Unavailable"
                        },
                        requester = updateRequester,
                        enabled = canDownload,
                        interactive = !downloading,
                        onClick = {
                            if (!downloading) {
                                failed = false
                                if (TvUpdateManager.needsInstallPermission(context)) {
                                    status = "Allow installs for VUEO, then return and choose Update again."
                                    TvUpdateManager.openInstallPermissionSettings(context.applicationContext)
                                } else {
                                    downloading = true
                                    progress = 0
                                    status = null
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
                                                failed = true
                                                status = failure.message ?: "Unable to install update."
                                            }
                                        downloading = false
                                    }
                                }
                            }
                        },
                        onBack = { if (!downloading) onLater() },
                        left = if (downloading) updateRequester else laterRequester,
                        right = if (downloading) updateRequester else laterRequester,
                        up = updateRequester,
                        down = updateRequester,
                        primary = true,
                    )
                }
            }
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
    interactive: Boolean = enabled,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) tvTunedFocusScale(1.035f) else 1f,
        animationSpec = tvTunedSpec(TvMotionGroup.FOCUS, focused, tween(
            durationMillis = if (focused) TvMotion.FOCUS_IN_MS else TvMotion.FOCUS_OUT_MS,
            easing = TvMotion.EaseOut,
        )),
        label = "updateActionScale",
    )

    val background = when {
        !interactive -> TvDesign.White.copy(alpha = .05f)
        focused -> TvDesign.White
        primary -> TvDesign.White.copy(alpha = .11f)
        else -> TvDesign.White.copy(alpha = .055f)
    }

    val border = when {
        focused && interactive -> TvDesign.White.copy(alpha = .86f)
        primary && interactive -> TvDesign.White.copy(alpha = .16f)
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
                        if (event.type == KeyEventType.KeyUp && interactive) onClick()
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
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        TextLine(
            text = label,
            sizeSp = 13,
            weight = if (primary) FontWeight.SemiBold else FontWeight.Medium,
            color = when {
                !interactive -> TvDesign.Dim
                focused -> Color.Black
                else -> TvDesign.White
            },
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
