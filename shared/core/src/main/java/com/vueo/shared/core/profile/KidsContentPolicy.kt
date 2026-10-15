package com.vueo.shared.core.profile

/** Conservative classification only: missing/ambiguous ratings never admit a Kids title. */
object KidsContentPolicy {
    val ageLimits = listOf(7, 12, 16)
    fun normalizeLimit(value: Int): Int = value.takeIf { it in ageLimits } ?: 7

    fun minimumAge(certification: String?): Int? {
        val rating = certification?.trim()?.uppercase(java.util.Locale.ROOT)
            ?.replace('_', '-')?.replace(Regex("\\s+"), " ") ?: return null
        return when (rating) {
            "G", "U", "TV-G", "TV-Y", "ALL", "SU", "L", "0", "AL" -> 0
            "TV-Y7", "TV-Y7-FV", "7", "7+", "6", "6+" -> 7
            "PG", "TV-PG" -> 10
            "12", "12+", "12A", "12B", "P12" -> 12
            "PG-13", "13", "13+", "P13" -> 13
            "TV-14", "14", "14+" -> 14
            "15", "15+" -> 15
            "16", "16+", "16A" -> 16
            "R", "17", "17+" -> 17
            "18", "18+", "TV-MA", "NC-17", "R18", "R18+", "X" -> 18
            else -> Regex("(?:FSK|USK) ?(0|6|12|16|18)").matchEntire(rating)
                ?.groupValues?.get(1)?.toIntOrNull()
        }
    }

    fun allows(certification: String?, ageLimit: Int?): Boolean =
        ageLimit == null || minimumAge(certification)?.let { it <= normalizeLimit(ageLimit) } == true
}
