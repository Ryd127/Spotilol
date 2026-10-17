'use strict';
const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'..'),source=path.join(root,'app/src/main/java/com/project/lol');
function walk(dir){return fs.readdirSync(dir,{withFileTypes:true}).flatMap(e=>e.isDirectory()?walk(path.join(dir,e.name)):[path.join(dir,e.name)]);}
const emitted=s=>s.replaceAll("${'$'}",'$');
function payload(name,key='CONTENT'){const text=fs.readFileSync(path.join(source,name),'utf8');const m=text.match(new RegExp('const val '+key+' = """([\\s\\S]*?)"""'));assert(m,name);return emitted(m[1]);}
let checked=0;
for(const file of walk(source).filter(f=>f.endsWith('.kt'))){
 const text=fs.readFileSync(file,'utf8');
 assert(!/^(<<<<<<<|>>>>>>>|\|\|\|\|\|\|\|) /m.test(text),'Merge marker: '+file);
 for(const match of text.matchAll(/const val (?:CONTENT|INSTALL) = """([\s\S]*?)"""/g)){
  new vm.Script(emitted(match[1]),{filename:path.relative(root,file)});checked++;
 }
}
// Test actual metadata dispatch: pacing must not suppress a seek or loop restart.
const calls=[],state={window:null,document:{querySelector:()=>null,querySelectorAll:()=>[]},location:{pathname:'/'},
 Date:{now:()=>state.now},setTimeout(){},Promise,AndBridge:{recMediaStatus:s=>calls.push(['status',JSON.parse(s)]),recMediaPosition:p=>calls.push(['position',p])},
 now:1000,track:'A',artist:'Artist',playing:true,repmode:'false',isfav:false,shuffle:false,duration:180000,position:0,cover:'https://i.scdn.co/image/a',lastState:null,lastPos:null};
state.window=state;
vm.runInNewContext(payload('webview/injections/MediaUpdater.kt'),state);
state.updMedia();assert.equal(calls.length,1);
for(let i=1;i<=80;i++){state.now=1000+i*10;state.position=i*10;state.updMedia();}
assert.equal(calls.length,1,'DOM burst flooded the native bridge');
state.now=1850;state.position=850;state.updMedia();assert.equal(calls.at(-1)[0],'position');
state.now=1860;state.position=25000;state.updMedia();assert.equal(calls.at(-1)[1],25000,'Seek not immediate');
state.now=1870;state.position=0;state.updMedia();assert.equal(calls.at(-1)[0],'status','Restart did not refresh metadata');
const count=calls.length;state.now=1880;state.position=10;state.updMedia();assert.equal(calls.length,count,'Restart loop floods status');
state.cover='https://i.scdn.co/image/new';state.updMedia();assert.equal(calls.at(-1)[1].cover,state.cover,'New cover suppressed');
const native={window:null,URL,Map,Set,WeakMap,Array,Promise,Date,
 location:{origin:'https://open.spotify.com',href:'https://open.spotify.com/',pathname:'/'},
 document:{querySelector:()=>null,querySelectorAll:()=>[]},history:{pushState:(a,b,p)=>native.navigated=p},
 PopStateEvent:class{},dispatchEvent(){},playFromUri:(...args)=>native.played=args};
