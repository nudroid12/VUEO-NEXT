package com.vueo.tv.player
import androidx.media3.common.text.Cue
import com.vueo.shared.core.player.SubtitleFlowDiagnostics
import com.vueo.shared.core.diagnostics.PerformanceDiagnostics
fun main(){
 val caption="Fixture secret words"
 val cue=Cue.Builder().setText(caption).setLine(-2f,Cue.LINE_TYPE_NUMBER).setPosition(.5f).build()
 val before=cue.copy()
 TvSubtitleCueDiagnostics.capture("https://fixture.invalid/?secret=token",1,"input",listOf(cue),"pad=0.08")
 check(SubtitleFlowDiagnostics.events.isEmpty())
 PerformanceDiagnostics.on=true
 TvSubtitleCueDiagnostics.capture("https://fixture.invalid/?secret=token",1,"input",List(20){cue},"pad=0.08")
 val events=SubtitleFlowDiagnostics.events
 check(events.size==9)
 check(events.drop(1).all{it.contains("line=-2.0")&&it.contains("rows=1")&&it.contains("pos=0.5")})
 check(events.none{it.contains(caption)||it.contains("secret=token")})
 check(cue==before)
 println("PASS diagnostics disabled silence, eight-cue bound, signed line/position evidence, caption/URL privacy, Cue unchanged")
}
