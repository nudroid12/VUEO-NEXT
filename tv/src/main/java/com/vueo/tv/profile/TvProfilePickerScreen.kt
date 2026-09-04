package com.vueo.tv.profile

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.zIndex
import com.vueo.shared.core.storage.ProfileStore
import com.vueo.shared.core.storage.VueoProfile
import com.vueo.tv.R
import com.vueo.tv.ui.theme.TvAccent
import kotlinx.coroutines.delay

private val PickerBlack = Color(0xFF050706)
private val PickerPanel = Color(0xFF101412)
private val PickerMuted = Color(0xFFAAB2AD)
private val PickerDanger = Color(0xFFFF8A80)

private data class TvProfileAvatar(
    val id: String,
    val drawableRes: Int,
)

private val TvProfileAvatars =
    listOf(
        TvProfileAvatar("avatar_man_1", R.drawable.avatar_man_1),
        TvProfileAvatar("avatar_man_2", R.drawable.avatar_man_2),
        TvProfileAvatar("avatar_woman_1", R.drawable.avatar_woman_1),
        TvProfileAvatar("avatar_woman_2", R.drawable.avatar_woman_2),
        TvProfileAvatar("avatar_boy_1", R.drawable.avatar_boy_1),
        TvProfileAvatar("avatar_boy_2", R.drawable.avatar_boy_2),
        TvProfileAvatar("avatar_girl_1", R.drawable.avatar_girl_1),
        TvProfileAvatar("avatar_girl_2", R.drawable.avatar_girl_2),
    )

