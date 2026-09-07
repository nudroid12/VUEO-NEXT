package com.vueo.tv.settings

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vueo.tv.ui.TvDesign
import com.vueo.tv.ui.TvPrimaryDestinations
import com.vueo.tv.ui.TvSidebar
import kotlinx.coroutines.delay

internal data class TvSettingsEntry(
    val id: String,
    val title: String,
    val subtitle: String,
    val value: String = "",
    val enabled: Boolean = true,
    val onActivate: (() -> Unit)? = null,
    val onPrevious: (() -> Unit)? = null,
    val onNext: (() -> Unit)? = null,
    val section: String? = null,
)

@Composable
internal fun TvSettingsListScreen(
    title: String,
    subtitle: String,
    entries: List<TvSettingsEntry>,
    onNavigate: (String) -> Unit,
    onProfile: () -> Unit,
    onBack: () -> Unit,
    topLabel: String? = null,
    footer: String? = null,
) {
    BackHandler(onBack = onBack)

    val navRequesters = remember { TvPrimaryDestinations.associateWith { FocusRequester() } }
    val profileRequester = remember { FocusRequester() }
    val rowRequesters = remember(entries.map { it.id }) {
        entries.associate { it.id to FocusRequester() }
    }
    val firstFocusable = entries.firstOrNull { it.enabled } ?: entries.firstOrNull()
    var lastFocusedId by remember(entries.map { it.id }) {
        mutableStateOf(firstFocusable?.id.orEmpty())
    }
    var navExpanded by remember { mutableStateOf(false) }

    val selectedEntry = entries.firstOrNull { it.id == lastFocusedId } ?: firstFocusable

    LaunchedEffect(firstFocusable?.id) {
        val first = firstFocusable ?: return@LaunchedEffect
        delay(90)
        runCatching { rowRequesters.getValue(first.id).requestFocus() }
    }

    fun focusSettingsNav() {
        navExpanded = true
        runCatching { navRequesters.getValue("Settings").requestFocus() }
    }

    fun restoreContentFocus(): Boolean {
        navExpanded = false
        val requester = rowRequesters[lastFocusedId] ?: rowRequesters.values.firstOrNull() ?: return false
        return runCatching {
            requester.requestFocus()
            true
        }.getOrDefault(false)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TvDesign.Black),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 100.dp, end = 48.dp, top = 44.dp, bottom = 30.dp),
        ) {
            TvSettingsContextPane(
                title = title,
                subtitle = subtitle,
                topLabel = topLabel,
                selectedEntry = selectedEntry,
                footer = footer,
                modifier = Modifier
                    .width(238.dp)
                    .fillMaxHeight(),
            )

            Spacer(Modifier.width(30.dp))

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                var previousSection: String? = null
                entries.forEachIndexed { index, entry ->
                    val section = entry.section?.takeIf { it.isNotBlank() }
                    if (section != null && section != previousSection) {
                        item(key = "section-$index-$section") {
                            Text(
                                text = section.uppercase(),
                                color = TvDesign.Dim,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.15.sp,
                                modifier = Modifier.padding(start = 14.dp, top = if (index == 0) 2.dp else 12.dp, bottom = 4.dp),
                            )
                        }
                        previousSection = section
                    }

                    item(key = entry.id) {
                        TvSettingsRow(
                            entry = entry,
                            requester = rowRequesters.getValue(entry.id),
                            first = entry.id == firstFocusable?.id,
                            onLeftToSidebar = ::focusSettingsNav,
                            onFocused = {
                                navExpanded = false
                                lastFocusedId = entry.id
                            },
                        )
                    }
                }

                item(key = "settings-bottom-space") { Spacer(Modifier.height(18.dp)) }
            }
        }

        TvSidebar(
            selected = "Settings",
            expanded = navExpanded,
            navRequesters = navRequesters,
            profileRequester = profileRequester,
            onFocused = { navExpanded = true },
            onNavigate = onNavigate,
            onProfile = onProfile,
            onReturnToContent = ::restoreContentFocus,
            modifier = Modifier.align(Alignment.CenterStart),
        )
    }
}

