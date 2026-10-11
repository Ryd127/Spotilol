"""Hybrid8: integrated native Android navigation and Now Playing shell.
Keep Spotilol WebView as the only account, playback, downloads and MediaSession engine.
"""
from pathlib import Path

root=Path("app/src/main/java/com/project/lol")
def patch(rel, old, new):
    file=root/rel
    value=file.read_text(encoding="utf-8")
    n=value.count(old)
    if n!=1: raise RuntimeError(f"Hybrid8 anchor {rel} {old[:100]!r}: {n}")
    file.write_text(value.replace(old,new,1),encoding="utf-8")

ui_src=Path("ci/hybrid/native-mobile/NativeMobileShell.kt")
ui_code=ui_src.read_text(encoding="utf-8")
ui_code=ui_code.replace("import androidx.compose.foundation.layout.weight\n","")
ui_code=ui_code.replace(
    'val detail = listOf("/playlist/", "/album/", "/artist/", "/collection/", "/show/").any { browse.path.startsWith(it) }',
    'val detail = listOf("/playlist/", "/album/", "/artist/", "/collection/", "/show/").any { browse.path.startsWith(it) } && !(tab == "Library" && browse.path == "/collection/tracks")'
)
assert "val detail = listOf" in ui_code
(root/"ui/components/NativeMobileShell.kt").write_text(ui_code,encoding="utf-8")
(root/"ui/components/NativeMobileJs.kt").write_text(
    Path("ci/hybrid/native-mobile/NativeMobileJs.kt").read_text(encoding="utf-8"),
    encoding="utf-8"
)

main="ui/MainActivity.kt"
patch(main,
    "import com.project.lol.ui.components.NativeMiniTrack",
    "import com.project.lol.ui.components.NativeMiniTrack\n"
    "import com.project.lol.ui.components.MobileBrowseState\n"
    "import com.project.lol.ui.components.NativeMobileJs\n"
    "import com.project.lol.ui.components.NativeMobileShell\n"
    "import kotlinx.coroutines.delay")
patch(main,
    "    private val nativeMiniEnabledState = mutableStateOf(false)",
    "    private val nativeMiniEnabledState = mutableStateOf(false)\n"
    "    private val nativeMobileEnabledState = mutableStateOf(true)\n"
    "    private val nativeMobileBrowseState = mutableStateOf(MobileBrowseState())")
patch(main,
    '        nativeMiniEnabledState.value = prefs.getBoolean("NativeMiniPlayer", false)',
    '        nativeMiniEnabledState.value = prefs.getBoolean("NativeMiniPlayer", false)\n'
    '        nativeMobileEnabledState.value = prefs.getBoolean("NativeMobileUI", true)')
patch(main,
    "            val nativeMiniEnabled = nativeMiniEnabledState.value",
    "            val nativeMiniEnabled = nativeMiniEnabledState.value\n"
    "            val nativeMobileEnabled = nativeMobileEnabledState.value\n"
    "            val nativeBrowse = nativeMobileBrowseState.value\n"
    "            val nativeMobileActive = nativeMobileEnabled && serviceEnabled && nativeBrowse.ready && !pipVideoActive.value && !videoFullscreenState.value && !wideVideoState.value")
patch(main,
    """                    onNativeMiniPlayerChange = { enabled ->
                        nativeMiniEnabledState.value = enabled
                        prefs.edit().putBoolean("NativeMiniPlayer", enabled).apply()
                        syncNativeMiniOverlay(webView)
                    },""",
    """                    onNativeMiniPlayerChange = { enabled ->
                        nativeMiniEnabledState.value = enabled
                        prefs.edit().putBoolean("NativeMiniPlayer", enabled).apply()
                        syncNativeMiniOverlay(webView)
                    },
                    onNativeMobileUIChange = { enabled ->
                        nativeMobileEnabledState.value = enabled
                        prefs.edit().putBoolean("NativeMobileUI", enabled).apply()
                    },""")
patch(main,
    "if (!hideTopBar && !pipFilling && !videoFullscreen && !wideVideoFullscreen) {",
    "if (!hideTopBar && !nativeMobileActive && !pipFilling && !videoFullscreen && !wideVideoFullscreen) {")
patch(main,
    """                                                syncNativeMiniOverlay(it)
                                            },""",
    """                                                syncNativeMiniOverlay(it)
                                                it.evaluateJavascript(NativeMobileJs.INSTALL, null)
                                            },""")
patch(main,
    """                            LaunchedEffect(webView) {
                                webView?.let { startMediaService() }
                            }""",
    """                            LaunchedEffect(webView) {
                                webView?.let { startMediaService() }
                            }
                            // Bounded UI state polling; never polls the WebView every display frame.
                            LaunchedEffect(nativeMobileEnabled, serviceEnabled) {
                                while (nativeMobileEnabled && serviceEnabled) {
                                    val browser = webView
                                    if (browser != null && WebSecurityPolicy.isPlayer(browser.url)) {
                                        browser.evaluateJavascript(
                                            "window.splNativeSnapshot?window.splNativeSnapshot():null"
                                        ) { raw ->
                                            if (raw != null && raw != "null") {
                                                runCatching {
                                                    val decoded = org.json.JSONTokener(raw).nextValue() as? String
                                                    if (decoded != null) {
                                                        val update = MobileBrowseState.parse(decoded)
                                                        if (nativeMobileBrowseState.value != update)
                                                            nativeMobileBrowseState.value = update
                                                    }
                                                }.onFailure { Logger.d(TAG, "native mobile snapshot unavailable") }
                                            }
                                        }
                                    }
                                    delay(1200)
                                }
                            }""")
