package com.vueo.shared.core.media
data class SubtitleCipherKey(val key: String, val iv: String)
data class SubtitleTextTransform(val algorithm: String, val scope: String, val keys: List<SubtitleCipherKey>)