@Composable
fun TvProfilePickerScreen(
    profileStore: ProfileStore,
    onProfileSelected: (String) -> Unit,
) {
    var revision by remember { mutableIntStateOf(0) }
    val profiles = remember(revision) { profileStore.profiles() }
    val firstRequester = remember { FocusRequester() }
    val manageRequester = remember { FocusRequester() }
    var managing by remember { mutableStateOf(false) }
    var lockedProfile by remember { mutableStateOf<VueoProfile?>(null) }
    var pinError by remember { mutableStateOf<String?>(null) }
    var pinResetToken by remember { mutableIntStateOf(0) }
    var editorOpen by remember { mutableStateOf(false) }
    var editorProfile by remember { mutableStateOf<VueoProfile?>(null) }
    var deleteTarget by remember { mutableStateOf<VueoProfile?>(null) }

    BackHandler {
        when {
            deleteTarget != null -> deleteTarget = null
            editorOpen -> editorOpen = false
            managing -> managing = false
            else -> Unit
        }
    }

    LaunchedEffect(managing, revision) {
        delay(100)
        if (managing) {
            runCatching { manageRequester.requestFocus() }
        } else {
            runCatching { firstRequester.requestFocus() }
        }
    }

    Box(
        modifier =
            Modifier
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
        contentAlignment = Alignment.Center,
    ) {
        if (managing) {
            ManageProfilesView(
                profiles = profiles,
                firstRequester = manageRequester,
                onBack = { managing = false },
                onEdit = {
                    editorProfile = it
                    editorOpen = true
                },
                onDelete = { deleteTarget = it },
                onAdd = {
                    editorProfile = null
                    editorOpen = true
                },
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Who's Watching?",
                    color = Color.White,
                    fontSize = 46.sp,
                    fontWeight = FontWeight.Black,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Choose a profile to continue",
                    color = PickerMuted,
                    fontSize = 17.sp,
                )
                Spacer(Modifier.height(40.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                    contentPadding = PaddingValues(horizontal = 28.dp),
                ) {
                    itemsIndexed(
                        profiles,
                        key = { _, profile -> profile.id },
                    ) { index, profile ->
                        PickerProfileCard(
                            profile = profile,
                            locked = profileStore.hasProfilePin(profile.id),
                            modifier = if (index == 0) Modifier.focusRequester(firstRequester) else Modifier,
                            onClick = {
                                if (profileStore.hasProfilePin(profile.id)) {
                                    pinError = null
                                    lockedProfile = profile
                                } else if (profileStore.setActiveProfile(profile.id)) {
                                    onProfileSelected(profile.id)
                                }
                            },
                        )
                    }

                    if (profiles.size < ProfileStore.MAX_PROFILES) {
                        item(key = "add-profile") {
                            AddProfileCard {
                                editorProfile = null
                                editorOpen = true
                            }
                        }
                    }
                }

                Spacer(Modifier.height(34.dp))
                TvPickerButton(
                    text = "Manage Profiles",
                    onClick = { managing = true },
                )
            }
        }

        val target = lockedProfile
        if (target != null) {
            key(target.id, pinResetToken) {
                TvPinEntryOverlay(
                    title = "Unlock ${target.name}",
                    subtitle = "Enter the 4-digit profile PIN",
                    errorText = pinError,
                    onComplete = { pin ->
                        if (profileStore.verifyProfilePin(target.id, pin)) {
                            pinError = null
                            lockedProfile = null
                            if (profileStore.setActiveProfile(target.id)) {
                                onProfileSelected(target.id)
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

        if (editorOpen) {
            TvProfileEditorOverlay(
                profile = editorProfile,
                onCancel = { editorOpen = false },
                onSave = { name, avatar, isKids ->
                    val saved =
                        if (editorProfile == null) {
                            runCatching {
                                profileStore.createProfile(name, avatar, isKids)
                            }.isSuccess
                        } else {
                            profileStore.updateProfile(editorProfile!!.id, name, avatar, isKids)
                        }
                    if (saved) {
                        editorOpen = false
                        revision += 1
                    }
                },
            )
        }

        deleteTarget?.let { targetProfile ->
            TvProfileDeleteOverlay(
                profile = targetProfile,
                onCancel = { deleteTarget = null },
                onDelete = {
                    if (profileStore.deleteProfile(targetProfile.id)) {
                        deleteTarget = null
                        revision += 1
                    }
                },
            )
        }
    }
}

@Composable
private fun ManageProfilesView(
    profiles: List<VueoProfile>,
    firstRequester: FocusRequester,
    onBack: () -> Unit,
    onEdit: (VueoProfile) -> Unit,
    onDelete: (VueoProfile) -> Unit,
    onAdd: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 72.dp, vertical = 58.dp),
    ) {
        Text("Manage Profiles", color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(6.dp))
        Text("Create, rename, choose an avatar, set Kids mode or remove a local profile.", color = PickerMuted, fontSize = 15.sp)
        Spacer(Modifier.height(28.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(profiles, key = { _, item -> item.id }) { index, profile ->
                ManageProfileRow(
                    profile = profile,
                    modifier = if (index == 0) Modifier.focusRequester(firstRequester) else Modifier,
                    canDelete = profile.id != ProfileStore.DEFAULT_PROFILE_ID,
                    onEdit = { onEdit(profile) },
                    onDelete = { onDelete(profile) },
                )
            }
            if (profiles.size < ProfileStore.MAX_PROFILES) {
                item(key = "manage-add") {
                    ManageActionRow("+  Add Profile", onAdd)
                }
            }
            item(key = "manage-back") {
                ManageActionRow("Back", onBack)
            }
        }
    }
}

@Composable
private fun ManageProfileRow(
    profile: VueoProfile,
    modifier: Modifier,
    canDelete: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.025f else 1f, label = "manageProfileScale")
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(88.dp)
                .scale(scale)
                .onFocusChanged { focused = it.isFocused }
                .clickable(onClick = onEdit)
                .focusable()
                .background(if (focused) Color.White.copy(alpha = 0.14f) else PickerPanel, RoundedCornerShape(14.dp))
                .border(if (focused) 2.dp else 1.dp, if (focused) Color.White else Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TvProfileAvatarImage(profile.avatar, 60)
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(profile.name, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(if (profile.isKids) "Kids Profile" else "Standard Profile", color = if (profile.isKids) TvAccent else PickerMuted, fontSize = 12.sp)
        }
        Text("Edit", color = if (focused) Color.White else PickerMuted, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        if (canDelete) {
            Spacer(Modifier.width(22.dp))
            TvInlineAction("Delete", PickerDanger, onDelete)
        }
    }
}

@Composable
private fun TvInlineAction(
    text: String,
    color: Color,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Text(
        text = text,
        color = if (focused) Color.White else color,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        modifier =
            Modifier
                .onFocusChanged { focused = it.isFocused }
                .clickable(onClick = onClick)
                .focusable()
                .background(if (focused) Color.White.copy(alpha = 0.15f) else Color.Transparent, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
private fun ManageActionRow(
    text: String,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .onFocusChanged { focused = it.isFocused }
                .clickable(onClick = onClick)
                .focusable()
                .background(if (focused) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.045f), RoundedCornerShape(12.dp))
                .border(if (focused) 2.dp else 1.dp, if (focused) Color.White else Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                .padding(horizontal = 18.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TvProfileEditorOverlay(
    profile: VueoProfile?,
    onCancel: () -> Unit,
    onSave: (String, String, Boolean) -> Unit,
) {
    var name by remember(profile?.id) { mutableStateOf(profile?.name.orEmpty()) }
    var avatar by remember(profile?.id) { mutableStateOf(profile?.avatar ?: TvProfileAvatars.first().id) }
    var isKids by remember(profile?.id) { mutableStateOf(profile?.isKids ?: false) }
    val nameRequester = remember { FocusRequester() }

    BackHandler(onBack = onCancel)
    LaunchedEffect(Unit) {
        delay(100)
        runCatching { nameRequester.requestFocus() }
    }

    Box(
        modifier = Modifier.fillMaxSize().zIndex(90f).background(Color.Black.copy(alpha = 0.92f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier =
                Modifier
                    .width(760.dp)
                    .background(Color(0xFF101412), RoundedCornerShape(22.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.13f), RoundedCornerShape(22.dp))
                    .padding(horizontal = 36.dp, vertical = 30.dp),
        ) {
            Text(if (profile == null) "Add Profile" else "Edit Profile", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(20.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(24) },
                singleLine = true,
                label = { Text("Profile name") },
                modifier = Modifier.fillMaxWidth().focusRequester(nameRequester),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color.White,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.18f),
                    focusedLabelColor = Color.White,
                    unfocusedLabelColor = PickerMuted,
                    cursorColor = Color.White,
                ),
            )

            Spacer(Modifier.height(20.dp))
            Text("Choose avatar", color = PickerMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                itemsIndexed(TvProfileAvatars, key = { _, item -> item.id }) { _, item ->
                    AvatarChoice(
                        avatar = item,
                        selected = avatar == item.id,
                        onClick = { avatar = item.id },
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            TvToggleActionRow(
                title = "Kids Profile",
                subtitle = "Labels this profile for age-aware controls and future Kids restrictions.",
                checked = isKids,
                onToggle = { isKids = !isKids },
            )

            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvPickerButton("Save", enabled = name.trim().isNotEmpty()) {
                    if (name.trim().isNotEmpty()) onSave(name.trim(), avatar, isKids)
                }
                TvPickerButton("Cancel", onClick = onCancel)
            }
        }
    }
}

@Composable
private fun AvatarChoice(
    avatar: TvProfileAvatar,
    selected: Boolean,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, label = "avatarChoiceScale")
    Box(
        modifier =
            Modifier
                .size(76.dp)
                .scale(scale)
                .clip(CircleShape)
                .onFocusChanged { focused = it.isFocused }
                .clickable(onClick = onClick)
                .focusable()
                .border(
                    if (focused) 3.dp else 2.dp,
                    when {
                        focused -> Color.White
                        selected -> TvAccent
                        else -> Color.White.copy(alpha = 0.12f)
                    },
                    CircleShape,
                ),
    ) {
        Image(
            painter = painterResource(avatar.drawableRes),
            contentDescription = avatar.id,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun TvToggleActionRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(72.dp)
                .onFocusChanged { focused = it.isFocused }
                .clickable(onClick = onToggle)
                .focusable()
                .background(if (focused) Color.White.copy(alpha = 0.13f) else Color.White.copy(alpha = 0.045f), RoundedCornerShape(12.dp))
                .border(if (focused) 2.dp else 1.dp, if (focused) Color.White else Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = PickerMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(
            modifier =
                Modifier
                    .width(54.dp)
                    .height(28.dp)
                    .background(if (checked) TvAccent.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.08f), RoundedCornerShape(999.dp))
                    .border(1.dp, if (checked) TvAccent.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.14f), RoundedCornerShape(999.dp))
                    .padding(4.dp),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(Modifier.size(20.dp).background(if (checked) TvAccent else PickerMuted, CircleShape))
        }
    }
}

@Composable
private fun TvProfileDeleteOverlay(
    profile: VueoProfile,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    BackHandler(onBack = onCancel)
    Box(
        modifier = Modifier.fillMaxSize().zIndex(92f).background(Color.Black.copy(alpha = 0.92f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier =
                Modifier
                    .width(560.dp)
                    .background(Color(0xFF101412), RoundedCornerShape(22.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.13f), RoundedCornerShape(22.dp))
                    .padding(34.dp),
        ) {
            Text("Delete ${profile.name}?", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(10.dp))
            Text("This also clears this profile's local Library, settings, PIN and playback history.", color = PickerMuted, fontSize = 14.sp)
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvPickerButton("Delete", danger = true, onClick = onDelete)
                TvPickerButton("Cancel", onClick = onCancel)
            }
        }
    }
}

@Composable
private fun PickerProfileCard(
    profile: VueoProfile,
    locked: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(targetValue = if (focused) 1.065f else 1f, label = "pickerProfileScale")

    Column(
        modifier =
            modifier
                .width(188.dp)
                .scale(scale)
                .onFocusChanged { focused = it.isFocused }
                .clickable(onClick = onClick)
                .focusable(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier =
                Modifier
                    .size(140.dp)
                    .background(if (focused) Color.White.copy(alpha = 0.12f) else PickerPanel, CircleShape)
                    .border(if (focused) 3.dp else 1.dp, if (focused) Color.White else Color.White.copy(alpha = 0.13f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            TvProfileAvatarImage(profile.avatar, 138)
            if (locked) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 8.dp)
                            .background(PickerBlack.copy(alpha = 0.92f), RoundedCornerShape(8.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.26f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 9.dp, vertical = 4.dp),
                ) {
                    Text("PIN", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black)
                }
            }
        }

        Spacer(Modifier.height(13.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = profile.name,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = if (focused) FontWeight.Black else FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (profile.isKids) {
                Text("  KIDS", color = TvAccent, fontSize = 10.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun TvProfileAvatarImage(
    avatarId: String,
    size: Int,
) {
    val item = TvProfileAvatars.firstOrNull { it.id == avatarId }
    Box(
        modifier = Modifier.size(size.dp).clip(CircleShape).background(PickerPanel),
        contentAlignment = Alignment.Center,
    ) {
        if (item != null) {
            Image(
                painter = painterResource(item.drawableRes),
                contentDescription = avatarId,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(avatarId.ifBlank { "🙂" }, color = Color.White, fontSize = (size / 2).sp)
        }
    }
}

@Composable
private fun AddProfileCard(onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.065f else 1f, label = "addProfileScale")
    Column(
        modifier =
            Modifier
                .width(188.dp)
                .scale(scale)
                .onFocusChanged { focused = it.isFocused }
                .clickable(onClick = onClick)
                .focusable(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier =
                Modifier
                    .size(140.dp)
                    .background(if (focused) Color.White.copy(alpha = 0.12f) else PickerPanel, CircleShape)
                    .border(if (focused) 3.dp else 1.dp, if (focused) Color.White else Color.White.copy(alpha = 0.13f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("+", color = TvAccent, fontSize = 52.sp, fontWeight = FontWeight.Light)
        }
        Spacer(Modifier.height(13.dp))
        Text("Add Profile", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TvPickerButton(
    text: String,
    enabled: Boolean = true,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused && enabled) 1.045f else 1f, label = "pickerButtonScale")
    Box(
        modifier =
            Modifier
                .scale(scale)
                .onFocusChanged { focused = it.isFocused }
                .clickable(enabled = enabled, onClick = onClick)
                .focusable(enabled)
                .background(
                    when {
                        !enabled -> Color.White.copy(alpha = 0.035f)
                        focused -> Color.White
                        else -> Color.White.copy(alpha = 0.08f)
                    },
                    RoundedCornerShape(11.dp),
                )
                .border(if (focused) 2.dp else 1.dp, if (focused) Color.White else Color.White.copy(alpha = 0.10f), RoundedCornerShape(11.dp))
                .padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
        Text(
            text,
            color = when {
                !enabled -> PickerMuted.copy(alpha = 0.45f)
                focused -> Color.Black
                danger -> PickerDanger
                else -> Color.White
            },
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
