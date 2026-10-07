package com.vueo.mobile.ui

import com.vueo.shared.core.media.EpisodeItem
import com.vueo.shared.core.media.StreamSource
import com.vueo.shared.core.media.SubtitleTrack
import com.vueo.shared.core.player.NextEpisodeSourcePolicy
import com.vueo.shared.core.source.SourceDiscoveryBundle
import com.vueo.shared.core.source.SourceDiscoveryControl
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Mutex

/**
 * One filtered next-episode source scan plus its independent subtitle scan.
 *
 * Source discovery can be stopped as soon as the same server is found while
 * subtitle discovery is allowed to finish in the background. The result is
 * only reusable by the playback session/server that created it.
 */
internal class MobileEpisodePrefetch(
    val mediaKey: String,
    val originVideoId: String,
    val target: EpisodeItem,
    val preferredSource: StreamSource,
    val seedSubtitles: List<SubtitleTrack> = emptyList(),
) {
    val control = SourceDiscoveryControl()
    val sourcesReady = CompletableDeferred<Unit>()
    val validationMutex = Mutex()
    var job: Job? = null
    var bundle: SourceDiscoveryBundle? = null
    var matchedSource: StreamSource? = null
    var matchedAtMs: Long = 0L
    var subtitlesResolved: Boolean = false
    var failed: Boolean = false
    var claimed: Boolean = false
    var onSubtitles: (() -> Unit)? = null

    fun reusable(
        media: String,
        origin: String,
        episode: EpisodeItem,
        source: StreamSource?,
        nowMs: Long,
    ): Boolean =
        !claimed &&
            !failed &&
            mediaKey == media &&
            originVideoId == origin &&
            target.id == episode.id &&
            source != null &&
            NextEpisodeSourcePolicy.sameServer(preferredSource, source) &&
            (matchedAtMs == 0L || nowMs - matchedAtMs < 600_000L)

    fun cancel() {
        onSubtitles = null
        job?.cancel()
        sourcesReady.cancel()
    }
}
