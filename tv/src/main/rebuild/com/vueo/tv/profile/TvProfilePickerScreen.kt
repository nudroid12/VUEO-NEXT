package com.vueo.tv.profile

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vueo.shared.core.R as SharedR
import com.vueo.shared.core.profile.ProfileAvatarCatalog
import com.vueo.shared.core.profile.ProfileAvatarSpec
import com.vueo.shared.core.storage.ProfileStore
import com.vueo.shared.core.storage.VueoProfile
import com.vueo.tv.R
import com.vueo.tv.ui.motion.tvFocusSpec
import com.vueo.tv.ui.motion.tvScreenFadeThrough
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
    onProfilesChanged: () -> Unit = {},
) {
    var revision by remember { mutableIntStateOf(0) }
    val profiles = remember(revision) { profileStore.profiles() }
    val activeProfileId = remember(revision) { profileStore.activeProfileId() }
    var mode by remember { mutableStateOf(ProfilePickerMode.WATCHING) }
    var editor by remember { mutableStateOf<TvProfileEditorState?>(null) }
    var lockedProfile by remember { mutableStateOf<VueoProfile?>(null) }
    var pinError by remember { mutableStateOf<String?>(null) }
    var pinResetToken by remember { mutableIntStateOf(0) }
    var deleteCandidate by remember { mutableStateOf<VueoProfile?>(null) }

    BackHandler {
        when {
            editor != null -> editor = null
            mode == ProfilePickerMode.MANAGE -> mode = ProfilePickerMode.WATCHING
            else -> Unit
        }
    }

    fun refreshProfiles() {
        revision += 1
        onProfilesChanged()
    }

    AnimatedContent(
        targetState = editor,
        transitionSpec = {
            tvScreenFadeThrough(
                enterDurationMillis = 380,
                exitDurationMillis = 200,
                enterDelayMillis = 34,
                initialScale = 0.986f,
                targetScale = 0.992f,
            )
        },
        modifier = Modifier.fillMaxSize().background(PickerBlack),
        label = "tvProfileEditorTransition",
    ) { editorState ->
        if (editorState != null) {
            TvProfileEditorScreen(
                state = editorState,
                profileStore = profileStore,
                onBack = { editor = null },
                onSaved = { refreshProfiles(); editor = null },
                onRequestDelete = { profile ->
                    editor = null
                    deleteCandidate = profile
                },
            )
        } else {
            AnimatedContent(
                targetState = mode,
                transitionSpec = {
                    tvScreenFadeThrough(
                        enterDurationMillis = 320,
                        exitDurationMillis = 175,
                        enterDelayMillis = 22,
                        initialScale = 0.994f,
                        targetScale = 0.996f,
                    )
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
                                if (profileStore.hasProfilePin(profile.id)) {
                                    pinError = null
                                    lockedProfile = profile
                                } else if (profileStore.setActiveProfile(profile.id)) {
                                    onProfileSelected(profile.id)
                                }
                            },
                            onManage = { mode = ProfilePickerMode.MANAGE },
                        )

                    ProfilePickerMode.MANAGE ->
                        TvManageProfiles(
                            profiles = profiles,
                            profileStore = profileStore,
                            onEdit = { editor = TvProfileEditorState(it) },
                            onAdd = { editor = TvProfileEditorState(null) },
                            onChanged = ::refreshProfiles,
                            onDone = { mode = ProfilePickerMode.WATCHING },
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
            message = "This removes this profile's local My List, history, playback progress and personal preferences.",
            confirmLabel = "Delete Profile",
            danger = true,
            onConfirm = {
                profileStore.deleteProfile(deleting.id)
                deleteCandidate = null
                refreshProfiles()
            },
            onCancel = { deleteCandidate = null },
        )
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
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onRequestDelete: (VueoProfile) -> Unit,
) {
    val profile = state.profile
    val hadPin = remember(profile?.id) { profile?.let { profileStore.hasProfilePin(it.id) } ?: false }
    val firstRequester = remember { FocusRequester() }
    var name by remember(profile?.id) { mutableStateOf(profile?.name.orEmpty()) }
    var avatar by remember(profile?.id) { mutableStateOf(profile?.avatar ?: ProfileAvatarCatalog.DEFAULT_ID) }
    var isKids by remember(profile?.id) { mutableStateOf(profile?.isKids ?: false) }
    var pinEnabled by remember(profile?.id) { mutableStateOf(hadPin) }
    var pendingPin by remember(profile?.id) { mutableStateOf<String?>(null) }
    var removeExistingPin by remember(profile?.id) { mutableStateOf(false) }
    var pinFlow by remember { mutableStateOf<TvEditorPinFlow?>(null) }
    var pinError by remember { mutableStateOf<String?>(null) }
    var pinResetToken by remember { mutableIntStateOf(0) }

    BackHandler(onBack = onBack)

    LaunchedEffect(profile?.id) {
        delay(110)
        runCatching { firstRequester.requestFocus() }
    }

    fun save() {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return
        if (profile == null) {
            val created = profileStore.createProfile(cleanName, avatar, isKids)
            pendingPin?.takeIf { pinEnabled && it.length == 4 }?.let {
                profileStore.setProfilePin(created.id, it)
            }
        } else {
            profileStore.updateProfile(profile.id, cleanName, avatar, isKids)
            when {
                removeExistingPin -> profileStore.clearProfilePin(profile.id)
                pendingPin != null && pinEnabled -> profileStore.setProfilePin(profile.id, pendingPin!!)
            }
        }
        onSaved()
    }

    TvProfileBackground {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 52.dp, end = 52.dp, top = 28.dp, bottom = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(34.dp),
        ) {
            // Left pane: header and primary actions are pinned. Only the form body
            // scrolls, so Create / Save / Delete are always visible inside TV safe area.
            Column(
                modifier = Modifier.width(410.dp).fillMaxHeight(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TvIconAction(
                        icon = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        onClick = onBack,
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(
                        text = if (profile == null) "Add Profile" else "Edit Profile",
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                    )
                }

                Spacer(Modifier.height(14.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(bottom = 18.dp),
                ) {
                    item(key = "preview") {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(22.dp),
                            color = PickerPanel,
                            border = BorderStroke(1.dp, PickerStroke),
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
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
                                    size = 78,
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
                                    Spacer(Modifier.height(4.dp))
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
                    }

                    item(key = "name") {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it.take(24) },
                            singleLine = true,
                            label = { Text("Profile name") },
                            modifier = Modifier.fillMaxWidth().focusRequester(firstRequester),
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

                    item(key = "kids") {
                        TvEditorToggleRow(
                            title = "Kids Profile",
                            subtitle = "Marks this profile for age-aware controls.",
                            checked = isKids,
                            onClick = { isKids = !isKids },
                        )
                    }

                    item(key = "pin") {
                        TvEditorToggleRow(
                            title = "Profile PIN",
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
                        item(key = "change-pin") {
                            TvProfileAction(
                                label = "Change PIN",
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
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TvProfileAction(
                        label = if (profile == null) "Create Profile" else "Save Changes",
                        onClick = ::save,
                        enabled = canSave,
                        width = if (profile == null || profile.id == ProfileStore.DEFAULT_PROFILE_ID) 230 else 210,
                        primary = true,
                    )

                    if (profile != null && profile.id != ProfileStore.DEFAULT_PROFILE_ID) {
                        TvProfileAction(
                            label = "Delete",
                            onClick = { onRequestDelete(profile) },
                            width = 150,
                            danger = true,
                            icon = Icons.Default.Delete,
                        )
                    }
                }
            }

            // Right pane scrolls independently so the avatar catalogue can stay large
            // enough for sofa viewing without pushing actions below the viewport.
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
            ) {
                Text(
                    text = "Choose an avatar",
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    text = "Built-in avatars are shared across VUEO Mobile and TV.",
                    color = PickerMuted,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(14.dp))

                LazyVerticalGrid(
                    columns = GridCells.Fixed(8),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 28.dp),
                ) {
                    items(
                        items = ProfileAvatarCatalog.selectable,
                        key = { it.id },
                    ) { choice ->
                        TvAvatarChoice(
                            avatar = choice,
                            selected = avatar == choice.id,
                            onClick = { avatar = choice.id },
                        )
                    }
                }
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
    // Same official lockup used by Mobile: lime V/play mark + white VUEO wordmark.
    // Only the physical size is increased for 10-foot viewing.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Image(
            painter = painterResource(SharedR.drawable.vueo_logo_mark),
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
        targetValue = if (focused) 1.04f else 1f,
        animationSpec = tvFocusSpec(),
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
        targetValue = if (focused) 1.04f else 1f,
        animationSpec = tvFocusSpec(),
        label = "manageProfileScale",
    )

    Column(
        modifier = modifier
            .width(154.dp)
            .scale(scale)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .focusable(),
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
        targetValue = if (focused) 1.04f else 1f,
        animationSpec = tvFocusSpec(),
        label = "addProfileScale",
    )
    Column(
        modifier = Modifier
            .width(154.dp)
            .scale(scale)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .focusable(),
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

@Composable
private fun ProfileAvatarImage(profile: VueoProfile, size: Int) {
    val avatarDrawable = ProfileAvatarCatalog.drawableRes(profile.avatar)
    Box(
        modifier = Modifier.size(size.dp).clip(CircleShape).background(PickerPanelRaised),
        contentAlignment = Alignment.Center,
    ) {
        if (avatarDrawable != null) {
            Image(
                painter = painterResource(avatarDrawable),
                contentDescription = profile.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
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
) {
    var focused by remember(avatar.id) { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.055f else 1f,
        animationSpec = tvFocusSpec(),
        label = "profileEditorAvatarScale",
    )

    Box(
        modifier = Modifier
            .size(76.dp)
            .scale(scale)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .focusable()
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
        Image(
            painter = painterResource(avatar.drawableRes),
            contentDescription = "Profile avatar",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
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
private fun TvPickerToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused && enabled) 1.014f else 1f,
        animationSpec = tvFocusSpec(),
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
            .focusable(enabled)
            .padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = if (enabled) Color.White else PickerMuted.copy(alpha = .5f), fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = PickerMuted.copy(alpha = if (enabled) 1f else .5f), fontSize = 11.sp)
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun TvEditorToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.012f else 1f,
        animationSpec = tvFocusSpec(),
        label = "profileEditorToggleScale",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .onFocusChanged { focused = it.isFocused }
            .background(if (focused) PickerPanelRaised else PickerPanel, RoundedCornerShape(17.dp))
            .border(if (focused) 2.dp else 1.dp, if (focused) Color.White else PickerStroke, RoundedCornerShape(17.dp))
            .clickable(onClick = onClick)
            .focusable()
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, tint = if (focused) Color.White else PickerMuted, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(3.dp))
            Text(subtitle, color = PickerMuted, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Switch(checked = checked, onCheckedChange = null)
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
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused && enabled) 1.025f else 1f,
        animationSpec = tvFocusSpec(),
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
        modifier = Modifier
            .width(width.dp)
            .height(52.dp)
            .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
            .scale(scale)
            .onFocusChanged { focused = it.isFocused }
            .background(background, RoundedCornerShape(50))
            .border(if (focused && enabled) 2.dp else 1.dp, if (focused && enabled) Color.White else PickerStroke, RoundedCornerShape(50))
            .clickable(enabled = enabled, onClick = onClick)
            .focusable(enabled),
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
        targetValue = if (focused) 1.05f else 1f,
        animationSpec = tvFocusSpec(),
        label = "profileIconActionScale:$contentDescription",
    )
    Box(
        modifier = Modifier
            .size(48.dp)
            .scale(scale)
            .onFocusChanged { focused = it.isFocused }
            .background(if (focused) Color.White.copy(alpha = .16f) else PickerPanel, CircleShape)
            .border(if (focused) 2.dp else 1.dp, if (focused) Color.White else PickerStroke, CircleShape)
            .clickable(onClick = onClick)
            .focusable(),
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
    BackHandler(onBack = onCancel)
    LaunchedEffect(Unit) {
        delay(80)
        runCatching { cancelRequester.requestFocus() }
    }

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
