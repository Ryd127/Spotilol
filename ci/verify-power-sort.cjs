'use strict';
const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'../app/src/main/java/com/project/lol');
function payload(name){
 const text=fs.readFileSync(path.join(root,'webview/injections',name+'.kt'),'utf8');
 return text.match(/const val CONTENT = """([\s\S]*?)"""/)[1].replaceAll("${'$'}",'$');
}
function powerEnvironment(pref=false){
 const videos=[],observers=[],listeners=new Map(),intervals=[];
 class Video {
  constructor({canvas=false,paused=false,id='',tag='VIDEO',ownPlay=false}={}){
   Object.assign(this,{canvas,paused,id,tagName:tag,isConnected:true,ended:false,plays:0,pauses:0,loads:0,currentTime:12});
   this.attrs=new Map([['src','https://fixture.invalid/canvas.mp4']]);
   if(ownPlay)Object.defineProperty(this,'play',{value:this.play,configurable:true,writable:true,enumerable:true});
   videos.push(this);
  }
  closest(){return this.canvas?{}:null;}
  querySelectorAll(){return [];}
  getAttribute(k){return this.attrs.has(k)?this.attrs.get(k):null;}
  setAttribute(k,v){this.attrs.set(k,String(v));}
  removeAttribute(k){this.attrs.delete(k);}
  pause(){this.paused=true;this.pauses++;}
  play(){this.paused=false;this.plays++;return Promise.resolve();}
  load(){this.loads++;}
 }
 const state={Promise,Map,Object,navigator:{},__splPowerSavePref:pref,
  setInterval(...args){intervals.push(args);return intervals.length;},clearInterval(){},
  setTimeout(){},requestAnimationFrame(){},
  document:{visibilityState:'visible',documentElement:{},querySelectorAll:()=>videos.filter(v=>v.isConnected&&v.tagName==='VIDEO'),
   addEventListener(type,fn){if(!listeners.has(type))listeners.set(type,[]);listeners.get(type).push(fn);}},
  MutationObserver:class {constructor(fn){this.fn=fn;observers.push(this);}observe(){this.connected=true;}disconnect(){this.connected=false;}}
 };
 state.window=state;vm.createContext(state);
 const timers=[state.setInterval,state.setTimeout,state.requestAnimationFrame,state.clearInterval];
 const run=name=>vm.runInContext(payload(name),state);
 run('WorkerNeutralize');run('PowerSave');run('VideoPark');
 function emit(type,target){for(const fn of listeners.get(type)||[])fn({target});}
 function mutation(record){for(const o of observers.filter(o=>o.connected))o.fn([record]);}
 return {state,videos,Video,observers,listeners,intervals,timers,run,emit,mutation};
}
function checkPower(){
 const e=powerEnvironment(),s=e.state;
 assert.equal(e.observers.length,0,'No foreground Canvas observer is needed while power saving is off');
 const canvas=new e.Video({canvas:true,ownPlay:true}),own=Object.getOwnPropertyDescriptor(canvas,'play');
 canvas.setAttribute('preload','metadata');
 const paused=new e.Video({canvas:true,paused:true});
 const music=new e.Video();music.muted=true;music.setAttribute('loop','');music.style={objectFit:'cover'};
 const custom=new e.Video({canvas:true,id:'spl-canvas'}),audio=new e.Video({tag:'AUDIO'});
 s.__splApplyPowerSave(true);
 assert(canvas.paused&&paused.paused);assert.equal(canvas.getAttribute('preload'),'none');
 assert.equal(music.pauses+custom.pauses+audio.pauses,0,'Muted/looping music/podcast/custom video and audio must not be classified as Canvas');
 assert.deepEqual([s.setInterval,s.setTimeout,s.requestAnimationFrame,s.clearInterval],e.timers,'Global scheduler was wrapped');
 let result;for(const delay of [0,100,250,500,1000,4999,6000,10000]){
  const token={delay};s.setInterval((...args)=>{result=args;},delay,'track',token);
  const call=e.intervals.at(-1);assert.equal(call[1],delay);call[0](...call.slice(2));assert.deepEqual(result,['track',token]);
 }
 assert.equal(e.observers.filter(o=>o.connected).length,1);
 s.__splApplyPowerSave(false);
 assert.equal(canvas.plays,1);assert.equal(paused.plays,0,'Initially paused Canvas must stay paused');
 assert.deepEqual(Object.getOwnPropertyDescriptor(canvas,'play'),own);
 assert.equal(canvas.getAttribute('preload'),'metadata');assert.equal(paused.getAttribute('preload'),null);
 assert.equal(canvas.currentTime,12);assert.equal(canvas.loads,0,'Canvas restore must not reload its source');
 s.__splApplyPowerSave(true);canvas.setAttribute('src','https://fixture.invalid/new-track.mp4');
 canvas.setAttribute('preload','auto');s.__splApplyPowerSave(false);
 assert.equal(canvas.plays,1,'Old playback state must not start a replacement track source');assert.equal(canvas.getAttribute('preload'),'auto');
 s.__splApplyPowerSave(true);canvas.setAttribute('src','https://fixture.invalid/requested-new.mp4');canvas.play();
 s.__splApplyPowerSave(false);assert.equal(canvas.plays,2,'A deliberate play request for the new Canvas source must not be stranded');
 s.__splParkVideos();assert(canvas.paused);s.document.visibilityState='hidden';s.__splRestoreVideos();
 assert(canvas.paused,'Activity resume must not override an actually hidden document');
 s.document.visibilityState='visible';e.emit('visibilitychange');
 assert.equal(e.observers.filter(o=>o.connected).length,0);
 s.__splApplyPowerSave(true);
 const added=new e.Video({canvas:true});e.mutation({type:'childList',addedNodes:[added]});assert(added.paused);
 const descriptor=added.play;added.isConnected=false;e.mutation({type:'childList',addedNodes:[]});
 assert.notEqual(added.play,descriptor,'Removed nodes must release saved closures');assert.equal(added.plays,0);
 const reclassified=new e.Video();reclassified.canvas=true;
 e.mutation({type:'attributes',target:reclassified});assert(reclassified.paused);
 reclassified.canvas=false;reclassified.play();assert.equal(reclassified.plays,1,'Reused non-Canvas elements must regain real play');
 const fresh=new e.Video({canvas:true,paused:true});fresh.paused=false;e.emit('play',fresh);assert(fresh.paused);
 const blob=new e.Video({canvas:true});blob.setAttribute('src','blob:fixture');e.mutation({type:'childList',addedNodes:[blob]});
 s.__splApplyPowerSave(false);assert.equal(blob.getAttribute('src'),'blob:fixture');assert.equal(blob.loads,0);
 assert.equal(fresh.plays,1,'An explicit intercepted play request can resume when saving is disabled');
 for(let i=0;i<20;i++){s.__splApplyPowerSave(true);s.__splApplyPowerSave(true);s.__splApplyPowerSave(false);}
 assert.equal(e.observers.filter(o=>o.connected).length,0);
 e.run('PowerSave');e.run('VideoPark');assert.equal(e.listeners.get('visibilitychange').length,1);
 const initial=powerEnvironment(true);assert.equal(initial.observers.filter(o=>o.connected).length,1,'Document-start preference was lost');
 console.log('PASS: unchanged timers/arguments; Canvas-only suppression; source/timecode/method preservation; background/foreground/toggle/DOM lifecycle.');
}
function sortEnvironment(items,{album=false,field='ARTIST',order='ASC',total=items.length,enabled=true}={}){
 const uri='spotify:'+(album?'album':'playlist')+':Fixture',requests=[],store=new Map();
 if(field)store.set('spotilol_playlist_sort',JSON.stringify({[uri]:{field,order}}));
 const empty={appendChild(){},querySelector:()=>null,querySelectorAll:()=>[]};
 const state={Promise,Map,Set,WeakMap,Array,Object,Date,Headers,Response,
  __splPlaylistSortEnabled:enabled,location:{pathname:'/intl-ru/'+(album?'album':'playlist')+'/Fixture',reload(){}},
  localStorage:{getItem:k=>store.get(k)||null,setItem:(k,v)=>store.set(k,v)},sessionStorage:{getItem:()=>null,setItem(){}},
  document:{body:empty,head:empty,documentElement:empty,createElement:()=>({...empty}),getElementById:()=>null,
   querySelector:()=>null,querySelectorAll:()=>[],addEventListener(){}},
  MutationObserver:class {observe(){}},setTimeout(){},clearTimeout(){},setInterval(){},
  fetch:async(input,init)=>{
   const request=JSON.parse(init.body),v=request.variables;requests.push(request);
   const page=items.slice(v.offset,v.offset+v.limit),content={items:page,totalCount:total};
   return new Response(JSON.stringify({data:album?{albumUnion:{name:'Album',tracksV2:content}}:{playlistV2:{name:'Playlist',content}}}));
  }
 };
 state.window=state;vm.createContext(state);vm.runInContext(payload('PlaylistSort'),state);
 async function fetchPage(offset=0,limit=50){
  const r=await state.fetch('https://api-partner.spotify.com/pathfinder/v2/query',{method:'POST',body:JSON.stringify({
   operationName:album?'getAlbum':'fetchPlaylist',variables:{uri,offset,limit}})});
  const j=await r.json();return album?j.data.albumUnion.tracksV2:j.data.playlistV2.content;
 }
 return {state,requests,store,uri,fetchPage};
}
async function checkSort(){
 const song=(id,name,artists)=>({id,itemV2:{data:{name,artists}}});
 const items=[song('z','Alpha',{items:[{profile:{name:'Zebra'}}]}),song('a','Zulu',[{name:'Alpha'}]),
  song('b','Beta',{items:[{profile:{name:'Alpha'}}]}),song('m','Missing',null),
  song('duet','Duet',{items:[{profile:{name:'Beta'}},{name:'Zebra'}]})];
 const original=JSON.stringify(items),ids=content=>content.items.map(x=>x.id);
 const asc=sortEnvironment(items);assert.equal(asc.state.splPlaylistSort.available(),true);
 assert.deepEqual(ids(await asc.fetchPage()),['a','b','duet','z','m']);
 assert.equal(JSON.stringify(items),original,'Sort mutated incoming metadata');
 assert.deepEqual(ids(await sortEnvironment(items,{order:'DESC'}).fetchPage()),['z','duet','a','b','m']);
 assert.deepEqual(ids(await sortEnvironment(items,{field:'TITLE_AND_ARTIST'}).fetchPage()),['z','b','duet','m','a'],'Existing title key lost compatibility');
 const album=items.map(x=>({id:x.id,track:x.itemV2.data}));
 assert.deepEqual(ids(await sortEnvironment(album,{album:true}).fetchPage()),['a','b','duet','z','m']);
 const none=sortEnvironment(items,{field:null});assert.deepEqual(ids(await none.fetchPage()),items.map(x=>x.id));
 const disabled=sortEnvironment(items,{enabled:false});await disabled.fetchPage();assert.equal(disabled.requests[0].variables.limit,50);
 asc.state.splPlaylistSort.cycle('ARTIST');assert.equal(asc.state.splPlaylistSort.get().order,'DESC');
 asc.state.splPlaylistSort.cycle('ARTIST');assert.equal(asc.state.splPlaylistSort.get(),null);
 asc.state.splPlaylistSort.set('ARTIST','ASC');assert.equal(JSON.parse(asc.store.get('spotilol_playlist_sort'))[asc.uri].field,'ARTIST');
 asc.state.location.pathname='/playlist/Other';asc.state.splPlaylistSort.set('ARTIST','DESC');
 const saved=JSON.parse(asc.store.get('spotilol_playlist_sort'));assert.equal(saved[asc.uri].order,'ASC');assert.equal(saved['spotify:playlist:Other'].order,'DESC');
 const many=Array.from({length:1001},(_,i)=>song(String(i),'Track '+i,{items:[{profile:{name:'Artist '+String(i).padStart(4,'0')}}]}));
 for(const field of [null,'ARTIST']){
  const e=sortEnvironment(many,{field});assert.equal((await e.fetchPage()).items.length,50);
  assert.equal((await e.fetchPage(field?1000:50)).items.length,field?1:50,'A later page was silently replaced by an empty cache slice');
  assert.equal(e.requests.length,2);
 }
 assert(payload('PlaylistSort').includes("['ARTIST', 'ARTIST', 'Artist']"),'Artist is not exposed in the existing columns menu');
 console.log('PASS: playlist/album Artist ASC/DESC, multiple artists, stable ties, nulls, title compatibility, per-URI persistence, locale routes and pagination beyond cached ranges.');
}
checkPower();checkSort().catch(error=>{console.error(error);process.exitCode=1;});
