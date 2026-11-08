package com.vueo.shared.core.player

private fun normalize(s: String) = String(SubtitleTextNormalizer.normalize(s.toByteArray()), Charsets.UTF_8)
fun main() {
    val cases = listOf(
        "1\n00:00:01.5 --> 00:00:02.75\nHello.\n",
        "\uFEFF1\r\n 00:00:01.5\t-->\t00:00:02,1 \t\r\nHello.\r\n",
        "\n \t1\n01:02.3-->02:03.45\nText.\n",
        "1\n123:4:05.6 --> 124:5:06.78\nText\n",
        "1\n00:00:01,500 --> 00:00:02,750\nAlready correct\n",
        "WEBVTT\n\n1\n00:01.5 --> 00:02.75\nKeep VTT\n",
        "[Script Info]\n1\n00:01.5 --> 00:02.75\nKeep ASS\n",
        "Ordinary text\n1\n00:01.5 --> 00:02.75\n",
        "1\n00:01.1234 --> 00:02.75\nMalformed\n",
        "1\n00:01.5 --> 00:02.75 align:start\nSettings unchanged\n",
        "1\n00:01.5 --> 00:02.75\n123\nText with 00:01.5 --> 00:02.75\n",
        "1\n00:01.5 --> 00:02.75\nNo final newline",
        "1\n00:01.5 --> 00:02.75\n\n2\n00:03.9 --> 00:04.123\nSecond",
        "1\n00:01.5 --> 00:02.75\n\n\uFEFF2\r\n00:03.9 --> 00:04.12\r\nSecond\r\n"
    )
    for ((index, input) in cases.withIndex()) {
        val bytes = input.toByteArray()
        val old = LegacySubtitleTextNormalizer.normalize(bytes)
        val fresh = SubtitleTextNormalizer.normalize(bytes)
        check(old.contentEquals(fresh)) { "Legacy mismatch case $index\nOLD=${String(old)}\nNEW=${String(fresh)}" }
        check(SubtitleTextNormalizer.normalize(fresh).contentEquals(fresh))
    }
    check(normalize("1\n00:00:01.5 --> 00:00:02.75\nHello.\n") ==
        "1\n00:00:01,500 --> 00:00:02,750\nHello.\n")
    val plain = "1\n00:00:01,500 --> 00:00:02,750\nAlready correct\n".toByteArray()
    check(SubtitleTextNormalizer.normalize(plain) === plain)
    val badUtf8 = byteArrayOf(0xc3.toByte(), 0x28)
    check(SubtitleTextNormalizer.normalize(badUtf8) === badUtf8)
    val random = java.util.Random(7)
    val generated = StringBuilder()
    repeat(3000) { cue ->
        val fraction = (1..(1 + random.nextInt(3))).joinToString("") { random.nextInt(10).toString() }
        val newline = if (cue % 2 == 0) "\r\n" else "\n"
        generated.append(cue + 1).append(newline)
            .append(if (cue % 3 == 0) "  " else "")
            .append("01:02:03").append(if (cue % 2 == 0) '.' else ',').append(fraction)
            .append(" --> 02:03:04.9").append(newline)
            .append("Dialogue intact: Malay 日本語 عربى 123 --> 456").append(newline).append(newline)
    }
    val big = generated.toString().toByteArray()
    val expected = LegacySubtitleTextNormalizer.normalize(big)
    val start = System.nanoTime()
    val actual = SubtitleTextNormalizer.normalize(big)
    val elapsed = (System.nanoTime() - start) / 1_000_000
    check(actual.contentEquals(expected))
    check(SubtitleTextNormalizer.normalize(actual) === actual)
    val longDialogue = ("1\n00:01.5 --> 00:02.75\n" + "x".repeat(1_000_000)).toByteArray()
    val out = SubtitleTextNormalizer.normalize(longDialogue)
    check(String(out).endsWith("x".repeat(1_000_000)))
    println("PASS ${cases.size} legacy-equivalence fixtures, UTF8/BOM/CRLF, VTT/ASS, malformed cues, unchanged identity, idempotence, 3000 cues (${big.size} bytes, new pass ${elapsed}ms on this JVM), 1MB dialogue")
}
