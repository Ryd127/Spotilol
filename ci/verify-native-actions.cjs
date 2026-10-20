'use strict';
const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict');
const source=fs.readFileSync(require('node:path').join(__dirname,'../app/src/main/java/com/project/lol/ui/components/NativeActionsJs.kt'),'utf8');
const script=source.match(/const val INSTALL = """([\s\S]*?)"""/)[1].replaceAll("${'$'}",'$');
const song='spotify:track:1234567890123456789012',other='spotify:track:2234567890123456789012';
const playlist='spotify:playlist:1234567890123456789012';
const turns=()=>new Promise(r=>setImmediate(r));
const response=(data,status=200)=>({ok:status>=200&&status<300,status,text:async()=>data==null?'':JSON.stringify(data)});
function env(handler){
 const calls=[],timers=new Map();let timer=0;
 const s={window:null,location:{origin:'https://open.spotify.com'},Map,Set,Promise,Date,AbortController,
  spotAuthToken:'Bearer synthetic-fixture-not-real',setTimeout(f,ms){timers.set(++timer,{f,ms});return timer;},clearTimeout(i){timers.delete(i);},
  splBestImage:()=>'',mngFetch:async(url,opts)=>{calls.push({url,...opts});return handler(url,opts);}};
 s.window=s;vm.runInNewContext(script,s);
 const start=(id,kind,extra={})=>s.splNativeAction({id:id.padEnd(8,'_'),kind,...extra});
 const result=(id,consume=false)=>JSON.parse(s.splNativeActionResult(id.padEnd(8,'_'),consume)||'null');
 return {s,calls,timers,start,result};
}
async function run(){
 let release;
 const e=env((url,o)=>new Promise(r=>{release=()=>r(response(null));}));
 e.start('save','setSaved',{uri:song,saved:true});await turns();
 assert.equal(e.result('save').state,'pending');assert.equal(e.s.splNativeSavedState(song),null,'Unconfirmed save painted a check');
 e.start('save2','setSaved',{uri:song,saved:true});await turns();
 assert.equal(e.result('save2').code,'busy');assert.equal(e.calls.length,1,'Double tap sent another mutation');
 release();await turns();assert.equal(e.result('save',true).saved,true);assert.equal(e.s.splNativeSavedState(song),true);
 e.start('save','setSaved',{uri:song,saved:true});await turns();
 assert.equal(e.calls.length,1,'Consumed mutation ID was executed again');
 assert.equal(e.result('save').state,'success');
 assert.equal(e.calls[0].method,'PUT');assert.equal(new URL(e.calls[0].url).searchParams.get('uris'),song);
 assert.equal(e.calls[0].noRetry,true,'Mutation used a retrying native transport');
 assert(!JSON.stringify(e.result('save')).includes('Bearer'),'Credential leaked across UI boundary');
 const denied=env(()=>response({error:{status:403}},403));denied.start('denied','setSaved',{uri:song,saved:true});await turns();
 assert.equal(denied.result('denied').code,'permission');assert.equal(denied.s.splNativeSavedState(song),null);
 const bad200=env(()=>response({error:{status:403}}));bad200.start('badreply','setSaved',{uri:song,saved:true});await turns();
 assert.equal(bad200.result('badreply').state,'error','JSON application error was treated as a save');
 let releaseRead;
 const race=env((url,o)=>o.method==='GET'?new Promise(r=>{releaseRead=()=>r(response([false]));}):response(null));
 race.start('readold','savedStatus',{uri:song});await turns();
 race.start('newsave','setSaved',{uri:song,saved:true});await turns();releaseRead();await turns();
 assert.equal(race.result('readold').code,'stale');assert.equal(race.s.splNativeSavedState(song),true,'Late read reversed a confirmed save');
 const create=env(()=>response({uri:playlist},201));create.start('create','createPlaylist',{name:'  My playlist  ',collaborative:true});await turns();
 assert.equal(create.result('create').uri,playlist);const body=JSON.parse(create.calls[0].body);
 assert.equal(body.name,'My playlist');assert.equal(body.public,false);assert.equal(body.collaborative,true);
 assert.equal(create.calls[0].noRetry,true);
 assert(create.calls[0].url.endsWith('/v1/me/playlists'));
 const add=env(()=>response({snapshot_id:'server-snapshot'},201));add.start('add','addToPlaylist',{uri:other,target:playlist});await turns();
 assert.equal(add.result('add').state,'success');assert(add.calls[0].url.endsWith('/playlists/1234567890123456789012/items'));
 assert.equal(JSON.parse(add.calls[0].body).uris[0],other,'Action applied to now-playing song instead of selected song');
 const invalid=env(()=>{throw Error('Must not request');});
 for(const [id,kind,extra] of [['x1','addToPlaylist',{uri:'spotify:album:1234567890123456789012',target:playlist}],
  ['x2','setSaved',{uri:'https://evil.invalid',saved:true}],['x3','createPlaylist',{name:'\n'}]]){
  invalid.start(id,kind,extra);assert.equal(invalid.result(id).code,'invalid');
 }
 assert.equal(invalid.calls.length,0);
 const rows=env(url=>url.endsWith('/me')?response({id:'my-user'}):response({items:[
  {uri:playlist,name:'Mine',owner:{id:'my-user'}},
  {uri:'spotify:playlist:2234567890123456789012',name:'Read-only',owner:{id:'someone'}},
  {uri:'spotify:playlist:3234567890123456789012',name:'Collaborative',owner:{id:'someone'},collaborative:true}],next:null}));
 rows.start('lists','playlists');await turns();
 assert.deepEqual(Array.from(rows.result('lists').playlists,x=>x.title),['Mine','Collaborative']);
 const hung=env(()=>new Promise(()=>{}));hung.start('timeout','createPlaylist',{name:'One'});await turns();
 [...hung.timers.values()].find(x=>x.ms===10000).f();
 assert.equal(hung.result('timeout').state,'unknown');assert.equal(hung.calls.length,1,'Mutation automatically retried');
 const missingId=env(()=>response({},201));missingId.start('noid','createPlaylist',{name:'One'});await turns();
 assert.equal(missingId.result('noid').state,'unknown','Ambiguous creation was presented as successful');
 const serverError=env(()=>response({error:{status:503}},503));serverError.start('server','createPlaylist',{name:'One'});await turns();
 assert.equal(serverError.result('server').state,'unknown','An ambiguous server failure invited duplicate creation');
 let releaseLate;
 const late=env(()=>new Promise(resolve=>{releaseLate=()=>resolve(response(null));}));
 late.start('late','setSaved',{uri:song,saved:true});await turns();
 [...late.timers.values()].find(x=>x.ms===10000).f();releaseLate();await turns();
 assert.equal(late.result('late').state,'unknown');assert.equal(late.s.splNativeSavedState(song),null,'Timed-out result silently changed UI state');
 let releaseAuth;
 const account=env(()=>new Promise(resolve=>{releaseAuth=()=>resolve(response(null));}));
 account.start('account','setSaved',{uri:song,saved:true});await turns();account.s.spotAuthToken='Bearer another-synthetic-account';
 releaseAuth();await turns();assert.equal(account.result('account').state,'unknown');assert.equal(account.s.splNativeSavedState(song),null);
 const badRead=env(()=>response({not:'a boolean array'}));badRead.start('badread','savedStatus',{uri:song});await turns();
 assert.equal(badRead.result('badread').state,'error');assert.equal(badRead.s.splNativeSavedState(song),null);
 const noAuth=env(()=>{throw Error('Must not fetch');});noAuth.s.spotAuthToken=null;
 noAuth.start('noauth','setSaved',{uri:song,saved:true});await turns();assert.equal(noAuth.result('noauth').code,'auth');assert.equal(noAuth.calls.length,0);
 const origin=env(()=>response(null));origin.s.location.origin='https://evil.invalid';
 assert.equal(origin.start('origin','setSaved',{uri:song,saved:true}),false);assert.equal(origin.calls.length,0);
 // Exercise the real JS -> native network adapter; the one-shot flag must survive it.
 const bridgeSource=fs.readFileSync(require('node:path').join(__dirname,'../app/src/main/java/com/project/lol/webview/injections/ClassicBridge.kt'),'utf8');
 const bridgeScript=bridgeSource.match(/const val CONTENT = """([\s\S]*?)"""/)[1];
 let forwarded;const bridgeEnv={window:null,Headers,TextEncoder,AndBridge:{nFetch:async(url,raw)=>{forwarded=JSON.parse(raw);return JSON.stringify({status:204,body:'',headers:{}});}}};
 bridgeEnv.window=bridgeEnv;vm.runInNewContext(bridgeScript,bridgeEnv);
 await bridgeEnv.mngFetch('https://api.spotify.com/v1/me/playlists',{method:'POST',body:'{}',noRetry:true});
 assert.equal(forwarded.noRetry,true);
 await bridgeEnv.mngFetch('https://api.spotify.com/v1/me',{method:'GET'});assert.equal(forwarded.noRetry,false);
 const nativeBridge=fs.readFileSync(require('node:path').join(__dirname,'../app/src/main/java/com/project/lol/bridge/SpotifyBridge.kt'),'utf8');
 const policy=fs.readFileSync(require('node:path').join(__dirname,'../app/src/main/java/com/project/lol/bridge/NativeRequestPolicy.kt'),'utf8');
 assert(policy.includes('retryOnConnectionFailure(false)')&&policy.includes('override fun isOneShot() = true')&&nativeBridge.includes('opts.optBoolean("noRetry", false)'));
 console.log('PASS: native library/playlist actions, confirmed-only saves, contextual targets, editable playlists, duplicate/stale/timeout/error/origin guards.');
}
run().catch(e=>{console.error(e);process.exitCode=1;});
