package com.project.lol.webview.injections

/*
 * CREDIT: Spotilol - Background Video Park/Restore. https://github.com/AldySan
 * Pause only explicitly identified decorative Canvas. Muted/loop/cover alone
 * do not distinguish Canvas from music videos and podcasts. Keep sources and
 * timecodes intact (including MediaSource); never reload the audio transport.
 */
object VideoPark {
    const val CONTENT = """
        (function(){
            if(window.__splVideoParkInit) return;
            window.__splVideoParkInit = true;
            var selector = '[data-testid="canvas-container"],[data-testid="canvas"]';
            var parked = new Map(), observer = null, background = !!window.__splBg;
            function isCanvas(v){
                return !!(v && v.tagName === 'VIDEO' && v.id !== 'spl-canvas' &&
                    v.closest && v.closest(selector));
            }
            function active(){
                return !!window.__splPowerSavePref || background || document.visibilityState === 'hidden';
            }
            function source(v){
                var sources = v.querySelectorAll('source'), parts = [v.getAttribute('src') || ''];
                for(var i=0;i<sources.length;i++) parts.push(sources[i].getAttribute('src') || '');
                return parts.join('\n');
            }
            function restore(v, resume){
                var saved = parked.get(v);
                if(!saved) return;
                parked.delete(v);
                try {
                    // Do not overwrite a method/attribute Spotify replaced while parked.
                    if(v.play === saved.blockPlay){
                        if(saved.ownPlay) Object.defineProperty(v, 'play', saved.ownPlay);
                        else delete v.play;
                    }
                    if(v.getAttribute('preload') === 'none'){
                        if(saved.preload === null) v.removeAttribute('preload');
                        else v.setAttribute('preload', saved.preload);
                    }
                    // A new track/source belongs to Spotify, not to this snapshot.
                    if(resume && v.isConnected && source(v) === saved.source && saved.playing){
                        var p = v.play();
                        if(p && p.catch) p.catch(function(){});
                    }
                } catch(e){}
            }
            function park(v){
                if(!isCanvas(v) || parked.has(v)) return;
                var saved = {source:source(v), playing:!v.paused && !v.ended,
                    preload:v.getAttribute('preload'), ownPlay:Object.getOwnPropertyDescriptor(v, 'play')};
                saved.blockPlay = function(){
                    if(!active() || !isCanvas(v)){
                        restore(v, false);
                        return v.play.apply(v, arguments);
                    }
                    // Remember an explicit play request without spinning up a decoder.
                    saved.source = source(v);
                    saved.playing = true;
                    return Promise.resolve();
                };
                parked.set(v, saved);
                try { Object.defineProperty(v, 'play', {value:saved.blockPlay, configurable:true, writable:true}); } catch(e){}
                try { v.pause(); v.setAttribute('preload', 'none'); } catch(e){}
            }
            function scan(root){
                if(!root) return;
                park(root);
                if(root.querySelectorAll){
                    var videos = root.querySelectorAll('video');
                    for(var i=0;i<videos.length;i++) park(videos[i]);
                }
            }
            function prune(){
                parked.forEach(function(saved, v){
                    if(!v.isConnected || !isCanvas(v)) restore(v, false);
                });
            }
            window.__splSyncCanvasPower = function(){
                if(!active()){
                    if(observer) observer.disconnect();
                    observer = null;
                    parked.forEach(function(saved, v){ restore(v, true); });
                    return;
                }
                prune();
                scan(document);
                if(observer || !document.documentElement) return;
                observer = new MutationObserver(function(mutations){
                    if(!active()) return;
                    prune();
                    mutations.forEach(function(m){
                        if(m.type === 'attributes') scan(m.target);
                        else for(var i=0;i<m.addedNodes.length;i++) scan(m.addedNodes[i]);
                    });
                });
                observer.observe(document.documentElement, {childList:true, subtree:true,
                    attributes:true, attributeFilter:['data-testid']});
            };
            window.__splParkVideos = function(){ background = true; window.__splSyncCanvasPower(); };
            window.__splRestoreVideos = function(){ background = false; window.__splSyncCanvasPower(); };
            document.addEventListener('visibilitychange', window.__splSyncCanvasPower);
            document.addEventListener('DOMContentLoaded', window.__splSyncCanvasPower, {once:true});
            document.addEventListener('play', function(event){
                var v = event.target;
                if(!active() || !isCanvas(v)) return;
                park(v);
                var saved = parked.get(v);
                if(saved){ saved.source = source(v); saved.playing = true; }
                try { v.pause(); } catch(e){}
            }, true);
            window.__splSyncCanvasPower();
        })();
    """
}
