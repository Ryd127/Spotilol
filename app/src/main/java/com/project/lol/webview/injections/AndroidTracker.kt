package com.project.lol.webview.injections

object AndroidTracker {
    const val CONTENT = """
            window.addAndAuto = function(){
                if(aaint) {
                    if(typeof aaint.disconnect==='function') { try { aaint.disconnect(); } catch(e){} }
                    else { clearInterval(aaint); }
                }
                function readTrackState(){
                    var ta = document.querySelector('a[data-testid=context-item-link]');
                    if(ta) track=ta.text; else track=null;
                    var aa = document.querySelector('a[data-testid=context-item-info-artist]');
                    if(!aa) aa = document.querySelector('a[data-testid=context-item-info-show]');
                    if(aa) artist=aa.text; else artist='';
                    var rr = document.querySelector('button[data-testid=control-button-repeat]');
                    if(rr) repmode=rr.getAttribute('aria-checked'); else repmode='false';
                    shuffle = (typeof window.splShuffleState === 'function') ? window.splShuffleState() : 'off';
                    var fb = document.querySelector('div[data-testid=now-playing-widget]>div:last-child>button');
                    if(fb && fb.getAttribute('aria-checked')==='true') isfav=true; else isfav=false;
                    playing=window.splIsPlayingSticky();
                    var rg = document.querySelector('div[data-testid=playback-progressbar] input[type=range]');
                    if(rg) { duration=parseInt(rg.getAttribute('max')); position=parseInt(rg.getAttribute('value')); }
                    else { duration=null; position=null; }
                    var href=ta&&(ta.getAttribute('href')||''),idMatch=href&&href.match(/\/track\/([A-Za-z0-9]+)/);
                    var currentMeta=idMatch&&window.__splTrackMeta&&window.__splTrackMeta[idMatch[1]];
                    var im = document.querySelector('[data-testid="now-playing-widget"] img[data-testid="cover-art-image"]')||
                        document.querySelector('aside[data-testid="now-playing-bar"] img[data-testid="cover-art-image"]');
                    if(im) {
                        cover=(currentMeta&&currentMeta.cover)||im.currentSrc||im.src;
                    } else cover=(currentMeta&&currentMeta.cover)||null;
                    if(typeof window.splTraceSnapshot==='function')
                        window.splTraceSnapshot(track,position,playing);
                    updMedia();
                }
                try {
                    var npTarget = document.querySelector('aside[data-testid="now-playing-bar"]') || document.body;
                    var readTimeout = null;
                    var lastReadAt = 0;
                    function scheduleTrackRead(){
                        if(readTimeout !== null) return;
                        var wait = Math.max(0, 160 - (Date.now() - lastReadAt));
                        readTimeout = setTimeout(function(){
                            readTimeout = null;
                            lastReadAt = Date.now();
                            readTrackState();
                        }, wait);
                    }
                    var obs = new MutationObserver(scheduleTrackRead);
                    obs.observe(npTarget, { childList: true, subtree: true, attributes: true, characterData: true });
                    aaint = obs;
                    readTrackState();
                } catch(e) {
                    aaint = setInterval(readTrackState, 1000);
                }
            };
        
    """
}
