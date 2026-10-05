package com.project.lol.webview.injections

object AndroidTracker {
    const val CONTENT = """
            window.addAndAuto = function(){
                if(aaint) {
                    if(typeof aaint.disconnect==='function') { try { aaint.disconnect(); } catch(e){} }
                    else { clearInterval(aaint); }
                }
                if(window.__splAndMetaHandler) {
                    try { window.removeEventListener('spltrackmeta', window.__splAndMetaHandler); } catch(e){}
                }
                if(window.__splAndTrackHandler) {
                    try { window.removeEventListener('trackchange', window.__splAndTrackHandler); } catch(e){}
                }
                if(window.__splAndRetryTimers) {
                    try {
                        for(var z=0; z<window.__splAndRetryTimers.length; z++) clearTimeout(window.__splAndRetryTimers[z]);
                    } catch(e){}
                }
                window.__splAndRetryTimers=[];

                function normCover(s){
                    if(!s) return '';
                    try {
                        s=String(s);
                        if(s.indexOf('i.scdn.co')!==-1) {
                            s=s.replace(/ab67616d0000[0-9a-f]{4}/,'ab67616d000082c1');
                            s=s.replace(/ab6761670000[0-9a-f]{4}/,'ab676167000082e8');
                        }
                    } catch(e){}
                    return s;
                }

                function mediaSessionCover(trackName, artistName){
                    try {
                        var ms=navigator.mediaSession;
                        var md=ms&&ms.metadata;
                        if(!md) return '';
                        var mt=(md.title||'').trim();
                        var ma=(md.artist||'').trim();
                        var titleOk=!trackName||!mt||mt===trackName;
                        var artistOk=!artistName||!ma||ma===artistName||artistName.indexOf(ma)!==-1||ma.indexOf(artistName)!==-1;
                        if(!titleOk||!artistOk) return '';
                        var aw=md.artwork;
                        if(!aw||!aw.length) return '';
                        var best=null,bestScore=-1;
                        for(var i=0;i<aw.length;i++){
                            var a=aw[i];
                            if(!a||!a.src) continue;
                            var score=0;
                            var m=String(a.sizes||'').match(/(\d+)x(\d+)/);
                            if(m) score=(parseInt(m[1])||0)*(parseInt(m[2])||0);
                            if(score>=bestScore){best=a;bestScore=score;}
                        }
                        return best?normCover(best.src):'';
                    } catch(e){}
                    return '';
                }

                function domCover(){
                    try {
                        var root=document.querySelector('[data-testid="now-playing-widget"]')||
                            document.querySelector('aside[data-testid="now-playing-bar"]')||
                            document;
                        var selectors=[
                            'img[data-testid="cover-art-image"]',
                            '[data-testid="cover-art-image"] img',
                            'img[src*="i.scdn.co/image"]',
                            'img[src*="scdn.co/image"]'
                        ];
                        for(var i=0;i<selectors.length;i++){
                            var im=root.querySelector(selectors[i]);
                            if(im&&im.src) return normCover(im.src);
                        }
                        var node=root.querySelector('[data-testid="cover-art-image"]');
                        if(node){
                            var bg=getComputedStyle(node).backgroundImage||'';
                            var m=bg.match(/url\(["']?(.*?)["']?\)/);
                            if(m&&m[1]) return normCover(m[1]);
                        }
                    } catch(e){}
                    return '';
                }

                function readTrackState(){
                    var ta=document.querySelector('a[data-testid=context-item-link]');
                    var trackId=window.splTrackId||'';
                    if(ta) {
                        track=ta.text;
                        if(!trackId) {
                            try {
                                var href=ta.getAttribute('href')||'';
                                var mt=href.match(/\/track\/([A-Za-z0-9]+)/);
                                if(mt&&mt[1]) trackId=mt[1];
                            } catch(e){}
                        }
                    } else track=window.__curTrackName||null;

                    var aa=document.querySelector('a[data-testid=context-item-info-artist]');
                    if(!aa) aa=document.querySelector('a[data-testid=context-item-info-show]');
                    if(aa) artist=aa.text;
                    else artist=window.__curTrackArtist||'';

                    var rr=document.querySelector('button[data-testid=control-button-repeat]');
                    if(rr) repmode=rr.getAttribute('aria-checked'); else repmode='false';
                    shuffle=(typeof window.splShuffleState==='function')?window.splShuffleState():'off';
                    var fb=document.querySelector('div[data-testid=now-playing-widget]>div:last-child>button');
                    if(fb&&fb.getAttribute('aria-checked')==='true') isfav=true; else isfav=false;
                    playing=window.splIsPlayingSticky();

                    var rg=document.querySelector('div[data-testid=playback-progressbar] input[type=range]');
                    if(rg) { duration=parseInt(rg.getAttribute('max')); position=parseInt(rg.getAttribute('value')); }
                    else { duration=null; position=null; }

                    var nextCover=mediaSessionCover(track||'',artist||'');

                    if(!nextCover&&trackId&&window.__splTrackMeta&&window.__splTrackMeta[trackId]) {
                        nextCover=normCover(window.__splTrackMeta[trackId].cover||'');
                    }

                    if(!nextCover&&window.__curTrackCover) {
                        var sameMetaTrack=(trackId&&window.__curTrackId===trackId);
                        var sameNamedTrack=(!trackId&&window.__curTrackName===track&&
                            (!window.__curTrackArtist||window.__curTrackArtist===artist));
                        if(sameMetaTrack||sameNamedTrack) nextCover=normCover(window.__curTrackCover);
                    }

                    if(!nextCover) nextCover=domCover();

                    var coverKey=(trackId||'')+'|'+(track||'')+'|'+(artist||'');
                    if(nextCover) {
                        window.__splLastCover=nextCover;
                        window.__splLastCoverTrackKey=coverKey;
                    } else if(window.__splLastCoverTrackKey===coverKey&&window.__splLastCover) {
                        nextCover=window.__splLastCover;
                    } else if(window.__splLastCoverTrackKey!==coverKey) {
                        window.__splLastCoverTrackKey=coverKey;
                        window.__splLastCover='';
                    }

                    cover=nextCover||null;
                    updMedia();
                }

                function scheduleArtworkRefresh(){
                    try {
                        for(var i=0;i<window.__splAndRetryTimers.length;i++) clearTimeout(window.__splAndRetryTimers[i]);
                    } catch(e){}
                    window.__splAndRetryTimers=[];
                    var waits=[0,120,350,800,1500,2500,4000];
                    for(var j=0;j<waits.length;j++){
                        (function(delay){
                            window.__splAndRetryTimers.push(setTimeout(readTrackState,delay));
                        })(waits[j]);
                    }
                }

                window.__splAndTrackHandler=function(){ scheduleArtworkRefresh(); };
                window.__splAndMetaHandler=function(ev){
                    try {
                        var id=ev&&ev.detail&&ev.detail.id;
                        if(!id||!window.splTrackId||id===window.splTrackId) readTrackState();
                    } catch(e){ readTrackState(); }
                };
                window.addEventListener('trackchange',window.__splAndTrackHandler);
                window.addEventListener('spltrackmeta',window.__splAndMetaHandler);

                try {
                    var npTarget=document.querySelector('aside[data-testid="now-playing-bar"]')||document.body;
                    var readTimeout;
                    var obs=new MutationObserver(function(){
                        clearTimeout(readTimeout);
                        readTimeout=setTimeout(readTrackState,200);
                    });
                    obs.observe(npTarget,{childList:true,subtree:true,attributes:true,characterData:true});
                    aaint=obs;
                    scheduleArtworkRefresh();
                } catch(e) {
                    aaint=setInterval(readTrackState,1000);
                }
            };
        
    """
}
