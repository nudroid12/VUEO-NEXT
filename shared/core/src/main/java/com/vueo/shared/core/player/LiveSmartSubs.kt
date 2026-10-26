package com.vueo.shared.core.player

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.text.Cue
import androidx.media3.extractor.text.SubtitleParser
import androidx.media3.extractor.text.webvtt.WebvttParser
import com.vueo.shared.core.media.SubtitleTrack
import java.util.IdentityHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Generated WebVTT is a text overlay, never a child of the playing video source.
 * All public operations and registry access run on the player's application thread.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class LiveSmartSubs(
    private val player: Player,
    private val platform: String,
    private val parsingDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val delayMs: () -> Long,
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var tracks = emptyList<SubtitleTrack>()
    private var selectedId: String? = null
    private var pendingId: String? = null
    private var generation = 0L
    private var loadJob: Job? = null
    private var renderJob: Job? = null
    private var timeline = SmartSubsCueTimeline<Cue>(emptyList())
    private var lastCues = emptyList<Cue>()
    private val trackListeners = linkedSetOf<(Boolean) -> Unit>()
    private val cueListeners = linkedSetOf<(List<Cue>) -> Unit>()
    private var closed = false
    private var cuesReported = false

    init {
        sessions[player] = this

    }

    fun update(subtitles: List<SubtitleTrack>) {
        val incoming = subtitles.filter(::isSmart).distinctBy(PlayerTrackPolicy::externalSubtitleSelectionId)
        if (incoming == tracks) return
        val old = tracks.firstOrNull { PlayerTrackPolicy.externalSubtitleSelectionId(it) == (selectedId ?: pendingId) }
        tracks = incoming
        val replacement = tracks.firstOrNull { PlayerTrackPolicy.externalSubtitleSelectionId(it) == (selectedId ?: pendingId) }
        if (old != null && (replacement == null || replacement.url != old.url)) clear()
        tracks.forEach { SubtitleSessionCache.register(it.url, it.headers, it.textTransform) }
        notifyTracks(choicesChanged = true)
        SubtitleFlowDiagnostics.event(platform, player.currentMediaItem?.localConfiguration?.uri?.toString(),
            "SMARTSUBS_OVERLAY_UPDATED", "tracks=${tracks.size} videoReset=false")
    }

    fun addTrackListener(listener: (Boolean) -> Unit) { trackListeners.add(listener) }
    fun removeTrackListener(listener: (Boolean) -> Unit) { trackListeners.remove(listener) }
    fun addCueListener(listener: (List<Cue>) -> Unit) { cueListeners.add(listener); listener(currentCues()) }
    fun removeCueListener(listener: (List<Cue>) -> Unit) { cueListeners.remove(listener) }

    private fun notifyTracks(choicesChanged: Boolean = false) { trackListeners.toList().forEach { it(choicesChanged) } }
    private fun emitCues(cues: List<Cue>) {
        if (cues == lastCues) return
        lastCues = cues
        if (selectedId != null && cues.isNotEmpty() && !cuesReported) {
            cuesReported = true
            SubtitleFlowDiagnostics.event(platform, player.currentMediaItem?.localConfiguration?.uri?.toString(),
                "SMARTSUBS_OVERLAY_CUES", "count=${cues.size} videoReset=false")
        }
        cueListeners.toList().forEach { it(cues) }
    }
    private fun currentCues(): List<Cue> = if (selectedId == null) player.currentCues.cues else
        timeline.at((player.currentPosition - delayMs()) * 1_000L)
    private fun render() { if (selectedId != null) emitCues(currentCues()) }

    private fun groups(): List<Tracks.Group> = tracks.map { subtitle ->
        val id = PlayerTrackPolicy.externalSubtitleSelectionId(subtitle)
        val format = Format.Builder().setId(id).setLabel(PlayerTrackPolicy.externalSubtitleLabel(subtitle))
            .setLanguage(subtitle.language).setSampleMimeType(MimeTypes.TEXT_VTT).build()
        Tracks.Group(TrackGroup(id, format), false, intArrayOf(C.FORMAT_HANDLED), booleanArrayOf(id == selectedId))
    }

    private fun select(override: TrackSelectionOverride): Boolean {
        val id = override.mediaTrackGroup.getFormat(0).id
        val track = tracks.firstOrNull { PlayerTrackPolicy.externalSubtitleSelectionId(it) == id } ?: return false
        if (selectedId == id || pendingId == id) return true
        clear()
        pendingId = id
        val requestGeneration = ++generation
        // Text selection may change; video/audio parameters and playback cursor do not.
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT).setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
        loadJob = scope.launch {
            try {
                check(SubtitleReadinessProbe.awaitReady(track.url)) { "Generated captions are not ready" }
                if (SubtitleSessionCache.get(track.url) == null) {
                    check(SubtitleReadinessProbe.awaitReady(track.url, forceNetwork = true)) { "Prepared captions expired" }
                }
                val bytes = requireNotNull(SubtitleSessionCache.get(track.url)) { "Prepared caption bytes unavailable" }
                val parsed = withContext(parsingDispatcher) {
                    val spans = mutableListOf<SmartSubsCueTimeline.Span<Cue>>()
                    WebvttParser().parse(bytes, 0, bytes.size, SubtitleParser.OutputOptions.allCues()) { output ->
                        check(spans.size < 50_000) { "Too many generated caption intervals" }
                        spans.add(SmartSubsCueTimeline.Span(output.startTimeUs, output.endTimeUs, output.cues.toList()))
                    }
                    SmartSubsCueTimeline(spans)
                }
                if (closed || generation != requestGeneration || pendingId != id) return@launch
                cuesReported = false
                timeline = parsed
                pendingId = null
                selectedId = id
                render()
                renderJob = scope.launch {
                    while (isActive && selectedId == id) {
                        render()
                        delay(100L)
                    }
                }
                notifyTracks()
                SubtitleFlowDiagnostics.event(platform, player.currentMediaItem?.localConfiguration?.uri?.toString(),
                    "SMARTSUBS_OVERLAY_SELECTED", "videoReset=false")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (generation == requestGeneration) {
                    pendingId = null
                    notifyTracks()
                    SubtitleFlowDiagnostics.event(platform, player.currentMediaItem?.localConfiguration?.uri?.toString(),
                        "SMARTSUBS_OVERLAY_FAILED", "videoReset=false")
                }
            }
        }
        return true
    }

    private fun clear() {
        generation++
        loadJob?.cancel()
        loadJob = null
        renderJob?.cancel()
        renderJob = null
        val changed = selectedId != null || pendingId != null
        selectedId = null
        pendingId = null
        timeline = SmartSubsCueTimeline(emptyList())
        // Off/native track selection restores the main player's current cues.
        emitCues(player.currentCues.cues)
        if (changed) notifyTracks()
    }

    override fun close() {
        if (closed) return
        closed = true
        scope.cancel()
        if (sessions[player] === this) sessions.remove(player)
        cueListeners.toList().forEach { it(emptyList()) }
        cueListeners.clear()
        trackListeners.clear()
        tracks = emptyList()
        timeline = SmartSubsCueTimeline(emptyList())
    }

    companion object {
        private val sessions = IdentityHashMap<Player, LiveSmartSubs>()
        fun isSmart(track: SubtitleTrack): Boolean = track.providerId == "vueo-smartsubs-ai"
        fun videoSubtitles(tracks: List<SubtitleTrack>): List<SubtitleTrack> = tracks.filterNot(::isSmart)
        fun get(player: Player): LiveSmartSubs? = sessions[player]
        fun mergedTracks(player: Player, videoTracks: Tracks = player.currentTracks): Tracks =
            sessions[player]?.let { Tracks(videoTracks.groups.toList() + it.groups()) } ?: videoTracks
        fun select(player: Player, override: TrackSelectionOverride): Boolean = sessions[player]?.select(override) == true
        fun clear(player: Player) { sessions[player]?.clear() }
        fun ownsCues(player: Player): Boolean = sessions[player]?.selectedId != null
        fun cues(player: Player): List<Cue> = sessions[player]?.currentCues() ?: player.currentCues.cues
    }
}
