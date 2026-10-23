package com.vueo.shared.core.trakt
import android.content.Context
import com.vueo.shared.core.media.MediaItem
class TraktService {
 data class Watch(val media:MediaItem,val watched:Boolean,val profile:String)
 val changes=mutableListOf<Watch>()
 fun watchedChanged(m:MediaItem,w:Boolean,p:String){changes+=Watch(m,w,p)}
 fun watchlistChanged(m:MediaItem,w:Boolean,p:String){}
 companion object { val service=TraktService(); fun get(c:Context)=service }
}
