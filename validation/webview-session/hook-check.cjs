const fs=require('fs'),vm=require('vm'),assert=require('assert'),path=require('path');
const resolver=fs.readFileSync(path.join(__dirname,'../../shared/core/src/main/java/com/vueo/shared/core/plugin/PluginWebViewResolver.kt'),'utf8');
const hook=resolver.split('private const val HOOK_JS = """')[1].split('"""')[0].split('<script>')[1].split('</script>')[0];
function load(wait){
 let messages=[],ticks=[];const video={tagName:'VIDEO',currentSrc:'https://media.example/movie.mp4',src:'',readyState:0,networkState:2,paused:true,error:null,getAttribute(){return '';}};
 const s={document:{title:'VidLink',body:{textContent:''},visibilityState:'hidden',baseURI:'https://player.example/',querySelectorAll(sel){return (sel==='video,source'||sel==='video')?[video]:[];},documentElement:{}},vueoCapture:{capture:v=>messages.push(v)},setTimeout:fn=>ticks.push(fn),setInterval:fn=>ticks.push(fn),URL:class extends URL{},MutationObserver:class{observe(){}},Element:class{setAttribute(){}},XMLHttpRequest:class{open(){}send(){}},console};
 s.window=s;vm.runInNewContext(hook.replaceAll('__VUEO_CAPTURE_SUBTITLES__','false').replaceAll('__VUEO_WAIT_FOR_MEDIA_READY__',String(wait)),s);
 return {video,messages,document:s.document,tick(){ticks.slice().forEach(f=>f());}};
}
let x=load(true);assert(x.messages.some(m=>m.startsWith('VUEO_VIDEO|')));assert(!x.messages.some(m=>m.startsWith('VUEO_MEDIA_READY|')));
x.video.readyState=1;x.tick();assert(!x.messages.some(m=>m.startsWith('VUEO_MEDIA_READY|')));
x.video.readyState=2;x.tick();assert(x.messages.includes('VUEO_MEDIA_READY|https://media.example/movie.mp4'));
x=load(true);x.video.readyState=2;x.video.error={code:4};x.tick();assert(!x.messages.some(m=>m.startsWith('VUEO_MEDIA_READY|')));
x=load(false);x.video.readyState=2;x.tick();assert(!x.messages.some(m=>m.startsWith('VUEO_MEDIA_READY|')));
x=load(true);x.video.readyState=2;x.video.currentSrc='blob:https://player.example/id';x.tick();assert(!x.messages.some(m=>m.startsWith('VUEO_MEDIA_READY|')));
console.log('PASS: actual injected hook requires video data; metadata-only/error/blob do not signal ready; opt-in disabled retains legacy messages');

function latest(x) {const rows=x.messages.filter(m=>m.startsWith('VUEO_MEDIA_STATE|'));return rows.length?JSON.parse(rows[rows.length-1].split('|')[1]):null;}
x=load(true);assert.equal(latest(x).readyState,0);assert.equal(latest(x).networkState,2);assert.equal(latest(x).sourceKind,'https');assert.equal(latest(x).visibility,'hidden');
let before=x.messages.length;x.tick();assert.equal(x.messages.filter(m=>m.startsWith('VUEO_MEDIA_STATE|')).length,1,'unchanged state emits once');
x.video.error={code:4,message:'private https://secret.example/token=private'};x.document.body.textContent='Please Disable Sandbox session=private';x.tick();assert.equal(latest(x).errorCode,4);assert.equal(latest(x).pageSignal,'sandbox_required');
x.document.body.textContent='Verify you are human';x.tick();assert.equal(latest(x).pageSignal,'human_verification');
x.document.body.textContent='428 Forbidden';x.tick();assert.equal(latest(x).pageSignal,'access_denied');
x.document.body.textContent='Video unavailable';x.tick();assert.equal(latest(x).pageSignal,'unavailable');
x.video.currentSrc='blob:https://secret.example/private';x.tick();assert.equal(latest(x).sourceKind,'blob');
assert(!x.messages.filter(m=>m.startsWith('VUEO_MEDIA_STATE|')).join('').includes('private'));
x=load(false);assert(!x.messages.some(m=>m.startsWith('VUEO_MEDIA_STATE|')));
console.log('PASS: actual hook diagnostics identify video/error/blob/page signals, deduplicate unchanged state, omit private URLs/text and stay disabled for legacy requests');
