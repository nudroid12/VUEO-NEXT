package com.vueo.tv.player
import androidx.media3.common.text.Cue

fun main() {
    val ids = listOf("external:HiAnime:en", "external:HiAnime:ms")
    val labels = ids.map { "vueo-subtitle:$it" }
    check(NativeIdentity.configurationIndex(ids[0], null, ids, labels) == 0)
    check(NativeIdentity.configurationIndex("2:${ids[1]}", null, ids, labels) == 1)
    check(NativeIdentity.configurationIndex("unrelated", labels[0], ids, labels) == 0)
    check(NativeIdentity.configurationIndex(null, labels[1], ids, labels) == 1)
    check(NativeIdentity.configurationIndex("unrelated", "English", ids, labels) == null)
    check(NativeIdentity.configurationIndex(null, "English", listOf(null), listOf("English")) == null)
    check(NativeIdentity.configurationIndex(null, labels[0], ids, listOf(labels[0], labels[0])) == null)

    val time = "00:00:00.000 --> 00:00:12.000"
    for (line in listOf(-1, -2, -3, -4, -9)) {
        check(!NativeIdentity.hasNativePositionSettings("$time line:$line"))
        // SmartSubs continues using the baseline protection rule.
        check(TvSubtitleTemporalPolicy.hasVttPositionSettings("$time line:$line"))
    }
    for (settings in listOf("line:20%", "line:0", "line:-1,start", "line:-1 position:50%", "size:100%", "vertical:rl", "region:sign")) {
        check(NativeIdentity.hasNativePositionSettings("$time $settings"))
    }
    val cues = listOf("title", "name", "age", "dialogue").mapIndexed { i, text ->
        Cue(text=text, line=-(i+1f), lineType=Cue.LINE_TYPE_NUMBER, position=.5f, size=1f)
    }
    // Use production split and stack functions, not copies of their logic.
    Layers.protectedTimelineText=emptySet()
    val split=Layers.splitLowerSubtitleLayers(cues,true)
    val main=tvStackCollidingSubtitleCues(split.lowerCues)
    val upper=tvStackCollidingSubtitleCues(split.upperCues)
    check(main.single().text.toString()=="dialogue")
    check(upper.single().text.toString()=="title\nname\nage")
    check(main.single().line==Cue.DIMEN_UNSET)
    check(upper.single().line==Cue.DIMEN_UNSET)
    // Unset line is the prerequisite for SubtitleView bottom padding; actual
    // Android drawing/padding interaction must still be checked on a TV.
    val protected = cues[0].copy(text="sign",position=.2f)
    check(tvStackCollidingSubtitleCues(listOf(protected),setOf("sign")).single()==protected)
    val commentary=Layers.splitLowerSubtitleLayers(listOf(Cue(text="dialogue"),Cue(text="(commentary)")),true)
    check(commentary.lowerCues.single().text=="dialogue")
    check(commentary.upperCues.single().text=="(commentary)")
    check(commentary.upperUsesCommentarySize)
    println("PASS: production identity/settings/split/stack regression checks (not Android rendering)")
}
