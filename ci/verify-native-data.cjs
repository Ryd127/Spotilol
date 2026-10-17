'use strict';
const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'../app/src/main/java/com/project/lol');
function script(file,key='CONTENT'){
 const text=fs.readFileSync(path.join(root,file),'utf8');
 const m=text.match(new RegExp('const val '+key+' = """([\\s\\S]*?)"""'));
 assert(m,file);return m[1].replaceAll("${'$'}",'$');
}
const art=(name,width)=>({url:'https://i.scdn.co/image/'+name,width,height:width});
const song={uri:'spotify:track:1234567890123456789012',name:'Real song',
 artists:{items:[{uri:'spotify:artist:ArtistA',profile:{name:'Real artist'}}]},
 albumOfTrack:{coverArt:{sources:[art('small',64),art('large',640)]}}};
const turns=()=>new Promise(resolve=>setImmediate(resolve));
function environment(){
 const timers=new Map(),notifications=[],requests=[],history=[];let timerId=0;
 const empty={querySelector:()=>null,querySelectorAll:()=>[],getAttribute:()=>null};
 const state={URL,Map,Set,WeakMap,Array,Promise,Date,AbortController,console:{error(){}},
  setTimeout(fn,ms){const id=++timerId;timers.set(id,{fn,ms});return id;},clearTimeout(id){timers.delete(id);},
  document:{querySelector:s=>s==='main'||s==='[data-testid="main-view-container"]'?empty:null,
   querySelectorAll:()=>[],getElementById:()=>null},
  location:{origin:'https://open.spotify.com',href:'https://open.spotify.com/',pathname:'/'},
  history:{pushState(a,b,p){history.push(p);}},PopStateEvent:class{},dispatchEvent(){},
  spotAuthToken:'Bearer test-fixture-not-a-real-token',spotCliToken:'test-client',opHash:()=>null,
  mediaLib:{playlists:[{id:'spotify:playlist:Saved',name:'Saved',image:art('saved',640).url}]},
  splIsLikedName:()=>false,splLikedName:()=> 'Liked songs',
  AndBridge:{onMediaItemsLoaded:(...x)=>notifications.push(x),onSearchCompleted:(...x)=>notifications.push(x)},
  playFromUri:(...x)=>{state.played=x;},
 };
 state.window=state;
 state.mngFetch=async(url,options)=>{
  const q=JSON.parse(options.body);requests.push(q);
  let data;
  if(q.operationName==='queryArtistOverview')data={artistUnion:{profile:{name:q.variables.uri.endsWith('ArtistB')?'Artist B':'Artist A'},
   visuals:{avatarImage:{sources:[art('avatar-small',64),art('avatar-large',640)]}},discography:{topTracks:{items:[{track:song}]}}}};
  else if(q.operationName==='searchDesktop')data={searchV2:{tracksV2:{items:[{item:{data:{...song,name:q.variables.searchTerm}}}]}}};
  else if(q.operationName==='fetchPlaylist')data={playlistV2:{name:'Playlist title',images:{items:[{sources:[art('playlist',640)]}]},content:{items:[{itemV2:{data:song}}]}}};
  else if(q.operationName==='getAlbum')data={albumUnion:{name:'Album title',coverArt:{sources:[art('album',640)]},tracksV2:{items:[{track:song}]}}};
  else throw Error('Unexpected operation '+q.operationName);
  return {ok:true,json:async()=>({data})};
 };
 vm.createContext(state);
 vm.runInContext(script('webview/injections/AndroidAuto.kt'),state);
 vm.runInContext(script('ui/components/NativeMobileJs.kt','INSTALL'),state);
 return {state,timers,notifications,requests,history};
}
async function run(){
 const e=environment(),s=e.state;
 const artist=await s.fetchMediaItems('spotify:artist:ArtistA',{native:true});
 assert.equal(artist.details.name,'Artist A');assert.equal(artist.details.image,art('avatar-large',640).url);
 assert.equal(artist.items[0].image,art('large',640).url);assert.equal(e.notifications.length,0,'Native browse leaked into Auto request callbacks');
 await s.fetchMediaItems('spotify:artist:ArtistA');assert.equal(e.notifications.length,1,'Android Auto callback was lost');
 assert.equal(s.splBestImage({sources:[art('small',64),art('large',640)]}),art('large',640).url);
 assert.equal(s.splBestImage([{url:'https://open.spotifycdn.com/equalizer-animated.gif',width:2000}]),'');
 s.splNativeNavigate('/artist/ArtistA');await turns();
 let page=JSON.parse(s.splNativeSnapshot());assert.equal(page.title,'Artist A');assert.equal(page.items.length,1);
 assert.equal(page.items[0].kind,'track','Artist page exposed unrelated artist/recommendation links');
 assert.equal(e.history.length,0,'Native detail browsing should not remount hidden React pages');
 s.splNativeNavigate('/search/'+encodeURIComponent('Дора & test'));await turns();
 page=JSON.parse(s.splNativeSnapshot());assert.equal(page.items[0].title,'Дора & test');
 assert.equal(page.items[0].image,art('large',640).url);assert.equal(page.error,'');
 s.splNativeRevealWeb();assert.equal(e.history.at(-1),'/search/'+encodeURIComponent('Дора & test'));
 s.splNativeNavigate('/album/AlbumA');await turns();page=JSON.parse(s.splNativeSnapshot());
 assert.equal(page.title,'Album title');assert.equal(page.items[0].kind,'track');
 s.splNativeNavigate('/playlist/PlaylistA');await turns();page=JSON.parse(s.splNativeSnapshot());
 assert.equal(page.title,'Playlist title');assert.equal(page.cover,art('playlist',640).url);
 // Deliberately ignore abort in this fixture: even an uncancellable late result cannot win.
 const race=environment();let releaseA;
 const regular=race.state.mngFetch;
 race.state.mngFetch=(url,o)=>JSON.parse(o.body).variables.uri==='spotify:artist:ArtistA'
  ?new Promise(resolve=>{releaseA=()=>regular(url,o).then(resolve);}):regular(url,o);
 race.state.splNativeNavigate('/artist/ArtistA');await turns();
 race.state.splNativeNavigate('/artist/ArtistB');await turns();releaseA();await turns();
 page=JSON.parse(race.state.splNativeSnapshot());assert.equal(page.title,'Artist B');assert.equal(page.path,'/artist/ArtistB');
 // A hung request becomes an explicit error, never somebody else's content or endless loading.
 const hung=environment();hung.state.mngFetch=()=>new Promise(()=>{});
 hung.state.splNativeNavigate('/artist/Hung');await turns();
 [...hung.timers.values()].find(t=>t.ms===8000).fn();
 page=JSON.parse(hung.state.splNativeSnapshot());assert.equal(page.loadingPage,false);assert.equal(page.error,'timeout');assert.equal(page.items.length,0);
 // Execute the real seek function against a React-like tracked range input.
 class Input {
  constructor(){this.raw='0';this.tracked='0';this.max='180000';this.min='0';this.disabled=false;
   Object.defineProperty(this,'value',{get:()=>this.raw,set:v=>{this.raw=String(v);this.tracked=String(v);}});}
  getAttribute(k){return this[k];}
  dispatchEvent(event){if(this.tracked!==this.raw){this.enginePosition=Number(this.raw);this.tracked=this.raw;}this.lastEvent=event.type;}
 }
 Object.defineProperty(Input.prototype,'value',{get(){return this.raw;},set(v){this.raw=String(v);}});
 const input=new Input(),seek={window:null,document:{querySelector:()=>input},HTMLInputElement:Input,
  Event:class{constructor(type){this.type=type;}},AndBridge:{dbg(){}},Number,Object,Math};seek.window=seek;
 vm.runInNewContext(script('webview/injections/PlaybackControls.kt'),seek);
 assert.equal(seek.actSeek(90000),true);assert.equal(input.enginePosition,90000,'React handler did not receive seek synchronously');
 seek.actSeek(999999);assert.equal(input.enginePosition,180000);
 assert.equal(seek.actSeek(NaN),false);input.disabled=true;assert.equal(seek.actSeek(1),false);
 console.log('PASS: real native browse/search parsers, query isolation, artwork selection, stale/timeout protection, Android Auto callbacks and React-compatible seek dispatch.');
}
run().catch(error=>{console.error(error);process.exitCode=1;});
