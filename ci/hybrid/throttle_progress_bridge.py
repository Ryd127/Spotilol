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
