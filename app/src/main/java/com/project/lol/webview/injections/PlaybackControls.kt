package com.project.lol.webview.injections

object PlaybackControls {
    const val CONTENT = """
            window.playFromUri = function(uri, contextUri) {
                var playContext = contextUri || uri;
                var isLikedSongs = (playContext === 'your_library' || playContext.indexOf('collection') !== -1 || playContext === 'playlists' || playContext === 'spotify:collection:tracks');

                var playOptions = {
                    license: 'tft',
                    skip_to: {},
                    player_options_override: {}
                };

                var commandContext = {
                    uri: playContext,
                    url: 'context://' + playContext,
                    metadata: {}
                };

                var featIdent = playContext.match(/^spotify:([^:]+)/);
                featIdent = featIdent ? featIdent[1] : null;
                if (featIdent == 'user' || isLikedSongs) featIdent = 'your_library';

                if (isLikedSongs) {
                    var tracks = window.likedSongsCache || [];
                    var targetUri = (uri && uri.indexOf(':track:') !== -1) ? uri : ((tracks[0] && tracks[0].id) || uri);
                    var trackList = (tracks || []).map(function(t) { return t.id || t.uri || t; }).filter(Boolean);

                    if (targetUri && targetUri.indexOf(':track:') !== -1 && trackList.indexOf(targetUri) === -1) {
                        trackList.unshift(targetUri);
                    }

                    var collectionUri = window.spotUserId ? 'spotify:user:' + window.spotUserId + ':collection' : 'spotify:collection:tracks';

                    commandContext = {
                        uri: collectionUri,
                        url: 'context://' + collectionUri,
                        metadata: { context_description: window.splLikedName() },
                        pages: [{ page_url: 'context://' + collectionUri, tracks: trackList.map(function(u) { return { uri: u }; }) }]
                    };

                    featIdent = 'collection-tracks';

                    playOptions.skip_to = { track_uri: targetUri };
                } else if (contextUri && contextUri !== uri) {
                    playOptions.skip_to = { track_uri: uri };
                }

                (window.mngFetch || oriFetch)('https://gew4-spclient.spotify.com/connect-state/v1/player/command/from/' + window.spotDevId + '/to/' + window.spotDevId, {
                    method: 'POST',
                    headers: { 'Authorization': window.spotAuthToken, 'Client-Token': window.spotCliToken, 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        command: {
                            context: commandContext,
                            play_origin: {
                                feature_identifier: featIdent || 'your_library',
                                feature_version: featVer,
                                referrer_identifier: 'your_library'
                            },
                            options: playOptions,
                            endpoint: 'play'
                        }
                    })
                });
            };
            window.splLikedName = function() {
                try {
                    var h1 = document.querySelector('main h1');
                    if (h1 && h1.textContent && h1.textContent.trim()) return h1.textContent.trim();
                } catch(e) {}
                try {
                    var lib = window.mediaLib;
                    if (lib && lib.playlists) {
                        for (var i = 0; i < lib.playlists.length; i++) {
                            var p = lib.playlists[i];
                            if ((p.id || '').indexOf('collection') !== -1 && p.name) return p.name;
                        }
                    }
                } catch(e) {}
                return 'Liked Songs';
            };
            window.splIsLikedName = function(n) {
                n = String(n || '').toLowerCase();
                if (!n) return false;
                return /liked songs|titres lik|titres aim|j'aime|canciones que te gustan|me gusta|lieblingstitel|brani che ti piacciono|m[uú]sicas curtidas|gelikete nummers|ulubione utwory|polubione utwory|любимые треки|мне нравится|улюблені треки|お気に入りの曲|いいねした曲|좋아요 표시한 곡|喜欢的歌曲|喜愛的歌曲|喜歡的歌曲|beğenilen şarkılar|gillade l[åa]tar|tykätyt kappaleet|obl[íi]bené skladby|obľúbené skladby|kedvelt dalok|melodii apreciate|αγαπημένα τραγούδια|שירים שאהבת|पसंद किए गए गाने|เพลงที่ถูกใจ|lagu yang disukai|bài hát đã thích|харесани песни/i.test(n);
            };
            window.splVisibleControl=function(testId){
                var all=document.querySelectorAll('button[data-testid="'+testId+'"]');
                var fallback=null;
                for(var i=0;i<all.length;i++){
                    var b=all[i];
                    if(!fallback) fallback=b;
                    try{
                        var r=b.getBoundingClientRect();
                        var cs=getComputedStyle(b);
                        if(r.width>0&&r.height>0&&cs.display!=='none'&&cs.visibility!=='hidden') return b;
                    }catch(e){}
                }
                return fallback;
            };
            window.splEnsurePb=function(){
                var first=window.splVisibleControl('control-button-playpause');
                if(first){ window.pBtn=first; return first; }
                var pb=window.pBtn;
                if(pb && document.documentElement.contains(pb)) return pb;
                try{ AndBridge.dbg('e','player command: no play button found'); }catch(e){}
                return null;
            };
            window.__splLastPlaybackRequestAt=0;
            window.__splLastPlaybackRequest=null;
            window.splPlaybackStateInfo=function(){
                if(typeof window.splReadPlayingStateInfo==='function') return window.splReadPlayingStateInfo();
                var v=(typeof window.splReadPlayingState==='function')?window.splReadPlayingState():null;
                return {value:v,source:'unknown'};
            };
            window.splPlaybackStrongState=function(info){
                return !!info && info.source==='button';
            };
            window.splPlaybackVerify=function(seq,desired,retried){
                if(window.__splPlaybackIntentSeq!==seq) return;
                var first=window.splPlaybackStateInfo();
                if(first.value===desired){
                    window.splClearPlaybackIntent(seq,first.value);
                    try{ AndBridge.dbg('s','player command: '+(desired?'play':'pause')+' confirmed'); }catch(e){}
                    return;
                }
                if(retried){
                    setTimeout(function(){
                        if(window.__splPlaybackIntentSeq!==seq) return;
                        var finalInfo=window.splPlaybackStateInfo();
                        window.splClearPlaybackIntent(seq,typeof finalInfo.value==='boolean'?finalInfo.value:undefined);
                        if(finalInfo.value!==desired){
                            try{ AndBridge.dbg('w','player command: '+(desired?'play':'pause')+' not confirmed after retry'); }catch(e){}
                        }
                    },320);
                    return;
                }
                setTimeout(function(){
                    if(window.__splPlaybackIntentSeq!==seq) return;
                    var second=window.splPlaybackStateInfo();
                    if(second.value===desired){
                        window.splClearPlaybackIntent(seq,second.value);
                        return;
                    }
                    // Never retry from the progress-bar/mediaSession heuristics: both can lag after
                    // a valid Spotify command and a blind second click would toggle back.
                    var stableOpposite=first.value===!desired && second.value===!desired &&
                        window.splPlaybackStrongState(first) && window.splPlaybackStrongState(second);
                    if(!stableOpposite){
                        setTimeout(function(){
                            if(window.__splPlaybackIntentSeq!==seq) return;
                            var last=window.splPlaybackStateInfo();
                            window.splClearPlaybackIntent(seq,typeof last.value==='boolean'?last.value:undefined);
                        },350);
                        return;
                    }
                    var pb=window.splEnsurePb();
                    if(!pb||pb.disabled||pb.getAttribute('aria-disabled')==='true'){
                        window.splClearPlaybackIntent(seq,second.value);
                        return;
                    }
                    try{
                        pb.click();
                        try{ AndBridge.dbg('w','player command: '+(desired?'play':'pause')+' retry once after stable DOM state'); }catch(e){}
                    }catch(e){
                        window.splClearPlaybackIntent(seq,second.value);
                        return;
                    }
                    setTimeout(function(){window.splPlaybackVerify(seq,desired,true);},220);
                },420);
            };
            window.splPlaybackReconcileSuperseded=function(seq,desired){
                // The previous click may still be in flight. Keep the newest user intent alive long
                // enough to catch a delayed first command instead of declaring success too early.
                var finish=function(){
                    if(window.__splPlaybackIntentSeq!==seq) return;
                    var info=window.splPlaybackStateInfo();
                    if(info.value===desired){
                        window.splClearPlaybackIntent(seq,info.value);
                        return;
                    }
                    if(info.value===!desired && window.splPlaybackStrongState(info)){
                        var pb=window.splEnsurePb();
                        if(!pb||pb.disabled||pb.getAttribute('aria-disabled')==='true'){
                            window.splClearPlaybackIntent(seq,info.value);
                            return;
                        }
                        try{ pb.click(); }
                        catch(e){ window.splClearPlaybackIntent(seq,info.value); return; }
                        setTimeout(function(){window.splPlaybackVerify(seq,desired,true);},220);
                        return;
                    }
                    window.splClearPlaybackIntent(seq,typeof info.value==='boolean'?info.value:undefined);
                };
                setTimeout(function(){
                    if(window.__splPlaybackIntentSeq!==seq) return;
                    var info=window.splPlaybackStateInfo();
                    if(info.value===!desired && window.splPlaybackStrongState(info)){
                        var pb=window.splEnsurePb();
                        if(!pb||pb.disabled||pb.getAttribute('aria-disabled')==='true'){
                            window.splClearPlaybackIntent(seq,info.value);
                            return;
                        }
                        try{ pb.click(); }
                        catch(e){ window.splClearPlaybackIntent(seq,info.value); return; }
                        setTimeout(function(){window.splPlaybackVerify(seq,desired,true);},220);
                        return;
                    }
                    // If state already matches the newest request, keep a short late guard because
                    // the superseded click could still land afterwards on a busy WebView.
                    setTimeout(finish,500);
                },550);
            };
            window.actPlayPause = function(play) {
                var now=Date.now();
                var raw=(typeof window.splReadPlayingState==='function')?window.splReadPlayingState():null;
                var desired;
                if(play===null||typeof play==='undefined'){
                    if(raw!==null) desired=!raw;
                    else desired=!(window.__splLastPlaying===true);
                } else desired=play===true;

                var pending=window.__splPlaybackIntent;
                // A fast second press means the user changed their mind. Do not drop it and do not
                // blindly click twice while Spotify is still applying the first command. Supersede
                // the pending intent and reconcile once the real player state catches up.
                if(typeof pending==='boolean' && pending!==desired){
                    window.__splLastPlaybackRequest=desired;
                    window.__splLastPlaybackRequestAt=now;
                    var supersedeSeq=window.splSetPlaybackIntent(desired,1200);
                    if(desired){ reqPause=false; }
                    else { reqPause=true; ulFlag=false; }
                    window.splPlaybackReconcileSuperseded(supersedeSeq,desired);
                    return true;
                }
                if(raw===desired){
                    if(typeof window.splClearPlaybackIntent==='function') window.splClearPlaybackIntent(0,raw);
                    return true;
                }
                if(window.__splLastPlaybackRequest===desired && now-window.__splLastPlaybackRequestAt<300 && pending===desired){
                    return true;
                }

                var pb = window.splEnsurePb();
                if (!pb || pb.disabled || pb.getAttribute('aria-disabled')==='true') return false;
                window.__splLastPlaybackRequest=desired;
                window.__splLastPlaybackRequestAt=now;
                var seq=window.splSetPlaybackIntent(desired,1200);
                try{
                    if(desired){ reqPause=false; }
                    else { reqPause=true; ulFlag=false; }
                    pb.click();
                }catch(e){
                    window.splClearPlaybackIntent(seq,raw===null?undefined:raw);
                    return false;
                }
                setTimeout(function(){window.splPlaybackVerify(seq,desired,false);},180);
                return true;
            };
            window.splSkip=function(dir,requeued){
                var testId=dir==='next'?'control-button-skip-forward':'control-button-skip-back';
                var btn=window.splVisibleControl(testId);
                if(!btn||btn.disabled||btn.getAttribute('aria-disabled')==='true'){
                    // React can replace the controls during a layout/track transition. Re-query once
                    // only when no usable button exists; never click a second time after a successful
                    // click because delayed metadata could otherwise skip two tracks.
                    if(!requeued){
                        setTimeout(function(){window.splSkip(dir,true);},120);
                        return true;
                    }
                    try{ AndBridge.dbg('w','player command: '+dir+' button unavailable'); }catch(e){}
                    return false;
                }
                try{ AndBridge.wakeUp(); btn.click(); return true; }catch(e){ return false; }
            };
            window.actSkipBack = function() { return window.splSkip('prev',false); };
            window.actSkipForward = function() { return window.splSkip('next',false); };
            window.splShuffleBtn = function() {
                var b = document.querySelector('button[data-testid="control-button-shuffle"]');
                if(b) return b;
                var sk = document.querySelector('button[data-testid="control-button-skip-back"]');
                if(sk) {
                    var p = sk.previousElementSibling;
                    if(p && p.tagName === 'BUTTON') return p;
                    var f = sk.parentElement ? sk.parentElement.querySelector('button') : null;
                    if(f && f !== sk) return f;
                }
                var bs = document.querySelectorAll('button');
                for(var i=0;i<bs.length;i++){
                    var ic = bs[i].querySelector('svg path');
                    if(ic && (ic.getAttribute('d')||'').indexOf('M13.151.922') === 0 && !/spl-btn/.test(bs[i].className||'')) return bs[i];
                }
                return null;
            };
            window.splShuffleState = function() {
                var b = window.splShuffleBtn();
                if(!b) return 'off';
                if(b.getAttribute('aria-disabled') === 'true') return 'disabled';
                if((b.className||'').indexOf('text-bright-accent') === -1) return 'off';
                return /smart|intelligent|inteligente|intelligente|inteligentny|slim|умный|スマート|스마트|智能|akıllı|älykäs/i.test(b.getAttribute('aria-label')||'') ? 'smart' : 'shuffle';
            };
            window.actToggleShuffle = function() {
                var sb = window.splShuffleBtn();
                if(sb && sb.getAttribute('aria-disabled') !== 'true') {
                    AndBridge.wakeUp();
                    sb.click();
                }
            };
            window.splRepeatBtn = function() {
                var b = document.querySelector('button[data-testid="control-button-repeat"]');
                if(b) return b;
                var fw = document.querySelector('button[data-testid="control-button-skip-forward"]');
                if(fw && fw.nextElementSibling && fw.nextElementSibling.tagName === 'BUTTON') return fw.nextElementSibling;
                var bs = document.querySelectorAll('button');
                for(var i=0;i<bs.length;i++){
                    var ic = bs[i].querySelector('svg path');
                    if(ic && (ic.getAttribute('d')||'').indexOf('M0 4.75') === 0 && !/spl-btn/.test(bs[i].className||'')) return bs[i];
                }
                return null;
            };
            window.actRepeat = function() {
                var rb = window.splRepeatBtn();
                if(rb) {
                    if(repmode=='false') repmode='true';
                    else if(repmode=='true') repmode='mixed';
                    else repmode='false';
                    updMedia();
                    rb.click();
                }
            };
            window.actAddToFav = function() {
                var fb = document.querySelector('div[data-testid=now-playing-widget]>div:last-child>button');
                if(fb) {
                    if(fb.getAttribute('aria-checked')==='false') {
                        fb.click();
                        isfav=true;
                        updMedia();
                    } else {
                        AndBridge.wakeUp();
                        fb.click();
                        var rfint = setInterval(function(){
                            var fr = document.querySelector('#context-menu button[role=menuitemcheckbox][aria-checked=true]');
                            if(fr) {
                                clearInterval(rfint);
                                fr.click();
                                setTimeout(function(){
                                    var sb = document.querySelector('#context-menu button[type=submit]');
                                    if(sb) { sb.click(); isfav=false; updMedia(); }
                                    AndBridge.wakeOff();
                                },500);
                            }
                        },1000);
                    }
                }
            };
            window.actSeek = function(pos) {
                var rg = document.querySelector('div[data-testid=playback-progressbar] input[type=range]');
                if(rg) { rg.value=pos+1; rg.dispatchEvent(new Event('change',{bubbles:true})); }
            };
        
    """
}
