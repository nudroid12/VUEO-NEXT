package com.vueo.mobile.ui

import com.vueo.shared.core.media.*
import com.vueo.shared.core.profile.KidsContentPolicy
import com.vueo.shared.core.search.*
import kotlinx.coroutines.*

fun main() = runBlocking {
    var checks = 0
    fun verify(value: Boolean) { check(value); checks++ }
    val nova = MediaItem("n1", "movie", "Nova", genres=listOf("Drama"), releaseInfo="2020", imdbRating=7.0, certification="G")
    val otherNova = MediaItem("n2", "series", "The Nova Story", genres=listOf("Sci-Fi", "Drama"), releaseInfo="2024", imdbRating=9.0, certification="TV-MA")
    val anime = MediaItem("a1", "series", "Other Adventure", genres=listOf("Adventure"), releaseInfo="2022", certification="TV-Y7")
    val japanese = MediaItem("a2", "movie", "Other Animation", genres=listOf("Animation"), originalLanguage="ja", releaseInfo="2021", certification="PG-13")
    val unknown = MediaItem("u1", "movie", "Other Unknown", genres=listOf("Drama"), releaseInfo="2025")
    val rows = listOf(
        CatalogRow("popular", "Popular", "A", listOf(nova,otherNova,unknown)),
        CatalogRow("anime-trending", "Anime", "B", listOf(anime,japanese,nova)),
    )
    val results = listOf(otherNova,nova,anime,japanese,unknown)
    // Behavior parity against the previous screen pipeline: filter first, then
    // order. The new implementation orders a reusable source before genre selection.
    for (query in listOf("", "nova", "other")) for (actor in listOf(false,true))
        for (limit in listOf(null,7,12,16)) for (type in SearchMediaFilter.entries)
            for (sort in DiscoverSortMode.entries) {
                val input = MobileSearchPreparationInput(rows,results,query,actor,"profile",limit,1)
                val prepared = prepareMobileSearchSource(input,type,sort)
                val animeKeys = rows.filter { row -> listOf(row.id,row.title,row.providerName).any { it.contains("anime",true) } }
                    .flatMap { it.items }.map { "${it.type}:${it.id}" }.toSet()
                val source = if(query.isNotBlank()) results else DiscoverCatalogPolicy.baseItems(rows,sort)
                val typed = source.filter { KidsContentPolicy.allows(it.certification,limit) && SearchResultOrderPolicy.matchesType(it,type,animeKeys) }
                val expectedGenres = typed.flatMap { it.genres }.map { it.trim() }.filter { it.isNotBlank() && !it.equals("anime",true) }
                    .distinctBy { it.lowercase() }.sortedBy { it.lowercase() }
                verify(prepared.genres == expectedGenres)
                for (genre in listOf(null,"Drama","Sci-Fi")) {
                    val filtered = typed.filter { SearchResultOrderPolicy.matchesGenre(it,genre) }
                    val expected = when {
                        query.isNotBlank() && actor -> SearchResultOrderPolicy.sortActorItems(filtered,sort)
                        query.isNotBlank() -> SearchResultOrderPolicy.sortTitleItems(filtered,sort,query)
                        else -> DiscoverCatalogPolicy.orderFiltered(filtered,sort)
                    }
                    val actual = prepareMobileSearchGenre(prepared,genre)
                    verify(actual.items == expected)
                    verify(actual.gridRows.flatten() == actual.items && actual.gridRows.all { it.size in 1..3 })
                }
            }
    val input = MobileSearchPreparationInput(rows,results,"nova",false,"profile",null,1)
    verify(prepareMobileSearchSource(input,SearchMediaFilter.ALL,DiscoverSortMode.POPULAR).items.first().id == "n1")
    verify(prepareMobileSearchSource(input.copy(query="other"),SearchMediaFilter.ALL,DiscoverSortMode.POPULAR).items.first().id != "n1")
    val safe = prepareMobileSearchSource(input,SearchMediaFilter.ALL,DiscoverSortMode.POPULAR)
    verify(prepareMobileSearchGenre(safe,null).items === safe.items)

    supervisorScope {
        lateinit var work: Deferred<MobileSearchPreparedSource>
        var visited = 0
        val large = object : AbstractList<MediaItem>() {
            override val size = 10000
            override fun get(index: Int): MediaItem {
                visited++
                if(index == 10) work.cancel()
                return nova
            }
        }
        work = async(Dispatchers.Default,start=CoroutineStart.LAZY) {
            prepareMobileSearchSource(input.copy(rows=emptyList(),results=large),SearchMediaFilter.ALL,DiscoverSortMode.POPULAR)
        }
        work.start()
        try { work.await(); error("cancelled work published") } catch (_: CancellationException) { verify(work.isCancelled) }
        verify(visited < 200)
    }
    println("Search preparation: $checks behavioral checks passed")
}
