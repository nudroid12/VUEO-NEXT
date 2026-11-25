package com.vueo.shared.core.storage

import android.content.Context
import com.vueo.shared.core.media.EpisodeItem
import com.vueo.shared.core.media.MediaItem

fun main() {
    val context = Context()
    val library = LibraryStore(context)
    val playback = PlaybackStore(context)
    val episodes = (1..180).map { EpisodeItem("ttlong:1:$it", "Episode $it", 1, it) }
    val special = EpisodeItem("ttlong:0:1", "Special", 0, 1)
    val future = EpisodeItem("ttlong:1:181", "Future", 1, 181, released = "2099-01-01")
    val show = MediaItem("ttlong", "series", "Long show", episodes = listOf(special) + episodes + future)
    val other = MediaItem("ttlong2", "series", "Other", episodes = episodes)
    fun watched(ep:EpisodeItem) = WatchedEpisodePolicy.contains(library.watchedEpisodeKeys(), show, ep)
    fun record(ep:EpisodeItem, position:Long=120_000, stamp:Long=PlaybackUpdateClock.next(), videoId:String=ep.id, media:MediaItem=show) =
        library.recordPlayback(media, videoId, ep.title, ep.season, ep.episode, position, 120_000, stamp)
    fun verify(name:String, condition:Boolean) { check(condition) { name }; println("PASS $name") }
    record(special)
    episodes.take(170).forEach { record(it) }
    verify("recent history remains bounded",library.history().size == 150)
    verify("old episodes and Specials survive pruning",watched(episodes.first()) && watched(special) && episodes.take(170).all(::watched))
    verify("new instance loads persistent watched status",LibraryStore(context).watchedEpisodeKeys() == library.watchedEpisodeKeys())
    verify("next episode still correct",library.continueWatching().single().episode == 171)
    record(episodes.first(), 30_000, videoId="alternate-provider-id")
    verify("rewatch does not lose completed status", watched(episodes.first()))
    verify("provider ID not part of watched identity",watched(episodes.first()))
    val stale = PlaybackUpdateClock.next()
    playback.savePositionMs("series:ttlong:${episodes.first().id}",30_000,120_000)
    playback.savePositionMs("series:ttlong:alternate-provider-id",30_000,120_000)
    library.setEpisodesWatched(show, listOf(episodes.first()), false)
    verify("single episode unwatched, other episodes unchanged", !watched(episodes.first()) && watched(episodes[1]))
    verify("all known episode resume IDs cleared",playback.positionMs("series:ttlong:${episodes.first().id}")==0L && playback.positionMs("series:ttlong:alternate-provider-id")==0L)
    record(episodes.first(),stamp=stale)
    verify("stale completed save cannot undo unwatch",!watched(episodes.first()))
    record(episodes.first())
    verify("new playback can mark watched again",watched(episodes.first()))
    val beforeManual = library.history().size
    library.setEpisodesWatched(show, episodes+future, true)
    verify("season marking includes available episodes but not future",episodes.all(::watched) && !watched(future))
    verify("manual mark creates no fake history",library.history().size == beforeManual)
    verify("continue watching skips already watched episodes",library.continueWatching().none { it.media.id==show.id })
    library.setEpisodesWatched(show, episodes, false)
    verify("season reset leaves specials intact",episodes.none(::watched) && watched(special))
    record(episodes.first(),media=other)
    context.activeProfile="second"
    verify("watched status isolated by profile",!watched(special))
    record(special)
    context.activeProfile="default"
    library.toggleWatchlist(show)
    library.resetTitleHistory(show)
    verify("delete title clears durable markers only for that title",!watched(special) && WatchedEpisodePolicy.contains(library.watchedEpisodeKeys(),other,episodes.first()) && library.isWatchlisted(show))
    context.activeProfile="second"
    verify("title reset preserves another profile",watched(special))
    context.activeProfile="default"
    playback.savePositionMs("series:ttlong2:old-resume", 30_000, 120_000)
    context.activeProfile="second"
    playback.savePositionMs("series:ttlong:second-resume", 30_000, 120_000)
    context.activeProfile="default"
    library.clearHistory()
    verify("clear history also clears old resume cursors",playback.positionMs("series:ttlong2:old-resume")==0L)
    context.activeProfile="second"
    verify("clear history retains other profile resume",playback.positionMs("series:ttlong:second-resume")==30_000L)
    context.activeProfile="default"
    verify("clear history clears durable status, not favorites",library.watchedEpisodeKeys().isEmpty() && library.isWatchlisted(show))
    val legacy = EpisodeItem("legacy:0:1","Legacy",0,1)
    val legacyShow = MediaItem("legacy","series","Legacy",episodes=listOf(legacy))
    record(legacy,media=legacyShow)
    context.getSharedPreferences("vueo_library",0).edit().remove("watched_episodes_v1").apply()
    verify("migration from existing completed history",WatchedEpisodePolicy.contains(library.watchedEpisodeKeys(),legacyShow,legacy))
    val s2 = legacy.copy(id="legacy:0:2",episode=2)
    val sOnly = legacyShow.copy(episodes=listOf(legacy,s2))
    val completed = LibraryPlaybackEntry(sOnly,legacy.id,season=0,episode=1,positionMs=120_000,durationMs=120_000)
    verify("specials-only title can advance to next special",ContinueWatchingPolicy.resolve(listOf(completed)).single().episode==2)
    val keys = setOf(WatchedEpisodePolicy.key(show,1,1), WatchedEpisodePolicy.key(show,1,2))
    verify("manual-only watched status chooses first unwatched episode",ContinueWatchingPolicy.targetEpisode(show,emptyList(),null,keys)?.episode == 3)
}
