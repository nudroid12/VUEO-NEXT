package com.vueo.mobile.ui

fun main() {
    var checks = 0
    fun verify(value: Boolean) { check(value); checks++ }
    // A playing or buffering play intent survives exactly one background cycle.
    val playing = MobilePlayerLifecycleState(true, true)
    verify(!playing.start(true))
    verify(playing.stop(true))
    verify(!playing.foreground)
    verify(!playing.stop(false)) // duplicate stop cannot overwrite the stored true intent
    verify(playing.start(true))
    verify(playing.foreground)
    verify(!playing.start(true)) // no second automatic play
    // Explicit pause is preserved, even though it was initially an autoplay session.
    verify(playing.stop(false))
    verify(!playing.start(true))
    // Prompt / exit / completed / handoff / error veto consumes the resume intent.
    repeat(5) {
        verify(playing.stop(true))
        verify(!playing.start(false))
        verify(!playing.start(true))
    }
    // A player created while stopped cannot play until visibility returns.
    val backgroundAutoplay = MobilePlayerLifecycleState(false, true)
    verify(!backgroundAutoplay.foreground)
    verify(!backgroundAutoplay.stop(false))
    verify(backgroundAutoplay.start(true))
    val backgroundPrompt = MobilePlayerLifecycleState(false, false)
    verify(!backgroundPrompt.start(true))
    // Repeated visible/hidden cycles maintain the actual user intent at stop.
    repeat(1000) { index ->
        val wanted = index % 3 == 0
        verify(playing.stop(wanted))
        verify(playing.start(true) == wanted)
    }
    println("Lifecycle intent: $checks checks passed")
}
