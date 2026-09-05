package com.vueo.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vueo.app.R
import com.vueo.app.core.storage.ProfileStore
import com.vueo.app.core.storage.VueoProfile
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class StockProfileAvatar(
    val id: String,
    val drawableRes: Int,
)

private val PROFILE_AVATARS =
    listOf(
        StockProfileAvatar("avatar_man_1", R.drawable.avatar_man_1),
        StockProfileAvatar("avatar_man_2", R.drawable.avatar_man_2),
        StockProfileAvatar("avatar_man_3", R.drawable.avatar_man_3),
        StockProfileAvatar("avatar_man_4", R.drawable.avatar_man_4),
        StockProfileAvatar("avatar_man_5", R.drawable.avatar_man_5),
        StockProfileAvatar("avatar_woman_1", R.drawable.avatar_woman_1),
        StockProfileAvatar("avatar_woman_2", R.drawable.avatar_woman_2),
        StockProfileAvatar("avatar_woman_3", R.drawable.avatar_woman_3),
        StockProfileAvatar("avatar_woman_4", R.drawable.avatar_woman_4),
        StockProfileAvatar("avatar_woman_5", R.drawable.avatar_woman_5),
        StockProfileAvatar("avatar_boy_1", R.drawable.avatar_boy_1),
        StockProfileAvatar("avatar_boy_2", R.drawable.avatar_boy_2),
        StockProfileAvatar("avatar_boy_3", R.drawable.avatar_boy_3),
        StockProfileAvatar("avatar_girl_1", R.drawable.avatar_girl_1),
        StockProfileAvatar("avatar_girl_2", R.drawable.avatar_girl_2),
        StockProfileAvatar("avatar_girl_3", R.drawable.avatar_girl_3),
        StockProfileAvatar("avatar_character_1", R.drawable.avatar_character_1),
        StockProfileAvatar("avatar_character_2", R.drawable.avatar_character_2),
        StockProfileAvatar("avatar_character_3", R.drawable.avatar_character_3),
        StockProfileAvatar("avatar_character_4", R.drawable.avatar_character_4),
        StockProfileAvatar("avatar_character_5", R.drawable.avatar_character_5),
        StockProfileAvatar("avatar_character_6", R.drawable.avatar_character_6),
        StockProfileAvatar("avatar_character_7", R.drawable.avatar_character_7),
        StockProfileAvatar("avatar_character_8", R.drawable.avatar_character_8),
        StockProfileAvatar("avatar_malaysia_1", R.drawable.avatar_malaysia_1),
        StockProfileAvatar("avatar_malaysia_2", R.drawable.avatar_malaysia_2),
        StockProfileAvatar("avatar_malaysia_3", R.drawable.avatar_malaysia_3),
        StockProfileAvatar("avatar_malaysia_4", R.drawable.avatar_malaysia_4),
        StockProfileAvatar("avatar_malaysia_5", R.drawable.avatar_malaysia_5),
        StockProfileAvatar("avatar_malaysia_6", R.drawable.avatar_malaysia_6),
        StockProfileAvatar("avatar_malaysia_7", R.drawable.avatar_malaysia_7),
        StockProfileAvatar("avatar_malaysia_8", R.drawable.avatar_malaysia_8),
    )

private val LEGACY_PROFILE_AVATARS =
    listOf(
        StockProfileAvatar("avatar_vueo_1", R.drawable.avatar_vueo_1),
        StockProfileAvatar("avatar_vueo_2", R.drawable.avatar_vueo_2),
        StockProfileAvatar("avatar_vueo_3", R.drawable.avatar_vueo_3),
        StockProfileAvatar("avatar_vueo_4", R.drawable.avatar_vueo_4),
    )

private fun stockAvatarDrawable(
    avatarId: String,
): Int? =
    (PROFILE_AVATARS + LEGACY_PROFILE_AVATARS)
        .firstOrNull {
            it.id == avatarId
        }
        ?.drawableRes

private const val LOCAL_AVATAR_PREFIX = "local_avatar:"
private const val PROFILE_AVATAR_SIZE = 512

private fun localAvatarFile(
    context: Context,
    avatarId: String,
): File? {
    if (!avatarId.startsWith(LOCAL_AVATAR_PREFIX)) return null
    val fileName = avatarId.removePrefix(LOCAL_AVATAR_PREFIX)
    if (fileName.isBlank() || fileName.contains('/') || fileName.contains('\\')) return null
    return File(File(context.filesDir, "profile_avatars"), fileName)
}

