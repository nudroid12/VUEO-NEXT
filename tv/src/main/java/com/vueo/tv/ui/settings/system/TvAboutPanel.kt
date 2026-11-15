package com.vueo.tv.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.vueo.tv.BuildConfig
import com.vueo.tv.R
import com.vueo.tv.ui.TvDesign

@Composable
internal fun TvAboutPanel(entries: List<TvSettingsEntry>, host: TvSettingsEmbeddedHost) {
    val rowIds = entries.map { it.id }
    LaunchedEffect(host.panelKey, rowIds) {
        host.onFocusableRowsChanged(rowIds)
        host.restorePanelFocus()
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("About VUEO", color = TvDesign.White, fontSize = 29.sp, fontWeight = FontWeight.Bold)
        Text(
            "App information, updates, and legal links",
            color = TvDesign.Muted,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 5.dp, bottom = 18.dp),
        )
        val shape = RoundedCornerShape(18.dp)
        Column(
            modifier = Modifier.fillMaxWidth()
                .background(TvDesign.Surface, shape)
                .border(1.dp, TvDesign.White.copy(alpha = .18f), shape)
                .padding(horizontal = 17.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Image(
                        painterResource(R.drawable.vueo_tv_logo),
                        contentDescription = null,
                        modifier = Modifier.size(72.dp, 54.dp),
                    )
                    Text("VUEO", color = TvDesign.White, fontSize = 33.sp, fontWeight = FontWeight.Bold)
                }
                Text("Version ${BuildConfig.VERSION_NAME}", color = TvDesign.Muted, fontSize = 12.sp)
            }
            entries.forEachIndexed { index, entry ->
                TvSettingsRow(
                    entry = entry.copy(value = entry.value.ifBlank { "›" }),
                    requester = host.requesterFor(entry.id),
                    first = index == 0,
                    last = index == entries.lastIndex,
                    onLeftToSidebar = host.onLeftToCategory,
                    onFocused = { host.onRowFocused(entry.id) },
                    aboutStyle = true,
                )
            }
        }
    }
}

internal enum class TvAboutDetails { PRIVACY, LICENSES }

@Composable
internal fun TvAboutDetailsDialog(page: TvAboutDetails, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var showLicense by remember(page) { mutableStateOf(false) }
    val heading = when {
        showLicense -> "GNU General Public License v3.0"
        page == TvAboutDetails.PRIVACY -> "Privacy"
        else -> "Licenses & Attribution"
    }
    val paragraphs = when {
        showLicense -> remember(context) {
            context.resources.openRawResource(R.raw.vueo_gpl_3_0).bufferedReader().use { it.readText() }
                .trim().split(Regex("\\r?\\n\\s*\\r?\\n"))
        }
        page == TvAboutDetails.PRIVACY -> listOf(
            "On this device\nVUEO stores app preferences and library activity on this device.",
            "VUEO Sync\nWhen you use Sync, data for enabled categories is sent to the VUEO Sync service. This can include profiles, favorites, playback progress, watched history, addon and provider setup, API keys, and Trakt account data.",
            "Connected services\nAddons, providers, and integrations receive requests when you use their features.",
            "Backups\nThe backup screen lets you choose whether to include credentials in an exported backup.",
        )
        else -> listOf(
            "Nuvio and Nuvio Addon\nVUEO includes work credited to Nuvio under the GNU General Public License v3.0. Both license files in the VUEO repository contain the same GPL-3.0 text.",
            "TMDB\nThis product uses the TMDB API but is not endorsed or certified by TMDB.",
        )
    }

    androidx.compose.runtime.key(heading) {
        val scroll = rememberScrollState()
        val close = remember { FocusRequester() }
        AlertDialog(
            onDismissRequest = { if (showLicense) showLicense = false else onDismiss() },
            modifier = Modifier.fillMaxWidth(.64f).onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || scroll.maxValue == 0) {
                    return@onPreviewKeyEvent false
                }
                when (event.key) {
                    Key.DirectionUp -> { scroll.dispatchRawDelta(-96f); true }
                    Key.DirectionDown -> { scroll.dispatchRawDelta(96f); true }
                    else -> false
                }
            },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            containerColor = TvDesign.SurfaceRaised,
            titleContentColor = TvDesign.White,
            title = { Text(heading) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp).verticalScroll(scroll),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    paragraphs.forEach { paragraph ->
                        Text(paragraph, color = TvDesign.White, fontSize = 14.sp, lineHeight = 20.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { if (showLicense) showLicense = false else onDismiss() },
                    modifier = Modifier.focusRequester(close),
                ) { Text(if (showLicense) "Back" else "Close", color = TvDesign.White) }
                LaunchedEffect(Unit) { close.requestFocus() }
            },
            dismissButton = if (page == TvAboutDetails.LICENSES && !showLicense) {
                { TextButton(onClick = { showLicense = true }) { Text("View GPL-3.0", color = TvDesign.White) } }
            } else null,
        )
    }
}