native.window=native;
vm.runInNewContext(payload('ui/components/NativeMobileJs.kt','INSTALL'),native);
assert.equal(native.splNativeNavigate('/playlist/Ab12'),true);assert.equal(native.navigated,'/playlist/Ab12');
for(const p of ['/track/Ab12','https://evil.invalid/x','javascript:alert(1)'])assert.equal(native.splNativeNavigate(p),false);
assert.equal(native.splNativePlay('spotify:track:1234567890123456789012','spotify:album:Ab12'),true);
assert.equal(native.played[0],'spotify:track:1234567890123456789012');assert.equal(native.navigated,'/playlist/Ab12','Play unexpectedly navigated');
assert.equal(native.splNativePlay('bad',''),false);
// Anonymous Spotify shortcuts: overlay/title duplicates, six-level title
// wrappers, a separate artist link, and a neighbouring card's unrelated image.
class Element {
 constructor(tag,attrs={},children=[]){this.tag=tag;this.attrs=attrs;this.children=children;this.style={backgroundImage:attrs.background||''};for(const c of children)c.parentElement=this;}
 get href(){return this.attrs.href?new URL(this.attrs.href,native.location.origin).href:'';}
 get src(){return this.attrs.src||'';} get currentSrc(){return this.src;}
 get textContent(){return this.attrs.text||this.children.map(c=>c.textContent).join('');}
 getAttribute(k){return this.attrs[k]||null;}
 matches(query){return query.split(',').some(s=>{s=s.trim();return s===this.tag||(s==='a[href]'&&this.tag==='a'&&!!this.href)||(s==='img'&&this.tag==='img')||(s==='[style*="background"]'&&!!this.attrs.background)||s==='[data-testid="tracklist-row"]'&&this.attrs['data-testid']==='tracklist-row'||s==='[data-testid*="card"]'&&String(this.attrs['data-testid']).includes('card');});}
 querySelectorAll(s){return this.children.flatMap(c=>[...(c.matches(s)?[c]:[]),...c.querySelectorAll(s)]);}
 querySelector(s){return this.querySelectorAll(s)[0]||null;}
}
const title=new Element('a',{href:'/playlist/Good',text:'My playlist'}),overlay=new Element('a',{href:'/playlist/Good'});
let wrapper=title;for(let i=0;i<5;i++)wrapper=new Element('div',{},[wrapper]);
const cover=new Element('img',{src:'https://i.scdn.co/image/correct'});
const card=new Element('div',{},[overlay,cover,wrapper]);
const other=new Element('div',{},[new Element('a',{href:'/playlist/Other',text:'Other'}),new Element('img',{src:'https://i.scdn.co/image/wrong'})]);
const shelf=new Element('section',{},[card,other]);new Element('main',{},[shelf]);
const probe={...native,window:null,getComputedStyle:n=>n.style};probe.window=probe;
delete probe.splNativeUiRevision;
const nativeSource=payload('ui/components/NativeMobileJs.kt','INSTALL').replace('window.splNativeSnapshot=function(){','window.qaCards={fromAnchor:fromAnchor,allowedPath:allowedPath};window.splNativeSnapshot=function(){');
vm.runInNewContext(nativeSource,probe);
assert.equal(probe.qaCards.fromAnchor(title).image,'https://i.scdn.co/image/correct','Deep anonymous tile lost its own cover');
assert.equal(probe.qaCards.fromAnchor(overlay).title,'My playlist','Overlay lost the sibling title');
assert.equal(probe.qaCards.fromAnchor(other.children[0]).image,'https://i.scdn.co/image/wrong','Neighbouring cover mixed');
assert.equal(probe.qaCards.allowedPath('/intl-es-419/playlist/Good'),'/playlist/Good');
const gradle=fs.readFileSync(path.join(root,'app/build.gradle.kts'),'utf8');
assert(gradle.includes('applicationId = if (vivoIsland) "com.spotify.music" else "com.project.lol"')&&gradle.includes('versionCode = 48'));
const manifest=fs.readFileSync(path.join(root,'app/src/main/AndroidManifest.xml'),'utf8');
assert(manifest.includes('android:authorities="${applicationId}.artwork"')&&!manifest.includes('com.spotify.mobile.android.mediaapi'));
const host=fs.readFileSync(path.join(source,'webview/WebViewHost.kt'),'utf8');
assert(host.includes('OriginScopedBridge.install(view, bridgeRef)')&&!host.includes('addJavascriptInterface(bridgeRef'));
assert(!host.includes('setBackForwardCacheEnabled'),'Do not force experimental restoration of the authenticated player');
const controls=payload('webview/injections/PlaybackControls.kt');
assert(!controls.includes('window.splPauseRetry=function'),'Restored blind legacy pause retry');
const activity=fs.readFileSync(path.join(source,'ui/MainActivity.kt'),'utf8');
assert(!/\banalytics\.logEvent\(/.test(activity),'Optional Firebase analytics must remain nullable-safe');
for(const file of ['service/MediaNotificationService.kt','service/OfflineMediaService.kt']) {
 const service=fs.readFileSync(path.join(source,file),'utf8');
 assert(!/\bregisterReceiver\(actionReceiver,\s*filter\)/.test(service),file+': internal actions need an explicit receiver export policy');
}
const chrome=fs.readFileSync(path.join(source,'ui/components/NativePlayerChrome.kt'),'utf8');
const shell=fs.readFileSync(path.join(source,'ui/components/NativeMobileShell.kt'),'utf8');
assert(chrome.includes('onDragCancel = { finishDrag(0f, cancelled = true) }'));
assert(chrome.includes('translationY = currentFraction() * size.height'),'Player drag must move a layer');
assert(!/evaluateJavascript|AndBridge|setInterval/.test(chrome),'Visual motion must not control the audio engine');
assert(shell.includes('PlayerSheetPolicy.miniHeightDp')&&shell.includes('.progressSemantics(progress(track))'));
assert(!shell.includes('if (swipe > 95f)'),'Legacy non-interactive swipe restored');
assert(chrome.includes('Animatable(if (visible) 0f else 1f)'), 'Restored open player must not replay entry');
assert(chrome.includes('val start = currentFraction()')&&chrome.includes('dragPx = start * heightPx'),
 'Interrupting a return must retain the displayed drag offset');
assert(chrome.includes('detectTapGestures { }'), 'Revealed background must not receive taps through the sheet');
assert(shell.includes('PlayerSheetPolicy.artworkSideDp')&&shell.includes('.verticalScroll(rememberScrollState())'),
 'Expanded player needs bounded artwork and scroll access on short/large-text screens');
const seek=fs.readFileSync(path.join(source,'ui/components/NativeSeekControls.kt'),'utf8');
assert(seek.includes('Triple(track.title, track.artist, track.duration)')&&seek.includes('key(seekIdentity)')&&
 seek.includes('currentIdentity.value == seekIdentity')&&seek.includes('enabled = track.duration > 0L'),
 'A stale or unknown-duration seek must not dispatch');
assert(shell.includes('clearAndSetSemantics')&&shell.includes('paneTitle = "Сейчас играет"'),
 'Background semantics must be hidden while the player pane is open');
assert(shell.includes('Modifier.fillMaxSize().background(bg).background('),'Expanded player requires an opaque base');
assert(!activity.includes('Открыть Native UI'),'Internal pilot terminology must not appear on the launch screen');
const splash=fs.readFileSync(path.join(source,'ui/SplashActivity.kt'),'utf8');
assert(!splash.includes('delay(SPLASH_MIN_MS)'),'Do not reintroduce the forced four-second launch delay');
assert(!fs.readFileSync(path.join(source,'webview/injections/AndroidTracker.kt'),'utf8').includes('ab67616d000082c1'),
 'Never invent a replacement artwork CDN key');
require('node:child_process').execFileSync(process.execPath,[path.join(__dirname,'verify-native-data.cjs')],{stdio:'inherit'});
console.log('PASS: '+checked+' embedded scripts; metadata pacing/seek/restart/artwork; native play/navigation; independent package/provider; guarded bridge and transport.');
