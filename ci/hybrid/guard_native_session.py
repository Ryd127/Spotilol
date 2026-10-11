"""Hybrid10: preserve login/error reachability when native UI is enabled."""
from pathlib import Path
root=Path("app/src/main/java/com/project/lol")
p=root/"ui/MainActivity.kt"
s=p.read_text(encoding="utf-8")
old="""            val nativeMobileActive = nativeMobileEnabled && serviceEnabled && nativeBrowse.ready && !pipVideoActive.value && !videoFullscreenState.value && !wideVideoState.value"""
new="""            val nativeMobileActive = nativeMobileEnabled && serviceEnabled && nativeBrowse.ready && webViewError.value == null && !pipVideoActive.value && !videoFullscreenState.value && !wideVideoState.value"""
assert s.count(old)==1,(old,s.count(old))
s=s.replace(old,new,1)
old="""                                    delay(1200)
                                }
                            }"""
new="""                                    // The login page and WebView error screen must never be covered
                                    // by a stale native UI snapshot from the previous session.
                                    if (browser == null || !WebSecurityPolicy.isPlayer(browser.url)) {
                                        if (nativeMobileBrowseState.value.ready)
                                            nativeMobileBrowseState.value = MobileBrowseState()
                                    }
                                    delay(1200)
                                }
                            }"""
assert s.count(old)==1,(old,s.count(old))
s=s.replace(old,new,1)
p.write_text(s,encoding="utf-8")

gradle=Path("app/build.gradle.kts")
data=gradle.read_text(encoding="utf-8")
for a,b in [
    ("versionCode = 39","versionCode = 40"),
    ('versionName = "1.2.0-vivo-hybrid9-native-ui"','versionName = "1.2.0-vivo-hybrid10-native-ui"')
]:
    assert data.count(a)==1
    data=data.replace(a,b,1)
gradle.write_text(data,encoding="utf-8")
print("Hybrid10: native UI yields on auth/logout/WebView error; versionCode 40")
