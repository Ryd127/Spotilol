package com.project.lol.ui.components

/**
 * The WebView is the only authenticated Spotify engine.
 * This code reads visible Spotify web data; it never forwards auth tokens to Compose.
 */
object NativeMobileJs {
    const val INSTALL = """
      (function(){
        if(window.splNativeUiRevision==='redesign13')return;
        window.splNativeUiRevision='redesign13';
        var lastLibrary=null, libPending=false, libLastTry=0, knownImages=Object.create(null);
        function clean(s,n){return String(s||'').replace(/\s+/g,' ').trim().slice(0,n||170);}
        function allowedPath(h){
          try{
            var u=new URL(h,location.origin);
            if(u.origin!==location.origin)return '';
            var p=u.pathname;
            return p==='/'||/^\/(track|album|playlist|artist|show|episode|collection|genre|section|search|user)(\/|$)/.test(p)?p:'';
          }catch(e){return '';}
        }
        function image(el){
          if(!el)return '';
          // Spotify Web uses both IMG tags and CSS backgrounds for browse tiles.
          // A lazy IMG often has no src until it scrolls into the viewport.
          var nodes=el.matches&&el.matches('img,source')?[el]:el.querySelectorAll('img,source');
          function normalize(value){
            value=clean(value,800);
            if(/^spotify:image:[0-9a-f]{32,}$/i.test(value))
              return 'https://i.scdn.co/image/'+value.slice(14);
            if(!/^https:\/\//i.test(value))return '';
            try{
              var host=new URL(value).hostname.toLowerCase();
              if(host==='scdn.co'||host.endsWith('.scdn.co')||
                 host==='spotifycdn.com'||host.endsWith('.spotifycdn.com'))return value;
            }catch(e){}
            return '';
          }
          for(var i=0;i<nodes.length&&i<12;i++){
            var node=nodes[i];
            var src=normalize(node.currentSrc||node.getAttribute('src')||node.getAttribute('data-src')||node.src);
            if(src)return src;
            var srcset=node.getAttribute('srcset')||node.getAttribute('data-srcset')||'';
            var match=srcset.match(/https:\/\/[^,\s]+/);
            if(match&&(src=normalize(match[0])))return src;
          }
          var elements=[el];
          if(el.querySelectorAll){
            var styled=el.querySelectorAll('[style*="background"],[data-testid*="image"],[class*="image"],[class*="Image"]');
            for(var j=0;j<styled.length&&j<14;j++)elements.push(styled[j]);
          }
          for(var k=0;k<elements.length;k++){
            var e=elements[k];
            var background=(e.style&&e.style.backgroundImage)||'';
            if(!background){
              try{background=getComputedStyle(e).backgroundImage||'';}catch(ex){}
            }
            var m=background.match(/url\(["']?([^"'\)]+)["']?\)/);
            if(m){var url=normalize(m[1]);if(url)return url;}
          }
          return '';
        }
        function closestCard(a){
          // Never choose a whole shelf/section: that repeated the first artist's
          // image on every tile in the previous native UI.
          var e=a;
          for(var n=0;n<4&&e&&e.parentElement;n++,e=e.parentElement){
            if((e.matches && e.matches('li,[data-testid*="card"],[data-testid="tracklist-row"]'))
                || (e.querySelectorAll('a[href]').length===1 && e.querySelector('img')))return e;
          }
          return a;
        }
        function fromAnchor(a){
          var p=allowedPath(a.href);
          if(!p||p==='/')return null;
          var card=closestCard(a);
          var label=clean(a.getAttribute('aria-label')||a.getAttribute('title')||a.textContent,140);
          if(!label) {
            var title=card.querySelector('[data-testid="card-title"],h3');
            label=clean(title&&title.textContent,140);
          }
          if(!label)return null;
          var type=p.split('/')[1],img=image(a)||image(card);
          // Never climb into a shelf with many links: that would apply one
          // artist/playlist picture to every unrelated card.
          function localCover(parent){
            if(!parent||!parent.querySelectorAll)return '';
            return parent.querySelectorAll('a[href]').length<=2?image(parent):'';
          }
          if(!img&&a.parentElement&&a.parentElement!==card)img=localCover(a.parentElement);
          if(!img&&a.parentElement&&a.parentElement.parentElement&&a.parentElement.parentElement!==card)
            img=localCover(a.parentElement.parentElement);
          if(!img&&knownImages[p])img=knownImages[p];
          if(img)knownImages[p]=img;
          var subtitle='';
          var sub=card.querySelector('[data-testid="card-subtitle"],[data-testid="card-description"]');
          if(sub)subtitle=clean(sub.textContent,130);
          return {path:p,title:label,subtitle:subtitle,image:img,kind:type};
        }
        function uniqueCards(root,max,includeTracks){
          var links=root.querySelectorAll('a[href]');
          var seen={},out=[];
          for(var i=0;i<links.length&&out.length<max;i++){
            var item=fromAnchor(links[i]);
            if(!item||seen[item.path]||(!includeTracks&&item.kind==='track'))continue;
            seen[item.path]=1;
            out.push(item);
          }
          return out;
        }
        function trackRows(root){
          var rows=root.querySelectorAll('[data-testid="tracklist-row"],[role="row"][aria-rowindex]');
          var result=[],seen={};
          for(var i=0;i<rows.length&&result.length<120;i++){
            var row=rows[i],a=row.querySelector('a[href*="/track/"]');
            var path=a&&allowedPath(a.href);
            if(!path||seen[path])continue;
            var artists=row.querySelectorAll('a[href*="/artist/"]'),names=[];
            for(var j=0;j<artists.length&&j<3;j++)names.push(clean(artists[j].textContent,50));
            var title=clean(a.textContent,160);
            if(!title)continue;
            seen[path]=1;
            result.push({path:path,title:title,subtitle:names.join(', '),image:image(row),kind:'track'});
          }
          return result;
        }
        function sections(root){
          var groups=root.querySelectorAll('section,[data-testid="shelf"],[data-testid="carousel"]');
          var result=[],seenTitles={};
          for(var i=0;i<groups.length&&result.length<10;i++){
            var group=groups[i],h=group.querySelector('h2,[role="heading"][aria-level="2"]');
            var title=clean((h&&h.textContent)||group.getAttribute('aria-label'),90);
            if(!title||seenTitles[title])continue;
            var cards=uniqueCards(group,22,false);
            if(cards.length<2)continue;
            seenTitles[title]=1;
            result.push({title:title,items:cards});
          }
          return result;
        }
        function library(){
          var items=[],seen={},lib=lastLibrary||window.mediaLib||null;
          if(!lib)return items;
          ['playlists','albums','artists','podcasts'].forEach(function(category){
            var values=lib[category]||[];
            for(var i=0;i<values.length&&items.length<200;i++){
              var v=values[i]||{},uri=clean(v.id||v.uri,130),p='';
              if(uri==='spotify:collection:tracks')p='/collection/tracks';
              else {
                var m=uri.match(/^spotify:(playlist|album|artist|show):([A-Za-z0-9]+)$/);
                if(m)p='/'+m[1]+'/'+m[2];
              }
              if(!p||seen[p])continue;
              seen[p]=1;
              var cover=typeof v.image==='string'?v.image:'';
              items.push({path:p,title:clean(v.name,150),subtitle:category==='artists'?'Исполнитель':
                category==='albums'?'Альбом':category==='podcasts'?'Подкаст':'Плейлист',
                image:cover,kind:p==='/collection/tracks'?'collection':p.split('/')[1]});
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
            .then(function(raw){lastLibrary=window.parseLibrary(raw);})
            .catch(function(){lastLibrary=null;})
            .then(function(){libPending=false;});
        };
        window.splNativeSnapshot=function(){
          var root=document.querySelector('main')||document.getElementById('main-view')||document.body;
          if(!root)return null;
          var path=location.pathname;
          var lib=library();
          if(!lib.length&&!libPending)window.splNativeLoadLibrary();
          // Prefer exact Spotify entity artwork over blank/lazy web tiles.
          for(var l=0;l<lib.length;l++){
            if(lib[l].image)knownImages[lib[l].path]=lib[l].image;
          }
          var h=root.querySelector('h1');
          var owner=root.querySelector('[data-testid="creator-link"],[data-testid="playlist-owner"],[data-testid="entity-subtitle"] a[href*="/user/"]');
          var creator=clean(owner&&owner.textContent,100);
          var stats=root.querySelector('[data-testid="entity-subtitle"],[data-testid="playlist-description"]');
          var meta=clean(stats&&stats.textContent,170);
          var rows=trackRows(root),cards=uniqueCards(root,100,path.startsWith('/search/'));
          function enrich(items){
            items.forEach(function(item){
              var cover=knownImages[item.path]||'';
              if(!item.image&&cover)item.image=cover;
              if(item.image)knownImages[item.path]=item.image;
            });
            return items;
          }
          enrich(rows);enrich(cards);
          var showRows=(path.startsWith('/album/')||path.startsWith('/playlist/')||path.startsWith('/collection/'));
          var firstCover='';
          var mainCover=root.querySelector('[data-testid="entity-image"],[data-testid="playlist-image"],main header img,main h1 + img');
          if(mainCover)firstCover=image(mainCover);
          if(!firstCover){
            var nearest=h&&h.closest('header,[data-testid*="header"]');
            firstCover=image(nearest);
          }
          return JSON.stringify({
            ready:!!(document.querySelector('[data-testid="main-view-container"]')||
                     document.querySelector('[data-testid="user-widget-link"]')||
                     document.querySelector('nav a[href="/"]')||
                     document.querySelector('a[href="/collection/tracks"]')),
            path:path,title:clean(h&&h.textContent,145),
            cover:firstCover,creator:creator,meta:meta,
            items:showRows&&rows.length?rows:cards,
            library:lib,
            sections:path==='/'?sections(root).map(function(group){
              group.items=enrich(group.items);return group;
            }):[],
            loadingLibrary:libPending,error:''
          });
        };
        window.splNativeNavigate=function(path){
          if(typeof path!=='string'||path.length>350)return false;
          if(path!=='/'&&!/^\/(album|playlist|artist|show|episode|collection|genre|section|search|user)(\/|$)/.test(path))return false;
          if(location.pathname===path)return true;
          history.pushState({},'',path);
          window.dispatchEvent(new PopStateEvent('popstate',{state:null}));
          return true;
        };
        window.splNativePlay=function(uri,context){
          if(!/^spotify:track:[A-Za-z0-9]{15,30}$/.test(uri))return false;
          if(context&&!/^spotify:(album|playlist|collection):[A-Za-z0-9:]+$/.test(context))context='';
          if(typeof window.playFromUri!=='function')return false;
          window.playFromUri(uri,context||undefined);
          return true;
        };
      })();
    """
}
