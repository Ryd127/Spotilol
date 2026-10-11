"""Redesign11: Spotify-like native layout using the existing audio/WebView engine.

This layer follows Hybrid10; Hybrid10 and main remain available unchanged.
"""
from pathlib import Path

root=Path("app/src/main/java/com/project/lol")
def once(rel, old, new):
    file=root/rel
    s=file.read_text(encoding="utf-8")
    n=s.count(old)
    if n!=1:
        raise RuntimeError(f"Redesign11 anchor missing/non-unique in {rel}: {n} {old[:80]!r}")
    file.write_text(s.replace(old,new,1),encoding="utf-8")

shell=Path("ci/hybrid/native-redesign/NativeMobileShell.kt").read_text(encoding="utf-8")
# Official tabler icon library already bundled in Spotilol.
assert "NativeMobileShell(" in shell
assert "СЕЙЧАС ИГРАЕТ" in shell
shell=shell.replace(
    "import androidx.compose.ui.layout.ContentScale",
    "import androidx.compose.ui.layout.ContentScale\nimport androidx.compose.ui.input.pointer.pointerInput"
)
(root/"ui/components/NativeMobileShell.kt").write_text(shell,encoding="utf-8")
(root/"ui/components/NativeMobileJs.kt").write_text(
    Path("ci/hybrid/native-redesign/NativeMobileJs.kt").read_text(encoding="utf-8"),
    encoding="utf-8"
)

main="ui/MainActivity.kt"
once(main,
    "    private val nativeMobileBrowseState = mutableStateOf(MobileBrowseState())",
    "    private val nativeMobileBrowseState = mutableStateOf(MobileBrowseState())\n"
    "    private val nativeMobileSessionReady = mutableStateOf(false)")
once(main,
    "nativeMobileEnabled && serviceEnabled && nativeBrowse.ready && webViewError.value == null",
    "nativeMobileEnabled && serviceEnabled && nativeMobileSessionReady.value && webViewError.value == null")

once(main,
    """                                                it.evaluateJavascript(NativeMobileJs.INSTALL, null)
                                            },""",
    """                                                it.evaluateJavascript(NativeMobileJs.INSTALL, null)
                                                // Once the authenticated player is reached, do not
                                                // tie the entire Compose tree to a volatile DOM widget.
                                                nativeMobileSessionReady.value = true
                                            },""")
once(main,
    """                                            if (raw != null && raw != "null") {
                                                runCatching {
                                                    val decoded = org.json.JSONTokener(raw).nextValue() as? String
                                                    if (decoded != null) {
                                                        val update = MobileBrowseState.parse(decoded)
                                                        if (nativeMobileBrowseState.value != update)
                                                            nativeMobileBrowseState.value = update
                                                    }
                                                }.onFailure { Logger.d(TAG, "native mobile snapshot unavailable") }
                                            }""",
    """                                            if (raw == null || raw == "null") {
                                                // A replaced document may have lost the JS bridge.
                                                // The underlying Spotify player stays untouched.
                                                if (WebSecurityPolicy.isPlayer(browser.url)) {
                                                    browser.evaluateJavascript(NativeMobileJs.INSTALL, null)
                                                }
                                            } else {
                                                runCatching {
                                                    val decoded = org.json.JSONTokener(raw).nextValue() as? String
                                                    if (decoded != null) {
                                                        val update = MobileBrowseState.parse(decoded)
                                                        if (update.ready) {
                                                            nativeMobileSessionReady.value = true
                                                        }
                                                        // Keep the last useful page across transient
                                                        // Spotify React DOM unmounts / route transitions.
                                                        if (update.ready || !nativeMobileBrowseState.value.ready) {
                                                            if (nativeMobileBrowseState.value != update)
                                                                nativeMobileBrowseState.value = update
                                                        }
                                                    }
                                                }.onFailure { Logger.d(TAG, "native mobile snapshot unavailable") }
                                            }""")
once(main,
    """                                        if (nativeMobileBrowseState.value.ready)
                                            nativeMobileBrowseState.value = MobileBrowseState()""",
    """                                        nativeMobileSessionReady.value = false
                                        if (nativeMobileBrowseState.value.ready)
                                            nativeMobileBrowseState.value = MobileBrowseState()""")

# Advanced WebView-only actions open a temporary fallback, NOT a permanent
# setting switch. The user can return to native UI immediately from the overlay.
once(main,
    "    private val nativeMobileSessionReady = mutableStateOf(false)",
    "    private val nativeMobileSessionReady = mutableStateOf(false)\n"
    "    private val nativeWebFallbackState = mutableStateOf(false)")
once(main,
    "nativeMobileEnabled && serviceEnabled && nativeMobileSessionReady.value && webViewError.value == null",
    "nativeMobileEnabled && !nativeWebFallbackState.value && serviceEnabled && nativeMobileSessionReady.value && webViewError.value == null")
once(main,
    """                                    onOpenWeb = {
                                        nativeMobileEnabledState.value = false
                                        prefs.edit().putBoolean("NativeMobileUI", false).apply()
                                    }""",
    """                                    onOpenWeb = {
                                        nativeWebFallbackState.value = true
                                    }""")
once(main,
    """                            if (showDialog) {
                                SleepTimerDialog(""",
    """                            if (nativeWebFallbackState.value && nativeMobileEnabledState.value &&
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
                            }

                            if (showDialog) {
                                SleepTimerDialog(""")

gradle=Path("app/build.gradle.kts")
g=gradle.read_text(encoding="utf-8")
for before,after in [
    ("versionCode = 40","versionCode = 41"),
    ('versionName = "1.2.0-vivo-hybrid10-native-ui"','versionName = "1.2.0-vivo-native-redesign11"')
]:
    assert g.count(before)==1, before
    g=g.replace(before,after,1)
gradle.write_text(g,encoding="utf-8")
assert "nativeMobileSessionReady.value" in (root/main).read_text(encoding="utf-8")
assert "redesign11" in (root/"ui/components/NativeMobileJs.kt").read_text(encoding="utf-8")
print("Redesign11: persistent Compose session, bridge reattach, exact-screen native redesign; versionCode 41")
