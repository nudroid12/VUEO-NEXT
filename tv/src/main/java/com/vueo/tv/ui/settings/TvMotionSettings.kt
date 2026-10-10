package com.vueo.tv.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.togetherWith
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vueo.tv.ui.TvDesign
import com.vueo.tv.ui.LocalTvModalFocusHost
import com.vueo.tv.ui.motion.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/** Quick remote access leaves the current route and its focus memory mounted. */
@Composable
internal fun TvMotionSettingsOverlay(onDismiss: () -> Unit, onNavigate: (String) -> Unit, onProfile: () -> Unit) {
    val host = LocalTvModalFocusHost.current
    DisposableEffect(host) { host?.open?.invoke(); onDispose { host?.close?.invoke() } }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        Box(Modifier.fillMaxSize().background(TvDesign.Black)) {
            TvMotionSettings(
                onNavigate = { onDismiss(); onNavigate(it) },
                onProfile = { onDismiss(); onProfile() },
                onBack = onDismiss,
            )
        }
    }
}

@Composable
internal fun TvMotionSettings(
    onNavigate: (String) -> Unit,
    onProfile: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    var group by remember { mutableStateOf(TvMotionGroup.FORWARD) }
    var preview by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Changes apply live. Save keeps them after restarting VUEO.") }
    val restoreAfterExport = rememberTvSettingsDeferredFocusRestore()
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        restoreAfterExport()
        if (uri != null) {
            status = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(TvMotionTuning.exportJson().toByteArray(Charsets.UTF_8)) }
                    ?: error("Unable to open destination")
                "Motion values exported."
            }.getOrElse { "Export failed: ${it.message}" }
        }
    }
    val value = TvMotionTuning.get(group)
    fun edit(change: (TvMotionValues) -> TvMotionValues) {
        val next = change(value)
        val automaticStyle = when (group) {
            TvMotionGroup.FOCUS, TvMotionGroup.TOPBAR -> TvMotionStyle.SCALE
            TvMotionGroup.HERO, TvMotionGroup.IMAGE, TvMotionGroup.PLAYER_ROUTE -> TvMotionStyle.FADE
            else -> TvMotionStyle.FADE_SCALE
        }
        TvMotionTuning.update(group, if (next.style == TvMotionStyle.ORIGINAL) next.copy(style = automaticStyle) else next)
        status = "Live changes applied. Save to keep after restart."
    }
    fun number(id: String, title: String, text: String, previous: () -> Unit, next: () -> Unit, description: String = "Left / Right to adjust. OK increases.") =
        choiceEntry(id, title, description, text, previous, next).copy(section = "VALUES")

    if (preview) TvMotionPreview(group) { preview = false }
    val entries = buildList {
        add(toggleEntry("motion-enabled", "Live Motion Tuning", "Off restores original motion while retaining your trial values.", TvMotionTuning.enabled) {
            TvMotionTuning.enabled = it; status = "Live tuning ${if (it) "enabled" else "disabled"}. Save to keep."
        }.copy(section = "TEMPORARY TV TOOLS"))
        add(toggleEntry("motion-reduce", "Reduce Motion", "Use immediate transitions and disable focus zoom in the connected components.", TvMotionTuning.reduceMotion) {
            TvMotionTuning.reduceMotion = it; status = "Reduce Motion updated. Save to keep."
        })
        add(choiceEntry("motion-group", "Component", "Each component has its own independent values.", group.title,
            { group = cycle(TvMotionGroup.entries.toList(), group, -1) },
            { group = cycle(TvMotionGroup.entries.toList(), group, 1) }).copy(section = "COMPONENT"))
        add(choiceEntry("motion-style", "Style", "Original uses the exact existing animation. Changing a value enables a trial style.", value.style.label,
            { TvMotionTuning.update(group, value.copy(style = cycle(group.styles, value.style, -1))) },
            { TvMotionTuning.update(group, value.copy(style = cycle(group.styles, value.style, 1))) }))
        if (value.style != TvMotionStyle.NONE) {
        add(toggleEntry("motion-spring", "Spring Physics", "Spring uses stiffness/damping; duration and exit hold apply to Tween only.", value.useSpring) { use -> edit { it.copy(useSpring = use) } })
        add(choiceEntry("motion-curve", "Easing", "Used by Tween. Spring follows its physics values.", value.curve.label,
            { edit { it.copy(curve = cycle(TvMotionCurve.entries.toList(), value.curve, -1)) } },
            { edit { it.copy(curve = cycle(TvMotionCurve.entries.toList(), value.curve, 1)) } }))
        add(number("motion-enter", if (group == TvMotionGroup.HERO) "Hero crossfade duration" else "Enter / focus-in duration", "${value.enterMs} ms", { edit { it.copy(enterMs = it.enterMs - 10) } }, { edit { it.copy(enterMs = it.enterMs + 10) } }))
        if (group != TvMotionGroup.HERO) add(number("motion-exit", "Exit / focus-out duration", "${value.exitMs} ms", { edit { it.copy(exitMs = it.exitMs - 10) } }, { edit { it.copy(exitMs = it.exitMs + 10) } }))
        if (!group.scalar) add(number("motion-delay", "Outgoing surface hold", "${value.exitDelayMs} ms", { edit { it.copy(exitDelayMs = it.exitDelayMs - 10) } }, { edit { it.copy(exitDelayMs = it.exitDelayMs + 10) } }, "Tween only. Keeps the outgoing surface visible briefly."))
        if (value.useSpring) {
            add(number("motion-stiffness", "Spring stiffness", "${value.stiffness.toInt()}", { edit { it.copy(stiffness = it.stiffness - 20f) } }, { edit { it.copy(stiffness = it.stiffness + 20f) } }))
            add(number("motion-damping", "Spring damping", String.format(Locale.US, "%.2f", value.damping), { edit { it.copy(damping = it.damping - .05f) } }, { edit { it.copy(damping = it.damping + .05f) } }, "1.00 has no bounce; lower values add bounce."))
        }
        if (group != TvMotionGroup.TOPBAR && (value.style.scales || group == TvMotionGroup.FOCUS)) {
            add(number("motion-scale", if (group == TvMotionGroup.FOCUS) "Focused scale" else "Enter / exit scale", String.format(Locale.US, "%.3fx", value.scale), { edit { it.copy(scale = it.scale - .005f) } }, { edit { it.copy(scale = it.scale + .005f) } }))
        }
        if (value.style.fades && group != TvMotionGroup.HERO && group != TvMotionGroup.IMAGE) {
            add(number("motion-alpha", "Fade start / end opacity", "${(value.initialAlpha * 100).toInt()}%", { edit { it.copy(initialAlpha = it.initialAlpha - .05f) } }, { edit { it.copy(initialAlpha = it.initialAlpha + .05f) } }))
        }
        if (value.style in setOf(TvMotionStyle.SLIDE_X, TvMotionStyle.SLIDE_Y, TvMotionStyle.FADE_SLIDE_X, TvMotionStyle.FADE_SLIDE_Y)) {
            add(number("motion-distance", "Slide distance", "${value.travelPercent}%", { edit { it.copy(travelPercent = it.travelPercent - 1) } }, { edit { it.copy(travelPercent = it.travelPercent + 1) } }, "Percentage of the animated surface width / height."))
        }
        }
        add(TvSettingsEntry("motion-preview", "Preview / Replay", "Try the selected motion here; navigate normally to test it on the real pages.", "Open", onActivate = { preview = true }, section = "TEST & SAVE"))
        add(TvSettingsEntry("motion-save", "Save All Values", "Keep all live trial values on this TV after restart.", "Save", onActivate = { TvMotionTuning.save(context); status = "All motion values saved." }))
        add(TvSettingsEntry("motion-export", "Export Trial Values", "Save a JSON copy of your trial values for applying the final motion later.", "Export", onActivate = { export.launch("VUEO-TV-Motion.json") }))
        add(TvSettingsEntry("motion-reset-component", "Reset This Component", "Return ${group.title} to Original.", "Reset", onActivate = { TvMotionTuning.reset(group); status = "Component reset to Original. Save to keep." }))
        add(TvSettingsEntry("motion-reset-all", "Reset All Motion", "Restore originals and remove saved trial values.", "Reset", onActivate = { TvMotionTuning.resetAll(context); status = "Original motion restored. Saved trial values removed." }))
        add(TvSettingsEntry("motion-status", "Status", status, enabled = false, section = "STATUS"))
    }
    TvSettingsListScreen("Motion Settings · Temporary", "Live on real TV pages. D-pad Left / Right adjusts values. Original preserves existing behavior.", entries, onNavigate, onProfile, onBack)
}

