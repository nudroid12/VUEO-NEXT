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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 92.dp, end = 58.dp, top = 54.dp, bottom = 32.dp),
        ) {
            if (!topLabel.isNullOrBlank()) {
                Text(
                    text = topLabel.uppercase(),
                    color = TvDesign.Accent.copy(alpha = .86f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                )
                Spacer(Modifier.height(5.dp))
            }
            Text(
                text = title,
                color = TvDesign.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = subtitle,
                color = TvDesign.Muted,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 6.dp),
            )

            Spacer(Modifier.height(22.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth(.78f)
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                itemsIndexed(
                    items = entries,
                    key = { _, item -> item.id },
                ) { _, entry ->
                    TvSettingsRow(
                        entry = entry,
                        requester = rowRequesters.getValue(entry.id),
                        first = entry.id == firstFocusable?.id,
                        onUpFromFirst = ::focusSettingsNav,
                        onLeftToSidebar = ::focusSettingsNav,
                        onFocused = {
                            navExpanded = false
                            lastFocusedId = entry.id
                        },
                    )
                }

                if (!footer.isNullOrBlank()) {
                    item(key = "settings-footer") {
                        Text(
                            text = footer,
                            color = TvDesign.Dim,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            modifier = Modifier.padding(start = 6.dp, top = 8.dp, bottom = 16.dp),
                        )
                    }
                } else {
                    item(key = "settings-bottom-space") { Spacer(Modifier.height(18.dp)) }
                }
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
private fun TvSettingsRow(
    entry: TvSettingsEntry,
    requester: FocusRequester,
    first: Boolean,
    onUpFromFirst: () -> Unit,
    onLeftToSidebar: () -> Unit,
    onFocused: () -> Unit,
) {
    var focused by remember(entry.id) { mutableStateOf(false) }
    val canAdjust = entry.onPrevious != null || entry.onNext != null

    Row(
        modifier = Modifier
            .fillMaxWidth()
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
                        keyCode == KeyEvent.KEYCODE_DPAD_UP -> {
                        onUpFromFirst()
                        true
                    }
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
                    !entry.enabled -> TvDesign.Surface.copy(alpha = .34f)
                    focused -> TvDesign.SurfaceRaised.copy(alpha = .98f)
                    else -> TvDesign.Surface.copy(alpha = .78f)
                },
                shape = RoundedCornerShape(12.dp),
            )
            .border(
                width = 1.dp,
                color = when {
                    !entry.enabled -> TvDesign.White.copy(alpha = .04f)
                    focused -> TvDesign.Accent.copy(alpha = .90f)
                    else -> TvDesign.White.copy(alpha = .075f)
                },
                shape = RoundedCornerShape(12.dp),
            )
            .focusable(enabled = entry.enabled)
            .padding(horizontal = 18.dp, vertical = 13.dp),
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
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (entry.value.isNotBlank()) {
            Spacer(Modifier.width(16.dp))
            Text(
                text = buildString {
                    if (canAdjust && focused) append("‹  ")
                    append(entry.value)
                    if (canAdjust && focused) append("  ›")
                },
                color = if (focused) TvDesign.White else TvDesign.Muted,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else if (entry.onActivate != null) {
            Text(
                text = "›",
                color = if (focused) TvDesign.White else TvDesign.Dim,
                fontSize = 20.sp,
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
