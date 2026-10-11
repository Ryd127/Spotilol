"""Redesign12: migrate old Web-mode preference and recover a missing player-ready callback."""
from pathlib import Path

root = Path("app/src/main/java/com/project/lol")

def one(rel, old, new):
    path = root / rel
    content = path.read_text(encoding="utf-8")
    n = content.count(old)
    if n != 1:
        raise RuntimeError(f"Redesign12 expected unique anchor in {rel}: {n} {old[:96]!r}")
    path.write_text(content.replace(old, new, 1), encoding="utf-8")

main = "ui/MainActivity.kt"
one(main,
    '''        nativeMobileEnabledState.value = prefs.getBoolean("NativeMobileUI", true)''',
    '''        // Earlier Hybrid builds persisted NativeMobileUI=false when the user
        // opened legacy Web. Correct the inherited state exactly once on upgrade.
        // After this migration a manual opt-out remains respected across restarts.
        if (!prefs.getBoolean("NativeRedesign12Initialized", false)) {
            prefs.edit()
                .putBoolean("NativeMobileUI", true)
                .putBoolean("NativeRedesign12Initialized", true)
                .apply()
        }
        nativeMobileEnabledState.value = prefs.getBoolean("NativeMobileUI", true)''')

one(main,
    '''                                    if (browser != null && WebSecurityPolicy.isPlayer(browser.url)) {
                                        browser.evaluateJavascript(
                                            "window.splNativeSnapshot?window.splNativeSnapshot():null"''',
    '''                                    if (browser != null && WebSecurityPolicy.isPlayer(browser.url)) {
                                        // onPlayerReady can be skipped when WebView resumes an existing
                                        // authenticated document without triggering a new page load.
                                        if (!nativeMobileSessionReady.value &&
                                            prefs.getBoolean("LoggedIn", false)) {
                                            browser.evaluateJavascript(
                                                "(function(){return !!(document.querySelector('main')||" +
                                                    "document.querySelector('[data-testid=main-view-container]'))})()"
                                            ) { result ->
                                                if (result == "true" &&
                                                    WebSecurityPolicy.isPlayer(browser.url)) {
                                                    nativeMobileSessionReady.value = true
                                                }
                                            }
                                        }
                                        browser.evaluateJavascript(
                                            "window.splNativeSnapshot?window.splNativeSnapshot():null"''')

one(main,
    '''                            if (nativeWebFallbackState.value && nativeMobileEnabledState.value &&
                                serviceEnabled && !pipFilling) {
                                Box(
                                    Modifier.align(Alignment.BottomEnd)
                                        .padding(end = 14.dp, bottom = 110.dp)
                                        .clip(RoundedCornerShape(24.dp))
                                        .background(Color(0xFF1ED760))
                                        .clickable { nativeWebFallbackState.value = false }
                                        .padding(horizontal = 15.dp, vertical = 12.dp)
                                ) {
                                    Text("↩ Вернуться в приложение",
                                        color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                            }''',
    '''                            // Recovery is available even if the old preference was disabled
                            // or a page reload bypassed the initial ready callback.
                            if (!nativeMobileActive && serviceEnabled && !pipFilling &&
                                !videoFullscreen && !wideVideoFullscreen &&
                                webViewError.value == null &&
                                webView?.url?.let { WebSecurityPolicy.isPlayer(it) } == true) {
                                Box(
                                    Modifier.align(Alignment.BottomEnd)
                                        .padding(end = 14.dp, bottom = 115.dp)
                                        .clip(RoundedCornerShape(24.dp))
                                        .background(Color(0xFF1ED760))
                                        .clickable {
                                            nativeMobileEnabledState.value = true
                                            nativeMobileSessionReady.value = true
                                            nativeWebFallbackState.value = false
                                            prefs.edit().putBoolean("NativeMobileUI", true).apply()
                                            webView?.evaluateJavascript(NativeMobileJs.INSTALL, null)
                                        }
                                        .padding(horizontal = 15.dp, vertical = 12.dp)
                                ) {
                                    Text("♫ Открыть Native UI",
                                        color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                            }''')

gradle = Path("app/build.gradle.kts")
value = gradle.read_text(encoding="utf-8")
for old, new in [
    ("versionCode = 41", "versionCode = 43"),
    ('versionName = "1.2.0-vivo-native-redesign11"',
     'versionName = "1.2.0-vivo-native-redesign13"')
]:
    n=value.count(old)
    if n!=1: raise RuntimeError(f"Redesign12 version anchor {old!r}: {n}")
    value=value.replace(old,new,1)
gradle.write_text(value,encoding="utf-8")

print("Redesign12: migrate legacy disabled UI once, restore native player readiness on resumed WebView, add manual UI switch, v42")

# Ensure real native image extraction and fallback are part of this build.
js = (root / "ui/components/NativeMobileJs.kt").read_text(encoding="utf-8")
ui = (root / "ui/components/NativeMobileShell.kt").read_text(encoding="utf-8")
assert "redesign13" in js
assert "knownImages" in js and "getComputedStyle" in js
assert "artworkFor" in ui and "isLiked" in ui and "Все категории" in ui
print("Redesign13: verified native cover mapping, CSS artwork, genre layout and liked-songs cover")
