from pathlib import Path

p=Path("app/src/main/java/com/project/lol/webview/injections/MediaUpdater.kt")
s=p.read_text(encoding="utf-8")
old="""                } else {
                    AndBridge.recMediaPosition(position);
                    lastPos=position;
                }"""
new="""                } else {
                    // Android MediaSession already extrapolates position when playing.
                    // Do not flood Binder state updates on continuous DOM mutations.
                    var now=Date.now();
                    var jumped=typeof position==='number' && typeof lastPos==='number' && Math.abs(position-lastPos)>=2000;
                    if(typeof position==='number' && isFinite(position) &&
                        (jumped || !window.__splLastPositionSentAt || now-window.__splLastPositionSentAt>=850)) {
                        AndBridge.recMediaPosition(position);
                        lastPos=position;
                        window.__splLastPositionSentAt=now;
                    }
                }"""
if s.count(old)!=1:
    raise RuntimeError(f"MediaUpdater progress anchor mismatch: {s.count(old)}")
p.write_text(s.replace(old,new),encoding="utf-8")
print("Hybrid4: position bridge paced to ~850ms, jumps immediate")


# Hybrid5: observability only. No new transport retries, locks or UX delays.
# This runs after all pinned-upstream and Hybrid1-4 overlays have been applied.
from pathlib import Path

root = Path("app/src/main/java/com/project/lol")
def patch_unique(path, before, after):
    original = path.read_text(encoding="utf-8")
    count = original.count(before)
    if count != 1:
        raise RuntimeError(f"Hybrid5 trace: {path}: expected 1 anchor, found {count}")
    path.write_text(original.replace(before, after, 1), encoding="utf-8")

player = root / "webview/injections/PlayerCore.kt"
trace_js = r'''
            // Hybrid5 trace: only a few markers per skip, Logger is gated by Settings.
            (function(){
                if(window.splTraceSkipStart) return;
                var nextSeq=0, active=null, maxAge=15000;
                var monotonic=function(){return (window.performance && performance.now)?performance.now():Date.now();};
                var emit=function(step,detail){
                    if(!active || Date.now()-active.epoch>maxAge) return;
                    try {
                        AndBridge.dbg('s','LAT/skip seq='+active.seq+' dir='+active.dir+
                            ' src='+active.source+' stage='+step+
                            ' dt='+Math.round(monotonic()-active.at)+'ms'+
                            (detail?' '+detail:''));
                    }catch(_){}
                };
                window.splTraceSkipStart=function(dir,source){
                    active={seq:++nextSeq,dir:dir,source:source,at:monotonic(),
                        epoch:Date.now(),oldTitle:String(window.track||''),
                        changed:false,progress:false,reportedPlaying:false,mediaEvent:false};
                    emit('COMMAND_RECEIVED','');
                    var seq=active.seq;
                    setTimeout(function(){
                        if(active && active.seq===seq && !active.progress)
                            emit('STILL_WAITING_6S','track_changed='+active.changed);
                    },6000);
                };
                window.splTraceSkipStage=function(stage,detail){emit(stage,detail||'');};
                window.splTraceSnapshot=function(title,position,isPlaying){
                    if(!active || Date.now()-active.epoch>maxAge) return;
                    var name=String(title||'');
                    if(!active.changed && name && name!==active.oldTitle){
                        active.changed=true;
                        emit('TRACK_CHANGED','');
                    }
                    if(active.changed && !active.reportedPlaying && isPlaying===true){
                        active.reportedPlaying=true;
                        emit('WEB_PLAYING_TRUE','not_audio_proof');
                    }
                    if(active.changed && !active.progress && typeof position==='number' &&
                       isFinite(position) && position>0){
                        active.progress=true;
                        emit('POSITION_MOVING','not_audio_proof');
                    }
                };
                document.addEventListener('click',function(ev){
                    try{
                        var node=ev.target;
                        var btn=node && node.closest ? node.closest('button[data-testid]') : null;
                        if(!btn) return;
                        var id=btn.getAttribute('data-testid')||'';
                        var dir=id==='control-button-skip-forward'?'next':
                                id==='control-button-skip-back'?'prev':null;
                        if(!dir) return;
                        if(ev.isTrusted) window.splTraceSkipStart(dir,'spotify_ui');
                        else if(!active || active.dir!==dir || Date.now()-active.epoch>1000)
                            window.splTraceSkipStart(dir,'synthetic');
                        emit('DOM_CLICK_EVENT',ev.isTrusted?'trusted':'synthetic');
                    }catch(_){}
                },true);
                document.addEventListener('playing',function(ev){
                    try{
                        if(!active || active.mediaEvent) return;
                        var tag=ev.target && ev.target.tagName;
                        if(tag==='AUDIO' || tag==='VIDEO'){
                            active.mediaEvent=true;
                            emit('HTML_MEDIA_PLAYING', 'not_audible_proof');
                        }
                    }catch(_){}
                },true);
            })();
'''
patch_unique(player, "            window.__splUnlocked=false;",
             "            window.__splUnlocked=false;\n" + trace_js.rstrip("\n"))

