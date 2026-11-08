package com.vueo.tv.player

private fun overlaps(a: TvSubtitleCollisionLayout.Box,b: TvSubtitleCollisionLayout.Box,gap:Float)=
 a.left<b.right&&a.right>b.left&&a.top<b.bottom+gap&&a.bottom+gap>b.top
fun main(){
 val dialog=TvSubtitleCollisionLayout.Box(200f,790f,800f,880f)
 val info=TvSubtitleCollisionLayout.Box(300f,650f,700f,820f)
 val moved=TvSubtitleCollisionLayout.resolve(info,listOf(dialog),0f,1080f,12f)
 check(moved.top==608f && moved.left==info.left && moved.right==info.right)
 check(!overlaps(moved,dialog,12f))
 val mobileSpaced=info.copy(bottom=740f)
 check(TvSubtitleCollisionLayout.resolve(mobileSpaced,listOf(dialog),0f,1080f,12f)===mobileSpaced)
 val commentary=dialog.copy(top=700f,bottom=770f)
 val both=TvSubtitleCollisionLayout.resolve(info,listOf(dialog,commentary),0f,1080f,12f)
 check(listOf(dialog,commentary).none{overlaps(both,it,12f)})
 val sidebar=info.copy(left=900f,right=1000f)
 check(TvSubtitleCollisionLayout.resolve(sidebar,listOf(dialog),0f,1080f,12f)===sidebar)
 val cover=TvSubtitleCollisionLayout.Box(0f,0f,1080f,1080f)
 check(TvSubtitleCollisionLayout.resolve(info,listOf(cover),0f,1080f,12f)==info)
 val rng=java.util.Random(23)
 repeat(5000){
  val width=400f+rng.nextInt(1600);val h=300f+rng.nextInt(1000)
  val obstacle=TvSubtitleCollisionLayout.Box(0f,h*.8f,width,h*.94f)
  val tall=30f+rng.nextInt(150)
  val x=TvSubtitleCollisionLayout.Box(width*.2f,h*.75f,width*.8f,h*.75f+tall)
  val r=TvSubtitleCollisionLayout.resolve(x,listOf(obstacle),0f,h,8f)
  if(r!=x){check(r.top>=0&&r.bottom<=h);check(!overlaps(r,obstacle,8f));check(r.height==x.height)}
 }
 println("PASS collision shift, already-spaced identity, commentary obstacle, horizontal separation, no-room preservation, 5000 geometry cases")
}
