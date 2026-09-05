package com.vueo.tv.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vueo.shared.core.storage.PreferredQuality
import com.vueo.tv.core.TvRuntime
import com.vueo.tv.ui.TvDesign
import com.vueo.tv.ui.TvPrimaryDestinations
import com.vueo.tv.ui.TvTopBar

@Composable
fun TvSettingsScreen(
    runtime: TvRuntime,
    onNavigate: (String) -> Unit,
    onProfile: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)

    var askOnStartup by remember { mutableStateOf(runtime.profileStore.askWhoIsWatchingOnStartup()) }
    var quality by remember { mutableStateOf(runtime.settingsStore.preferredQuality()) }
    var subtitles by remember { mutableStateOf(runtime.settingsStore.subtitlesOnByDefault()) }

    val navRequesters = remember { TvPrimaryDestinations.associateWith { FocusRequester() } }
    val profileRequester = remember { FocusRequester() }

    Box(Modifier.fillMaxSize().background(TvDesign.Black)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 52.dp, end = 52.dp, top = 112.dp, bottom = 42.dp),
        ) {
            Text(
                text = "Settings",
                color = TvDesign.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Simple TV controls backed by the same stores used on Mobile.",
                color = TvDesign.Muted,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 6.dp, bottom = 24.dp),
            )

            Column(
                modifier = Modifier.fillMaxWidth(.68f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SettingRow(
                    title = "Profile",
                    value = runtime.profileStore.activeProfile().name,
                    onClick = onProfile,
                )
                SettingRow(
                    title = "Ask who’s watching on startup",
                    value = if (askOnStartup) "On" else "Off",
                    onClick = {
                        askOnStartup = !askOnStartup
                        runtime.profileStore.setAskWhoIsWatchingOnStartup(askOnStartup)
                    },
                )
                SettingRow(
                    title = "Preferred source quality",
                    value = quality.label,
                    onClick = {
                        val values = PreferredQuality.entries
                        quality = values[(values.indexOf(quality) + 1) % values.size]
                        runtime.settingsStore.setPreferredQuality(quality)
                    },
                )
                SettingRow(
                    title = "Subtitles by default",
                    value = if (subtitles) "On" else "Off",
                    onClick = {
                        subtitles = !subtitles
                        runtime.settingsStore.setSubtitlesOnByDefault(subtitles)
                    },
                )
            }
        }

        TvTopBar(
            selected = "Settings",
            expanded = true,
            navRequesters = navRequesters,
            profileRequester = profileRequester,
            onFocused = {},
            onNavigate = onNavigate,
            onProfile = onProfile,
            onDownFromNav = { false },
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

@Composable
private fun SettingRow(
    title: String,
    value: String,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (focused) TvDesign.White.copy(alpha = .11f) else TvDesign.Surface.copy(alpha = .72f),
                shape = RoundedCornerShape(12.dp),
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) TvDesign.White.copy(alpha = .90f) else TvDesign.White.copy(alpha = .08f),
                shape = RoundedCornerShape(12.dp),
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = TvDesign.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = value,
            color = if (focused) TvDesign.White else TvDesign.Muted,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
