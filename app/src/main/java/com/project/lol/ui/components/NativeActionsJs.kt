package com.project.lol.ui.components

/** Own implementation of documented library/playlist requests in the existing signed-in WebView. */
object NativeActionsJs {
    const val INSTALL = """
      (function(){
        if(location.origin!=='https://open.spotify.com'||window.splNativeActionsRevision==='native19')return;
        window.splNativeActionsRevision='native19';
        var jobs=new Map(),completed=new Map(),locks=new Set(),confirmed=new Map(),versions=new Map(),profile=null,profileToken=null;
        function uri(s){return typeof s==='string'&&/^spotify:(track|episode|album|artist|playlist|show):[A-Za-z0-9]{22}${'$'}/.test(s);}
        function failure(code){var e=new Error(code);e.safeCode=code;return e;}
        function codeFor(status){return status===401?'auth':status===403?'permission':status===429?'rate':status>=500?'unknown':'unavailable';}
        async function api(path,method,body,signal,token){
          if(!token||token!==window.spotAuthToken)throw failure('auth');
          var fetcher=window.mngFetch||window.oriFetch||window.fetch;
          var response=await fetcher('https://api.spotify.com/v1'+path,{method:method||'GET',signal:signal,
            headers:{Authorization:token,'Content-Type':'application/json'},
            body:body?JSON.stringify(body):undefined,redirect:'error',noRetry:method!=='GET'});
          if(token!==window.spotAuthToken)throw failure(method==='GET'?'auth':'unknown');
          if(!response.ok)throw failure(codeFor(response.status));
          var text=await response.text();
          if(!text)return null;
          var data;try{data=JSON.parse(text);}catch(e){throw failure('unknown');}
          if(data&&data.error)throw failure(codeFor(data.error.status));
          return data;
        }
        function publish(uri,saved){
          confirmed.delete(uri);confirmed.set(uri,{value:saved,at:Date.now(),auth:window.spotAuthToken});
          while(confirmed.size>128)confirmed.delete(confirmed.keys().next().value);
          if(typeof window.splPlayingMediaUri==='function'&&window.splPlayingMediaUri()===uri){
            window.isfav=saved;window.lastState=null;
            if(typeof window.updMedia==='function')window.updMedia();
          }
        }
        window.splNativeSavedState=function(uri){
          var item=confirmed.get(uri);return item&&item.auth===window.spotAuthToken&&Date.now()-item.at<60000?item.value:null;
        };
        async function execute(r,signal){
          async function call(path,method,body){
            var result=await api(path,method,body,signal,r.auth);
            if(r.cancelled)throw failure('unknown');
            return result;
          }
          var encoded=encodeURIComponent(r.uri||'');
          if(r.kind==='savedStatus'){
            var status=await call('/me/library/contains?uris='+encoded,'GET');
            if(!Array.isArray(status)||typeof status[0]!=='boolean')throw failure('unavailable');
            if((versions.get(r.uri)||0)!==r.revision)throw failure('stale');
            publish(r.uri,status[0]);return {uri:r.uri,saved:status[0]};
          }
          if(r.kind==='setSaved'){
            await call('/me/library?uris='+encoded,r.saved?'PUT':'DELETE');
            versions.set(r.uri,(versions.get(r.uri)||0)+1);
            publish(r.uri,r.saved);
            if(typeof window.splNativeInvalidateLibrary==='function')window.splNativeInvalidateLibrary(r.uri);
            return {uri:r.uri,saved:r.saved};
          }
          if(r.kind==='createPlaylist'){
            var created=await call('/me/playlists','POST',{name:r.name,public:false,collaborative:r.collaborative===true});
            var createdUri=created&&(created.uri||('spotify:playlist:'+created.id));
            if(!uri(createdUri)||createdUri.indexOf('spotify:playlist:')!==0)throw failure('unknown');
            profile=null;
            if(typeof window.splNativeInvalidateLibrary==='function')window.splNativeInvalidateLibrary(createdUri);
            return {uri:createdUri};
          }
          if(r.kind==='addToPlaylist'){
            var result=await call('/playlists/'+r.target.slice(17)+'/items','POST',{uris:[r.uri]});
            if(!result||typeof result.snapshot_id!=='string'||!result.snapshot_id)throw failure('unknown');
            if(typeof window.splNativeInvalidatePage==='function')window.splNativeInvalidatePage(r.target);
            return {uri:r.uri};
          }
          if(r.kind==='playlists'){
            if(profileToken!==r.auth){profile=null;profileToken=r.auth;}
            if(!profile)profile=await call('/me','GET');
            if(!profile||typeof profile.id!=='string'||!profile.id)throw failure('unavailable');
            var offset=0,rows=[],more=false,seen=new Set();
            // Bounded work. Never follow a server-supplied URL with an access token.
            for(var page=0;page<10;page++){
              var data=await call('/me/playlists?limit=50&offset='+offset,'GET');
              if(!data||!Array.isArray(data.items))throw failure('unavailable');
              data.items.forEach(function(p){
                var value=p&&(p.uri||('spotify:playlist:'+p.id));
                if(!uri(value)||value.indexOf('spotify:playlist:')!==0||seen.has(value))return;
                if(!(p.owner&&p.owner.id===profile.id)&&p.collaborative!==true)return;
                seen.add(value);
                rows.push({path:'/playlist/'+value.slice(17),title:String(p.name||'').slice(0,150),kind:'playlist',
                  subtitle:p.owner&&p.owner.id===profile.id?'Мой плейлист':'Совместный плейлист',
                  image:typeof window.splBestImage==='function'?window.splBestImage(p.images):''});
              });
              offset+=data.items.length;more=!!data.next;
              if(!more||data.items.length===0)break;
            }
            return {playlists:rows,more:more};
          }
          throw failure('invalid');
        }
        window.splNativeAction=function(input){
          if(location.origin!=='https://open.spotify.com'||!input||typeof input.id!=='string'||
             !/^[A-Za-z0-9_-]{8,80}${'$'}/.test(input.id))return false;
          if(jobs.has(input.id))return true;
          if(completed.has(input.id)){jobs.set(input.id,completed.get(input.id));return true;}
          while(jobs.size>=32){var done=[...jobs.entries()].find(function(e){return e[1].state!=='pending';});if(!done)return false;jobs.delete(done[0]);}
          var r={id:input.id,kind:input.kind,uri:input.uri,target:input.target,name:input.name,
            saved:input.saved,collaborative:input.collaborative===true};
          var valid=(r.kind==='savedStatus'&&uri(r.uri))||
            (r.kind==='setSaved'&&uri(r.uri)&&typeof r.saved==='boolean')||r.kind==='playlists'||
            (r.kind==='createPlaylist'&&typeof r.name==='string'&&r.name.trim().length>0&&r.name.length<=100&&!/[\x00-\x1f]/.test(r.name))||
            (r.kind==='addToPlaylist'&&uri(r.uri)&&/^spotify:(track|episode):/.test(r.uri)&&uri(r.target)&&r.target.indexOf('spotify:playlist:')===0);
          var key=r.kind==='createPlaylist'?'create':r.kind==='addToPlaylist'?'playlist:'+r.target:
            r.kind==='setSaved'?'saved:'+r.uri:'read:'+r.kind+':'+r.uri;
          if(!valid||locks.has(key)){
            jobs.set(r.id,{state:'error',code:valid?'busy':'invalid'});return true;
          }
          if(r.kind==='createPlaylist')r.name=r.name.trim();
          r.auth=window.spotAuthToken;
          if(r.kind==='setSaved')versions.set(r.uri,(versions.get(r.uri)||0)+1);
          r.revision=versions.get(r.uri)||0;
          locks.add(key);jobs.set(r.id,{state:'pending'});
          var mutation=['setSaved','createPlaylist','addToPlaylist'].indexOf(r.kind)!==-1;
          var controller=typeof AbortController==='function'?new AbortController():null;
          var settled=false,timer=setTimeout(function(){
            if(settled)return;settled=true;r.cancelled=true;if(controller)controller.abort();locks.delete(key);
            finish({state:mutation?'unknown':'error',code:'timeout'});
          },10000);
          function finish(result){
            jobs.set(r.id,result);
            if(mutation){completed.set(r.id,result);while(completed.size>128)completed.delete(completed.keys().next().value);}
          }
          Promise.resolve().then(function(){return execute(r,controller&&controller.signal);}).then(function(result){
            if(settled)return;
            finish(Object.assign({state:'success',code:''},result));
          }).catch(function(error){
            if(settled)return;
            var safe=error&&error.safeCode;
            finish({state:mutation&&(!safe||safe==='unknown')?'unknown':'error',code:safe||'network'});
          }).then(function(){if(!settled){settled=true;clearTimeout(timer);locks.delete(key);}});
          return true;
        };
        window.splNativeActionResult=function(id,consume){
          var item=jobs.get(id);if(!item)return null;
          if(consume&&item.state!=='pending')jobs.delete(id);
          return JSON.stringify(item);
        };
      })();
    """
}
