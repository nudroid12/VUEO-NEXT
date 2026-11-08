package com.vueo.tv.player
import androidx.media3.common.text.Cue
fun main(){
 val dialog=Cue.Builder().setText("Dialogue").build()
 val info=Cue.Builder().setText("Info").setLine(.82f,Cue.LINE_TYPE_FRACTION).build()
 for(input in listOf(listOf(dialog,info),listOf(info,dialog))){
  val x=ExtractedLayers.splitLowerSubtitleLayers(input,true)
  check(x.upperCues.isEmpty())
  check(x.lowerCues.contains(dialog)&&x.lowerCues.contains(info))
 }
 val comment=info.buildUpon().setText("(Comment)").build()
 val on=ExtractedLayers.splitLowerSubtitleLayers(listOf(dialog,info,comment),true)
 check(on.upperCues==listOf(comment)&&on.upperUsesCommentarySize)
 check(on.lowerCues.contains(info)&&on.lowerCues.contains(dialog))
 val off=ExtractedLayers.splitLowerSubtitleLayers(listOf(dialog,info,comment),false)
 check(off.upperCues.isEmpty()&&!off.lowerCues.contains(comment))
 val ordinary=comment.buildUpon().setText("(First) followed by speech").build()
 check(ExtractedLayers.splitLowerSubtitleLayers(listOf(dialog,ordinary),true).upperCues.isEmpty())
 println("PASS no last-cue selection, both source orders, commentary on/off, commentary size flag, partial parentheses")
}
