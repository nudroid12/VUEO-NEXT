package com.vueo.shared.core.source

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class SourceDiscoveryControlTest {
    @Test fun stoppingAllSourcesPreservesLateSubtitlesAndParent() = runBlocking {
        withTimeout(2000) {
            val control = SourceDiscoveryControl()
            val plugins = launch { awaitCancellation() }
            val addons = launch { awaitCancellation() }
            control.attachPlugins(plugins)
            control.attachAddons(addons)
            val release = CompletableDeferred<Unit>()
            val subtitles = async { release.await(); "late subtitles" }
            control.stopSources()
            plugins.join()
            addons.join()
            assertTrue(plugins.isCancelled)
            assertTrue(addons.isCancelled)
            assertTrue(control.sourcesStopped)
            assertTrue(subtitles.isActive)
            assertTrue(currentCoroutineContext().isActive)
            release.complete(Unit)
            assertEquals("late subtitles", subtitles.await())
        }
        Unit
    }

    @Test fun stoppingSourcesBeforeRegistrationCancelsBothBranches() = runBlocking {
        val control = SourceDiscoveryControl()
        control.stopSources()
        var ran = false
        val plugins = launch(start = CoroutineStart.LAZY) { ran = true }
        val addons = launch(start = CoroutineStart.LAZY) { ran = true }
        control.attachPlugins(plugins)
        control.attachAddons(addons)
        plugins.start(); addons.start()
        plugins.join(); addons.join()
        assertFalse(ran)
        assertTrue(plugins.isCancelled)
        assertTrue(addons.isCancelled)
    }

    @Test fun stoppingPluginsKeepsAddonAndSubtitleSiblingsActive() = runBlocking {
        withTimeout(2000) {
            val control = SourceDiscoveryControl()
            val started = CompletableDeferred<Unit>()
            val plugin = launch { started.complete(Unit); awaitCancellation() }
            control.attachPlugins(plugin)
            val release = CompletableDeferred<Unit>()
            val addons = async { release.await(); "addon source" }
            val subtitles = async { release.await(); "late subtitle" }
            started.await()
            control.stopPlugins()
            plugin.join()
            assertTrue(plugin.isCancelled)
            assertTrue(currentCoroutineContext().isActive)
            assertTrue(addons.isActive)
            assertTrue(subtitles.isActive)
            release.complete(Unit)
            assertEquals("addon source", addons.await())
            assertEquals("late subtitle", subtitles.await())
        }
        Unit
    }

    @Test fun stopBeforeRegistrationPreventsPluginWorkFromStarting() = runBlocking {
        val control = SourceDiscoveryControl()
        control.stopPlugins()
        var ran = false
        val plugin = launch(start = CoroutineStart.LAZY) { ran = true }
        control.attachPlugins(plugin)
        plugin.start()
        plugin.join()
        assertTrue(plugin.isCancelled)
        assertFalse(ran)
    }

    @Test fun manualRefreshGetsIndependentControl() = runBlocking {
        withTimeout(2000) {
            val previous = SourceDiscoveryControl()
            val refreshed = SourceDiscoveryControl()
            previous.stopPlugins()
            val result = async { "refreshed source" }
            refreshed.attachPlugins(result)
            assertEquals("refreshed source", result.await())
            assertFalse(refreshed.pluginsStopped)
        }
        Unit
    }

    @Test fun completedPluginsAreNotReportedAsStopped() = runBlocking {
        val control = SourceDiscoveryControl()
        val plugin = launch { }
        control.attachPlugins(plugin)
        plugin.join()
        control.detachPlugins(plugin)
        control.stopPlugins()
        assertFalse(control.pluginsStopped)
    }
}