@Composable
private fun TvMotionPreview(group: TvMotionGroup, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val replayRequester = remember { FocusRequester() }
    val restoreFocus = rememberTvSettingsDeferredFocusRestore()
    var showing by remember { mutableStateOf(false) }
    var replayJob by remember { mutableStateOf<Job?>(null) }
    val value = TvMotionTuning.get(group)
    val progress by animateFloatAsState(
        targetValue = if (showing) 1f else 0f,
        animationSpec = tvTunedSpec(group, showing, tween(group.enterMs)), label = "motionPreviewProgress",
    )
    fun dismiss() { replayJob?.cancel(); onDismiss(); restoreFocus() }
    LaunchedEffect(Unit) { replayRequester.requestFocus(); showing = true }
    Dialog(onDismissRequest = ::dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.width(520.dp).background(TvDesign.SurfaceRaised, RoundedCornerShape(18.dp)).padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(group.title, color = TvDesign.White, fontSize = 20.sp)
            Text("${value.style.label} · ${if (value.useSpring) "Spring" else "${value.enterMs} / ${value.exitMs} ms"}", color = TvDesign.Muted, fontSize = 13.sp)
            Box(Modifier.fillMaxWidth().height(170.dp).background(TvDesign.Black, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                if (group.scalar) {
                    val scale = if (group == TvMotionGroup.FOCUS) 1f + (tvTunedFocusScale(1.025f) - 1f) * progress else 1f
                    Box(Modifier.width(if (group == TvMotionGroup.TOPBAR) (180f + 160f * progress).dp else 260.dp).height(110.dp).graphicsLayer {
                        scaleX = scale; scaleY = scale
                        alpha = if (group == TvMotionGroup.HERO || group == TvMotionGroup.IMAGE) progress.coerceIn(0f, 1f) else 1f
                    }.background(TvDesign.Accent, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                        Text("VUEO", color = TvDesign.Black, fontSize = 24.sp)
                    }
                } else {
                    val recipe = when (group) {
                        TvMotionGroup.FORWARD -> tvScreenFadeThrough()
                        TvMotionGroup.BACK -> tvScreenBackTransition()
                        TvMotionGroup.TABS -> tvTabCrossTransition()
                        TvMotionGroup.PLAYER_ROUTE -> tvPlayerFadeThrough()
                        else -> tvTunedEnter(group, androidx.compose.animation.fadeIn(tween(group.enterMs))) togetherWith
                            tvTunedExit(group, androidx.compose.animation.fadeOut(tween(group.exitMs)))
                    }
                    AnimatedVisibility(showing, enter = recipe.targetContentEnter, exit = recipe.initialContentExit) {
                        Box(Modifier.size(260.dp, 110.dp).background(TvDesign.Accent, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                            Text("VUEO", color = TvDesign.Black, fontSize = 24.sp)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(modifier = Modifier.focusRequester(replayRequester), onClick = {
                    replayJob?.cancel()
                    replayJob = scope.launch {
                        showing = false
                        delay((value.exitMs + value.exitDelayMs + 80).toLong())
                        showing = true
                    }
                }) { Text("Replay", color = TvDesign.White) }
                TextButton(onClick = ::dismiss) { Text("Close", color = TvDesign.White) }
            }
        }
    }
}
