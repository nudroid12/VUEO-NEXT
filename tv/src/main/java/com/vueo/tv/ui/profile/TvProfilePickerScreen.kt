package com.vueo.tv.profile

import com.vueo.tv.ui.motion.*
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vueo.shared.core.profile.ProfileAvatarCatalog
import com.vueo.shared.core.profile.ProfileAvatarSpec
import com.vueo.shared.core.storage.ProfileStore
import com.vueo.shared.core.storage.VueoProfile
import com.vueo.tv.ui.TvModalDialog
import com.vueo.tv.R
import com.vueo.tv.ui.motion.tvFocusSpec
import com.vueo.tv.ui.motion.tvScreenBackTransition
import com.vueo.tv.ui.motion.tvScreenFadeThrough
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay

private val PickerBlack = Color(0xFF050706)
private val PickerPanel = Color(0xFF101412)
private val PickerPanelRaised = Color(0xFF171C19)
private val PickerGreen = Color(0xFFB6FF00)
private val PickerMuted = Color(0xFFAAB2AD)
private val PickerStroke = Color.White.copy(alpha = 0.14f)
private val PickerDanger = Color(0xFFFF7B72)

private enum class ProfilePickerMode {
    WATCHING,
    MANAGE,
}

private data class TvProfileEditorState(
    val profile: VueoProfile?,
)

private sealed interface TvEditorPinFlow {
    data object SetFirst : TvEditorPinFlow
    data class SetConfirm(val firstPin: String) : TvEditorPinFlow
    data object VerifyChange : TvEditorPinFlow
    data object VerifyRemove : TvEditorPinFlow
}

@Composable
fun TvProfilePickerScreen(
    profileStore: ProfileStore,
    onProfileSelected: (String) -> Unit,
    onBack: () -> Unit,
    onProfilesChanged: () -> Unit = {},
    startInManage: Boolean = false,
) {
    var revision by remember { mutableIntStateOf(0) }
    val profiles = remember(revision) { profileStore.profiles() }
    val activeProfileId = remember(revision) { profileStore.activeProfileId() }
    var mode by remember(startInManage) {
        mutableStateOf(if (startInManage) ProfilePickerMode.MANAGE else ProfilePickerMode.WATCHING)
    }
    var editor by remember { mutableStateOf<TvProfileEditorState?>(null) }
    var lockedProfile by remember { mutableStateOf<VueoProfile?>(null) }
    var pinError by remember { mutableStateOf<String?>(null) }
    var pinResetToken by remember { mutableIntStateOf(0) }
    var parentalAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    fun parentApprove(action: () -> Unit) {
        if (profileStore.needsParentalApproval()) parentalAction = action else action()
    }
    var deleteCandidate by remember { mutableStateOf<VueoProfile?>(null) }

    BackHandler {
        when {
            editor != null -> editor = null
            mode == ProfilePickerMode.MANAGE && !startInManage -> mode = ProfilePickerMode.WATCHING
            else -> onBack()
        }
    }

    fun refreshProfiles() {
        revision += 1
        onProfilesChanged()
    }

    AnimatedContent(
        targetState = editor,
        transitionSpec = {
            if (targetState == null) {
                tvScreenBackTransition()
            } else {
                tvScreenFadeThrough()
            }
        },
        modifier = Modifier.fillMaxSize().background(PickerBlack),
        label = "tvProfileEditorTransition",
    ) { editorState ->
        if (editorState != null) {
            TvProfileEditorScreen(
                state = editorState,
                profileStore = profileStore,
                deleteConfirmationOpen = deleteCandidate != null,
                onBack = { editor = null },
                onSaved = { refreshProfiles(); editor = null },
                onRequestDelete = { profile ->
                    deleteCandidate = profile
                },
            )
        } else {
            AnimatedContent(
                targetState = mode,
                transitionSpec = {
                    if (targetState == ProfilePickerMode.WATCHING) {
                        tvScreenBackTransition()
                    } else {
                        tvScreenFadeThrough()
                    }
                },
                modifier = Modifier.fillMaxSize(),
                label = "tvProfileManageTransition",
            ) { currentMode ->
                when (currentMode) {
                    ProfilePickerMode.WATCHING ->
                        TvWhosWatching(
                            profiles = profiles,
                            activeProfileId = activeProfileId,
                            profileStore = profileStore,
                            onSelect = { profile ->
                                val select = {
                                    if (profileStore.hasProfilePin(profile.id)) {
                                        pinError = null
                                        lockedProfile = profile
                                    } else if (profileStore.setActiveProfile(profile.id)) {
                                        onProfileSelected(profile.id)
                                    }
                                }
                                if (profileStore.needsParentalApprovalForSwitch(profile) || (profile.isKids && !profileStore.hasParentalPin())) parentApprove(select) else select()
                            },
                            onManage = { mode = ProfilePickerMode.MANAGE },
                        )

                    ProfilePickerMode.MANAGE ->
                        TvManageProfiles(
                            profiles = profiles,
                            profileStore = profileStore,
                            onEdit = { target ->
                                val edit = { editor = TvProfileEditorState(target) }
                                if (profileStore.activeProfile().isKids) parentApprove(edit) else edit()
                            },
                            onAdd = {
                                val add = { editor = TvProfileEditorState(null) }
                                if (profileStore.activeProfile().isKids) parentApprove(add) else add()
                            },
                            onChanged = ::refreshProfiles,
                            onDone = { if (startInManage) onBack() else mode = ProfilePickerMode.WATCHING },
                        )
                }
            }
        }
    }

    val locked = lockedProfile
    if (locked != null) {
        key(locked.id, pinResetToken) {
            TvPinEntryOverlay(
                title = "Unlock ${locked.name}",
                subtitle = "Enter the 4-digit profile PIN",
                errorText = pinError,
                onComplete = { pin ->
                    if (profileStore.verifyProfilePin(locked.id, pin)) {
                        pinError = null
                        lockedProfile = null
                        if (profileStore.setActiveProfile(locked.id)) {
                            onProfileSelected(locked.id)
                        }
                    } else {
                        pinError = "Incorrect PIN"
                        pinResetToken += 1
                    }
                },
                onCancel = {
                    pinError = null
                    lockedProfile = null
                },
            )
        }
    }

    val deleting = deleteCandidate
    if (deleting != null) {
        TvProfileConfirmOverlay(
            title = "Delete ${deleting.name}?",
            message = "This removes this profile's local Watchlist, history, playback progress and personal preferences.",
            confirmLabel = "Delete Profile",
            danger = true,
            onConfirm = {
                parentApprove {
                    profileStore.deleteProfile(deleting.id)
                    editor = null
                    deleteCandidate = null
                    refreshProfiles()
                }
            },
            onCancel = { deleteCandidate = null },
        )
    }
    parentalAction?.let { action ->
        TvParentalPinGate(profileStore, onGranted = { parentalAction = null; action() }, onCancel = { parentalAction = null })
    }
}

