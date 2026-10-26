package com.vueo.shared.core.plugin

import org.junit.Assert.*
import org.junit.Test

class PluginDiscoveryCacheScopeTest {
    @Test fun unfilteredAndProviderRequestsNeverShareAnEntryOrFlight() {
        val all = PluginDiscoveryCacheScope.key("movie|42", null)
        val a = PluginDiscoveryCacheScope.key("movie|42", "Repo / A")
        val b = PluginDiscoveryCacheScope.key("movie|42", "Repo / B")
        assertNotEquals(all, a)
        assertNotEquals(a, b)
        assertNotEquals(all, PluginDiscoveryCacheScope.key("movie|42", ""))
    }
    @Test fun scopeMatchesCaseInsensitiveProviderSelectionWithoutMergingMedia() {
        assertEquals(PluginDiscoveryCacheScope.key("tv|42|2|5", "Repo / A"),
            PluginDiscoveryCacheScope.key("tv|42|2|5", "REPO / a"))
        assertNotEquals(PluginDiscoveryCacheScope.key("tv|42|2|5", "Repo / A"),
            PluginDiscoveryCacheScope.key("tv|42|1|5", "Repo / A"))
        assertNotEquals(PluginDiscoveryCacheScope.key("tv|42", "all"),
            PluginDiscoveryCacheScope.key("tv|42", null))
    }
}
