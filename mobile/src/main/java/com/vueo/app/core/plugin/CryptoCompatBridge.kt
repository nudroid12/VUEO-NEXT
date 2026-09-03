package com.vueo.app.core.plugin

/** Mobile compatibility facade. CryptoJS compatibility lives in shared/core. */
object CryptoCompatBridge {
    fun execute(requestJson: String): String =
        com.vueo.shared.core.plugin.CryptoCompatBridge.execute(requestJson)
}