private fun saveProfilePhoto(
    context: Context,
    uri: Uri,
): String? =
    runCatching {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }

        var sample = 1
        val largest = maxOf(bounds.outWidth, bounds.outHeight)
        while (largest / sample > 1600) sample *= 2

        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return@runCatching null

        val side = minOf(decoded.width, decoded.height)
        val x = (decoded.width - side) / 2
        val y = (decoded.height - side) / 2
        val square = Bitmap.createBitmap(decoded, x, y, side, side)
        val scaled = if (side == PROFILE_AVATAR_SIZE) {
            square
        } else {
            Bitmap.createScaledBitmap(square, PROFILE_AVATAR_SIZE, PROFILE_AVATAR_SIZE, true)
        }

        val directory = File(context.filesDir, "profile_avatars").apply { mkdirs() }
        val fileName = "avatar_${UUID.randomUUID()}.jpg"
        val target = File(directory, fileName)
        target.outputStream().buffered().use { output ->
            check(scaled.compress(Bitmap.CompressFormat.JPEG, 90, output))
        }

        if (scaled !== square) scaled.recycle()
        if (square !== decoded) square.recycle()
        decoded.recycle()

        "$LOCAL_AVATAR_PREFIX$fileName"
    }.getOrNull()

private fun deleteLocalAvatar(
    context: Context,
    avatarId: String,
) {
    runCatching { localAvatarFile(context, avatarId)?.delete() }
}