patch(main,
    "if (nativeMiniEnabled && !pipFilling",
    "if (nativeMiniEnabled && !nativeMobileActive && !pipFilling")
patch(main,
    """                            if (showDialog) {
                                SleepTimerDialog(""",
    """                            if (nativeMobileActive && !playerExpanded) {
                                NativeMobileShell(
                                    browse = nativeBrowse,
                                    track = nativeMiniTrackState.value,
                                    onNavigate = { path ->
                                        val value = JSONObject.quote(path)
                                        webView?.evaluateJavascript(
                                            "window.splNativeNavigate&&window.splNativeNavigate(" + value + ")",
                                            null
                                        )
                                    },
                                    onPlay = { uri, context ->
                                        webView?.evaluateJavascript(
                                            "window.splNativePlay&&window.splNativePlay(" +
                                                JSONObject.quote(uri) + "," + JSONObject.quote(context) + ")",
                                            null
                                        )
                                    },
                                    onPrevious = { webView?.evaluateJavascript("actSkipBack()", null) },
                                    onToggle = {
                                        val shouldPlay = !nativeMiniTrackState.value.playing
                                        webView?.evaluateJavascript("actPlayPause(" + shouldPlay + ")", null)
                                    },
                                    onNext = { webView?.evaluateJavascript("actSkipForward()", null) },
                                    onSeek = { pos ->
                                        webView?.evaluateJavascript("actSeek(" + pos + ")", null)
                                    },
                                    onOpenSettings = { settingsDialogOpen = true },
                                    onOpenWeb = {
                                        nativeMobileEnabledState.value = false
                                        prefs.edit().putBoolean("NativeMobileUI", false).apply()
                                    }
                                )
                            }

                            if (showDialog) {
                                SleepTimerDialog(""")

dialog="ui/components/SettingsDialog.kt"
patch(dialog,
    """    onNativeMiniPlayerChange: (Boolean) -> Unit = {},
    content: @Composable () -> Unit""",
    """    onNativeMiniPlayerChange: (Boolean) -> Unit = {},
    onNativeMobileUIChange: (Boolean) -> Unit = {},
    content: @Composable () -> Unit""")
patch(dialog,
    """                                        onNativeMiniPlayerChange = onNativeMiniPlayerChange
                                    )""",
    """                                        onNativeMiniPlayerChange = onNativeMiniPlayerChange,
                                        onNativeMobileUIChange = onNativeMobileUIChange
                                    )""")

settings="ui/screens/SettingsScreen.kt"
patch(settings,
    """    onNativeMiniPlayerChange: (Boolean) -> Unit = {}
) {""",
    """    onNativeMiniPlayerChange: (Boolean) -> Unit = {},
    onNativeMobileUIChange: (Boolean) -> Unit = {}
) {""")
patch(settings,
    '    var nativeMini by remember { mutableStateOf(prefs.getBoolean("NativeMiniPlayer", false)) }',
    '    var nativeMini by remember { mutableStateOf(prefs.getBoolean("NativeMiniPlayer", false)) }\n'
    '    var nativeMobile by remember { mutableStateOf(prefs.getBoolean("NativeMobileUI", true)) }')
patch(settings,
    """                        title = "Native Android mini-player (preview)",""",
    """                        title = "Native Android mini-player (preview)",""")
# Insert full app toggle directly before the pilot, keeping pilot as a rollback option.
patch(settings,
    """                    SettingSwitchTile(
                        title = "Native Android mini-player (preview)",""",
    """                    SettingSwitchTile(
                        title = "Native Android mobile UI",
                        subtitle = "Home, Search, Library and Now Playing in Compose. Use Web to restore old view.",
                        icon = TablerIcons.PlayerPlay,
                        checked = nativeMobile,
                        onCheckedChange = { enabled ->
                            nativeMobile = enabled
                            onNativeMobileUIChange(enabled)
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 44.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                    SettingSwitchTile(
                        title = "Native Android mini-player (preview)",""")

# The original preferences sync navigates temporarily to /preferences. Only go back
# if it is still on that temporary route, avoiding unexpected later navigation.
prefsFile=root/"webview/injections/WebPrefs.kt"
prefsText=prefsFile.read_text(encoding="utf-8")
old="setTimeout(function(){ if(back) history.back(); busy=false; },400);"
assert prefsText.count(old)==1
prefsFile.write_text(prefsText.replace(old,"setTimeout(function(){ if(back && location.pathname==='/preferences') history.back(); busy=false; },400);"),encoding="utf-8")

gradle=Path("app/build.gradle.kts")
version=gradle.read_text(encoding="utf-8")
for old,new in [("versionCode = 37","versionCode = 38"),
  ('versionName = "1.2.0-vivo-hybrid7-native-mini"','versionName = "1.2.0-vivo-hybrid8-native-ui"')]:
    assert version.count(old)==1,(old,version.count(old))
    version=version.replace(old,new,1)
gradle.write_text(version,encoding="utf-8")
print("Hybrid8: native shell + authenticated WebView content snapshots, tab navigation, fullscreen player and safe prefs back")
