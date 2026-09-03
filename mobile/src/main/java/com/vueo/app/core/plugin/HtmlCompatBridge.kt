package com.vueo.app.core.plugin

/** Mobile compatibility facade. DOM/Cheerio compatibility lives in shared/core. */
class HtmlCompatBridge {
    private val delegate = com.vueo.shared.core.plugin.HtmlCompatBridge()

    fun execute(requestJson: String): String =
        delegate.execute(requestJson)
}
