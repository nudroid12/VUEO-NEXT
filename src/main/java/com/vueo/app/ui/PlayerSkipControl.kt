package com.vueo.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vueo.app.core.player.PlayerSkipKind
import com.vueo.app.core.player.PlayerSkipSegment

@Composable
internal fun PlayerSkipControl(
    segment: PlayerSkipSegment?,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = segment != null,
        modifier = modifier,
        enter = fadeIn() + scaleIn(initialScale = .94f),
        exit = fadeOut() + scaleOut(targetScale = .94f),
    ) {
        val label = when (segment?.kind) {
            PlayerSkipKind.INTRO -> "Skip Intro"
            PlayerSkipKind.RECAP -> "Skip Recap"
            PlayerSkipKind.ENDING -> "Skip Ending"
            null -> "Skip"
        }
        Row(
            modifier = Modifier
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = .22f),
                    shape = RoundedCornerShape(50),
                )
                .background(
                    color = Color(0xE6161719),
                    shape = RoundedCornerShape(50),
                )
                .clickable(onClick = onSkip)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Icon(
                imageVector = Icons.Default.SkipNext,
                contentDescription = null,
                tint = Color(0xFFB9FF3A),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
