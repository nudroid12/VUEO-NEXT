const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict');
const script=fs.readFileSync(process.argv[2] || 'shared/core/src/main/assets/provider_frame_interaction.js','utf8').replace('__VUEO_FRAME_PLAY_SELECTORS__',JSON.stringify(['button#bigPlay'])).replace('__VUEO_PLAYER_FRAME_SELECTORS__',JSON.stringify(['iframe#player_frame']));
function frame(seed){
 let clock=1000,button=null,player=null,clicks=0,domCallback,intervalCallback,stopCallback,observerCallback,disconnected=false;
 const events=[];
 const document={readyState:'loading',querySelector:s=>s==='button#bigPlay'?button:s==='iframe#player_frame'?player:null,
  addEventListener:(name,cb)=>{if(name==='DOMContentLoaded')domCallback=cb;},removeEventListener:()=>{}};
 const window={vueoCapture:{capture:s=>events.push(JSON.parse(s.split('|')[1]))},getComputedStyle:el=>el.style,addEventListener:()=>{}};
 Object.defineProperty(window,'parent',{get(){throw Error('Must not traverse another origin');}});
 const env={window,document,Date:{now:()=>clock},Math:{random:()=>seed},WeakMap,
  MutationObserver:class{constructor(cb){observerCallback=cb;}observe(){}disconnect(){disconnected=true;}},
  setInterval:cb=>{intervalCallback=cb;return 1;},clearInterval:()=>{intervalCallback=null;},
  setTimeout:cb=>{stopCallback=cb;return 2;},clearTimeout:()=>{}};
 vm.runInNewContext(script,env,{timeout:1000});
 return {events,ready(){document.readyState='interactive';domCallback();},
  showPlay(){button={tagName:'BUTTON',disabled:false,style:{display:'block',visibility:'visible',opacity:'1'},getAttribute:()=>null,getBoundingClientRect:()=>({width:100,height:100}),click(){clicks++;button.style.display='none';player={tagName:'IFRAME',getAttribute:k=>k==='src'?'/embed/player/movie/fixture?vs=private':null};}};observerCallback();},
  showUnrelated(){button={tagName:'INPUT',style:{display:'block'},getAttribute:()=>null};observerCallback();},
  poll(){clock+=1000;if(intervalCallback)intervalCallback();},stop(){stopCallback();},clicks:()=>clicks,disconnected:()=>disconnected};
}
const top=frame(.1),nested=frame(.2);top.ready();nested.showPlay();assert.equal(nested.clicks(),0,'Wait for DOM handlers before clicking');
nested.ready();nested.poll();assert.equal(nested.clicks(),1);assert.equal(top.clicks(),0);
assert.ok(nested.events.some(e=>e.event==='player-frame'));assert.ok(nested.disconnected(),'Stop observing after the actual player iframe appears');
assert.equal(nested.events.filter(e=>e.event==='play-click').length,1);
const late=frame(.3);late.ready();for(let i=0;i<7;i++)late.poll();late.showPlay();late.poll();assert.equal(late.clicks(),1,'A Play button appearing after six seconds must activate');
const unrelated=frame(.4);unrelated.ready();unrelated.showUnrelated();unrelated.poll();assert.equal(unrelated.clicks(),0,'No generic form/verification controls');
const empty=frame(.5);empty.ready();empty.stop();assert.ok(empty.disconnected());
for(const e of [...top.events,...nested.events,...late.events])assert.deepEqual(Object.keys(e).sort(),['event','id']);
console.log('Frame-local interaction: late iframe Play, readiness, isolation, single activation, player detection, cleanup and content-free diagnostics passed.');
