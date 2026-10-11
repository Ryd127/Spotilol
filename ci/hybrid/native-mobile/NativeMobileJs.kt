package com.project.lol.ui.components

/**
 * Read-only snapshots from the existing trusted Spotify Web document.
 * We do not expose local Android privileged methods or the user's auth tokens.
 */
object NativeMobileJs {
    const val INSTALL = """
      (function(){
        if(window.splNativeSnapshot) return;
        var lastLib=null;
        var libPending=false;
        var libLastTry=0;
        function safe(s,n){return String(s||'').trim().slice(0,n||180);}
        function pic(el){
          if(!el) return '';
          var img=el.querySelector('img[src]')||(el.matches && el.matches('img[src]')?el:null);
          return img?safe(img.currentSrc||img.src,800):'';
        }
        function hrefPath(h){
          try{
            var u=new URL(h,location.origin);
            if(u.origin!==location.origin)return '';
            var p=u.pathname;
            if(!/^\/(track|album|playlist|artist|show|episode|collection|genre|section|search|user)\//.test(p))return '';
            return p;
          }catch(e){return '';}
        }
        function kind(p){return (p.split('/')[1]||'');}
        function readHome(root){
          var all=[],seen={};
          var as=root.querySelectorAll('a[href]');
          for(var i=0;i<as.length&&all.length<45;i++){
            var a=as[i],p=hrefPath(a.href);
            if(!p||seen[p]||kind(p)==='track')continue;
            var container=a.closest('[data-testid]')||a;
            var name=safe(a.getAttribute('aria-label')||a.textContent||a.title,120);
            var im=pic(a)||pic(container);
            if(!name)continue;
            seen[p]=1;
            all.push({path:p,title:name,subtitle:kind(p),image:im,kind:kind(p)});
          }
          return all;
        }
        function readTracks(root){
          var rows=root.querySelectorAll('[data-testid="tracklist-row"]');
          if(!rows.length)return null;
          var out=[],seen={};
          for(var i=0;i<rows.length && out.length<70;i++){
            var row=rows[i];
            var a=row.querySelector('a[href*="/track/"]');
            var p=a?hrefPath(a.href):'';
            if(!p)continue;
            var title=safe(a.textContent,130);
            if(!title||seen[p])continue;
            seen[p]=1;
            var people=row.querySelectorAll('a[href*="/artist/"]');
            var sub=[];
            for(var j=0;j<people.length&&j<3;j++) sub.push(safe(people[j].textContent,55));
            out.push({path:p,title:title,subtitle:sub.join(', '),image:pic(row),kind:'track'});
          }
          return out;
        }
        function currentLibrary(){
          var lib=lastLib||window.mediaLib||null;
          if(!lib)return [];
          var items=[],seen={};
          ['playlists','albums','artists','podcasts'].forEach(function(category){
            var list=lib[category]||[];
            for(var i=0;i<list.length&&items.length<75;i++){
              var item=list[i]||{};
              var uri=safe(item.id||item.uri,120);
              var m=uri.match(/^spotify:(playlist|album|artist|show|collection):(.+)$/);
              var p='';
              if(m) p='/' + m[1] + '/' + m[2];
              if(uri==='spotify:collection:tracks')p='/collection/tracks';
              if(!p||seen[p])continue;
              seen[p]=1;
              items.push({path:p,title:safe(item.name,130),
                subtitle:category.replace(/s$/,''),image:safe(item.image,800),kind:m?m[1]:'collection'});
            }
          });
          return items;
        }
        window.splNativeLoadLibrary=function(){
          if(libPending||Date.now()-libLastTry<20000)return;
          if(typeof window.fetchAllLibrary!=='function'||typeof window.parseLibrary!=='function')return;
          libPending=true;
          libLastTry=Date.now();
          Promise.resolve().then(function(){return window.fetchAllLibrary();})
            .then(function(raw){lastLib=window.parseLibrary(raw);})
            .catch(function(){lastLib=null;})
            .then(function(){libPending=false;});
        };
        window.splNativeSnapshot=function(){
          if(!document.body)return null;
          var root=document.querySelector('main')||document.getElementById('main-view')||document.body;
          var path=location.pathname;
          var lib=currentLibrary();
          if(lib.length===0&&!libPending)window.splNativeLoadLibrary();
          var h=root.querySelector('h1');
          var tracks=readTracks(root);
          var items=tracks && tracks.length?tracks:readHome(root);
          return JSON.stringify({ready:!!document.querySelector('[data-testid="user-widget-link"]'),
            path:path,title:h?safe(h.textContent,140):'',
            items:items,library:lib,loadingLibrary:libPending,error:''});
        };
        window.splNativeNavigate=function(path){
          if(typeof path!=='string'||path.length>300)return false;
          if(path!=='/' && !/^\/(album|playlist|artist|show|episode|collection|genre|section|search|user)(\/|$)/.test(path))return false;
          if(location.pathname===path)return true;
          history.pushState({},'',path);
          window.dispatchEvent(new PopStateEvent('popstate',{state:null}));
          return true;
        };
        window.splNativePlay=function(uri,context){
          if(!/^spotify:(track|album|playlist|collection):[A-Za-z0-9:]+$/.test(uri))return false;
          if(context && !/^spotify:(album|playlist|collection):[A-Za-z0-9:]+$/.test(context))context='';
          if(typeof window.playFromUri!=='function')return false;
          window.playFromUri(uri,context||undefined);
          return true;
        };
      })();
    """
}