@Composable
private fun TvSettingsContextPane(
    title: String,
    subtitle: String,
    topLabel: String?,
    selectedEntry: TvSettingsEntry?,
    footer: String?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        if (!topLabel.isNullOrBlank()) {
            Text(
                text = topLabel.uppercase(),
                color = TvDesign.Accent.copy(alpha = .88f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.25.sp,
            )
            Spacer(Modifier.height(6.dp))
        }

        Text(
            text = title,
            color = TvDesign.White,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 34.sp,
        )
        Text(
            text = subtitle,
            color = TvDesign.Muted,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            modifier = Modifier.padding(top = 8.dp),
        )

        Spacer(Modifier.height(30.dp))

        selectedEntry?.let { entry ->
            Text(
                text = "SELECTED",
                color = TvDesign.Dim,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.15.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = entry.title,
                color = if (entry.enabled) TvDesign.White else TvDesign.Dim,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = entry.subtitle,
                color = TvDesign.Muted,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 7.dp),
            )
            if (entry.value.isNotBlank()) {
                Text(
                    text = entry.value,
                    color = TvDesign.White.copy(alpha = .88f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .background(TvDesign.White.copy(alpha = .07f), RoundedCornerShape(50))
                        .padding(horizontal = 11.dp, vertical = 6.dp),
                )
            }
        }

        Spacer(Modifier.weight(1f))

        if (!footer.isNullOrBlank()) {
            Text(
                text = footer,
                color = TvDesign.Dim,
                fontSize = 10.sp,
                lineHeight = 14.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            Text(
                text = "OK  Select    ◀ ▶  Adjust",
                color = TvDesign.Dim.copy(alpha = .82f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun TvSettingsRow(
    entry: TvSettingsEntry,
    requester: FocusRequester,
    first: Boolean,
    onLeftToSidebar: () -> Unit,
    onFocused: () -> Unit,
) {
    var focused by remember(entry.id) { mutableStateOf(false) }
    val canAdjust = entry.onPrevious != null || entry.onNext != null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .focusRequester(requester)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .onPreviewKeyEvent { event ->
                if (!entry.enabled) return@onPreviewKeyEvent false
                val keyCode = event.nativeKeyEvent.keyCode
                when {
                    first &&
                        event.type == KeyEventType.KeyDown &&
                        keyCode == KeyEvent.KEYCODE_DPAD_UP -> true
                    event.type == KeyEventType.KeyDown &&
                        keyCode == KeyEvent.KEYCODE_DPAD_LEFT &&
                        entry.onPrevious == null -> {
                        onLeftToSidebar()
                        true
                    }
                    event.type == KeyEventType.KeyDown &&
                        keyCode == KeyEvent.KEYCODE_DPAD_LEFT &&
                        entry.onPrevious != null -> {
                        entry.onPrevious.invoke()
                        true
                    }
                    event.type == KeyEventType.KeyDown &&
                        keyCode == KeyEvent.KEYCODE_DPAD_RIGHT &&
                        entry.onNext != null -> {
                        entry.onNext.invoke()
                        true
                    }
                    event.isTvActivationKey() -> {
                        if (event.type == KeyEventType.KeyUp) entry.onActivate?.invoke()
                        true
                    }
                    else -> false
                }
            }
            .background(
                color = when {
                    !entry.enabled -> TvDesign.Surface.copy(alpha = .18f)
                    focused -> TvDesign.White.copy(alpha = .10f)
                    else -> TvDesign.Surface.copy(alpha = .36f)
                },
                shape = RoundedCornerShape(11.dp),
            )
            .border(
                width = if (focused) 1.5.dp else 1.dp,
                color = when {
                    !entry.enabled -> TvDesign.White.copy(alpha = .025f)
                    focused -> TvDesign.White.copy(alpha = .88f)
                    else -> TvDesign.White.copy(alpha = .045f)
                },
                shape = RoundedCornerShape(11.dp),
            )
            .focusable(enabled = entry.enabled)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = entry.title,
                color = if (entry.enabled) TvDesign.White else TvDesign.Dim,
                fontSize = 14.sp,
                fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = entry.subtitle,
                color = if (entry.enabled) TvDesign.Muted else TvDesign.Dim.copy(alpha = .65f),
                fontSize = 10.sp,
                lineHeight = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.width(14.dp))

        if (entry.value.isNotBlank()) {
            Text(
                text = buildString {
                    if (canAdjust && focused) append("‹  ")
                    append(entry.value)
                    if (canAdjust && focused) append("  ›")
                },
                color = if (focused) TvDesign.White else TvDesign.Muted,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .background(
                        color = if (focused) TvDesign.White.copy(alpha = .10f) else TvDesign.White.copy(alpha = .045f),
                        shape = RoundedCornerShape(50),
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        } else if (entry.onActivate != null) {
            Text(
                text = "›",
                color = if (focused) TvDesign.White else TvDesign.Dim,
                fontSize = 19.sp,
                fontWeight = FontWeight.Light,
            )
        }
    }
}

@Composable
internal fun TvTextEntryDialog(
    title: String,
    initialValue: String,
    secret: Boolean = false,
    placeholder: String = "",
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var value by remember(title, initialValue) { mutableStateOf(initialValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                placeholder = { if (placeholder.isNotBlank()) Text(placeholder) },
                visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(value.trim()) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
internal fun TvConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String = "Confirm",
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

internal fun <T> cycle(values: List<T>, current: T, delta: Int): T {
    if (values.isEmpty()) return current
    val index = values.indexOf(current).takeIf { it >= 0 } ?: 0
    val next = (index + delta).floorMod(values.size)
    return values[next]
}

private fun Int.floorMod(divisor: Int): Int = ((this % divisor) + divisor) % divisor

private fun androidx.compose.ui.input.key.KeyEvent.isTvActivationKey(): Boolean =
    nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
        nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER ||
        nativeKeyEvent.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