controls = root / "webview/injections/PlaybackControls.kt"
patch_unique(controls,
    "            window.splSkip=function(dir,requeued){",
    """            window.splSkip=function(dir,requeued){
                if(!requeued && window.splTraceSkipStart) window.splTraceSkipStart(dir,'js_command');
                else if(requeued && window.splTraceSkipStage) window.splTraceSkipStage('REQUERY_BUTTON');""")
patch_unique(controls,
    """                    if(!requeued){
                        setTimeout(function(){window.splSkip(dir,true);},120);""",
    """                    if(!requeued){
                        if(window.splTraceSkipStage) window.splTraceSkipStage('BUTTON_UNAVAILABLE','first_lookup');
                        setTimeout(function(){window.splSkip(dir,true);},120);""")
patch_unique(controls,
    "                try{ AndBridge.wakeUp(); btn.click(); return true; }catch(e){ return false; }",
    """                try{
                    AndBridge.wakeUp();
                    if(window.splTraceSkipStage) window.splTraceSkipStage('WAKE_RETURN');
                    btn.click();
                    if(window.splTraceSkipStage) window.splTraceSkipStage('DOM_CLICK_RETURN');
                    return true;
                }catch(e){
                    if(window.splTraceSkipStage) window.splTraceSkipStage('DOM_CLICK_EXCEPTION');
                    return false;
                }""")

tracker = root / "webview/injections/AndroidTracker.kt"
patch_unique(tracker,
    "                    updMedia();",
    """                    if(typeof window.splTraceSnapshot==='function')
                        window.splTraceSnapshot(track,position,playing);
                    updMedia();""")

service = root / "service/MediaNotificationService.kt"
patch_unique(service,
    """    private fun wakeAndRun(js: String) {
        val wv = webView ?: return
        Handler(Looper.getMainLooper()).post {""",
    """    private fun wakeAndRun(js: String) {
        val wv = webView ?: return
        val traceSkip = js == "actSkipForward();" || js == "actSkipBack();"
        val traceAt = android.os.SystemClock.uptimeMillis()
        if (traceSkip && Logger.isEnabled()) {
            Logger.s("LAT/native", "event=MEDIA_SESSION_RECEIVED dir=" +
                (if (js == "actSkipForward();") "next" else "prev"))
        }
        Handler(Looper.getMainLooper()).post {""")
patch_unique(service,
    "                wv.evaluateJavascript(js, null)",
    """                if (traceSkip) {
                    if (Logger.isEnabled()) Logger.s("LAT/native",
                        "event=WEBVIEW_EVAL_QUEUED dt=" +
                            (android.os.SystemClock.uptimeMillis() - traceAt) + "ms")
                    wv.evaluateJavascript(js) { _ ->
                        if (Logger.isEnabled()) Logger.s("LAT/native",
                            "event=WEBVIEW_EVAL_CALLBACK dt=" +
                                (android.os.SystemClock.uptimeMillis() - traceAt) + "ms")
                    }
                } else {
                    wv.evaluateJavascript(js, null)
                }""")
# Hybrid1 overlay changed the notification receiver; avoid assuming the
# original ACTION_NEXT/ACTION_PREV implementation. JS capture still traces clicks.

gradle = Path("app/build.gradle.kts")
patch_unique(gradle, "versionCode = 34", "versionCode = 35")
patch_unique(gradle, 'versionName = "1.2.0-vivo-hybrid4"',
                    'versionName = "1.2.0-vivo-hybrid5"')
assert "splTraceSkipStart" in player.read_text()
assert "LAT/native" in service.read_text()
print("Hybrid5: event-timed, bounded diagnostic skip tracing; no player behavior changes")
