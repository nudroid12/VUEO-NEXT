package com.vueo.tv.settings

import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.vueo.shared.core.sync.VueoSyncService
import com.vueo.tv.ui.TvDesign
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@Composable
internal fun TvVueoSyncSettings(
    onNavigate: (String) -> Unit,
    onProfile: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val service = remember(context) { VueoSyncService.get(context) }
    val status by service.state.collectAsState()
    val revision by service.revision.collectAsState()
    val connected = remember(revision) { service.connected() }
    val syncOn = remember(revision) { service.syncEnabled() }
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var validationAttempt by remember { mutableIntStateOf(0) }
    var confirmSignOut by remember { mutableStateOf(false) }
    fun action(block: suspend () -> String) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                message = block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                message = VueoSyncService.message(error)
            } finally {
                password = ""
                busy = false
            }
        }
    }
    fun authenticate(register: Boolean) {
        if (busy) return
        if (email.isBlank() || password.isBlank()) {
            message = ""
            validationAttempt++
            return
        }
        val submittedEmail = email
        val submittedPassword = password
        action { service.signIn(submittedEmail, submittedPassword, register) }
    }

    val entries = buildList {
        if (!connected) {
            add(TvSettingsEntry("email", "Email", "", formControl = { modifier, left, first ->
                TvAccountField("Email", email, {
                    email = it
                    validationAttempt = 0
                }, busy, false, modifier, left, first)
            }))
            add(TvSettingsEntry("password", "Password", "", formControl = { modifier, left, first ->
                TvAccountField("Password", password, {
                    password = it
                    validationAttempt = 0
                }, busy, true, modifier, left, first)
            }))
            add(TvSettingsEntry("sign-in", "Sign In", "", formControl = { modifier, left, _ ->
                var focused by remember { mutableStateOf(false) }
                Button(
                    onClick = { authenticate(register = false) },
                    modifier = modifier.accountActionNavigation(left)
                        .onFocusChanged { focused = it.isFocused }
                        .border(2.dp, if (focused) TvDesign.Accent else Color.Transparent, RoundedCornerShape(8.dp)),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TvDesign.Accent,
                        contentColor = TvDesign.OnAccent,
                    ),
                ) { Text(if (busy) "Please wait…" else "Sign In") }
            }))
            add(TvSettingsEntry("register", "Create Account", "", formControl = { modifier, left, _ ->
                var focused by remember { mutableStateOf(false) }
                TextButton(
                    onClick = { authenticate(register = true) },
                    modifier = modifier.accountActionNavigation(left, last = true)
                        .onFocusChanged { focused = it.isFocused },
                    contentPadding = PaddingValues(8.dp),
                ) {
                    Text(
                        "New to VUEO? Create Account",
                        color = if (focused) TvDesign.Accent else TvDesign.White,
                        textDecoration = if (focused) TextDecoration.Underline else TextDecoration.None,
                    )
                }
            }))
            if (validationAttempt > 0 || message.isNotBlank()) {
                add(TvSettingsEntry("message", "", "", enabled = false, formControl = { modifier, _, _ ->
                    if (validationAttempt > 0) {
                        TvAccountValidationNotice(validationAttempt, modifier)
                    } else {
                        Text(
                            message,
                            modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            color = TvDesign.White,
                            textAlign = TextAlign.Center,
                        )
                    }
                }))
            }
        } else {
            add(TvSettingsEntry("status", "Account", service.email(), "Connected", enabled = false, section = "ACCOUNT"))
            add(TvSettingsEntry("message", "Account Sync", if (message.isBlank() || message == status) status else "$message\n$status", enabled = false))
            add(TvSettingsEntry(
                id = "sync-all",
                title = if (syncOn) "Sync On" else "Sync Off",
                subtitle = "One control for all account data.",
                value = if (syncOn) "On" else "Off",
                enabled = !busy,
                onActivate = {
                    action {
                        service.setSyncEnabledAndWait(!syncOn)
                        service.state.value
                    }
                },
            ))
            add(TvSettingsEntry(
                id = "sync-now",
                title = "Sync Now",
                subtitle = "Sync your library and connections now.",
                enabled = !busy && syncOn,
                onActivate = {
                    action {
                        service.syncNow()
                        service.state.value
                    }
                },
                section = "ACTIONS",
            ))
            add(TvSettingsEntry(
                id = "sign-out",
                title = if (confirmSignOut) "Confirm Sign Out" else "Sign Out",
                subtitle = "Local data stays on this TV.",
                enabled = !busy,
                onActivate = {
                    if (confirmSignOut) {
                        action {
                            service.signOut()
                            confirmSignOut = false
                            "Signed out"
                        }
                    } else {
                        confirmSignOut = true
                    }
                },
            ))
            if (confirmSignOut) {
                add(TvSettingsEntry("cancel-sign-out", "Cancel", "Stay connected.", onActivate = {
                    confirmSignOut = false
                }))
            }
        }
    }
    TvSettingsListScreen(
        if (connected) "Account" else "Sign in to VUEO",
        if (connected) "Your library and connections across mobile and TV."
        else "Use the same account you use on mobile.",
        entries, onNavigate, onProfile, onBack,
        centerHeader = !connected,
    )
}

