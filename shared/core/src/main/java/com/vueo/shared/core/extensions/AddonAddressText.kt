package com.vueo.shared.core.extensions

data class AddonAddressText(val description: String, val address: String?) {
    companion object {
        private val suffix = Regex("""(?:^|\s)Address:\s*(https?://\S+)\s*$""", RegexOption.IGNORE_CASE)
        fun parse(description: String?): AddonAddressText {
            val text = description.orEmpty().trim()
            val match = suffix.find(text) ?: return AddonAddressText(text, null)
            return AddonAddressText(text.substring(0, match.range.first).trim(), match.groupValues[1])
        }
    }
}