@Composable
private fun TvWhosWatching(
    profiles: List<VueoProfile>,
    activeProfileId: String,
    profileStore: ProfileStore,
    onSelect: (VueoProfile) -> Unit,
    onManage: () -> Unit,
) {
    val firstRequester = remember { FocusRequester() }

    LaunchedEffect(profiles.size) {
        delay(100)
        runCatching { firstRequester.requestFocus() }
    }

    TvProfileBackground {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 72.dp, vertical = 46.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            TvProfileBrand()
            Spacer(Modifier.height(18.dp))
            Text(
                text = "Who's Watching?",
                color = Color.White,
                fontSize = 44.sp,
                fontWeight = FontWeight.Black,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Choose a profile to continue",
                color = PickerMuted,
                fontSize = 16.sp,
            )
            Spacer(Modifier.height(34.dp))

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                contentPadding = PaddingValues(horizontal = 24.dp),
            ) {
                itemsIndexed(profiles, key = { _, profile -> profile.id }) { index, profile ->
                    PickerProfileCard(
                        profile = profile,
                        active = profile.id == activeProfileId,
                        locked = profileStore.hasProfilePin(profile.id),
                        modifier = if (index == 0) Modifier.focusRequester(firstRequester) else Modifier,
                        onClick = { onSelect(profile) },
                    )
                    if (index != profiles.lastIndex) Spacer(Modifier.width(24.dp))
                }
            }

            Spacer(Modifier.height(34.dp))
            TvProfileAction(
                label = "Manage Profiles",
                onClick = onManage,
                width = 230,
            )
        }
    }
}

@Composable
private fun TvManageProfiles(
    profiles: List<VueoProfile>,
    profileStore: ProfileStore,
    onEdit: (VueoProfile) -> Unit,
    onAdd: () -> Unit,
    onChanged: () -> Unit,
    onDone: () -> Unit,
) {
    val firstRequester = remember { FocusRequester() }
    var askOnStartup by remember(profiles.size) {
        mutableStateOf(profileStore.askWhoIsWatchingOnStartup())
    }

    LaunchedEffect(profiles.size) {
        delay(100)
        runCatching { firstRequester.requestFocus() }
    }

    TvProfileBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 64.dp, end = 64.dp, top = 28.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TvProfileBrand()
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Manage Profiles",
                color = Color.White,
                fontSize = 36.sp,
                fontWeight = FontWeight.Black,
            )

            // The profile row owns the flexible middle of the screen. Bottom actions
            // stay outside this area so overscan can never clip Done.
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
            ) {
                itemsIndexed(profiles, key = { _, profile -> profile.id }) { index, profile ->
                    ManageProfileCard(
                        profile = profile,
                        locked = profileStore.hasProfilePin(profile.id),
                        modifier = if (index == 0) Modifier.focusRequester(firstRequester) else Modifier,
                        onClick = { onEdit(profile) },
                    )
                    Spacer(Modifier.width(22.dp))
                }
                if (profiles.size < ProfileStore.MAX_PROFILES) {
                    item(key = "add-profile") {
                        AddProfileCard(onClick = onAdd)
                    }
                }
            }

            TvPickerToggleRow(
                title = "Ask on startup",
                subtitle = "Show Who's Watching when VUEO launches",
                checked = askOnStartup,
                enabled = profiles.size > 1,
                onClick = {
                    if (profiles.size > 1) {
                        askOnStartup = !askOnStartup
                        profileStore.setAskWhoIsWatchingOnStartup(askOnStartup)
                        onChanged()
                    }
                },
            )
            Spacer(Modifier.height(12.dp))
            TvProfileAction(label = "Done", onClick = onDone, width = 180)
        }
    }
}

