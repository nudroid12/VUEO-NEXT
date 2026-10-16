package com.vueo.shared.core.extensions

import android.content.Context
import com.vueo.shared.core.storage.ContentManagerStore
import com.vueo.mobile.core.extensions.MobileAddonConfigurationReloader
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean

class AddonIdentityTest {
    private fun addon(url: String, id: String = "same.provider", version: String = "1") = object : MediaExtension {
        override val descriptor = ExtensionDescriptor(id, "Example", version, ExtensionKind.STREMIO_ADDON, url,
            catalogs = listOf(CatalogDescriptor(type = "movie", id = "popular", name = "Popular")))
    }
    private val firstUrl = "https://addon.test/config-a/manifest.json"
    private val secondUrl = "https://addon.test/config-b/manifest.json"
    private fun key(addon: MediaExtension) = addon.descriptor.installationId

    @Test fun identityUsesConfigurationAndDoesNotExposeSecret() {
        val first = addon(firstUrl)
        val second = addon(secondUrl)
        assertNotEquals(key(first), key(second))
        assertEquals("same.provider", first.descriptor.id)
        assertFalse(key(first).contains("config-a"))
        assertEquals(64 + "stremio-install-v1-".length, key(first).length)
        assertEquals(key(first), key(addon("  $firstUrl  ", id = "provider.changed", version = "2")))
    }
    @Test fun twoConfigurationsWithSameManifestIdCoexist() {
        val registry = InstalledExtensionRegistry()
        val a = addon(firstUrl); val b = addon(secondUrl)
        registry.install(a); registry.install(b)
        assertEquals(2, registry.installed().size)
        assertSame(a, registry.extension(key(a)))
        assertSame(b, registry.extension(key(b)))
    }
    @Test fun repeatedRefreshReplacesOnlyItsUrlAndPreservesOff() {
        val registry = InstalledExtensionRegistry()
        val a = addon(firstUrl); val b = addon(secondUrl)
        registry.install(a); registry.install(b)
        registry.setEnabled(key(a), false)
        repeat(100) { registry.install(addon(firstUrl, "changed.$it", "$it")); registry.install(addon(secondUrl)) }
        assertEquals(listOf(firstUrl, secondUrl), registry.installed().map { it.descriptor.baseUrl })
        assertFalse(registry.isEnabled(key(a)))
        assertTrue(registry.isEnabled(key(b)))
    }
    @Test fun toggleAndRemovalAffectOnlySelectedInstallation() {
        val registry = InstalledExtensionRegistry()
        val a = addon(firstUrl); val b = addon(secondUrl)
        registry.install(a); registry.install(b)
        registry.setEnabled(key(a), false)
        assertFalse(registry.isEnabled(key(a))); assertTrue(registry.isEnabled(key(b)))
        registry.uninstall(key(a))
        assertEquals(listOf(b), registry.installed())
        assertTrue(registry.isEnabled(key(b)))
    }
    @Test fun legacyLookupOnlyResolvesUnambiguousManifestId() {
        val registry = InstalledExtensionRegistry()
        val a = addon(firstUrl); val b = addon(secondUrl)
        registry.install(a)
        assertSame(a, registry.extension("same.provider"))
        registry.install(b)
        assertNull(registry.extension("same.provider"))
        assertFalse(registry.uninstall("same.provider"))
        assertFalse(registry.setEnabled("same.provider", false))
        assertEquals(2, registry.installed().size)
    }
    @Test fun nonStremioIdentityIsUnchanged() {
        assertEquals("plugin.id", AddonInstallationIdentity.key(ExtensionKind.PROVIDER_PLUGIN, "plugin.id", firstUrl))
    }
    @Test fun catalogKeysAreIndependentForIdenticalCatalogDescriptors() {
        val a = addon(firstUrl); val b = addon(secondUrl)
        assertNotEquals(AddonInstallationIdentity.catalogKey(key(a), "movie", "popular"),
            AddonInstallationIdentity.catalogKey(key(b), "movie", "popular"))
    }
    @Test fun addingExistingAddonPreservesExplicitOffAcrossStoreInstances() {
        val context = Context()
        val store = ContentManagerStore(context)
        store.add(firstUrl); store.setAddonEnabled(firstUrl, false)
        ContentManagerStore(context).add(" $firstUrl ")
        assertFalse(store.isAddonEnabled(firstUrl))
        assertEquals(listOf(firstUrl), store.manifestUrls())
    }
    @Test fun newInstallationStartsOnAndExplicitRemoveThenAddStartsOn() {
        val store = ContentManagerStore(Context())
        store.add(firstUrl)
        assertTrue(store.isAddonEnabled(firstUrl))
        store.setAddonEnabled(firstUrl, false); store.remove(firstUrl); store.add(firstUrl)
        assertTrue(store.isAddonEnabled(firstUrl))
    }
    @Test fun migrationPreservesLegacyCatalogOffAndPosition() {
        val store = ContentManagerStore(Context()); val a = addon(firstUrl)
        val legacy = "same.provider:movie:popular"
        val scoped = AddonInstallationIdentity.catalogKey(key(a), "movie", "popular")
        store.setCatalogOrder(listOf("before", legacy, "after")); store.setCatalogEnabled(legacy, false)
        store.migrateAddonCatalogIdentity(a.descriptor)
        assertFalse(store.isCatalogEnabled(scoped))
        assertEquals(listOf("before", legacy, scoped, "after"), store.catalogOrder())
        store.setCatalogEnabled(scoped, true)
        store.migrateAddonCatalogIdentity(a.descriptor)
        assertTrue(store.isCatalogEnabled(scoped))
    }
    @Test fun bothConfigurationsMigrateButSubsequentChoicesRemainIndependent() {
        val store = ContentManagerStore(Context()); val a = addon(firstUrl); val b = addon(secondUrl)
        val legacy = "same.provider:movie:popular"
        val ak = AddonInstallationIdentity.catalogKey(key(a), "movie", "popular")
        val bk = AddonInstallationIdentity.catalogKey(key(b), "movie", "popular")
        store.setCatalogEnabled(legacy, false)
        store.migrateAddonCatalogIdentity(a.descriptor)
        store.migrateAddonCatalogIdentity(b.descriptor)
        assertFalse(store.isCatalogEnabled(ak)); assertFalse(store.isCatalogEnabled(bk))
        store.setCatalogEnabled(ak, true)
        store.migrateAddonCatalogIdentity(a.descriptor); store.migrateAddonCatalogIdentity(b.descriptor)
        assertTrue(store.isCatalogEnabled(ak)); assertFalse(store.isCatalogEnabled(bk))
    }
    @Test fun incompleteManifestSnapshotDoesNotDeleteUnavailablePreferences() {
        val store = ContentManagerStore(Context())
        store.setCatalogOrder(listOf("missing:movie:catalog"))
        store.setCatalogEnabled("missing:movie:catalog", false)
        store.migrateAddonCatalogIdentity(addon(firstUrl).descriptor)
        store.reconcileCatalogOrder(listOf("present:movie:catalog"))
        assertTrue("missing:movie:catalog" in store.catalogOrder())
        assertFalse(store.isCatalogEnabled("missing:movie:catalog"))
    }
    @Test fun installReadsPersistedToggleAtCommitWithoutTemporaryOn() {
        val registry = InstalledExtensionRegistry(); val enabled = AtomicBoolean(false)
        val a = addon(firstUrl)
        registry.install(a) { enabled.get() }
        val violation = AtomicBoolean(false)
        val start = CountDownLatch(1)
        val reader = Thread {
            start.await()
            repeat(3000) { if (registry.isEnabled(key(a)) || registry.installed().size != 1) violation.set(true) }
        }
        reader.start(); start.countDown()
        repeat(100) { registry.install(addon(firstUrl, version = "$it")) { enabled.get() } }
        reader.join(5000)
        assertFalse(reader.isAlive); assertFalse(violation.get())
    }
    @Test fun concurrentRefreshKeepsAllFourInstallations() {
        val registry = InstalledExtensionRegistry()
        val urls = (1..4).map { "https://addon.test/config-$it/manifest.json" }
        urls.forEach { registry.install(addon(it)) }
        val start = CountDownLatch(1)
        val workers = urls.map { url -> Thread { start.await(); repeat(50) { registry.install(addon(url, version = "$it")) } } }
        workers.forEach(Thread::start); start.countDown(); workers.forEach { it.join(5000); assertFalse(it.isAlive) }
        assertEquals(urls.toSet(), registry.installed().map { it.descriptor.baseUrl }.toSet())
        assertEquals(4, registry.installed().size)
    }
    @Test fun actualMobileReloadAcceptsDuplicateManifestIdsAndKeepsToggle() = runBlocking {
        val store = ContentManagerStore(Context())
        val engine = com.vueo.mobile.core.extensions.UnifiedMediaEngine()
        store.add(firstUrl); store.add(secondUrl); store.setAddonEnabled(firstUrl, false)
        val reload = MobileAddonConfigurationReloader(engine, store) { addon(it) }
        val first = reload.reload()
        assertEquals(2, first.loaded); assertEquals(0, first.conflicts)
        assertEquals(2, engine.stremioAddons().size)
        assertFalse(engine.isExtensionEnabled(key(addon(firstUrl))))
        assertTrue(engine.isExtensionEnabled(key(addon(secondUrl))))
        assertEquals(2, reload.reload(forceRefresh = true).loaded)
        assertEquals(2, engine.stremioAddons().size)
        assertFalse(engine.isExtensionEnabled(key(addon(firstUrl))))
    }
    @Test fun failedManifestRefreshRetainsBothInstallations() = runBlocking {
        val store = ContentManagerStore(Context())
        val engine = com.vueo.mobile.core.extensions.UnifiedMediaEngine()
        store.add(firstUrl); store.add(secondUrl)
        MobileAddonConfigurationReloader(engine, store) { addon(it) }.reload()
        store.setAddonEnabled(secondUrl, false)
        val failed = MobileAddonConfigurationReloader(engine, store) { throw IOException() }.reload(forceRefresh = true)
        assertEquals(2, failed.failed)
        assertEquals(2, engine.stremioAddons().size)
        assertFalse(engine.isExtensionEnabled(key(addon(secondUrl))))
    }
    @Test fun failedEnabledStateReadDoesNotReplaceWorkingInstallation() {
        val registry = InstalledExtensionRegistry()
        val working = addon(firstUrl)
        registry.install(working) { false }
        try {
            registry.install(addon(firstUrl, version = "2")) { throw IllegalStateException() }
            fail("Expected failed preference read")
        } catch (_: IllegalStateException) { }
        assertSame(working, registry.extension(key(working)))
        assertFalse(registry.isEnabled(key(working)))
    }
}
