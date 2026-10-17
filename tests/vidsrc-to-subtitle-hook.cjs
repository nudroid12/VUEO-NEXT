const fs=require('fs'),vm=require('vm'),assert=require('assert');
 const kt=fs.readFileSync(require('path').join(__dirname,'../shared/core/src/main/java/com/vueo/shared/core/plugin/PluginWebViewResolver.kt'),'utf8');
 const raw=kt.split('private const val HOOK_JS = """')[1].split('"""')[0].replace('<script>','').replace('</script>','');
 function hook(enabled) {
  let captured=[];let tracks=[{file:'https://subs.test/en.vtt',label:'English',kind:'captions'},{file:'https://subs.test/thumb.vtt',kind:'thumbnails'}];
  let s={URL,location:{href:'https://vsembed.ru/embed/movie/1288069/'},document:{baseURI:'https://vsembed.ru/',querySelectorAll:()=>[]},setTimeout(){},setInterval(){},vueoCapture:{capture:x=>captured.push(x)},jwplayer:()=>({getPlaylist:()=>[{tracks}]})};s.window=s;
  vm.runInNewContext(raw.replaceAll('__VUEO_CAPTURE_SUBTITLES__',String(enabled)),s);
  return captured.filter(x=>x.startsWith('VUEO_SUBTITLE|')).map(x=>JSON.parse(x.slice('VUEO_SUBTITLE|'.length)));
 }
 let rows=hook(true);assert.equal(rows.length,1);assert.equal(rows[0].label,'English');assert.equal(rows[0].url,'https://subs.test/en.vtt');assert.equal(hook(false).length,0);
 assert(kt.includes('.replace("__VUEO_CAPTURE_SUBTITLES__", request.captureSubtitles.toString())'));

console.log('PASS: actual hook JS syntax, opt-in on/off, JW track capture and thumbnail exclusion.');