@Composable
private fun TvProfileEditorScreen(
    state: TvProfileEditorState,
    profileStore: ProfileStore,
    deleteConfirmationOpen: Boolean,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onRequestDelete: (VueoProfile) -> Unit,
) {
    val profile = state.profile
    val hadPin = remember(profile?.id) { profile?.let { profileStore.hasProfilePin(it.id) } ?: false }
    val firstRequester = remember { FocusRequester() }
    val pinRequester = remember { FocusRequester() }
    val changePinRequester = remember { FocusRequester() }
    val saveRequester = remember { FocusRequester() }
    val deleteRequester = remember { FocusRequester() }
    var restoreDeleteFocus by remember { mutableStateOf(false) }
    LaunchedEffect(deleteConfirmationOpen) {
        if (deleteConfirmationOpen) {
            restoreDeleteFocus = true
        } else if (restoreDeleteFocus) {
            // The editor stays composed under the dialog, including all draft fields.
            repeat(4) {
                withFrameNanos { }
                if (runCatching { deleteRequester.requestFocus() }.getOrDefault(false)) {
                    restoreDeleteFocus = false
                    return@LaunchedEffect
                }
            }
            restoreDeleteFocus = false
        }
    }
    val keyboardController = LocalSoftwareKeyboardController.current
    var editingName by remember(profile?.id) { mutableStateOf(false) }
    LaunchedEffect(editingName) {
        if (editingName) {
            delay(100)
            keyboardController?.show()
        } else {
            keyboardController?.hide()
        }
    }
    var name by remember(profile?.id) { mutableStateOf(profile?.name.orEmpty()) }
    var avatar by remember(profile?.id) { mutableStateOf(profile?.avatar ?: ProfileAvatarCatalog.DEFAULT_ID) }
    var kidsMaxAge by remember(profile?.id) { mutableIntStateOf(profile?.kidsMaxAge ?: 7) }
    var parentalSave by remember { mutableStateOf(false) }
    var isKids by remember(profile?.id) { mutableStateOf(profile?.isKids ?: false) }
    var pinEnabled by remember(profile?.id) { mutableStateOf(hadPin) }
    var pendingPin by remember(profile?.id) { mutableStateOf<String?>(null) }
    var removeExistingPin by remember(profile?.id) { mutableStateOf(false) }
    var pinFlow by remember { mutableStateOf<TvEditorPinFlow?>(null) }
    var pinError by remember { mutableStateOf<String?>(null) }
    var pinResetToken by remember { mutableIntStateOf(0) }

    BackHandler {
        if (editingName) {
            editingName = false
            keyboardController?.hide()
        } else onBack()
    }

    LaunchedEffect(profile?.id) {
        delay(110)
        runCatching { firstRequester.requestFocus() }
    }

    fun commitSave() {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return
        if (profile == null) {
            val created = profileStore.createProfile(cleanName, avatar, isKids, kidsMaxAge)
            pendingPin?.takeIf { pinEnabled && it.length == 4 }?.let {
                profileStore.setProfilePin(created.id, it)
            }
        } else {
            profileStore.updateProfile(profile.id, cleanName, avatar, isKids, kidsMaxAge)
            when {
                removeExistingPin -> profileStore.clearProfilePin(profile.id)
                pendingPin != null && pinEnabled -> profileStore.setProfilePin(profile.id, pendingPin!!)
            }
        }
        onSaved()
    }

    fun save() {
        if (isKids || profile?.isKids == true) parentalSave = true else commitSave()
    }
    if (parentalSave) {
        TvParentalPinGate(profileStore, onGranted = { parentalSave = false; commitSave() }, onCancel = { parentalSave = false })
    }

    TvProfileBackground {
        Layout(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 52.dp, end = 52.dp, top = 28.dp, bottom = 28.dp),
            content = {
                // Pin the header, profile preview and actions. Only editable rows scroll;
                // focusing a text field must never scroll the preview under the header.
                Column(
                    modifier = Modifier.width(410.dp).padding(horizontal = 6.dp),
                ) {
                    Row(modifier = Modifier.height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (profile == null) "Add Profile" else "Edit Profile",
                            color = Color.White,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    // Preview stays visible while focus moves through the form.
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(17.dp),
                        color = PickerPanel,
                        border = BorderStroke(1.dp, PickerStroke),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ProfileAvatarImage(
                                profile = VueoProfile(
                                    id = profile?.id ?: "preview",
                                    name = name.ifBlank { "New profile" },
                                    avatar = avatar,
                                    isKids = isKids,
                                    createdAtEpochMs = profile?.createdAtEpochMs ?: 0L,
                                ),
                                size = 60,
                            )
                            Spacer(Modifier.width(15.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = name.ifBlank { "New profile" },
                                    color = Color.White,
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(1.dp))
                                Text(
                                    text = buildString {
                                        append(if (isKids) "Kids profile" else "Standard profile")
                                        if (pinEnabled) append(" • PIN")
                                    },
                                    color = PickerMuted,
                                    fontSize = 12.sp,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {

                        Box {
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it.take(24) },
                                singleLine = true,
                                readOnly = !editingName,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = {
                                    editingName = false
                                    keyboardController?.hide()
                                }),
                                label = { Text("Profile name") },
                                shape = RoundedCornerShape(17.dp),
                                modifier = Modifier.fillMaxWidth().height(68.dp)
                                    .focusRequester(firstRequester)
                                    .onFocusChanged { if (!it.isFocused) editingName = false }
                                    .onPreviewKeyEvent { event ->
                                        if (!editingName && (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter)) {
                                            if (event.type == KeyEventType.KeyUp) editingName = true
                                            true
                                        } else false
                                    },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color.White,
                                    unfocusedBorderColor = PickerStroke,
                                    focusedLabelColor = Color.White,
                                    unfocusedLabelColor = PickerMuted,
                                    cursorColor = Color.White,
                                ),
                            )
                        }

                        Box {
                            TvEditorToggleRow(
                                title = "Kids Profile",
                                subtitle = "Only titles within the selected age rating.",
                                checked = isKids,
                                leadingIcon = Icons.Default.ChildCare,
                                onClick = { isKids = !isKids },
                            )
                        }

                        if (isKids) {
                            Text("Maximum age: $kidsMaxAge years", color = Color.White, fontSize = 14.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                com.vueo.shared.core.profile.KidsContentPolicy.ageLimits.forEach { age ->
                                    Box(Modifier.weight(1f)) {
                                        TvProfileAction(
                                            label = "$age years", width = 0, fillWidth = true,
                                            primary = kidsMaxAge == age, scaleOnFocus = false,
                                            onClick = { kidsMaxAge = age },
                                        )
                                    }
                                }
                            }
                            Text("Unrated titles are hidden. A parental PIN is needed to save Kids settings.", color = PickerMuted, fontSize = 11.sp)
                        }

                        Box {
                            TvEditorToggleRow(
                                title = "Profile PIN",
                                modifier = Modifier.focusRequester(pinRequester).focusProperties {
                                    down = saveRequester
                                },
                                subtitle = if (pinEnabled) "A 4-digit PIN is required to open this profile." else "Protect this profile with a 4-digit PIN.",
                                checked = pinEnabled,
                                leadingIcon = Icons.Default.Lock,
                                onClick = {
                                    when {
                                        pinEnabled && hadPin && !removeExistingPin -> {
                                            pinError = null
                                            pinFlow = TvEditorPinFlow.VerifyRemove
                                        }
                                        pinEnabled -> {
                                            pinEnabled = false
                                            pendingPin = null
                                        }
                                        hadPin && removeExistingPin -> {
                                            removeExistingPin = false
                                            pinEnabled = true
                                        }
                                        else -> {
                                            pinError = null
                                            pinFlow = TvEditorPinFlow.SetFirst
                                        }
                                    }
                                },
                            )
                        }

                        if (pinEnabled && hadPin && !removeExistingPin) {
                            Box {
                                TvProfileAction(
                                    label = "Change PIN",
                                    requester = changePinRequester,
                                    modifier = Modifier.focusProperties { up = pinRequester; down = saveRequester },
                                    onClick = {
                                        pinError = null
                                        pinFlow = TvEditorPinFlow.VerifyChange
                                    },
                                    width = 180,
                                )
                            }
                        }

                    }

                    Spacer(Modifier.height(12.dp))
                    val canSave = name.trim().isNotBlank() && (!pinEnabled || hadPin || pendingPin?.length == 4)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (profile != null && profile.id != ProfileStore.DEFAULT_PROFILE_ID) {
                            Box(modifier = Modifier.weight(1f)) {
                                TvProfileAction(
                                    label = "Delete Profile",
                                    requester = deleteRequester,
                                    modifier = Modifier.focusProperties { right = saveRequester; up = pinRequester },
                                    onClick = { onRequestDelete(profile) },
                                    width = 0,
                                    fillWidth = true,
                                    scaleOnFocus = false,
                                    danger = true,
                                    icon = Icons.Default.Delete,
                                )
                            }
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            TvProfileAction(
                                label = if (profile == null) "Create Profile" else "Save Changes",
                                requester = saveRequester,
                                modifier = Modifier.focusProperties {
                                    up = if (pinEnabled && hadPin && !removeExistingPin) changePinRequester else pinRequester
                                    left = if (profile != null && profile.id != ProfileStore.DEFAULT_PROFILE_ID) deleteRequester else FocusRequester.Default
                                },
                                onClick = ::save,
                                enabled = canSave,
                                width = 0,
                                fillWidth = true,
                                scaleOnFocus = false,
                                primary = true,
                            )
                        }
                    }
                }

                // The grid panel follows the measured form height; it never stretches
                // the form or the gaps between avatars to reach the screen bottom.
                Column(
                    modifier = Modifier.fillMaxHeight().padding(horizontal = 6.dp),
                ) {
                    Box(modifier = Modifier.height(48.dp), contentAlignment = Alignment.CenterStart) {
                        Text(
                            text = "Choose an avatar",
                            color = Color.White,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Black,
                        )
                    }
                    Spacer(Modifier.height(10.dp))

                    Surface(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        shape = RoundedCornerShape(17.dp),
                        color = PickerPanel,
                        border = BorderStroke(1.dp, PickerStroke),
                    ) {
                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                            val columns = 4
                            val inset = 12.dp
                            val gap = 12.dp
                            val cellSize = ((maxWidth - inset * 2 - gap * (columns - 1)) / columns)
                                .coerceAtLeast(1.dp)
                            // Reserve the full 1.18x focused diameter inside every cell.
                            // Scaling only changes drawing, never grid measurement.
                            val avatarSize = cellSize / 1.18f
                            TvAvatarFocusScroll {
                        LazyVerticalGrid(
                                columns = GridCells.Fixed(columns),
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.spacedBy(gap),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(inset),
                            ) {
                                items(
                                    items = ProfileAvatarCatalog.selectable,
                                    key = { it.id },
                                ) { choice ->
                                    Box(
                                        modifier = Modifier.fillMaxWidth().height(cellSize),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        TvAvatarChoice(
                                            avatar = choice,
                                            selected = avatar == choice.id,
                                            size = avatarSize,
                                            onClick = { avatar = choice.id },
                                        )
                                    }
                                }
                            }
                        }
                        }
                    }
                }
            },
        ) { panes, constraints ->
            val leftWidth = 410.dp.roundToPx()
            val gap = 34.dp.roundToPx()
            val left = panes[0].measure(
                Constraints(
                    minWidth = leftWidth,
                    maxWidth = leftWidth,
                    maxHeight = constraints.maxHeight,
                )
            )
            // Measure the form first, then give the scrollable panel exactly its
            // height in this same layout pass. No delayed size-state feedback.
            val right = panes[1].measure(
                Constraints.fixed(
                    width = (constraints.maxWidth - leftWidth - gap).coerceAtLeast(0),
                    height = left.height,
                )
            )
            layout(constraints.maxWidth, constraints.maxHeight) {
                left.placeRelative(0, 0)
                right.placeRelative(leftWidth + gap, 0)
            }
        }
    }

    val flow = pinFlow
    if (flow != null) {
        key(flow, pinResetToken) {
            TvPinEntryOverlay(
                title = when (flow) {
                    TvEditorPinFlow.SetFirst -> "Set Profile PIN"
                    is TvEditorPinFlow.SetConfirm -> "Confirm Profile PIN"
                    TvEditorPinFlow.VerifyChange -> "Verify Current PIN"
                    TvEditorPinFlow.VerifyRemove -> "Remove Profile PIN"
                },
                subtitle = when (flow) {
                    TvEditorPinFlow.SetFirst -> "Choose a new 4-digit PIN"
                    is TvEditorPinFlow.SetConfirm -> "Enter the same PIN again"
                    TvEditorPinFlow.VerifyChange -> "Enter the current PIN before changing it"
                    TvEditorPinFlow.VerifyRemove -> "Enter the current PIN to remove the lock"
                },
                errorText = pinError,
                onComplete = { pin ->
                    when (flow) {
                        TvEditorPinFlow.SetFirst -> {
                            pinError = null
                            pinFlow = TvEditorPinFlow.SetConfirm(pin)
                        }
                        is TvEditorPinFlow.SetConfirm -> {
                            if (pin == flow.firstPin) {
                                pendingPin = pin
                                pinEnabled = true
                                removeExistingPin = false
                                pinFlow = null
                                pinError = null
                            } else {
                                pinError = "PINs do not match"
                                pinResetToken += 1
                            }
                        }
                        TvEditorPinFlow.VerifyChange -> {
                            if (profile != null && profileStore.verifyProfilePin(profile.id, pin)) {
                                pinError = null
                                pinFlow = TvEditorPinFlow.SetFirst
                            } else {
                                pinError = "Incorrect PIN"
                                pinResetToken += 1
                            }
                        }
                        TvEditorPinFlow.VerifyRemove -> {
                            if (profile != null && profileStore.verifyProfilePin(profile.id, pin)) {
                                pinEnabled = false
                                removeExistingPin = true
                                pendingPin = null
                                pinFlow = null
                                pinError = null
                            } else {
                                pinError = "Incorrect PIN"
                                pinResetToken += 1
                            }
                        }
                    }
                },
                onCancel = {
                    pinFlow = null
                    pinError = null
                },
            )
        }
    }
}

