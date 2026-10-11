package com.vueo.tv.ui.trakt

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vueo.shared.core.media.MediaItem
import com.vueo.shared.core.trakt.TraktService

@Composable
internal fun TraktRatingDialog(media: MediaItem, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val service = remember { TraktService.get(context) }
    val profile = service.profileId()
    var rating by remember(media.id, profile) { mutableIntStateOf(service.rating(media, profile).takeIf { it > 0 } ?: 5) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Rate ${media.name} on Trakt") }, text = {
        Column {
            if (!service.connected(profile)) Text("Connect Trakt in Settings → Enhancements first.")
            else Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TextButton(enabled = rating > 1, onClick = { rating-- }) { Text("−") }
                Text("$rating / 10", Modifier.padding(top = 12.dp))
                TextButton(enabled = rating < 10, onClick = { rating++ }) { Text("+") }
            }
        }
    }, confirmButton = { TextButton(enabled = service.connected(profile), onClick = { service.rate(media, rating, profile); onDismiss() }) { Text("Save") } },
        dismissButton = { Row {
            if (service.connected(profile)) TextButton(onClick = { service.rate(media, 0, profile); onDismiss() }) { Text("Remove rating") }
            TextButton(onClick = onDismiss) { Text("Cancel") }
        } })
}
