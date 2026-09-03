package com.vueo.shared.core.plugin

import android.content.Context

data class PluginRepositoryInstallResult(
    val repository: PluginRepositoryDescriptor,
    val codeSync: ProviderCodeSyncResult,
)

class PluginRepositoryManager(context: Context) {
    private val store = PluginStore(context.applicationContext)
    private val codeSync = ProviderCodeSyncManager(context.applicationContext)

    suspend fun installOrRefresh(
        inputUrl: String,
        forceCodeRefresh: Boolean = true,
    ): PluginRepositoryInstallResult {
        val repository = PluginRepositoryClient.fetch(inputUrl)
        val sync = codeSync.syncRepository(repository, force = forceCodeRefresh)
        store.upsert(repository)
        return PluginRepositoryInstallResult(repository, sync)
    }
}
