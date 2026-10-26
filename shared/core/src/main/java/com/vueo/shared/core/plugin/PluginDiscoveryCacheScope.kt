package com.vueo.shared.core.plugin

internal object PluginDiscoveryCacheScope {
    fun key(baseKey: String, providerNameFilter: String?): String {
        // Blank is a real filtered request, not equivalent to the unfiltered scan.
        val scope = providerNameFilter?.lowercase(java.util.Locale.ROOT)
        return "$baseKey|provider=" + if (scope == null) "all" else "${scope.length}:$scope"
    }
}
