package com.vueo.shared.core.storage
import android.content.Context
import com.vueo.shared.core.media.MediaItem
import com.vueo.shared.core.trakt.TraktService
fun main(){
 var count=0
 fun verify(name:String,ok:Boolean){check(ok){name};count++;println("PASS $name")}
 val context=Context();val library=LibraryStore(context);val playback=PlaybackStore(context)
 val show=MediaItem("tt42","series","Show");val other=MediaItem("tt420","series","Other")
 fun key(m:MediaItem,ep:String)="${m.type}:${m.id}:$ep"
 fun record(m:MediaItem,ep:String,stamp:Long=PlaybackUpdateClock.next())=
  library.recordPlayback(m,ep,ep,1,1,30_000,120_000,stamp)
 library.toggleWatchlist(show);record(show,"one");record(show,"two");record(other,"one")
 playback.savePositionMs(key(show,"one"),30_000,120_000)
 playback.savePositionMs(key(show,"outside-history"),40_000,120_000)
 playback.capturePosition(key(show,"memory-only"),50_000,120_000)
 playback.savePositionMs(key(other,"one"),60_000,120_000)
 val stale=playback.capturePosition(key(show,"queued"),45_000,120_000)
 val oldStamp=PlaybackUpdateClock.next()
 val remoteOld=LibraryPlaybackEntry(show,"remote",positionMs=120_000,durationMs=120_000,lastWatchedEpochMs=oldStamp)
 context.activeProfile="second";val second=LibraryStore(context);record(show,"second")
 playback.savePositionMs(key(show,"one"),70_000,120_000)
 context.activeProfile="default"
 library.removeFromContinueWatching(library.history().first{it.media.id==show.id})
 library.setMarkedWatched(show,true)
 TraktService.service.changes.clear()
 library.resetTitleHistory(show)
 verify("all episode history removed",library.history().none{it.media.id==show.id})
 verify("continue watching removed",library.continueWatchingPlaybackEntries().none{it.media.id==show.id})
 verify("home startup cursor cache cleared",library.homeStartupContinueWatching().none{it.media.id==show.id})
 verify("marked watched reset",!library.isMarkedWatched(show))
 verify("dismissed continue watching marker cleared",!context.getSharedPreferences("vueo_library",0).getStringSet("dismissed_continue_watching",emptySet()).orEmpty().contains("series:tt42"))
 verify("Favorites retained",library.isWatchlisted(show))
 verify("other title retained",library.history().any{it.media.id==other.id} && playback.positionMs(key(other,"one"))==60_000L)
 verify("episode resume cleared",playback.positionMs(key(show,"one"))==0L)
 verify("cursor outside bounded history cleared",playback.positionMs(key(show,"outside-history"))==0L)
 verify("memory-only cursor cleared",playback.positionMs(key(show,"memory-only"))==0L)
 playback.persistSnapshot(stale)
 verify("queued old resume save cannot resurrect",playback.positionMs(key(show,"queued"))==0L)
 record(show,"queued-library",oldStamp)
 verify("queued old history save cannot resurrect",library.history().none{it.media.id==show.id})
 library.mergeTraktHistory(listOf(remoteOld))
 verify("old Trakt show history cannot reappear",library.history().none{it.media.id==show.id})
 val movie=MediaItem("ttm","movie","Movie")
 record(movie,"movie");library.resetTitleHistory(movie)
 library.mergeTraktHistory(listOf(LibraryPlaybackEntry(movie,"movie",lastWatchedEpochMs=oldStamp)))
 verify("old Trakt movie watched marker stays cleared",!library.isMarkedWatched(movie))
 verify("Trakt removal emitted for selected profile",TraktService.service.changes.any{it.media.id==show.id && !it.watched && it.profile=="default"})
 context.activeProfile="second"
 verify("other profile history and resume retained",second.history().any{it.media.id==show.id} && playback.positionMs(key(show,"one"))==70_000L)
 context.activeProfile="default"
 record(show,"fresh")
 playback.savePositionMs(key(show,"fresh"),35_000,120_000)
 verify("fresh viewing can be recorded normally",library.history().any{it.videoId=="fresh"} && playback.positionMs(key(show,"fresh"))==35_000L)
 val future=PlaybackUpdateClock.next()
 library.mergeTraktHistory(listOf(remoteOld.copy(videoId="new-remote",lastWatchedEpochMs=future)))
 verify("new remote viewing after reset accepted",library.history().any{it.videoId=="new-remote"})
 library.resetTitleHistory(show);library.resetTitleHistory(show)
 verify("repeated reset is safe",library.history().none{it.media.id==show.id} && library.isWatchlisted(show))
 val pinned=LibraryStore(context,profileIdOverride="second")
 pinned.resetTitleHistory(show)
 context.activeProfile="second"
 verify("pinned profile deletion targets that profile",second.history().none{it.media.id==show.id} && playback.positionMs(key(show,"one"))==0L)
 context.activeProfile="default"
 println("$count title reset checks passed")
}
