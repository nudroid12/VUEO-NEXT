package com.vueo.app.core.plugin

import okhttp3.OkHttpClient

/**
 * Mobile compatibility facade. Canonical plugin networking now lives in shared/core.
 */
object PluginHttp {
    val client: OkHttpClient
        get() = com.vueo.shared.core.plugin.PluginHttp.client

    suspend fun getText(url: String): String =
        com.vueo.shared.core.plugin.PluginHttp.getText(url)

    suspend fun executeJson(requestJson: String): String =
        com.vueo.shared.core.plugin.PluginHttp.executeJson(requestJson)
}
