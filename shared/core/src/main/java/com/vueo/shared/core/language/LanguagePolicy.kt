package com.vueo.shared.core.language

import java.util.Locale

/**
 * Canonical language handling shared by source ranking and both players.
 * Keep provider aliases here so Mobile, TV and Smart Source never diverge.
 */
object LanguagePolicy {
    fun canonicalCode(value: String?): String? {
        val normalized = normalize(value) ?: return null
        val words = words(normalized)

        if ("indonesian" in words || "indonesia" in words) return "id"
        if ("malay" in words || "melayu" in words) return "ms"

        knownCode(normalized)?.let { return it }

        val primary = normalized.substringBefore('-')
        return primary.takeIf {
            it.length in 2..3 && it.all(Char::isLetter)
        }
    }

    fun canonicalOrUnknown(value: String?): String {
        canonicalCode(value)?.let { return it }
        val normalized = normalize(value) ?: return "und"
        return normalized.substringBefore('-').ifBlank { "und" }
    }

    fun knownCode(value: String?): String? {
        val normalized = normalize(value) ?: return null
        val primary = normalized.substringBefore('-')
        return LANGUAGE_ALIASES[normalized] ?: LANGUAGE_ALIASES[primary]
    }

    fun detectCodes(value: String?): Set<String> {
        val normalized = normalize(value) ?: return emptySet()
        val tokens = normalized
            .replace(Regex("[^a-z-]+"), " ")
            .split(Regex("\\s+"))
            .filter(String::isNotBlank)

        return buildSet {
            tokens.forEach { token ->
                LANGUAGE_ALIASES[token]?.let(::add)
            }
            LANGUAGE_ALIASES.forEach { (alias, code) ->
                if (
                    ' ' in alias &&
                    Regex("\\b${Regex.escape(alias)}\\b").containsMatchIn(normalized)
                ) {
                    add(code)
                }
            }
        }
    }

    fun friendlyName(value: String?): String {
        val code = canonicalOrUnknown(value)
        if (code == "und") return "Unknown"

        if (code.length in 2..3 && code.all(Char::isLetter)) {
            Locale.forLanguageTag(code)
                .getDisplayLanguage(Locale.ENGLISH)
                .takeIf { it.isNotBlank() && !it.equals(code, ignoreCase = true) }
                ?.replaceFirstChar { it.titlecase(Locale.ENGLISH) }
                ?.let { return it }
        }

        return value
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?.replaceFirstChar { it.titlecase(Locale.ENGLISH) }
            ?: code.uppercase(Locale.ROOT)
    }

    private fun normalize(value: String?): String? =
        value
            ?.trim()
            ?.lowercase(Locale.ROOT)
            ?.replace('_', '-')
            ?.takeIf(String::isNotBlank)

    private fun words(normalized: String): List<String> =
        normalized
            .replace(Regex("[^a-z]+"), " ")
            .trim()
            .split(Regex("\\s+"))
            .filter(String::isNotBlank)

    private val LANGUAGE_ALIASES = mapOf(
        "en" to "en",
        "eng" to "en",
        "english" to "en",
        "es" to "es",
        "spa" to "es",
        "spanish" to "es",
        "espanol" to "es",
        "pt" to "pt",
        "por" to "pt",
        "portuguese" to "pt",
        "fr" to "fr",
        "fra" to "fr",
        "fre" to "fr",
        "french" to "fr",
        "de" to "de",
        "deu" to "de",
        "ger" to "de",
        "german" to "de",
        "it" to "it",
        "ita" to "it",
        "italian" to "it",
        "nl" to "nl",
        "nld" to "nl",
        "dut" to "nl",
        "dutch" to "nl",
        "zh" to "zh",
        "zho" to "zh",
        "chi" to "zh",
        "chinese" to "zh",
        "ja" to "ja",
        "jpn" to "ja",
        "japanese" to "ja",
        "ko" to "ko",
        "kor" to "ko",
        "korean" to "ko",
        "th" to "th",
        "tha" to "th",
        "thai" to "th",
        "id" to "id",
        "ind" to "id",
        "idn" to "id",
        "indonesian" to "id",
        "indonesia" to "id",
        "bahasa indonesia" to "id",
        "ms" to "ms",
        "may" to "ms",
        "msa" to "ms",
        "zsm" to "ms",
        "malay" to "ms",
        "melayu" to "ms",
        "bahasa melayu" to "ms",
        "bahasa malaysia" to "ms",
        "hi" to "hi",
        "hin" to "hi",
        "hindi" to "hi",
        "ta" to "ta",
        "tam" to "ta",
        "tamil" to "ta",
        "te" to "te",
        "tel" to "te",
        "telugu" to "te",
        "ml" to "ml",
        "mal" to "ml",
        "malayalam" to "ml",
        "kn" to "kn",
        "kan" to "kn",
        "kannada" to "kn",
        "bn" to "bn",
        "ben" to "bn",
        "bengali" to "bn",
        "ur" to "ur",
        "urd" to "ur",
        "urdu" to "ur",
        "pa" to "pa",
        "pan" to "pa",
        "punjabi" to "pa",
        "mr" to "mr",
        "mar" to "mr",
        "marathi" to "mr",
        "ar" to "ar",
        "ara" to "ar",
        "arabic" to "ar",
        "ru" to "ru",
        "rus" to "ru",
        "russian" to "ru",
        "fa" to "fa",
        "fas" to "fa",
        "per" to "fa",
        "persian" to "fa",
        "mk" to "mk",
        "mkd" to "mk",
        "mac" to "mk",
        "macedonian" to "mk",
    )
}
