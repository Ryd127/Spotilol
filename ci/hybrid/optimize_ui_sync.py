from pathlib import Path

root = Path("app/src/main/java/com/project/lol/webview/injections")

def replace_once(path, old, new):
    data = path.read_text(encoding="utf-8")
    if data.count(old) != 1:
        raise RuntimeError(f"{path.name}: expected ONE anchor, found {data.count(old)}: {old[:75]!r}")
    path.write_text(data.replace(old, new), encoding="utf-8")

# Frequent DOM mutations used to restart a 200ms trailing timer indefinitely.
# Instead, coalesce updates with a bounded 160ms leading-edge scheduler.
tracker = root / "AndroidTracker.kt"
replace_once(tracker,
"""                    var readTimeout;
                    var obs = new MutationObserver(function(){
                        clearTimeout(readTimeout);
                        readTimeout = setTimeout(readTrackState, 200);
                    });""",
"""                    var readTimeout = null;
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
                    var obs = new MutationObserver(scheduleTrackRead);""")

# The previous fast bootstrap polling stopped after the FIRST button binding,
# so React remounts depended on slow 5-second housekeeping. Keep a tiny
# independent 350ms re-binder and clear it when scripts are reinitialized.
mainloop = root / "MainLoop.kt"
replace_once(mainloop,
"""                var tries = 0;
                var bootIv = setInterval(function(){
                    tries++;
                    var pb = document.querySelector('aside button[data-testid=control-button-playpause]:not(.fuckd)');
                    if(pb){ clearInterval(bootIv); wirePlayBtn(pb); }
                    else if(tries > 100){ clearInterval(bootIv); }
                },300);""",
"""                // React may replace the player controls after any track change.
                // Keep this independent of the heavier 5s UI housekeeping tick.
                if(window.__splPlayBindPoll) clearInterval(window.__splPlayBindPoll);
                window.__splPlayBindPoll = setInterval(function(){
                    var pb = document.querySelector('aside button[data-testid=control-button-playpause]:not(.fuckd)');
                    if(pb) wirePlayBtn(pb);
                },350);""")

# A cover URL may arrive separately from title/artist; include artwork in
# the status fingerprint only if the existing hybrid patch hasn't done so.
media = root / "MediaUpdater.kt"
content = media.read_text(encoding="utf-8")
statusline = next(x for x in content.splitlines() if "var currState=" in x)
if "+'|'+cover" not in statusline:
    if "+'|'+album;" in statusline:
        content = content.replace("+'|'+album;", "+'|'+album+'|'+(cover||'');", 1)
        media.write_text(content, encoding="utf-8")
    else:
        raise RuntimeError(f"MediaUpdater changed upstream: {statusline}")

gradle_path = Path("app/build.gradle.kts")
gradle = gradle_path.read_text(encoding="utf-8")
for before, after in [
    ('versionCode = 33', 'versionCode = 34'),
    ('versionName = "1.2.0-vivo-hybrid3"', 'versionName = "1.2.0-vivo-hybrid4"')
]:
    if gradle.count(before) != 1:
        raise RuntimeError(f"Version anchor mismatch: {before}")
    gradle = gradle.replace(before, after)
gradle_path.write_text(gradle, encoding="utf-8")

assert "clearTimeout(readTimeout)" not in tracker.read_text(), "Old starvation timer remains"
assert "window.__splPlayBindPoll" in mainloop.read_text(), "Fast play-button rebinder missing"
print("Hybrid4: bounded track read 160ms; React button rebind 350ms; version 1.2.0-vivo-hybrid4 (34)")