@Composable
private fun TvProfileBackground(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF111513),
                        Color(0xFF080B09),
                        PickerBlack,
                        PickerBlack,
                    )
                )
            ),
    ) {
        content()
    }
}

@Composable
private fun TvProfileBrand() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Image(
            painter = painterResource(R.drawable.vueo_tv_logo),
            contentDescription = "VUEO",
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(42.dp),
        )
        Spacer(Modifier.width(13.dp))
        Text(
            text = "VUEO",
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 27.sp,
            letterSpacing = 3.5.sp,
        )
    }
}

@Composable
private fun PickerProfileCard(
    profile: VueoProfile,
    active: Boolean,
    locked: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) tvTunedFocusScale(1.04f) else 1f,
        animationSpec = tvFocusSpec(focused),
        label = "pickerProfileScale",
    )

    Column(
        modifier = modifier
            .width(166.dp)
            .scale(scale)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .focusable(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.size(132.dp), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(124.dp)
                    .clip(CircleShape)
                    .border(
                        width = if (focused) 4.dp else if (active) 3.dp else 1.dp,
                        color = when {
                            focused -> Color.White
                            active -> PickerGreen
                            else -> PickerStroke
                        },
                        shape = CircleShape,
                    )
                    .padding(if (active && !focused) 4.dp else 0.dp),
            ) {
                ProfileAvatarImage(profile = profile, size = if (active && !focused) 116 else 124)
            }

            if (active) {
                Surface(
                    modifier = Modifier.align(Alignment.BottomEnd).size(30.dp),
                    shape = CircleShape,
                    color = PickerGreen,
                    border = BorderStroke(3.dp, PickerBlack),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("✓", color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = profile.name,
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(7.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (profile.isKids) TvProfileBadge("KIDS")
            if (locked) TvProfileBadge("PIN")
            if (!profile.isKids && !locked) Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun ManageProfileCard(
    profile: VueoProfile,
    locked: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) tvTunedFocusScale(1.04f) else 1f,
        animationSpec = tvFocusSpec(focused),
        label = "manageProfileScale",
    )

    Column(
        modifier = modifier
            .width(154.dp)
            .scale(scale)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.size(126.dp), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(116.dp)
                    .clip(CircleShape)
                    .border(if (focused) 4.dp else 1.dp, if (focused) Color.White else PickerStroke, CircleShape),
            ) {
                ProfileAvatarImage(profile = profile, size = 116)
            }
            Surface(
                modifier = Modifier.align(Alignment.BottomEnd).size(31.dp),
                shape = CircleShape,
                color = Color.White,
                border = BorderStroke(3.dp, PickerBlack),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit ${profile.name}", tint = Color.Black, modifier = Modifier.size(16.dp))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(profile.name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            if (profile.isKids) TvProfileBadge("KIDS")
            if (locked) TvProfileBadge("PIN")
            if (!profile.isKids && !locked) Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun AddProfileCard(onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) tvTunedFocusScale(1.04f) else 1f,
        animationSpec = tvFocusSpec(focused),
        label = "addProfileScale",
    )
    Column(
        modifier = Modifier
            .width(154.dp)
            .scale(scale)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            modifier = Modifier.size(116.dp),
            shape = CircleShape,
            color = if (focused) PickerPanelRaised else PickerPanel,
            border = BorderStroke(if (focused) 4.dp else 1.dp, if (focused) Color.White else PickerStroke),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Add, contentDescription = "Add Profile", tint = if (focused) Color.White else PickerMuted, modifier = Modifier.size(42.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("Add Profile", color = if (focused) Color.White else PickerMuted, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))
    }
}

// Source circles in the bundled 512px portraits are offset inside their tiles.
// Recenter their usable inner disk so a circular UI mask cannot expose a black crescent.
private data class AvatarArtworkCrop(val x: Float, val y: Float, val radius: Float)
private val AvatarArtworkCrops = mapOf(
    "avatar_boy_1" to AvatarArtworkCrop(0.600436f, 0.560951f, 0.395658f),
    "avatar_boy_2" to AvatarArtworkCrop(0.606057f, 0.562047f, 0.390037f),
    "avatar_boy_3" to AvatarArtworkCrop(0.495726f, 0.567697f, 0.428397f),
    "avatar_character_1" to AvatarArtworkCrop(0.608173f, 0.590849f, 0.387920f),
    "avatar_character_2" to AvatarArtworkCrop(0.618617f, 0.589822f, 0.377476f),
    "avatar_character_3" to AvatarArtworkCrop(0.493224f, 0.582040f, 0.414054f),
    "avatar_character_4" to AvatarArtworkCrop(0.521430f, 0.583089f, 0.413004f),
    "avatar_character_5" to AvatarArtworkCrop(0.550958f, 0.545447f, 0.445136f),
    "avatar_character_6" to AvatarArtworkCrop(0.530377f, 0.556352f, 0.439742f),
    "avatar_character_7" to AvatarArtworkCrop(0.564759f, 0.544646f, 0.431335f),
    "avatar_character_8" to AvatarArtworkCrop(0.570375f, 0.556027f, 0.425719f),
    "avatar_girl_1" to AvatarArtworkCrop(0.524432f, 0.580517f, 0.415577f),
    "avatar_girl_2" to AvatarArtworkCrop(0.547346f, 0.582259f, 0.413835f),
    "avatar_girl_3" to AvatarArtworkCrop(0.567883f, 0.585580f, 0.410513f),
    "avatar_malaysia_1" to AvatarArtworkCrop(0.513895f, 0.514648f, 0.481445f),
    "avatar_malaysia_2" to AvatarArtworkCrop(0.505490f, 0.514039f, 0.482055f),
    "avatar_malaysia_3" to AvatarArtworkCrop(0.494169f, 0.509433f, 0.486660f),
    "avatar_malaysia_4" to AvatarArtworkCrop(0.505075f, 0.510483f, 0.485611f),
    "avatar_malaysia_5" to AvatarArtworkCrop(0.519060f, 0.486994f, 0.477034f),
    "avatar_malaysia_6" to AvatarArtworkCrop(0.505464f, 0.495365f, 0.490630f),
    "avatar_malaysia_7" to AvatarArtworkCrop(0.486839f, 0.494500f, 0.482933f),
    "avatar_malaysia_8" to AvatarArtworkCrop(0.500054f, 0.492379f, 0.488472f),
    "avatar_man_1" to AvatarArtworkCrop(0.512729f, 0.543886f, 0.452208f),
    "avatar_man_2" to AvatarArtworkCrop(0.535629f, 0.548497f, 0.447597f),
    "avatar_man_3" to AvatarArtworkCrop(0.550973f, 0.545426f, 0.445121f),
    "avatar_man_4" to AvatarArtworkCrop(0.564618f, 0.544813f, 0.431476f),
    "avatar_man_5" to AvatarArtworkCrop(0.592563f, 0.547843f, 0.403531f),
    "avatar_woman_1" to AvatarArtworkCrop(0.600757f, 0.549197f, 0.395336f),
    "avatar_woman_2" to AvatarArtworkCrop(0.503470f, 0.552506f, 0.443588f),
    "avatar_woman_3" to AvatarArtworkCrop(0.530452f, 0.556174f, 0.439920f),
    "avatar_woman_4" to AvatarArtworkCrop(0.557371f, 0.559622f, 0.436471f),
    "avatar_woman_5" to AvatarArtworkCrop(0.570219f, 0.556442f, 0.425875f),
)

@Composable
private fun ProfileAvatarArtwork(avatarId: String, drawableRes: Int, description: String) {
    val crop = AvatarArtworkCrops[avatarId]
    Image(
        painter = painterResource(drawableRes),
        contentDescription = description,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize().graphicsLayer {
            if (crop != null) {
                val zoom = .5f / crop.radius
                scaleX = zoom
                scaleY = zoom
                translationX = (.5f - crop.x) * size.width * zoom
                translationY = (.5f - crop.y) * size.height * zoom
            }
        },
    )
}

@Composable
private fun ProfileAvatarImage(profile: VueoProfile, size: Int) {
    val avatarDrawable = ProfileAvatarCatalog.drawableRes(profile.avatar)
    Box(
        modifier = Modifier.size(size.dp).clip(CircleShape).background(PickerPanelRaised),
        contentAlignment = Alignment.Center,
    ) {
        if (avatarDrawable != null) {
            ProfileAvatarArtwork(profile.avatar, avatarDrawable, profile.name)
        } else {
            Text(
                text = profile.name.trim().firstOrNull()?.uppercase() ?: "V",
                color = Color.White,
                fontSize = (size * 0.34f).sp,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

@Composable
private fun TvAvatarChoice(
    avatar: ProfileAvatarSpec,
    selected: Boolean,
    onClick: () -> Unit,
    size: Dp = 76.dp,
) {
    var focused by remember(avatar.id) { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.18f else 1f,
        animationSpec = tvFocusSpec(focused),
        label = "profileEditorAvatarScale",
    )

    Box(
        modifier = Modifier
            .size(size)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .scale(scale)
            .clip(CircleShape)
            .border(
                width = if (focused) 4.dp else if (selected) 3.dp else 1.dp,
                color = when {
                    focused -> Color.White
                    selected -> PickerGreen
                    else -> PickerStroke
                },
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        ProfileAvatarArtwork(avatar.id, avatar.drawableRes, "Profile avatar")
        if (selected) {
            Surface(
                modifier = Modifier.align(Alignment.BottomEnd).size(22.dp),
                shape = CircleShape,
                color = PickerGreen,
                border = BorderStroke(2.dp, PickerBlack),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("✓", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
private fun TvProfileBadge(label: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = PickerGreen.copy(alpha = .10f),
        border = BorderStroke(1.dp, PickerGreen.copy(alpha = .34f)),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            color = PickerGreen,
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
private fun profileSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = PickerBlack,
    checkedTrackColor = PickerGreen,
    checkedBorderColor = PickerGreen,
    uncheckedThumbColor = PickerMuted,
    uncheckedTrackColor = PickerPanelRaised,
    uncheckedBorderColor = PickerStroke,
    disabledCheckedThumbColor = PickerBlack.copy(alpha = .5f),
    disabledCheckedTrackColor = PickerGreen.copy(alpha = .35f),
    disabledCheckedBorderColor = PickerGreen.copy(alpha = .35f),
    disabledUncheckedThumbColor = PickerMuted.copy(alpha = .35f),
    disabledUncheckedTrackColor = PickerPanel,
    disabledUncheckedBorderColor = PickerStroke,
)

@Composable
private fun TvPickerToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused && enabled) tvTunedFocusScale(1.014f) else 1f,
        animationSpec = tvFocusSpec(focused),
        label = "pickerToggleScale",
    )

    Row(
        modifier = Modifier
            .width(520.dp)
            .scale(scale)
            .onFocusChanged { focused = it.isFocused }
            .background(if (focused && enabled) PickerPanelRaised else PickerPanel, RoundedCornerShape(18.dp))
            .border(if (focused && enabled) 2.dp else 1.dp, if (focused && enabled) Color.White else PickerStroke, RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = if (enabled) Color.White else PickerMuted.copy(alpha = .5f), fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = PickerMuted.copy(alpha = if (enabled) 1f else .5f), fontSize = 11.sp)
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled, colors = profileSwitchColors())
    }
}

@Composable
private fun TvEditorToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(68.dp)
            .onFocusChanged { focused = it.isFocused }
            .background(if (focused) PickerPanelRaised else PickerPanel, RoundedCornerShape(17.dp))
            .border(if (focused) 2.dp else 1.dp, if (focused) Color.White else PickerStroke, RoundedCornerShape(17.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            if (leadingIcon != null) {
                Icon(leadingIcon, contentDescription = null, tint = if (focused) Color.White else PickerMuted, modifier = Modifier.size(22.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 16.sp, lineHeight = 19.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(1.dp))
            Text(subtitle, color = PickerMuted, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Box(modifier = Modifier.size(width = 44.dp, height = 36.dp), contentAlignment = Alignment.Center) {
            Switch(
                checked = checked,
                onCheckedChange = null,
                colors = profileSwitchColors(),
                modifier = Modifier.scale(.78f),
            )
        }
    }
}

@Composable
private fun TvProfileAction(
    label: String,
    onClick: () -> Unit,
    width: Int,
    enabled: Boolean = true,
    primary: Boolean = false,
    danger: Boolean = false,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    requester: FocusRequester? = null,
    fillWidth: Boolean = false,
    scaleOnFocus: Boolean = true,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused && enabled && scaleOnFocus) tvTunedFocusScale(1.025f) else 1f,
        animationSpec = tvFocusSpec(focused),
        label = "profileActionScale:$label",
    )
    val background = when {
        !enabled -> PickerPanel.copy(alpha = .55f)
        danger && focused -> PickerDanger.copy(alpha = .20f)
        danger -> PickerDanger.copy(alpha = .10f)
        primary -> if (focused) Color.White else PickerGreen
        focused -> Color.White.copy(alpha = .16f)
        else -> PickerPanel
    }
    val foreground = when {
        !enabled -> PickerMuted.copy(alpha = .5f)
        primary -> if (focused) Color.Black else Color.Black
        danger -> PickerDanger
        else -> Color.White
    }

    Row(
        modifier = modifier
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier.width(width.dp))
            .height(52.dp)
            .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
            .scale(scale)
            .onFocusChanged { focused = it.isFocused }
            .background(background, RoundedCornerShape(50))
            .border(if (focused && enabled) 2.dp else 1.dp, if (focused && enabled) Color.White else PickerStroke, RoundedCornerShape(50))
            .clickable(enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = foreground, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(label, color = foreground, fontSize = 14.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun TvIconAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) tvTunedFocusScale(1.05f) else 1f,
        animationSpec = tvFocusSpec(focused),
        label = "profileIconActionScale:$contentDescription",
    )
    Box(
        modifier = Modifier
            .size(48.dp)
            .scale(scale)
            .onFocusChanged { focused = it.isFocused }
            .background(if (focused) Color.White.copy(alpha = .16f) else PickerPanel, CircleShape)
            .border(if (focused) 2.dp else 1.dp, if (focused) Color.White else PickerStroke, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = Color.White, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun TvProfileConfirmOverlay(
    title: String,
    message: String,
    confirmLabel: String,
    danger: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val cancelRequester = remember { FocusRequester() }
    TvModalDialog(onDismissRequest = onCancel, initialFocus = cancelRequester) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = .94f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .width(600.dp)
                    .background(PickerPanelRaised, RoundedCornerShape(24.dp))
                    .border(1.dp, PickerStroke, RoundedCornerShape(24.dp))
                    .padding(horizontal = 36.dp, vertical = 30.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(title, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(10.dp))
                Text(message, color = PickerMuted, fontSize = 14.sp, lineHeight = 20.sp)
                Spacer(Modifier.height(26.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    TvProfileAction(
                        label = "Cancel",
                        onClick = onCancel,
                        width = 150,
                        requester = cancelRequester,
                    )
                    TvProfileAction(label = confirmLabel, onClick = onConfirm, width = 190, danger = danger)
                }
            }
        }
    }
}


/** Reveal only clipped rows. Horizontal moves within a visible row cannot re-pivot the grid. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TvAvatarFocusScroll(content: @Composable () -> Unit) {
    val reveal = remember {
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
                val focusInset = size * .09f
                return when {
                    offset - focusInset < 0f -> offset - focusInset
                    offset + size + focusInset > containerSize -> offset + size + focusInset - containerSize
                    else -> 0f
                }
            }
        }
    }
    CompositionLocalProvider(LocalBringIntoViewSpec provides reveal, content = content)
}

@Composable
private fun TvParentalPinGate(store: ProfileStore, onGranted: () -> Unit, onCancel: () -> Unit) {
    val scope = rememberCoroutineScope()
    val existing = remember { store.hasParentalPin() }
    var first by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reset by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    key(reset) {
        TvPinEntryOverlay(
            title = if (existing) "Parental PIN" else if (first == null) "Create parental PIN" else "Confirm parental PIN",
            subtitle = if (existing) "Enter the 4-digit parental PIN" else "Protect Kids controls on this device. Keep this PIN private.",
            errorText = error,
            onComplete = { pin ->
                if (!busy) {
                    if (!existing && first == null) { first = pin; reset++ }
                    else if (!existing && first != pin) { first = null; error = "PINs did not match"; reset++ }
                    else {
                        busy = true
                        scope.launch {
                            val accepted = withContext(Dispatchers.IO) {
                                if (existing) store.verifyParentalPin(pin) else store.setParentalPin(pin)
                            }
                            busy = false
                            if (accepted) onGranted() else { error = "Incorrect PIN"; reset++ }
                        }
                    }
                }
            },
            onCancel = onCancel,
        )
    }
}
