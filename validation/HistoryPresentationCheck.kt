package com.vueo.shared.core.storage
import com.vueo.shared.core.media.MediaItem
fun main() {
 var checks=0
 fun verify(name:String,ok:Boolean){check(ok){name};checks++;println("PASS $name")}
 val a=MediaItem("tt1","series","Series")
 val b=MediaItem("tt2","movie","Movie")
 val old=LibraryPlaybackEntry(a,"old",season=1,episode=1,lastWatchedEpochMs=10)
 val recent=old.copy(videoId="recent",episode=2,lastWatchedEpochMs=30,positionMs=30_000,durationMs=60_000)
 val movie=LibraryPlaybackEntry(b,"movie",lastWatchedEpochMs=20)
 val entries=listOf(old,movie,recent)
 val rows=LibraryHistoryPresentation.latestTitles(entries)
 verify("newest titles first",rows==listOf(recent,movie))
 verify("one latest episode per title",rows.count{it.media.id==a.id}==1 && rows.first().episode==2)
 verify("input history remains intact",entries==listOf(old,movie,recent))
 verify("episode and progress caption",LibraryHistoryPresentation.caption(recent)=="S01 E02 • 50% watched")
 verify("season zero retained",LibraryHistoryPresentation.caption(recent.copy(season=0)).startsWith("S00 E02"))
 verify("empty history supported",LibraryHistoryPresentation.latestTitles(emptyList()).isEmpty())
 verify("unknown duration uses watched minutes",LibraryHistoryPresentation.caption(movie.copy(positionMs=180_000))=="3 min watched")
 verify("no timing has neutral label",LibraryHistoryPresentation.caption(movie)=="Watched")
 verify("progress bounded",LibraryHistoryPresentation.caption(recent.copy(positionMs=180_000)).endsWith("100% watched"))
 verify("same ID across media types remains distinct",LibraryHistoryPresentation.latestTitles(listOf(movie,movie.copy(media=b.copy(type="series")))).size==2)
 verify("projection only includes supplied profile entries",LibraryHistoryPresentation.latestTitles(listOf(movie))==listOf(movie))
 println("$checks history presentation checks passed")
}