@Composable
internal fun WhosWatchingScreen(
    profileStore: ProfileStore,
    profileVersion: Int,
    onProfileSelected: () -> Unit,
    onProfilesChanged: () -> Unit,
) {
    val profiles = remember(profileVersion) {
        profileStore.profiles()
    }
    val activeProfileId = remember(profileVersion) {
        profileStore.activeProfileId()
    }
    var managingProfiles by remember {
        mutableStateOf(false)
    }
    var lockedProfile by remember {
        mutableStateOf<VueoProfile?>(null)
    }

    lockedProfile?.let { profile ->
        ProfileUnlockDialog(
            profile = profile,
            onDismiss = { lockedProfile = null },
            onUnlocked = {
                lockedProfile = null
                if (profileStore.setActiveProfile(profile.id)) {
                    onProfileSelected()
                }
            },
            verifyPin = { pin ->
                profileStore.verifyProfilePin(profile.id, pin)
            },
        )
    }

    AnimatedContent(
        targetState = managingProfiles,
        transitionSpec = {
            vueoFadeThrough(
                enterDurationMillis = 320,
                exitDurationMillis = 180,
                enterDelayMillis = 24,
                initialScale = 0.994f,
                targetScale = 0.996f,
            )
        },
        modifier = Modifier
            .fillMaxSize()
            .background(VueoPalette.Background),
        label = "VUEO profile manage transition",
    ) { managing ->
        if (managing) {
            ProfileSettingsScreen(
                profileStore = profileStore,
                profileVersion = profileVersion,
                onProfilesChanged = onProfilesChanged,
                onActiveProfileChanged = onProfilesChanged,
                onBack = { managingProfiles = false },
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(VueoPalette.Background)
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(
                    start = 28.dp,
                    end = 28.dp,
                    top = 82.dp,
                    bottom = 28.dp,
                ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                item {
                    VueoBrandLockup()
                }

                item {
                    Text(
                        "Who's Watching?",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 31.sp,
                        textAlign = TextAlign.Center,
                    )
                }

                items(
                    profiles.chunked(2),
                    key = { row -> row.joinToString("|") { it.id } },
                ) { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(28.dp),
                    ) {
                        row.forEach { profile ->
                            WatchingProfileCard(
                                profile = profile,
                                active = profile.id == activeProfileId,
                                locked = profileStore.hasProfilePin(profile.id),
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    if (profileStore.hasProfilePin(profile.id)) {
                                        lockedProfile = profile
                                    } else if (profileStore.setActiveProfile(profile.id)) {
                                        onProfileSelected()
                                    }
                                },
                            )
                        }

                        if (row.size == 1) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }

                item {
                    OutlinedButton(
                        onClick = { managingProfiles = true },
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(
                            1.dp,
                            VueoPalette.Stroke,
                        ),
                        contentPadding = PaddingValues(
                            horizontal = 24.dp,
                            vertical = 10.dp,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            tint = VueoPalette.Muted,
                            modifier = Modifier.size(17.dp),
                        )
                        Spacer(Modifier.size(7.dp))
                        Text(
                            "Manage Profiles",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ProfileSettingsScreen(
    profileStore: ProfileStore,
    profileVersion: Int,
    onProfilesChanged: () -> Unit,
    onActiveProfileChanged: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val profiles = remember(profileVersion) { profileStore.profiles() }
    var editor by remember { mutableStateOf<ProfileEditorState?>(null) }
    var deleteCandidate by remember { mutableStateOf<VueoProfile?>(null) }
    var askOnStartup by remember(profileVersion) {
        mutableStateOf(profileStore.askWhoIsWatchingOnStartup())
    }

    deleteCandidate?.let { profile ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Delete ${profile.name}?") },
            text = {
                Text("This removes this profile's local My List, history, playback progress and personal preferences.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        val wasActive = profile.id == profileStore.activeProfileId()
                        if (profileStore.deleteProfile(profile.id)) {
                            deleteLocalAvatar(context, profile.avatar)
                            if (wasActive) onActiveProfileChanged()
                            onProfilesChanged()
                        }
                        deleteCandidate = null
                    },
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) { Text("Cancel") }
            },
        )
    }

    AnimatedContent(
        targetState = editor,
        transitionSpec = {
            vueoFadeThrough(
                enterDurationMillis = 380,
                exitDurationMillis = 210,
                enterDelayMillis = 34,
                initialScale = 0.985f,
                targetScale = 0.992f,
            )
        },
        modifier = Modifier
            .fillMaxSize()
            .background(VueoPalette.Background),
        label = "VUEO profile editor transition",
    ) { editorState ->
        if (editorState != null) {
            ProfileEditorScreen(
                state = editorState,
                profileStore = profileStore,
                onBack = { editor = null },
                onSave = { name, avatar, isKids, pinEnabled, pin ->
                    val existing = editorState.profile
                    if (existing == null) {
                        val created = profileStore.createProfile(
                            name = name,
                            avatar = avatar,
                            isKids = isKids,
                        )
                        if (pinEnabled && pin.length == 4) {
                            profileStore.setProfilePin(created.id, pin)
                        }
                    } else {
                        val previousAvatar = existing.avatar
                        profileStore.updateProfile(
                            profileId = existing.id,
                            name = name,
                            avatar = avatar,
                            isKids = isKids,
                        )
                        if (!pinEnabled && profileStore.hasProfilePin(existing.id)) {
                            profileStore.clearProfilePin(existing.id)
                        } else if (pinEnabled && pin.length == 4) {
                            profileStore.setProfilePin(existing.id, pin)
                        }
                        if (previousAvatar != avatar) {
                            deleteLocalAvatar(context, previousAvatar)
                        }
                    }
                    onProfilesChanged()
                    editor = null
                },
                onDelete = editorState.profile?.takeIf {
                    it.id != ProfileStore.DEFAULT_PROFILE_ID
                }?.let { profile ->
                    {
                        editor = null
                        deleteCandidate = profile
                    }
                },
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(VueoPalette.Background)
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(
                    start = 28.dp,
                    end = 28.dp,
                    top = 82.dp,
                    bottom = 28.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item {
                    VueoBrandLockup()
                }

                item {
                    Text(
                        text = "Who's Watching?",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 31.sp,
                        textAlign = TextAlign.Center,
                    )
                }

                items(
                    items = profiles.chunked(2),
                    key = { row -> "manage:${row.joinToString("|") { it.id }}" },
                ) { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(28.dp),
                    ) {
                        row.forEach { profile ->
                            ManageProfileAvatar(
                                profile = profile,
                                locked = profileStore.hasProfilePin(profile.id),
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    editor = ProfileEditorState(profile)
                                },
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }

                if (profiles.size < ProfileStore.MAX_PROFILES) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(28.dp),
                        ) {
                            AddProfileAvatar(
                                modifier = Modifier.weight(1f),
                                onClick = { editor = ProfileEditorState(null) },
                            )
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }

                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = profiles.size > 1) {
                                askOnStartup = !askOnStartup
                                profileStore.setAskWhoIsWatchingOnStartup(askOnStartup)
                                onProfilesChanged()
                            },
                        shape = RoundedCornerShape(16.dp),
                        color = VueoPalette.Surface,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = "Ask on startup",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                )
                                Text(
                                    text = "Show profile picker when VUEO launches",
                                    color = VueoPalette.Muted,
                                    fontSize = 10.sp,
                                )
                            }
                            Switch(
                                checked = askOnStartup,
                                enabled = profiles.size > 1,
                                onCheckedChange = { enabled ->
                                    askOnStartup = enabled
                                    profileStore.setAskWhoIsWatchingOnStartup(enabled)
                                    onProfilesChanged()
                                },
                            )
                        }
                    }
                }

                item {
                    OutlinedButton(
                        onClick = onBack,
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(horizontal = 34.dp, vertical = 11.dp),
                    ) {
                        Text("Done", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private data class ProfileEditorState(
    val profile: VueoProfile?,
)

@Composable
private fun ProfileEditorScreen(
    state: ProfileEditorState,
    profileStore: ProfileStore,
    onBack: () -> Unit,
    onSave: (String, String, Boolean, Boolean, String) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profile = state.profile
    val initialAvatar = remember(profile?.id) {
        profile?.avatar ?: PROFILE_AVATARS.first().id
    }
    var name by remember(profile?.id) { mutableStateOf(profile?.name.orEmpty()) }
    var avatar by remember(profile?.id) { mutableStateOf(initialAvatar) }
    var isKids by remember(profile?.id) { mutableStateOf(profile?.isKids ?: false) }
    val hadPin = remember(profile?.id) {
        profile?.let { profileStore.hasProfilePin(it.id) } ?: false
    }
    var pinEnabled by remember(profile?.id) { mutableStateOf(hadPin) }
    var pin by remember(profile?.id) { mutableStateOf("") }
    var photoBusy by remember { mutableStateOf(false) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                photoBusy = true
                val saved = withContext(Dispatchers.IO) {
                    saveProfilePhoto(context, uri)
                }
                photoBusy = false
                if (saved != null) {
                    if (avatar != initialAvatar) deleteLocalAvatar(context, avatar)
                    avatar = saved
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(VueoPalette.Background)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = 18.dp,
            bottom = 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        if (avatar != initialAvatar) deleteLocalAvatar(context, avatar)
                        onBack()
                    },
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Text(
                    text = if (profile == null) "Add Profile" else "Edit Profile",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 29.sp,
                )
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = VueoPalette.Surface,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ProfileAvatar(
                        profile = VueoProfile(
                            id = profile?.id ?: "preview",
                            name = name.ifBlank { "New profile" },
                            avatar = avatar,
                            isKids = isKids,
                            createdAtEpochMs = profile?.createdAtEpochMs ?: 0L,
                        ),
                        size = 82,
                    )
                    Column(
                        modifier = Modifier.weight(1f).padding(start = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = name.ifBlank { "New profile" },
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                        )
                        Text(
                            text = buildString {
                                append(if (isKids) "Kids profile" else "Standard profile")
                                if (pinEnabled) append(" • PIN")
                            },
                            color = VueoPalette.Muted,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(24) },
                singleLine = true,
                label = { Text("Profile name") },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Profile photo",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                )
                OutlinedButton(
                    enabled = !photoBusy,
                    onClick = {
                        photoPicker.launch("image/*")
                    },
                ) {
                    Text(if (photoBusy) "Preparing photo…" else "Upload Photo")
                }
                Text(
                    text = "Photos stay local on this device and are resized for profile use.",
                    color = VueoPalette.Muted,
                    fontSize = 11.sp,
                )
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Choose an avatar",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                )
                PROFILE_AVATARS.chunked(4).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        row.forEach { choice ->
                            val selected = avatar == choice.id
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clickable {
                                        if (avatar != initialAvatar) deleteLocalAvatar(context, avatar)
                                        avatar = choice.id
                                    },
                                shape = CircleShape,
                                color = VueoPalette.SurfaceStrong,
                                border = BorderStroke(
                                    if (selected) 3.dp else 1.dp,
                                    if (selected) VueoPalette.BrandLime else VueoPalette.Stroke,
                                ),
                            ) {
                                Image(
                                    painter = painterResource(choice.drawableRes),
                                    contentDescription = "Choose profile avatar",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }

        item {
            Surface(shape = RoundedCornerShape(18.dp), color = VueoPalette.Surface) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Kids Profile", color = Color.White, fontWeight = FontWeight.Bold)
                        Text(
                            "Marks this profile for age-aware controls.",
                            color = VueoPalette.Muted,
                            fontSize = 11.sp,
                        )
                    }
                    Switch(checked = isKids, onCheckedChange = { isKids = it })
                }
            }
        }

        item {
            Surface(shape = RoundedCornerShape(18.dp), color = VueoPalette.Surface) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Profile PIN", color = Color.White, fontWeight = FontWeight.Bold)
                            Text(
                                "Require a 4-digit PIN before opening this profile.",
                                color = VueoPalette.Muted,
                                fontSize = 11.sp,
                            )
                        }
                        Switch(
                            checked = pinEnabled,
                            onCheckedChange = {
                                pinEnabled = it
                                if (!it) pin = ""
                            },
                        )
                    }
                    if (pinEnabled) {
                        OutlinedTextField(
                            value = pin,
                            onValueChange = { value ->
                                if (value.length <= 4 && value.all(Char::isDigit)) pin = value
                            },
                            singleLine = true,
                            label = {
                                Text(if (hadPin) "New PIN (optional)" else "4-digit PIN")
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

        item {
            Button(
                enabled = name.trim().isNotEmpty() && (!pinEnabled || hadPin || pin.length == 4),
                onClick = {
                    onSave(name.trim(), avatar, isKids, pinEnabled, pin)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(if (profile == null) "Create Profile" else "Save Changes")
            }
        }

        if (onDelete != null) {
            item {
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text("Delete Profile")
                }
            }
        }
    }
}

@Composable
private fun ManageProfileAvatar(
    profile: VueoProfile,
    locked: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Box(modifier = Modifier.size(112.dp), contentAlignment = Alignment.Center) {
            ProfileAvatar(profile = profile, size = 104)
            Surface(
                modifier = Modifier.align(Alignment.BottomEnd).size(28.dp),
                shape = CircleShape,
                color = Color.White,
                border = BorderStroke(3.dp, VueoPalette.Background),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit ${profile.name}",
                        tint = Color.Black,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
        }
        Text(
            text = profile.name,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            maxLines = 1,
        )
        if (profile.isKids || locked) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                if (profile.isKids) ProfileStatusChip("KIDS")
                if (locked) ProfileStatusChip("PIN")
            }
        } else {
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun AddProfileAvatar(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Surface(
            modifier = Modifier.size(104.dp),
            shape = CircleShape,
            color = VueoPalette.Surface,
            border = BorderStroke(2.dp, VueoPalette.Stroke),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Add Profile",
                    tint = VueoPalette.Muted,
                    modifier = Modifier.size(38.dp),
                )
            }
        }
        Text(
            text = "Add Profile",
            color = VueoPalette.Muted,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun WatchingProfileCard(
    profile: VueoProfile,
    active: Boolean,
    locked: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Box(
            modifier = Modifier.size(112.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(106.dp)
                    .then(
                        if (active) {
                            Modifier.border(
                                width = 2.dp,
                                color = VueoPalette.BrandLime,
                                shape = CircleShape,
                            )
                        } else {
                            Modifier
                        },
                    )
                    .padding(if (active) 4.dp else 0.dp),
                contentAlignment = Alignment.Center,
            ) {
                ProfileAvatar(
                    profile = profile,
                    size = if (active) 96 else 104,
                )
            }

            if (active) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(25.dp),
                    shape = CircleShape,
                    color = VueoPalette.BrandLime,
                    border = BorderStroke(
                        3.dp,
                        VueoPalette.Background,
                    ),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            "✓",
                            color = Color.Black,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                        )
                    }
                }
            }
        }

        Text(
            text = profile.name,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            maxLines = 1,
        )

        if (profile.isKids || locked) {
            Row(
                modifier = Modifier.height(18.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (profile.isKids) {
                    ProfileStatusChip("KIDS")
                }
                if (locked) {
                    ProfileStatusChip("PIN")
                }
            }
        } else {
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun ProfileStatusChip(
    text: String,
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = VueoPalette.BrandLime.copy(alpha = .10f),
        border = BorderStroke(
            1.dp,
            VueoPalette.BrandLime.copy(alpha = .28f),
        ),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(
                horizontal = 7.dp,
                vertical = 2.dp,
            ),
            color = VueoPalette.BrandLime,
            fontSize = 8.sp,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
private fun ProfileUnlockDialog(
    profile: VueoProfile,
    onDismiss: () -> Unit,
    onUnlocked: () -> Unit,
    verifyPin: (String) -> Boolean,
) {
    var pin by remember(profile.id) {
        mutableStateOf("")
    }
    var error by remember(profile.id) {
        mutableStateOf(false)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Unlock ${profile.name}")
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "Enter the 4-digit profile PIN.",
                    color = VueoPalette.Muted,
                )
                OutlinedTextField(
                    value = pin,
                    onValueChange = { value ->
                        if (
                            value.length <= 4 &&
                            value.all(Char::isDigit)
                        ) {
                            pin = value
                            error = false
                        }
                    },
                    singleLine = true,
                    isError = error,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                    ),
                    visualTransformation = PasswordVisualTransformation(),
                    label = { Text("PIN") },
                    supportingText = {
                        if (error) {
                            Text("Incorrect PIN")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                enabled = pin.length == 4,
                onClick = {
                    if (verifyPin(pin)) {
                        onUnlocked()
                    } else {
                        pin = ""
                        error = true
                    }
                },
            ) {
                Text("Continue")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun AddProfileCard(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = VueoPalette.SurfaceElevated,
        ),
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 12.dp,
                    vertical = 20.dp,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = VueoPalette.SurfaceStrong,
            ) {
                Box(
                    modifier = Modifier.size(74.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        tint = VueoPalette.Accent,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
            Text(
                "Add Profile",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
            )
            Text(
                "Local",
                color = VueoPalette.Muted,
                fontSize = 11.sp,
            )
        }
    }
}
@Composable
private fun ManageProfileCard(
    profile: VueoProfile,
    selected: Boolean,
    canDelete: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(
                    onClick = onSelect
                ),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (selected) {
                        VueoPalette
                            .SurfaceStrong
                    } else {
                        VueoPalette.Surface
                    },
            ),
        shape =
            RoundedCornerShape(
                18.dp
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        14.dp
                    ),
            verticalAlignment =
                Alignment.CenterVertically,
        ) {
            ProfileAvatar(
                profile = profile,
                size = 46,
            )

            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(
                            horizontal =
                                12.dp
                        ),
            ) {
                Text(
                    text = profile.name,
                    color =
                        Color.White,
                    fontWeight =
                        FontWeight.SemiBold,
                )

                Text(
                    text = buildString {
                        append(
                            if (selected) {
                                "Active"
                            } else {
                                "Tap to switch"
                            }
                        )
                        if (profile.isKids) {
                            append(" • Kids")
                        }
                    },
                    color =
                        if (selected) {
                            VueoPalette.Accent
                        } else {
                            VueoPalette.Muted
                        },
                    fontSize = 12.sp,
                )
            }

            IconButton(
                onClick = onEdit,
            ) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription =
                        "Edit ${profile.name}",
                    tint =
                        Color.White,
                )
            }

            if (canDelete) {
                IconButton(
                    onClick = onDelete,
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription =
                            "Delete ${profile.name}",
                        tint =
                            VueoPalette.Muted,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileSummaryCard(
    profile: VueoProfile,
    profileCount: Int,
) {
    Card(
        colors =
            CardDefaults.cardColors(
                containerColor =
                    VueoPalette.SurfaceElevated,
            ),
        shape =
            RoundedCornerShape(
                20.dp
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        18.dp
                    ),
            verticalAlignment =
                Alignment.CenterVertically,
            horizontalArrangement =
                Arrangement.spacedBy(
                    14.dp
                ),
        ) {
            ProfileAvatar(
                profile = profile,
                size = 54,
            )

            Column {
                Text(
                    text = "Watching as ${profile.name}",
                    color =
                        Color.White,
                    fontWeight =
                        FontWeight.Bold,
                    fontSize = 17.sp,
                )
                Text(
                    text = "$profileCount local ${if (profileCount == 1) "profile" else "profiles"}",
                    color =
                        VueoPalette.Muted,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
private fun ProfileAvatar(
    profile: VueoProfile,
    size: Int,
) {
    val context = LocalContext.current
    val stockDrawable = stockAvatarDrawable(profile.avatar)
    val localBitmap = remember(profile.avatar) {
        localAvatarFile(context, profile.avatar)
            ?.takeIf { it.isFile }
            ?.let { BitmapFactory.decodeFile(it.absolutePath) }
            ?.asImageBitmap()
    }

    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(VueoPalette.SurfaceStrong),
        contentAlignment = Alignment.Center,
    ) {
        when {
            stockDrawable != null -> {
                Image(
                    painter = painterResource(stockDrawable),
                    contentDescription = "${profile.name} profile avatar",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            localBitmap != null -> {
                Image(
                    bitmap = localBitmap,
                    contentDescription = "${profile.name} profile photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            else -> {
                Text(
                    text = profile.avatar.ifBlank { "🙂" },
                    fontSize = (size / 2).sp,
                )
            }
        }
    }
}
