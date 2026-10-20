package com.vueo.shared.core.player
import kotlin.random.Random
import org.junit.Test
class SmartSubsCueTimelineTest {
@Test fun `cue index matches ordered scan across seek and overlap boundaries`() {
 var n=0
 fun verify(v:Boolean){check(v);n++}
 val t=SmartSubsCueTimeline(listOf(
  SmartSubsCueTimeline.Span(0,1000,listOf("a")),
  SmartSubsCueTimeline.Span(100,200,listOf("b")),
  SmartSubsCueTimeline.Span(100,300,listOf("c")),
  SmartSubsCueTimeline.Span(-1,200,listOf("invalid")),
  SmartSubsCueTimeline.Span(100,100,listOf("empty")),
 ))
 verify(t.at(-1).isEmpty());verify(t.at(0)==listOf("a"))
 verify(t.at(100)==listOf("a","b","c"));verify(t.at(200)==listOf("a","c"))
 verify(t.at(300)==listOf("a"));verify(t.at(1000).isEmpty())
 // Compare overlapping intervals and arbitrary seeks to a simple independent scan.
 val rng=Random(44)
 repeat(100) {
  val spans=List(100){i->val start=rng.nextLong(0,10000);SmartSubsCueTimeline.Span(start,start+rng.nextLong(1,3000),listOf(i))}
  val index=SmartSubsCueTimeline(spans)
  repeat(100) {
   val pos=rng.nextLong(-1000,14000)
   verify(index.at(pos)==spans.sortedBy{it.startUs}.filter{pos>=it.startUs && pos<it.endUs}.flatMap{it.values})
  }
 }
 println("Cue timeline: $n checks passed")
}

}
