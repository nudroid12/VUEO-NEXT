package com.vueo.app.core.plugin

/** Mobile compatibility facade. WHATWG URL compatibility lives in shared/core. */
object UrlCompatBridge {
    fun execute(requestJson: String): String =
        com.vueo.shared.core.plugin.UrlCompatBridge.execute(requestJson)
}
