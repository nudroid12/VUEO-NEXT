package com.vueo.shared.core.collections

import android.content.Context
import com.vueo.shared.core.extensions.*
import com.vueo.shared.core.media.*
import java.nio.file.Files
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class CollectionTest {
    private val source = CollectionCatalogSource("installation-a", "movie", "popular", "Animation")
    private fun collection(id: String = "c", enabled: Boolean = true) = Collection(id, "My collection", enabled,
        FolderViewMode.ROWS, false, listOf(CollectionFolder("folder", "Anime", "https://images.example/a.png", "🎬", listOf(source))))
    private fun store() = CollectionStore.get(Context(Files.createTempDirectory("vueo-collections").toFile()), "profile")
    private fun descriptor(url: String = "https://a.example/manifest.json", catalogs: List<CatalogDescriptor> = listOf(CatalogDescriptor("movie", "popular", "Popular", listOf(CatalogExtraDescriptor("skip"))))) =
        ExtensionDescriptor("same-raw-id", "Test addon", "1", ExtensionKind.STREMIO_ADDON, url, catalogs = catalogs)
    private fun addon(descriptor: ExtensionDescriptor = descriptor(), body: suspend (String, String, Map<String,String>) -> CatalogPage = { _,_,_-> CatalogPage(emptyList()) }) = StremioAddonExtension(descriptor, body)
    @Test fun jsonRoundTripPreservesAllFields() { val list = listOf(collection(), collection("c2", false)); assertEquals(list, CollectionJson.decode(CollectionJson.encode(list))) }
    @Test fun reorderPreservesIdentityAndBoundaries() { assertEquals(listOf("b", "a", "c"), CollectionPolicy.move(listOf("a","b","c"), 1, 0)); assertEquals(listOf("a"), CollectionPolicy.move(listOf("a"), 0, -1)) }
    @Test fun duplicateIdsRejected() { assertThrows(IllegalArgumentException::class.java) { CollectionPolicy.validate(listOf(collection(), collection())) } }
    @Test fun duplicateFoldersRejected() { val c = collection(); assertThrows(IllegalArgumentException::class.java) { CollectionPolicy.validate(listOf(c.copy(folders = c.folders + c.folders))) } }
    @Test fun duplicateSourcesRejected() { val c = collection(); assertThrows(IllegalArgumentException::class.java) { CollectionPolicy.validate(listOf(c.copy(folders = listOf(c.folders[0].copy(catalogSources = listOf(source, source)))))) } }
    @Test fun invalidImageUrlsRejected() { listOf("http://example.com/a", "file:///a", "javascript:alert(1)", "https://user:secret@example.com/a").forEach { assertFalse(it, CollectionPolicy.validImageUrl(it)) }; assertTrue(CollectionPolicy.validImageUrl("https://example.com/image.png?token=signed")) }
    @Test fun importRejectsOtherFormatsAndOversizedInput() { assertThrows(Exception::class.java) { CollectionJson.decode("[]") }; assertThrows(IllegalArgumentException::class.java) { CollectionJson.decode("a".repeat(CollectionJson.MAX_BYTES + 1)) } }
    @Test fun offAndOrderSurviveReload() = runBlocking {
        val directory = Files.createTempDirectory("vueo-collections").toFile()
        val s = CollectionStore.get(Context(directory), "p"); s.save(collection()); s.save(collection("second")); s.toggle("c"); s.move("second", -1)
        val file = directory.resolve("collections").listFiles()!!.single()
        val persisted = CollectionJson.decode(file.readText())
        assertEquals(listOf("second", "c"), persisted.map { it.id }); assertFalse(persisted[1].enabled)
    }
    @Test fun profileIsolation() = runBlocking { val context = Context(Files.createTempDirectory("vueo-collections").toFile()); val a = CollectionStore.get(context, "a"); val b = CollectionStore.get(context, "b"); a.save(collection()); b.load(); assertTrue(b.collections.value.isEmpty()) }
    @Test fun importMergesWithoutRemovingOthers() = runBlocking { val s = store(); s.save(collection()); s.save(collection("kept")); s.merge(CollectionJson.encode(listOf(collection().copy(title="Updated"), collection("new")))); assertEquals(listOf("c","kept","new"), s.collections.value.map { it.id }); assertEquals("Updated", s.collections.value[0].title) }
    @Test fun concurrentSavesDoNotLoseCollections() = runBlocking { val s = store(); coroutineScope { repeat(30) { i -> launch(Dispatchers.Default) { s.save(collection(i.toString())) } } }; assertEquals(30, s.collections.value.size) }
    @Test fun corruptDiskFileIsNotOverwritten() = runBlocking {
        val context = Context(Files.createTempDirectory("vueo-collections").toFile()); val s = CollectionStore.get(context, "p")
        // derive the same profile filename without relying on implementation internals
        val key = java.security.MessageDigest.getInstance("SHA-256").digest("p".toByteArray()).joinToString("") { "%02x".format(it) }
        val file = context.filesDir.resolve("collections/$key.json"); file.parentFile.mkdirs(); file.writeText("broken")
        try { s.save(collection()); fail("Must reject corrupt stored JSON") } catch (_: Exception) { }
        assertEquals("broken", file.readText())
    }
    @Test fun sameManifestIdResolvesExactInstallation() = runBlocking {
        val a = addon(body = { _,_,_-> CatalogPage(listOf(MediaItem("a","movie","A"))) }); val b = addon(descriptor("https://b.example/manifest.json"), { _,_,_-> CatalogPage(listOf(MediaItem("b","movie","B"))) })
        val engine = UnifiedMediaEngine(listOf(a,b)); val ref = source.copy(addonId=b.descriptor.installationId,genre=null)
        val page = CollectionCatalogResolver.load(engine, ref); assertEquals("b",page.items.single().id); assertEquals(b.descriptor.installationId,page.items.single().sourceExtensionId)
    }
    @Test fun missingAndOffSourcesNeverUseAnotherAddon() = runBlocking {
        var requests=0; val a = addon(body={ _,_,_-> requests++; CatalogPage(emptyList()) }); val e = UnifiedMediaEngine(listOf(a))
        try { CollectionCatalogResolver.load(e,source); fail("Missing source") } catch (_: IllegalStateException) { }
        e.off.add(a.descriptor.installationId)
        try { CollectionCatalogResolver.load(e,source.copy(addonId=a.descriptor.installationId)); fail("Off source") } catch (_: IllegalStateException) { }
        assertEquals(0,requests)
    }
    @Test fun paginationAndGenreAreSentAndKidsPolicyApplied() = runBlocking {
        val a = addon(body={ _,_,extras -> assertEquals(mapOf("skip" to "20", "genre" to "Animation"),extras); CatalogPage(listOf(MediaItem("allowed","movie","A"),MediaItem("blocked","movie","B"))) })
        val page = CollectionCatalogResolver.load(UnifiedMediaEngine(listOf(a),allowed={ it.id=="allowed" }),source.copy(addonId=a.descriptor.installationId),20)
        assertEquals(listOf("allowed"),page.items.map { it.id }); assertEquals(22,page.nextSkip)
    }
    @Test fun hiddenTitlesDoNotTerminatePagination() = runBlocking {
        val a = addon(body={ _,_,_-> CatalogPage(listOf(MediaItem("hidden","movie","Hidden"))) })
        val page = CollectionCatalogResolver.load(UnifiedMediaEngine(listOf(a),allowed={ false }),source.copy(addonId=a.descriptor.installationId))
        assertTrue(page.items.isEmpty()); assertEquals(1,page.nextSkip); assertEquals(setOf("movie:hidden"),page.itemKeys)
    }
    @Test fun requiredExtrasExcludeSearchAndValidateGenre() = runBlocking {
        val d = descriptor(catalogs=listOf(CatalogDescriptor("movie","search",extras=listOf(CatalogExtraDescriptor("search",true))), CatalogDescriptor("movie","popular",extras=listOf(CatalogExtraDescriptor("genre",true,listOf("Animation"))))))
        val a=addon(d);val e=UnifiedMediaEngine(listOf(a));assertEquals(1,CollectionCatalogResolver.available(e).size)
        try { CollectionCatalogResolver.load(e,source.copy(addonId=d.installationId,genre=null)); fail("Genre required") } catch (_: IllegalStateException) { }
    }
    @Test fun cancellationPropagates() = runBlocking {
        val a = addon(body={ _,_,_-> throw CancellationException("left screen") })
        try { CollectionCatalogResolver.load(UnifiedMediaEngine(listOf(a)),source.copy(addonId=a.descriptor.installationId)); fail("Cancellation swallowed") } catch (_: CancellationException) { }
    }
}
