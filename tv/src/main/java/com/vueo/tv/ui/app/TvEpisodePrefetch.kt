package com.vueo.tv

import com.vueo.shared.core.media.EpisodeItem
import com.vueo.shared.core.media.StreamSource
import com.vueo.shared.core.player.NextEpisodeSourcePolicy
import com.vueo.shared.core.source.SourceDiscoveryControl
import com.vueo.tv.core.TvSourceBundle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job

/** One source scan and one independent subtitle scan for the next episode. */
internal class TvEpisodePrefetch(
    val mediaKey: String,
    val originSession: Int,
    val target: EpisodeItem,
    val preferredSource: StreamSource,
) {
    val control = SourceDiscoveryControl()
    val sourcesReady = CompletableDeferred<Unit>()
    var job: Job? = null
    var bundle: TvSourceBundle? = null
    var matchedSource: StreamSource? = null
    var matchedAtMs: Long = 0L
    var subtitlesResolved = false
    var failed = false
    var claimed = false
    var onSubtitles: (() -> Unit)? = null

    fun reusable(media: String, session: Int, episode: EpisodeItem,
        source: StreamSource?, nowMs: Long): Boolean =
        !claimed && !failed && mediaKey == media && originSession == session &&
            target.id == episode.id && source != null &&
            NextEpisodeSourcePolicy.sameServer(preferredSource, source) &&
            (matchedAtMs == 0L || nowMs - matchedAtMs < 600_000L)

    fun cancel() {
        onSubtitles = null
        job?.cancel()
        sourcesReady.cancel()
    }
}
