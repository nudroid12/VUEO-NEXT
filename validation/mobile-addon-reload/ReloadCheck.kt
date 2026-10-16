package com.vueo.mobile.core.extensions
import com.vueo.mobile.core.storage.AddonStore
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicInteger

fun main() = runBlocking {
    var checks = 0
    fun verify(value: Boolean) { check(value); checks++ }
    fun ext(url: String, id: String = url) = MediaExtension(Descriptor(id, url))
    val engine = UnifiedMediaEngine()
    val store = AddonStore()
    store.urls += "https://old"
    engine.install(ext("https://old"))
    val calls = AtomicInteger()
    val reloader = MobileAddonConfigurationReloader(engine, store) { url -> calls.incrementAndGet(); ext(url) }
    verify(reloader.reload().reused == 1)
    verify(calls.get() == 0)
    store.disabled += "https://old"
    reloader.reload()
    verify(!engine.isExtensionEnabled("https://old"))
    verify(calls.get() == 0)
    store.urls += "https://new"
    verify(reloader.reload().loaded == 1)
    verify(engine.stremioAddons().size == 2)
    store.urls -= "https://new"
    verify(reloader.reload().removed == 1)
    verify(engine.stremioAddons().single().descriptor.baseUrl == "https://old")
    val failing = MobileAddonConfigurationReloader(engine, store) { error("offline") }
    verify(failing.reload(forceRefresh = true).failed == 1)
    verify(engine.stremioAddons().single().descriptor.id == "https://old")
    val offlineStore = AddonStore().apply { urls += "https://old"; urls += "https://offline" }
    val offlineEngine = UnifiedMediaEngine().apply { install(ext("https://old")) }
    val offline = MobileAddonConfigurationReloader(offlineEngine, offlineStore) { error("offline") }
    verify(offline.reload().failed == 1)
    verify(offlineEngine.stremioAddons().single().descriptor.baseUrl == "https://old")
    val changedId = MobileAddonConfigurationReloader(engine, store) { url -> ext(url,"changed") }
    verify(changedId.reload(true).loaded == 1)
    verify(engine.stremioAddons().single().descriptor.id == "changed")
    verify(!engine.isExtensionEnabled("changed"))
    store.urls += "https://collision"
    val conflict = MobileAddonConfigurationReloader(engine, store) { url -> ext(url,"changed") }
    verify(conflict.reload().conflicts == 1)
    verify(engine.stremioAddons().single().descriptor.baseUrl == "https://old")

    val manyEngine = UnifiedMediaEngine()
    val manyStore = AddonStore()
    repeat(9) { manyStore.urls += "https://$it" }
    val active = AtomicInteger()
    val peak = AtomicInteger()
    val total = AtomicInteger()
    val bounded = MobileAddonConfigurationReloader(manyEngine, manyStore) { url ->
        val count = active.incrementAndGet(); peak.updateAndGet { maxOf(it,count) }; total.incrementAndGet()
        try { delay(30); ext(url) } finally { active.decrementAndGet() }
    }
    coroutineScope { listOf(async { bounded.reload() }, async { bounded.reload() }).awaitAll() }
    verify(peak.get() == 2)
    verify(total.get() == 9) // second reload reuses the first, rather than fetching twice
    verify(manyEngine.stremioAddons().size == 9)

    val entered = CompletableDeferred<Unit>()
    val release = CompletableDeferred<Unit>()
    val movingStore = AddonStore().apply { urls += "https://moving" }
    val movingEngine = UnifiedMediaEngine()
    val moving = MobileAddonConfigurationReloader(movingEngine, movingStore) { url ->
        entered.complete(Unit); release.await(); ext(url)
    }
    val pending = async { moving.reload() }
    entered.await(); movingStore.urls.clear(); release.complete(Unit)
    verify(pending.await().loaded == 0)
    verify(movingEngine.stremioAddons().isEmpty())

    val cancelEntered = CompletableDeferred<Unit>()
    val cancelStore = AddonStore().apply { urls += "https://old"; urls += "https://waiting" }
    val cancelEngine = UnifiedMediaEngine().apply { install(ext("https://old")) }
    val cancellable = MobileAddonConfigurationReloader(cancelEngine, cancelStore) {
        cancelEntered.complete(Unit); awaitCancellation()
    }
    val cancelled = launch { cancellable.reload() }
    cancelEntered.await(); cancelled.cancelAndJoin()
    verify(cancelEngine.stremioAddons().single().descriptor.baseUrl == "https://old")

    val toggleEntered = CompletableDeferred<Unit>()
    val toggleRelease = CompletableDeferred<Unit>()
    val toggles = AddonStore().apply { urls += "https://toggle" }
    val toggledEngine = UnifiedMediaEngine()
    val toggler = MobileAddonConfigurationReloader(toggledEngine,toggles) { url ->
        toggleEntered.complete(Unit); toggleRelease.await(); ext(url)
    }
    val toggling = async { toggler.reload() }
    toggleEntered.await(); toggles.disabled += "https://toggle"; toggleRelease.complete(Unit); toggling.await()
    verify(!toggledEngine.isExtensionEnabled("https://toggle"))
    println("Addon reload: $checks behavioral checks passed")
}
