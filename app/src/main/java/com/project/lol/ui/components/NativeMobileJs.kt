package com.project.lol.ui.components

/**
 * The WebView is the only authenticated Spotify engine.
 * This code reads visible Spotify web data; it never forwards auth tokens to Compose.
 */
object NativeMobileJs {
    const val INSTALL = """
      (function(){
        if(window.splNativeUiRevision==='native19')return;
        window.splNativeUiRevision='native19';
        var lastLibrary=null, libPending=false, libDirty=false, libLastTry=0, knownImages=Object.create(null),memoLibSource=null,memoLibItems=[];
        function clean(s,n){return String(s||'').replace(/\s+/g,' ').trim().slice(0,n||170);}
        function allowedPath(h){
          try{
            var u=new URL(h,location.origin);
            if(u.origin!==location.origin)return '';
            var p=u.pathname.replace(/^\/intl-[a-z]{2,3}(?:-[a-z0-9]{2,8}){0,2}(?=\/|$)/i,'')||'/';
            return p==='/'||/^\/(track|album|playlist|artist|show|episode|collection|genre|section|search|user)(\/|$)/.test(p)?p:'';
          }catch(e){return '';}
        }
        // Native page identity is independent from the hidden WebView's current DOM.
        // Never label arbitrary sidebar/recommendation links as an artist's tracks.
        var routePath=allowedPath(location.href)||'/', pages=new Map(), requestSeq=0, activeRequest=null;
        function uriFor(path){
          var m=path.match(/^\/(artist|album|playlist|show)\/([A-Za-z0-9]+)$/);
          if(m)return 'spotify:'+m[1]+':'+m[2];
          return path==='/collection/tracks'?'spotify:collection:tracks':'';
        }
        function mediaItem(value){
          var uri=clean(value.id||value.uri,120),m=uri.match(/^spotify:(track|episode|album|playlist|artist|show):([A-Za-z0-9]+)$/);
          if(!m)return null;
          var title=clean(value.name||value.title,160);
          if(!title)return null;
          return {path:'/'+m[1]+'/'+m[2],title:title,kind:m[1],
            subtitle:clean((value.artists||[]).join(', '),140),image:clean(value.image,800)};
        }
        function loadPage(path,force){
          var search=path.indexOf('/search/')===0,uri=uriFor(path);
          if(!search&&!uri)return false;
          var fetcher=search?window.searchMediaItems:window.fetchMediaItems;
          if(typeof fetcher!=='function')return false;
          var old=pages.get(path),now=Date.now();
          if(!force&&old&&(old.loading||now-old.at<30000))return true;
          if(activeRequest&&activeRequest.controller)activeRequest.controller.abort();
          var seq=++requestSeq,controller=typeof AbortController==='function'?new AbortController():null;
          var page={loading:true,items:old&&old.items||[],details:old&&old.details||{},error:'',at:now};
          pages.set(path,page);
          while(pages.size>12)pages.delete(pages.keys().next().value);
          activeRequest={path:path,controller:controller};
          var timer=setTimeout(function(){
            if(seq!==requestSeq)return;
            if(controller)controller.abort();
            page.loading=false;page.error='timeout';page.at=Date.now();
          },8000);
          var argument=uri;
          if(search){try{argument=decodeURIComponent(path.slice(8));}catch(e){argument=path.slice(8);}}
          Promise.resolve().then(function(){return fetcher(argument,{native:true,signal:controller&&controller.signal});})
            .then(function(result){
              if(seq!==requestSeq||page.error==='timeout')return;
              var values=search?result:result&&result.items;
              var seen=Object.create(null);
              page.items=(values||[]).slice(0,140).map(mediaItem).filter(function(item){
                if(!item||seen[item.path])return false;seen[item.path]=true;
                return search||item.kind==='track'||item.kind==='episode';
              });
              page.details=search?{}:result&&result.details||{};
              page.loading=false;page.at=Date.now();
            }).catch(function(error){
              if(seq!==requestSeq)return;
              page.loading=false;page.error=error&&error.name==='AbortError'?'cancelled':'unavailable';page.at=Date.now();
            }).then(function(){clearTimeout(timer);if(seq===requestSeq)activeRequest=null;});
          return true;
        }
        function image(el){
          if(!el)return '';
          // Spotify Web uses both IMG tags and CSS backgrounds for browse tiles.
          // A lazy IMG often has no src until it scrolls into the viewport.
          var nodes=el.matches&&el.matches('img,source')?[el]:el.querySelectorAll('img,source');
          function normalize(value){
            value=clean(value,800);
            if(/(equaliz|placeholder|favicon|playing[-_.])/i.test(value))return '';
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
          var best='',bestRank=-1;
          function consider(url,rank){var valid=normalize(url);if(valid&&rank>bestRank){best=valid;bestRank=rank;}}
          for(var i=0;i<nodes.length&&i<12;i++){
            var node=nodes[i];
            consider(node.currentSrc||node.getAttribute('src')||node.getAttribute('data-src')||node.src,Number(node.naturalWidth)||0);
            var srcset=node.getAttribute('srcset')||node.getAttribute('data-srcset')||'';
            srcset.split(',').forEach(function(candidate){
              var match=candidate.trim().match(/^(https:\/\/\S+)(?:\s+(\d+(?:\.\d+)?)(w|x))?$/);
              if(match)consider(match[1],Number(match[2]||1)*(match[3]==='x'?1000:1));
            });
          }
          if(best)return best;
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
        function categoryColor(root){
          if(!root)return '';
          var nodes=[root], desc=root.querySelectorAll?
            root.querySelectorAll('[style*="background"],[class*="genre"],[class*="Genre"]'):[];
          for(var i=0;i<desc.length&&i<14;i++)nodes.push(desc[i]);
          for(var j=0;j<nodes.length;j++){
            var color='';
            try{color=getComputedStyle(nodes[j]).backgroundColor;}catch(e){}
            var c=(color||'').match(/^rgba?\((\d+),\s*(\d+),\s*(\d+)/i);
            if(!c)continue;
            var r=+c[1],g=+c[2],b=+c[3];
            // Ignore transparent and neutral dark card surfaces.
            if(Math.max(r,g,b)-Math.min(r,g,b)<32)continue;
            return '#'+[r,g,b].map(function(v){return v.toString(16).padStart(2,'0');}).join('');
          }
          return '';
        }
        function closestCard(a){
          // Never choose a whole shelf/section: that repeated the first artist's
          // image on every tile in the previous native UI.
          var e=a, own=allowedPath(a.href);
          for(var n=0;n<8&&e;n++,e=e.parentElement){
            if(e.matches&&e.matches('body,main,nav,section,[role="list"],[role="grid"],[data-testid="shelf"],[data-testid="carousel"]'))break;
            var links=e.querySelectorAll('a[href]');
            if(links.length>8)break;
            var paths=[];
            for(var j=0;j<links.length;j++){
              var path=allowedPath(links[j].href);
              if(path&&path!=='/'&&!(own.indexOf('/artist/')!==0&&path.indexOf('/artist/')===0)&&paths.indexOf(path)===-1)paths.push(path);
            }
            // Repeated overlay/title links share an entity; shelves do not.
            if(paths.length===1&&paths[0]===own&&
               (e.matches('li,[data-testid*="card"],[data-testid="tracklist-row"]')||e.querySelector('img,[style*="background"]')))return e;
          }
          return a;
        }
        function fromAnchor(a){
          var p=allowedPath(a.href);
          if(!p||p==='/')return null;
          var card=closestCard(a);
          var label=clean(a.getAttribute('aria-label')||a.getAttribute('title')||a.textContent,140);
          if(!label) {
            var title=card.querySelector('[data-testid="card-title"],h3')||
              Array.from(card.querySelectorAll('a[href]')).find(function(link){return allowedPath(link.href)===p&&clean(link.textContent);});
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
          return {path:p,title:label,subtitle:subtitle,image:img,kind:type,
            color:(type==='genre'||type==='section')?categoryColor(card):''};
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
          var items=[],seen={},lib=window.mediaLib||lastLibrary||null;
          if(!lib)return items;
          if(lib===memoLibSource)return memoLibItems;
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
          memoLibSource=lib;memoLibItems=items;return items;
        }
        window.splNativeLoadLibrary=function(force){
          if(libPending){if(force===true)libDirty=true;return;}
          if(force!==true&&Date.now()-libLastTry<20000)return;
          if(typeof window.checkMediaLib!=='function'&&(typeof window.fetchAllLibrary!=='function'||typeof window.parseLibrary!=='function'))return;
          libPending=true;
          libLastTry=Date.now();
          Promise.resolve().then(function(){
              if(typeof window.checkMediaLib==='function')return window.checkMediaLib(force===true).then(function(){return window.mediaLib;});
              return window.fetchAllLibrary().then(window.parseLibrary);
            })
            .then(function(lib){lastLibrary=lib;})
            .catch(function(){lastLibrary=null;})
            .then(function(){
              libPending=false;
              if(libDirty){libDirty=false;window.splNativeLoadLibrary(true);}
            });
        };
        window.splNativeSnapshot=function(){
          var root=document.querySelector('[data-testid="main-view-container"]')||document.querySelector('main')||document.getElementById('main-view');
          var path=routePath;
          var lib=library();
          if(!lib.length&&!libPending)window.splNativeLoadLibrary();
          // Prefer exact Spotify entity artwork over blank/lazy web tiles.
          for(var l=0;l<lib.length;l++){
            if(lib[l].image)knownImages[lib[l].path]=lib[l].image;
          }
          loadPage(path,false);
          var page=pages.get(path),entity=lib.find(function(item){return item.path===path;});
          if(page){
            var details=page.details||{};
            var pageTitle=path==='/collection/tracks'?((entity&&entity.title)||'Любимые треки'):(details.name||(entity&&entity.title));
            return JSON.stringify({ready:true,path:path,title:clean(pageTitle,145),
              cover:clean(details.image||(entity&&entity.image),800),creator:clean(details.creator,100),meta:'',
              items:page.items,library:lib,sections:[],loadingLibrary:libPending,loadingPage:page.loading,error:page.error});
          }
          if(!root)return JSON.stringify({ready:false,path:path,items:[],library:lib,loadingPage:true,error:''});
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
          var showRows=!!uriFor(path);
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
            items:showRows?rows:cards,
            library:lib,
            sections:path==='/'?sections(root).map(function(group){
              group.items=enrich(group.items);return group;
            }):[],
            loadingLibrary:libPending,loadingPage:showRows,error:''
          });
        };
        window.splNativeNavigate=function(path){
          if(typeof path!=='string'||path.length>350)return false;
          if(path!=='/'&&!/^\/(album|playlist|artist|show|episode|collection|genre|section|search|user)(\/|$)/.test(path))return false;
          if(allowedPath(path)!==path)return false;
          if(activeRequest&&activeRequest.path!==path){
            requestSeq++;if(activeRequest.controller)activeRequest.controller.abort();
            var pending=pages.get(activeRequest.path);if(pending&&pending.loading)pages.delete(activeRequest.path);
            activeRequest=null;
          }
          routePath=path;
          if(loadPage(path,false))return true;
          if(allowedPath(location.href)===path)return true;
          history.pushState({},'',path);
          window.dispatchEvent(new PopStateEvent('popstate',{state:null}));
          return true;
        };
        window.splNativeCurrentPath=function(){return routePath;};
        window.splNativeRetry=function(){return loadPage(routePath,true);};
        function invalidatePage(path){
          pages.delete(path);
          if(activeRequest&&activeRequest.path===path){
            requestSeq++;if(activeRequest.controller)activeRequest.controller.abort();activeRequest=null;
          }
        }
        window.splNativeInvalidatePage=function(uri){
          var m=String(uri||'').match(/^spotify:(playlist|album|artist|show):([A-Za-z0-9]+)${'$'}/);
          if(m)invalidatePage('/'+m[1]+'/'+m[2]);
        };
        window.splNativeInvalidateLibrary=function(uri){
          memoLibSource=null;libLastTry=0;invalidatePage('/collection/tracks');
          if(/^spotify:track:/.test(String(uri||'')))window.likedSongsCache=null;
          window.splNativeInvalidatePage(uri);window.splNativeLoadLibrary(true);
        };
        window.splNativeRevealWeb=function(explicit){
          var target=typeof explicit==='string'?allowedPath(explicit):routePath;
          if(!target)return false;
          if(allowedPath(location.href)!==target){history.pushState({},'',target);window.dispatchEvent(new PopStateEvent('popstate',{state:null}));}
          return true;
        };
        window.splNativePlay=function(uri,context){
          if(!/^spotify:(track|episode):[A-Za-z0-9]{15,30}$/.test(uri))return false;
          if(context&&!/^spotify:(album|playlist|artist|collection):[A-Za-z0-9:]+$/.test(context))context='';
          if(typeof window.playFromUri!=='function')return false;
          window.playFromUri(uri,context||undefined);
          return true;
        };
      })();
    """
}
