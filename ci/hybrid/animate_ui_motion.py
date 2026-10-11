"""Hybrid6: composited micro-animations and no redundant play-button SVG rewrites.
Only patches the custom player. Does not modify audio or navigation.
Runs after upstream 1.2.0 + all Hybrid1-5 overlays.
"""
from pathlib import Path

player = Path("app/src/main/java/com/project/lol/webview/injections/SpotilolPlayer.kt")
data = player.read_text(encoding="utf-8")

def replace_once(old, new):
    global data
    n = data.count(old)
    if n != 1:
        raise RuntimeError(f"Hybrid6 player anchor not unique: {n}, {old[:85]!r}")
    data = data.replace(old, new, 1)

replace_once(
    """            window.initSpotilolPlayer=function(){
                if(document.getElementById('spotilolPlayerControls')) return;""",
    """            window.initSpotilolPlayer=function(){
                if(document.getElementById('spotilolPlayerControls')) return;
                // CSS keyframes are composited by WebView at the actual display refresh rate:
                // no 60/120/144 Hz timer or expensive full DOM traversal each frame.
                if(!document.getElementById('spl-player-motion-css')){
                    var motionCss=document.createElement('style');
                    motionCss.id='spl-player-motion-css';
                    motionCss.textContent=
                        '@keyframes splArtReveal{from{opacity:.62;transform:translateY(3px) scale(.987)}to{opacity:1;transform:translateY(0) scale(1)}}'+
                        '@keyframes splTextReveal{from{opacity:.52;transform:translateY(4px)}to{opacity:1;transform:translateY(0)}}'+
                        '#spotilolPlayerControls .spl-art-reveal{animation:splArtReveal .24s cubic-bezier(.2,.8,.2,1) both}'+
                        '#spotilolPlayerControls .spl-text-reveal{animation:splTextReveal .20s cubic-bezier(.2,.8,.2,1) both}'+
                        '@media(prefers-reduced-motion:reduce){#spotilolPlayerControls .spl-art-reveal,#spotilolPlayerControls .spl-text-reveal{animation:none!important}}';
                    (document.head||document.documentElement).appendChild(motionCss);
                }
                function splAnimateOnce(el,cls){
                    if(!el || (window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches))return;
                    el.classList.remove(cls);
                    void el.offsetWidth; // only on actual metadata change, never on RAF
                    el.classList.add(cls);
                }""")
replace_once(
    "                            ci.setAttribute('data-src',lo);",
    "                            ci.setAttribute('data-src',lo);\n                            splAnimateOnce(ci,'spl-art-reveal');")
replace_once(
    "                        if(tk&&trackEl&&trackEl.textContent&&tk.textContent!==trackEl.textContent) tk.textContent=trackEl.textContent;",
    """                        if(tk&&trackEl&&trackEl.textContent&&tk.textContent!==trackEl.textContent){
                            tk.textContent=trackEl.textContent;
                            splAnimateOnce(tk,'spl-text-reveal');
                        }""")
replace_once(
    "                        if(ar&&artistEl&&tk.textContent!=='No track') ar.textContent=artistEl.textContent||'';",
    """                        if(ar&&artistEl&&tk&&tk.textContent!=='No track'){
                            var newArtist=artistEl.textContent||'';
                            if(ar.textContent!==newArtist){
                                ar.textContent=newArtist;
                                splAnimateOnce(ar,'spl-text-reveal');
                            }
                        }""")
replace_once(
    """                            if(pp)pp.innerHTML=ph;
                            if(ppm)ppm.innerHTML=ph;""",
    """                            // Avoid destroying and recreating both SVG icons every 100ms.
                            // Keeps transitions stable and reduces unnecessary DOM work.
                            var key=isPlaying?'1':'0';
                            if(pp&&pp.getAttribute('data-spl-playing')!==key){
                                pp.innerHTML=ph;
                                pp.setAttribute('data-spl-playing',key);
                            }
                            if(ppm&&ppm.getAttribute('data-spl-playing')!==key){
                                ppm.innerHTML=ph;
                                ppm.setAttribute('data-spl-playing',key);
                            }""")
player.write_text(data, encoding="utf-8")
assert "splArtReveal" in player.read_text(encoding="utf-8")
assert "data-spl-playing" in player.read_text(encoding="utf-8")

gradle=Path("app/build.gradle.kts")
content=gradle.read_text(encoding="utf-8")
for before,after in [
    ("versionCode = 35", "versionCode = 36"),
    ('versionName = "1.2.0-vivo-hybrid5"', 'versionName = "1.2.0-vivo-hybrid6"')
]:
    if content.count(before)!=1:
        raise RuntimeError(f"Hybrid6 version anchor mismatch: {before}")
    content=content.replace(before,after,1)
gradle.write_text(content,encoding="utf-8")
print("Hybrid6: composited player metadata motion + reduced duplicate SVG work; playback unchanged")
