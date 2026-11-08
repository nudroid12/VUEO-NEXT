package androidx.media3.common.text
import android.text.Layout
data class Cue(
 val text:CharSequence?=null,val bitmap:Any?=null,val verticalType:Int=TYPE_UNSET,
 val line:Float=DIMEN_UNSET,val lineType:Int=TYPE_UNSET,val lineAnchor:Int=TYPE_UNSET,
 val position:Float=DIMEN_UNSET,val positionAnchor:Int=TYPE_UNSET,val size:Float=DIMEN_UNSET,
 val textAlignment:Layout.Alignment?=null,val multiRowAlignment:Layout.Alignment?=null,val shearDegrees:Float=0f
){
 companion object {
  const val TYPE_UNSET=Int.MIN_VALUE;const val DIMEN_UNSET=-Float.MAX_VALUE
  const val LINE_TYPE_FRACTION=0;const val LINE_TYPE_NUMBER=1
  const val ANCHOR_TYPE_START=0;const val ANCHOR_TYPE_MIDDLE=1;const val ANCHOR_TYPE_END=2
 }
 fun buildUpon()=Builder(this)
 class Builder(private var value:Cue=Cue()){
  fun setText(v:CharSequence)=apply{value=value.copy(text=v)}
  fun setLine(v:Float,t:Int)=apply{value=value.copy(line=v,lineType=t)}
  fun setLineAnchor(v:Int)=apply{value=value.copy(lineAnchor=v)}
  fun setPosition(v:Float)=apply{value=value.copy(position=v)}
  fun setPositionAnchor(v:Int)=apply{value=value.copy(positionAnchor=v)}
  fun setSize(v:Float)=apply{value=value.copy(size=v)}
  fun setTextAlignment(v:Layout.Alignment)=apply{value=value.copy(textAlignment=v)}
  fun setMultiRowAlignment(v:Layout.Alignment)=apply{value=value.copy(multiRowAlignment=v)}
  fun build()=value
 }
}
