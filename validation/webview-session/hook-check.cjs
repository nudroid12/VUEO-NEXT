const fs=require('fs'),vm=require('vm'),assert=require('assert'),path=require('path');
const resolver=fs.readFileSync(path.join(__dirname,'../../shared/core/src/main/java/com/vueo/shared/core/plugin/PluginWebViewResolver.kt'),'utf8');
const hook=resolver.split('private const val HOOK_JS = """')[1].split('"""')[0].split('<script>')[1].split('</script>')[0];
function load(wait,allowBlob=false,responses=false){
 let messages=[],ticks=[];const video={tagName:'VIDEO',currentSrc:'https://media.example/movie.mp4',src:'',readyState:0,networkState:2,paused:true,error:null,getAttribute(){return '';}};
 class MediaSource {constructor(){this.sourceBuffers=[];}addSourceBuffer(){const b={};this.sourceBuffers.push(b);return b;}}
 class TestURL extends URL {static createObjectURL(){return 'blob:https://player.example/owned-'+(++this.sequence);}}TestURL.sequence=0;
 class XHR {open(method,url){this.responseURL=url;}send(){if(this.listener)this.listener.call(this);}addEventListener(name,fn){if(name==='load')this.listener=fn;}getResponseHeader(k){return k==='content-type'?this.mime:'1024';}}
 const s={fetch:async r=>r,MediaSource,document:{title:'VidLink',body:{textContent:''},visibilityState:'hidden',baseURI:'https://player.example/',querySelectorAll(sel){return (sel==='video,source'||sel==='video')?[video]:[];},documentElement:{}},vueoCapture:{capture:v=>messages.push(v)},setTimeout:fn=>ticks.push(fn),setInterval:fn=>ticks.push(fn),URL:TestURL,MutationObserver:class{observe(){}},Element:class{setAttribute(){}},XMLHttpRequest:XHR,console};
 s.window=s;vm.runInNewContext(hook.replaceAll('__VUEO_CAPTURE_SUBTITLES__','false').replaceAll('__VUEO_WAIT_FOR_MEDIA_READY__',String(wait)).replaceAll('__VUEO_ALLOW_BLOB_MEDIA_READY__',String(allowBlob)).replaceAll('__VUEO_CAPTURE_SESSION_RESPONSES__',String(responses)).replace('["__VUEO_JSON_RESPONSE_PREFIXES__"]',JSON.stringify(['https://player.example/api/movie/'])),s);
 return {video,messages,document:s.document,scope:s,tick(){ticks.slice().forEach(f=>f());}};
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

function owned(x) {const mse=new x.scope.MediaSource();x.video.currentSrc=x.scope.URL.createObjectURL(mse);return mse;}
function blobState(x) {return x.messages.filter(m=>m.startsWith('VUEO_BLOB_READY|')).slice(-1)[0];}
x=load(true,true);let mse=owned(x);x.video.readyState=4;x.tick();assert.equal(blobState(x),'VUEO_BLOB_READY|false','MSE without source buffer rejected');
mse.addSourceBuffer('video/mp4');x.tick();assert.equal(blobState(x),'VUEO_BLOB_READY|true');assert(!x.messages.some(m=>m.startsWith('VUEO_MEDIA_READY|blob:')));
x.video.error={code:2};x.tick();assert.equal(blobState(x),'VUEO_BLOB_READY|false','error revokes blob readiness');
x.video.error=null;x.video.readyState=1;x.tick();assert.equal(blobState(x),'VUEO_BLOB_READY|false');
x.video.readyState=2;x.tick();assert.equal(blobState(x),'VUEO_BLOB_READY|true');
x.video.currentSrc='blob:https://player.example/unknown';x.tick();assert.equal(blobState(x),'VUEO_BLOB_READY|false','unowned blob rejected');
x=load(true,true);mse=owned(x);mse.addSourceBuffer('video/mp4');x.video.readyState=4;let query=x.document.querySelectorAll;x.document.querySelectorAll=sel=>sel==='video'?[x.video,x.video]:query(sel);x.tick();assert.equal(blobState(x),'VUEO_BLOB_READY|false','multiple videos rejected');
x=load(true,false);owned(x).addSourceBuffer('video/mp4');x.video.readyState=4;x.tick();assert.equal(blobState(x),undefined,'explicit opt-in required');
x=load(false,true);owned(x).addSourceBuffer('video/mp4');x.video.readyState=4;x.tick();assert.equal(blobState(x),undefined,'legacy remains disabled');
console.log('PASS: owned MSE blob + source buffer + one healthy video with data; unknown blob/metadata/error/multiple videos rejected; false revokes readiness; opt-in only; no blob HTTP handoff');

function response(url,status,mime,text,length='1024') {return {url,status,headers:{get:k=>k==='content-type'?mime:length},clone:()=>({text:async()=>text})};}
async function settle(){for(let i=0;i<6;i++)await Promise.resolve();}
(async()=>{
 let x=load(true,false,true);
 const api='https://player.example/api/movie/token';
 await x.scope.fetch(response(api,200,'application/json','{"stream":{"playlist":"https://media.example/master.mpd"}}'));await settle();
 let json=x.messages.filter(m=>m.startsWith('VUEO_JSON_RESPONSE|'));assert.equal(json.length,1);assert.equal(JSON.parse(json[0].slice('VUEO_JSON_RESPONSE|'.length)).url,api);
 await x.scope.fetch(response(api,200,'application/json','{}'));await settle();assert.equal(x.messages.filter(m=>m.startsWith('VUEO_JSON_RESPONSE|')).length,1,'dedupe');
 for(const r of [response('https://other.example/api/movie/token',200,'application/json','{}'),response('https://player.example/api/movie2/token',200,'application/json','{}'),response(api+'2',403,'application/json','{}'),response(api+'3',200,'application/json','x'.repeat(262145)),response(api+'4',200,'application/json','[]')]){await x.scope.fetch(r);await settle();}
 assert.equal(x.messages.filter(m=>m.startsWith('VUEO_JSON_RESPONSE|')).length,1,'wrong prefix/status/oversize/array rejected');
 await x.scope.fetch(response('https://media.example/master.mpd',200,'application/dash+xml','<MPD/>'));await settle();
 assert(x.messages.some(m=>m.startsWith('VUEO_HTTP_RESPONSE|')&&JSON.parse(m.slice('VUEO_HTTP_RESPONSE|'.length)).mime==='application/dash+xml'));
 const before=x.messages.filter(m=>m.startsWith('VUEO_HTTP_RESPONSE|')).length;
 await x.scope.fetch(response('https://media.example/fail.mpd',428,'application/dash+xml',''));await settle();assert.equal(x.messages.filter(m=>m.startsWith('VUEO_HTTP_RESPONSE|')).length,before);
 let xhr=new x.scope.XMLHttpRequest();xhr.open('GET',api+'-xhr');xhr.mime='application/json';xhr.status=200;xhr.responseType='json';xhr.response={stream:{}};xhr.send();assert(x.messages.some(m=>m.startsWith('VUEO_JSON_RESPONSE|')&&m.includes('-xhr')));
 xhr=new x.scope.XMLHttpRequest();xhr.open('GET',api+'-text');xhr.mime='application/json';xhr.status=200;xhr.responseType='text';xhr.responseText='{"stream":{}}';xhr.send();assert(x.messages.some(m=>m.startsWith('VUEO_JSON_RESPONSE|')&&m.includes('-text')));
 x=load(true,false,false);await x.scope.fetch(response(api,200,'application/json','{}'));await settle();assert(!x.messages.some(m=>/^VUEO_(JSON|HTTP)_RESPONSE/.test(m)));
 console.log('PASS: actual fetch/XHR hooks capture bounded allowlisted successful JSON and completed DASH responses; wrong prefix/status/oversize/array/dedup rejection; new capture disabled by default; no additional fetch');
})().catch(e=>{console.error(e);process.exit(1)});
