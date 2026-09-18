package com.vueo.tv.player

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vueo.shared.core.media.EpisodeItem
import com.vueo.shared.core.storage.PlayerVideoFit
import com.vueo.tv.ui.TvDesign
import com.vueo.tv.ui.TvNetworkImage

@Composable
internal fun VueoPlayerAudioWorkspace(
    tracks: List<TvPlayerTrackChoice>,
    automaticSelected: Boolean,
    activeSourceLabel: String?,
    onInteraction: () -> Unit,
    onAutomatic: () -> Unit,
    onSelect: (TvPlayerTrackChoice) -> Unit,
) {
    val options = remember(tracks, automaticSelected, activeSourceLabel) {
        buildList {
            add(
                TvPlayerOption(
                    key = TV_AUDIO_AUTO,
                    title = "Stream default",
                    meta = activeSourceLabel?.takeIf { it.isNotBlank() } ?: "Select audio automatically",
                    selected = automaticSelected,
                )
            )
            tracks.forEach { track ->
                add(
                    TvPlayerOption(
                        key = track.selectionId,
                        title = track.label,
                        meta = listOfNotNull(
                            track.metadata?.takeIf { it.isNotBlank() },
                            track.sourceLabel.takeIf { it.isNotBlank() },
                        ).distinct().joinToString(" • "),
                        selected = !automaticSelected && track.selected,
                    )
                )
            }
        }
    }
    val cardBackground = Color(0xFF17191C).copy(alpha = .92f)
    val cardBorder = Color.White.copy(alpha = .065f)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = .20f))
            .background(
                Brush.horizontalGradient(
                    0f to Color.Black.copy(alpha = .60f),
                    .38f to Color.Black.copy(alpha = .30f),
                    .72f to Color.Black.copy(alpha = .15f),
                    1f to Color.Black.copy(alpha = .08f),
                )
            )
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(520.dp)
                .padding(start = 20.dp, top = 24.dp, end = 28.dp, bottom = 48.dp),
        ) {
            Text(
                "Audio",
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (tracks.isEmpty()) "No selectable alternate audio tracks" else "Choose an exact audio track",
                color = Color.White.copy(alpha = .56f),
                fontSize = 11.sp,
            )
            Spacer(Modifier.height(14.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(PanelShape)
                    .background(cardBackground)
                    .border(1.dp, cardBorder, PanelShape)
                    .padding(horizontal = 14.dp, vertical = 14.dp),
            ) {
                VueoOptionList(
                    options = options,
                    maxHeightFraction = 1f,
                    onInteraction = onInteraction,
                    onSelected = { option ->
                        if (option.key == TV_AUDIO_AUTO) onAutomatic()
                        else tracks.firstOrNull { it.selectionId == option.key }?.let(onSelect)
                    },
                )
            }
        }
    }
}

