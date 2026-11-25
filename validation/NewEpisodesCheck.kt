package com.vueo.shared.core.storage

import android.content.Context
import com.vueo.shared.core.media.EpisodeItem
import com.vueo.shared.core.media.MediaItem
import java.time.Instant
import kotlinx.coroutines.runBlocking

fun main() {
    var checks = 0
    fun verify(name: String, ok: Boolean) { check(ok) { name }; checks++; println("PASS $name") }

    val now = Instant.parse("2026-11-26T12:00:00Z").toEpochMilli()
    fun ep(n: Int, date: String?, season: Int = 1) = EpisodeItem("episode:$season:$n", "Episode $n", season, n, released = date)
    val old = ep(1, "2026-11-01T12:00:00Z")
    val recent = ep(2, "2026-11-23T10:00:00Z")
    val newest = ep(3, "2026-11-26T10:00:00Z")
    val future = ep(4, "2026-11-27T00:00:00Z")
    val unknown = ep(5, null)
    val malformed = ep(6, "2026-11-20 broken")
    val dateToday = ep(7, "2026-11-26")
    val dateYesterday = ep(8, "2026-11-25")
    val special = ep(1, "2026-11-24T13:00:00Z", 0)
    val show = MediaItem("new-episodes-test-show", "series", "Show", episodes=listOf(old,recent,newest,future,unknown,malformed,dateToday,special))
    val movie = show.copy(id="new-episodes-test-movie", type="movie")
    val another = show.copy(id="new-episodes-test-another", episodes=listOf(recent))
    val playback = LibraryPlaybackEntry(another, recent.id, season=1, episode=2,positionMs=120_000,durationMs=120_000)

    verify("movie not eligible", NewEpisodesPolicy.select(listOf(movie),emptySet(),now).isEmpty())
    verify("latest recently released episode",NewEpisodesPolicy.select(listOf(show),emptySet(),now).single().episode == newest)
    verify("future and invalid dates rejected",NewEpisodesPolicy.select(listOf(show.copy(episodes=listOf(future,unknown,malformed,dateToday))),emptySet(),now).isEmpty())
    verify("older unwatched backlog excluded",NewEpisodesPolicy.select(listOf(show.copy(episodes=listOf(old))),emptySet(),now).isEmpty())
    verify("date-only today held until date has passed",NewEpisodesPolicy.releasedAt(dateToday)!! > now)
    verify("date-only yesterday qualifies",NewEpisodesPolicy.select(listOf(show.copy(episodes=listOf(dateYesterday))),emptySet(),now).single().episode==dateYesterday)
    verify("offset timestamp handled",NewEpisodesPolicy.releasedAt(ep(9,"2026-11-26T18:00:00+08:00"))==NewEpisodesPolicy.releasedAt(newest))
    verify("season-zero badge supported",NewEpisodesPolicy.select(listOf(show.copy(episodes=listOf(special))),emptySet(),now).single().episodeLabel=="S00 E01")
    verify("one title card, duplicates excluded",NewEpisodesPolicy.select(listOf(show,show),emptySet(),now).size==1)
    verify("release order across series",NewEpisodesPolicy.select(listOf(another,show),emptySet(),now).first().media.id==show.id)
    val watched = setOf(WatchedEpisodePolicy.key(show,1,3))
    verify("completed newest falls back to remaining new episode",NewEpisodesPolicy.select(listOf(show),watched,now).single().episode==special)
    val allRecent = watched + WatchedEpisodePolicy.key(show,0,1) + WatchedEpisodePolicy.key(show,1,2)
    verify("all recently aired episodes watched removes title",NewEpisodesPolicy.select(listOf(show),allRecent,now).isEmpty())
    verify("followed includes caught-up playback and saved titles",NewEpisodesPolicy.followed(listOf(movie,show),listOf(playback),emptySet()).map{it.id}==listOf(show.id,another.id))
    verify("dismissed playback titles excluded",NewEpisodesPolicy.followed(emptyList(),listOf(playback),setOf("series:${another.id}")).isEmpty())
    verify("watchlist remains explicit follow",NewEpisodesPolicy.followed(listOf(another),listOf(playback),setOf("series:${another.id}")).size==1)

    val context = Context()
    val store = LibraryStore(context)
    val preferences = NewEpisodesPreferences(context)
    verify("new episodes defaults on", preferences.enabled("default"))
    preferences.setEnabled("default",false)
    verify("settings persist and isolate profiles", !NewEpisodesPreferences(context).enabled("default") && preferences.enabled("second"))
    store.toggleWatchlist(show)
    verify("saved title reads actual persisted episode index", store.newEpisodeEntries("default",now).single().episode==newest)
    store.recordPlayback(show,newest.id,newest.title,1,3,120_000,120_000)
    verify("actual completed playback clears latest new flag", store.newEpisodeEntries("default",now).single().episode==special)
    store.setEpisodesWatched(show,listOf(recent,special),true)
    verify("manual episode watched clears new card",store.newEpisodeEntries("default",now).isEmpty())
    store.setEpisodesWatched(show,listOf(newest),false)
    verify("mark unwatched restores recent badge",store.newEpisodeEntries("default",now).single().episode==newest)
    store.removeWatchlist(show)
    // The unwatch removed the only resume cursor; no follow source remains.
    verify("unfollow removes saved-only title",store.newEpisodeEntries("default",now).isEmpty())

    val caught = another.copy(episodes=listOf(recent,newest))
    store.recordPlayback(caught,recent.id,recent.title,1,2,120_000,120_000)
    verify("caught-up series matches new unwatched episode",store.newEpisodeEntries("default",now).any{it.media.id==caught.id && it.episode==newest})
    val resume = store.continueWatchingPlaybackEntries().first{it.media.id==caught.id}
    store.removeFromContinueWatching(resume)
    verify("dismissed CW title hidden from New Episodes",store.newEpisodeEntries("default",now).none{it.media.id==caught.id})
    store.toggleWatchlist(caught)
    verify("saved series remains followed despite dismissed CW",store.newEpisodeEntries("default",now).any{it.media.id==caught.id})

    context.activeProfile = "second"
    verify("other profile cannot see followed titles",store.newEpisodeEntries("second",now).isEmpty() && store.newEpisodeEntries("default",now).isEmpty())
    store.toggleWatchlist(show)
    verify("watched state per profile",store.newEpisodeEntries("second",now).single().episode==newest)
    context.activeProfile="default"

    val savedOnly = (1..12).map { i -> MediaItem("saved-new-$i","series","Saved $i") }
    savedOnly.forEach(store::toggleWatchlist)
    val candidates = store.continueWatchingMetadataCandidates(now,true)
    verify("only bounded saved batch added",candidates.size==NewEpisodesPolicy.WATCHLIST_BATCH_SIZE)
    verify("switch off leaves saved-only checks disabled",store.continueWatchingMetadataCandidates(now,false).isEmpty())
    val first = candidates.first()
    verify("refresh claim allows one request",store.claimContinueWatchingMetadata(first,"default",now))
    verify("refresh claim prevents duplicate",!store.claimContinueWatchingMetadata(first,"default",now+1))
    verify("wrong profile cannot claim",!store.claimContinueWatchingMetadata(first,"second",now))
    verify("later batch rotates to unattempted titles",store.continueWatchingMetadataCandidates(now+1,true).none { it.id==first.id })
    val fresh = first.copy(episodes=listOf(newest))
    verify("watchlist metadata refresh populates episode index",store.updateContinueWatchingMetadata(fresh,"default",true) &&
        store.newEpisodeEntries("default",now).any{it.media.id==first.id})
    verify("corrected future release date removes badge",store.updateContinueWatchingMetadata(fresh.copy(episodes=listOf(newest.copy(released="2099-01-01"))),"default",true) &&
        store.newEpisodeEntries("default",now).none{it.media.id==first.id})
    verify("wrong profile refresh rejected",!store.updateContinueWatchingMetadata(fresh,"second",true))
    verify("new episode setting does not remove actual Watchlist",store.isWatchlisted(caught))

    val systemNow = System.currentTimeMillis()
    val newer = ep(10,Instant.ofEpochMilli(systemNow-3_600_000).toString())
    val networkTitle = MediaItem("new-episodes-refresh-only","series","Refresh only")
    context.activeProfile="third"
    store.toggleWatchlist(networkTitle)
    var requested = 0
    val changed = runBlocking {
        ContinueWatchingMetadataRefresh.refresh(store, { media ->
            requested++
            media.copy(episodes=listOf(newer))
        }, includeWatchlist=true)
    }
    verify("shared metadata refresh loads Watchlist and updates new episodes",changed && requested==1 &&
        store.newEpisodeEntries("third",systemNow).single().episode==newer)
    val repeatChanged = runBlocking { ContinueWatchingMetadataRefresh.refresh(store,{ media -> requested++; media },true) }
    verify("second metadata refresh respects throttle",!repeatChanged && requested==1)
    println("$checks New Episodes checks passed")
}
