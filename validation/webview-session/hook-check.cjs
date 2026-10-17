const fs=require('fs'),vm=require('vm'),assert=require('assert'),path=require('path');
const resolver=fs.readFileSync(path.join(__dirname,'../../shared/core/src/main/java/com/vueo/shared/core/plugin/PluginWebViewResolver.kt'),'utf8');
const hook=resolver.split('private const val HOOK_JS = """')[1].split('"""')[0].split('<script>')[1].split('</script>')[0];
function load(wait){
 let messages=[],ticks=[];const video={tagName:'VIDEO',currentSrc:'https://media.example/movie.mp4',src:'',readyState:0,error:null,getAttribute(){return '';}};
 const s={document:{baseURI:'https://player.example/',querySelectorAll(sel){return sel==='video,source'?[video]:[];},documentElement:{}},vueoCapture:{capture:v=>messages.push(v)},setTimeout:fn=>ticks.push(fn),setInterval:fn=>ticks.push(fn),URL:class extends URL{},MutationObserver:class{observe(){}},Element:class{setAttribute(){}},XMLHttpRequest:class{open(){}send(){}},console};
 s.window=s;vm.runInNewContext(hook.replaceAll('__VUEO_CAPTURE_SUBTITLES__','false').replaceAll('__VUEO_WAIT_FOR_MEDIA_READY__',String(wait)),s);
 return {video,messages,tick(){ticks.slice().forEach(f=>f());}};
}
let x=load(true);assert(x.messages.some(m=>m.startsWith('VUEO_VIDEO|')));assert(!x.messages.some(m=>m.startsWith('VUEO_MEDIA_READY|')));
x.video.readyState=1;x.tick();assert(!x.messages.some(m=>m.startsWith('VUEO_MEDIA_READY|')));
x.video.readyState=2;x.tick();assert(x.messages.includes('VUEO_MEDIA_READY|https://media.example/movie.mp4'));
x=load(true);x.video.readyState=2;x.video.error={code:4};x.tick();assert(!x.messages.some(m=>m.startsWith('VUEO_MEDIA_READY|')));
x=load(false);x.video.readyState=2;x.tick();assert(!x.messages.some(m=>m.startsWith('VUEO_MEDIA_READY|')));
x=load(true);x.video.readyState=2;x.video.currentSrc='blob:https://player.example/id';x.tick();assert(!x.messages.some(m=>m.startsWith('VUEO_MEDIA_READY|')));
console.log('PASS: actual injected hook requires video data; metadata-only/error/blob do not signal ready; opt-in disabled retains legacy messages');