@Composable
private fun TvAccountValidationNotice(attempt: Int, modifier: Modifier) {
    val offset = remember { Animatable(0f) }
    val errorColor = MaterialTheme.colorScheme.error

    LaunchedEffect(attempt) {
        offset.snapTo(0f)
        for (position in listOf(-6f, 6f, -4f, 4f, 0f)) {
            offset.animateTo(position, tween(durationMillis = 45, easing = LinearEasing))
        }
    }

    Row(
        modifier = modifier
            .graphicsLayer { translationX = offset.value.dp.toPx() }
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(20.dp).border(1.5.dp, errorColor, CircleShape)
                .clearAndSetSemantics {},
            contentAlignment = Alignment.Center,
        ) {
            Text("!", color = errorColor, style = MaterialTheme.typography.labelMedium)
        }
        Text("Enter your email and password.", color = errorColor, textAlign = TextAlign.Center)
    }
}

private fun Modifier.accountActionNavigation(onLeft: () -> Unit, last: Boolean = false) =
    onPreviewKeyEvent { event ->
        when (event.nativeKeyEvent.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (event.type == KeyEventType.KeyDown) onLeft()
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> last
            else -> false
        }
    }

@Composable
private fun TvAccountField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    busy: Boolean,
    secret: Boolean,
    modifier: Modifier,
    onLeft: () -> Unit,
    first: Boolean,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val imeInsets = WindowInsets.ime
    val density = LocalDensity.current
    var editing by remember { mutableStateOf(false) }
    var secretVisible by remember(label) { mutableStateOf(false) }
    var revealFocused by remember { mutableStateOf(false) }
    val inputRequester = remember { FocusRequester() }
    val revealRequester = remember { FocusRequester() }

    LaunchedEffect(busy, value.isEmpty()) {
        if (busy || value.isEmpty()) secretVisible = false
    }

    fun stopEditing() {
        editing = false
        keyboard?.hide()
    }

    BackHandler(enabled = editing && !busy, onBack = ::stopEditing)
    LaunchedEffect(editing, imeInsets, density) {
        if (!editing) return@LaunchedEffect
        var keyboardWasVisible = false
        snapshotFlow { imeInsets.getBottom(density) > 0 }.collect { visible ->
            if (!visible && keyboardWasVisible) editing = false
            keyboardWasVisible = visible
        }
    }

    var keyboardRequest by remember { mutableIntStateOf(0) }
    LaunchedEffect(editing, busy, keyboardRequest) {
        if (editing && !busy) {
            withFrameNanos { }
            keyboard?.show()
        }
    }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .focusRequester(inputRequester)
            .onFocusChanged {
                if (!it.isFocused && editing) stopEditing()
            }
            .onPreviewKeyEvent { event ->
                if (revealFocused) return@onPreviewKeyEvent false
                when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                        if (busy) false
                        else {
                            if (event.type == KeyEventType.KeyUp) {
                                editing = true
                                keyboardRequest++
                            }
                            true
                        }
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (!secret || busy || (editing && imeInsets.getBottom(density) > 0)) false
                        else {
                            if (event.type == KeyEventType.KeyDown) revealRequester.requestFocus()
                            true
                        }
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (editing && imeInsets.getBottom(density) > 0) false
                        else {
                            if (event.type == KeyEventType.KeyDown) onLeft()
                            true
                        }
                    }
                    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                        if (editing && imeInsets.getBottom(density) > 0) false
                        else {
                            val up = event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_UP
                            if (event.type == KeyEventType.KeyDown && (!up || !first)) {
                                focusManager.moveFocus(if (up) FocusDirection.Up else FocusDirection.Down)
                            }
                            true
                        }
                    }
                    else -> false
                }
            },
        readOnly = busy || !editing,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (secret && !secretVisible) PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon = if (secret) {
            {
                TvSecretVisibilityButton(
                    visible = secretVisible,
                    enabled = !busy,
                    modifier = Modifier.focusRequester(revealRequester).focusProperties { left = inputRequester },
                    onFocus = {
                        revealFocused = it
                        if (it) {
                            editing = false
                            keyboard?.hide()
                        }
                    },
                    onToggle = { secretVisible = !secretVisible },
                )
            }
        } else null,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (secret) KeyboardType.Password else KeyboardType.Email,
            imeAction = ImeAction.Done,
            showKeyboardOnFocus = false,
        ),
        keyboardActions = KeyboardActions(onDone = { stopEditing() }),
        shape = RoundedCornerShape(8.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = TvDesign.Accent,
            focusedLabelColor = TvDesign.Accent,
            unfocusedBorderColor = TvDesign.Muted,
            cursorColor = TvDesign.Accent,
        ),
    )
}
