package com.vueo.tv.player

import androidx.media3.common.text.Cue

fun main() {
    val dialog = Cue.Builder().setText("A leaf got into the curry!").build()
    val info = Cue.Builder().setText("The Curry Fairy\nLolie-tan\nage 6")
        .setLine(.82f, Cue.LINE_TYPE_FRACTION).setLineAnchor(Cue.ANCHOR_TYPE_MIDDLE)
        .setPosition(.5f).setPositionAnchor(Cue.ANCHOR_TYPE_MIDDLE).setSize(.7f).build()
    for (input in listOf(listOf(dialog, info), listOf(info, dialog))) {
        val output = tvStackCollidingSubtitleCues(input)
        val d = output.single { it.text.toString() == dialog.text.toString() }
        val i = output.single { it.text.toString() == info.text.toString() }
        check(d.line == Cue.DIMEN_UNSET)
        check(i === info)
        check(i.line == .82f && i.lineAnchor == Cue.ANCHOR_TYPE_MIDDLE && i.position == .5f && i.size == .7f)
    }
    val numberInfo = info.buildUpon().setLine(-2f, Cue.LINE_TYPE_NUMBER).build()
    check(tvStackCollidingSubtitleCues(listOf(dialog, numberInfo)).single { it.text == numberInfo.text } === numberInfo)
    val other = Cue.Builder().setText("Another unpositioned cue").build()
    val stacked = tvStackCollidingSubtitleCues(listOf(dialog, other)).single()
    check(stacked.line == Cue.DIMEN_UNSET)
    check(stacked.text.toString() == "A leaf got into the curry!\nAnother unpositioned cue")
    val commentary = info.buildUpon().setText("(A commentary cue)").build()
    check(tvStackCollidingSubtitleCues(listOf(commentary), preserveAuthoredPositions = false).single().line == Cue.DIMEN_UNSET)
    println("PASS reversed cue order, source position/anchors/size, fractional and numbered authored cues, unpositioned stacking, legacy commentary positioning")
}
