"""Hybrid7: opt-in Compose native mini-player shell, existing WebView owns playback."""
from pathlib import Path
import shutil
root=Path("app/src/main/java/com/project/lol")

def patch(rel, old, new):
    path=root/rel
    data=path.read_text(encoding="utf-8")
    n=data.count(old)
    if n!=1:
        raise RuntimeError(f"Hybrid7: {rel}, anchor {old[:90]!r}, count={n}")
    path.write_text(data.replace(old,new,1),encoding="utf-8")

native_src=Path("ci/hybrid/native-mini/NativeMiniPlayer.kt")
native_dst=root/"ui/components/NativeMiniPlayer.kt"
assert native_src.is_file()
native_code=native_src.read_text(encoding="utf-8").replace(
    "import androidx.compose.foundation.layout.weight\n", "")
native_dst.write_text(native_code,encoding="utf-8")

main="ui/MainActivity.kt"
patch(main,
    "import com.project.lol.ui.components.SettingsDialog",
    "import com.project.lol.ui.components.SettingsDialog\n"
    "import com.project.lol.ui.components.NativeMiniPlayer\n"
    "import com.project.lol.ui.components.NativeMiniTrack")
patch(main,
    "    private val blockServiceWorkerState = mutableStateOf(true)",
    "    private val blockServiceWorkerState = mutableStateOf(true)\n"
    "    private val nativeMiniEnabledState = mutableStateOf(false)\n"
    "    private val nativeMiniTrackState = mutableStateOf(NativeMiniTrack())")
patch(main,
    """        paletteSeedState.value = prefs.getString("PaletteSeed", null)
        blockServiceWorkerState.value = prefs.getBoolean("BlockServiceWorker", true)
        applyOrientation()""",
    """        paletteSeedState.value = prefs.getString("PaletteSeed", null)
        blockServiceWorkerState.value = prefs.getBoolean("BlockServiceWorker", true)
        nativeMiniEnabledState.value = prefs.getBoolean("NativeMiniPlayer", false)
        applyOrientation()""")
patch(main,
    "            val blockServiceWorker = blockServiceWorkerState.value",
    "            val blockServiceWorker = blockServiceWorkerState.value\n"
    "            val nativeMiniEnabled = nativeMiniEnabledState.value")
patch(main,
    """                    onBlockServiceWorkerChange = { enabled ->
                        blockServiceWorkerState.value = enabled
                        prefs.edit { putBoolean("BlockServiceWorker", enabled) }
                    },""",
    """                    onBlockServiceWorkerChange = { enabled ->
                        blockServiceWorkerState.value = enabled
                        prefs.edit { putBoolean("BlockServiceWorker", enabled) }
                    },
                    onNativeMiniPlayerChange = { enabled ->
                        nativeMiniEnabledState.value = enabled
                        prefs.edit().putBoolean("NativeMiniPlayer", enabled).apply()
                        syncNativeMiniOverlay(webView)
                    },""")
patch(main,
    """                            bridge.onMediaStatus = { json ->
                                handleMediaStatus(json)
                            }""",
    """                            bridge.onMediaStatus = { json ->
                                handleMediaStatus(json)
                            }
                            bridge.onMediaPosition = { newPosition ->
                                runOnUiThread {
                                    val old = nativeMiniTrackState.value
                                    if (old.position != newPosition) {
                                        nativeMiniTrackState.value = old.copy(position = newPosition)
                                    }
                                }
                            }""")
patch(main,
    "                                            onPlayerReady = { playPendingSearch(it) },",
    "                                            onPlayerReady = {\n"
    "                                                playPendingSearch(it)\n"
    "                                                syncNativeMiniOverlay(it)\n"
    "                                            },")
patch(main,
    "                            if (showDialog) {",
    """                            // Pilot is opt-in; the original WebView remains mounted and continues
                            // handling all Spotify commands and account/navigation state.
                            if (nativeMiniEnabled && !pipFilling && !videoFullscreen &&
                                !wideVideoFullscreen && !playerExpanded &&
                                nativeMiniTrackState.value.title.isNotBlank()) {
                                NativeMiniPlayer(
                                    track = nativeMiniTrackState.value,
                                    onPrevious = {
                                        webView?.evaluateJavascript("actSkipBack()", null)
                                    },
                                    onTogglePlayback = {
                                        val wanted = !nativeMiniTrackState.value.playing
                                        webView?.evaluateJavascript(
                                            "actPlayPause(" + wanted.toString() + ")", null
                                        )
                                    },
                                    onNext = {
                                        webView?.evaluateJavascript("actSkipForward()", null)
                                    },
                                    modifier = Modifier.align(Alignment.BottomCenter)
                                        .padding(horizontal = 10.dp, vertical = 10.dp)
                                )
                            }

                            if (showDialog) {""")
patch(main,
    "            runOnUiThread { updatePipParams() }",
    """            val nativeSnapshot = NativeMiniTrack(
                title = obj.optString("track", "").take(180),
                artist = obj.optString("artist", "").take(180),
                cover = coverUrl.take(800),
                playing = pipPlaying,
                position = obj.optLong("position", 0L).coerceAtLeast(0L),
                duration = obj.optLong("duration", 0L).coerceAtLeast(0L)
            )
            runOnUiThread {
                nativeMiniTrackState.value = nativeSnapshot
                updatePipParams()
            }""")
patch(main,
    "    private fun handleMediaStatus(json: String) {",
    """    private fun syncNativeMiniOverlay(wv: WebView?) {
        val active = nativeMiniEnabledState.value
        wv?.post {
            if (!WebSecurityPolicy.isPlayer(wv.url)) return@post
            val js = if (active) {
                "(function(){var s=document.getElementById('spl-native-mini-hide');" +
                "if(!s){s=document.createElement('style');s.id='spl-native-mini-hide';" +
                "s.textContent='#spotilolPlayerControls:not(.spl-full){display:none!important}';" +
                "(document.head||document.documentElement).appendChild(s);}})();"
            } else {
                "(function(){var s=document.getElementById('spl-native-mini-hide');" +
                "if(s)s.remove();})();"
            }
            wv.evaluateJavascript(js, null)
        }
    }

    private fun handleMediaStatus(json: String) {""")

dialog="ui/components/SettingsDialog.kt"
patch(dialog,
    """    onBlockServiceWorkerChange: (Boolean) -> Unit,
    content: @Composable () -> Unit""",
    """    onBlockServiceWorkerChange: (Boolean) -> Unit,
    onNativeMiniPlayerChange: (Boolean) -> Unit = {},
    content: @Composable () -> Unit""")
patch(dialog,
    """                                        onBlockServiceWorkerChange = onBlockServiceWorkerChange
                                    )""",
    """                                        onBlockServiceWorkerChange = onBlockServiceWorkerChange,
                                        onNativeMiniPlayerChange = onNativeMiniPlayerChange
                                    )""")

settings="ui/screens/SettingsScreen.kt"
patch(settings,
    """    onBlockServiceWorkerChange: (Boolean) -> Unit
) {""",
    """    onBlockServiceWorkerChange: (Boolean) -> Unit,
    onNativeMiniPlayerChange: (Boolean) -> Unit = {}
) {""")
patch(settings,
    """    var playerMode by remember { mutableStateOf(prefs.getString("PlayerMode", "spotilol") ?: "spotilol") }""",
    """    var playerMode by remember { mutableStateOf(prefs.getString("PlayerMode", "spotilol") ?: "spotilol") }
    var nativeMini by remember { mutableStateOf(prefs.getBoolean("NativeMiniPlayer", false)) }""")
patch(settings,
    """                        onClick = { showPlayerModeDialog = true }
                    )

                    HorizontalDivider(modifier = Modifier.padding(start = 44.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))""",
    """                        onClick = { showPlayerModeDialog = true }
                    )

                    HorizontalDivider(modifier = Modifier.padding(start = 44.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

                    SettingSwitchTile(
                        title = "Native Android mini-player (preview)",
                        subtitle = "Compose player; Web engine stays unchanged. Turn off to restore old UI.",
                        icon = TablerIcons.PlayerPlay,
                        checked = nativeMini,
                        onCheckedChange = { enabled ->
                            nativeMini = enabled
                            onNativeMiniPlayerChange(enabled)
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(start = 44.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))""")

gradle=Path("app/build.gradle.kts")
code=gradle.read_text(encoding="utf-8")
for old,new in [
    ("versionCode = 36","versionCode = 37"),
    ('versionName = "1.2.0-vivo-hybrid6"','versionName = "1.2.0-vivo-hybrid7-native-mini"')
]:
    assert code.count(old)==1,(old,code.count(old))
    code=code.replace(old,new,1)
gradle.write_text(code,encoding="utf-8")
assert "onNativeMiniPlayerChange" in (root/dialog).read_text()
assert "nativeMiniTrackState" in (root/main).read_text()
assert "NativeMiniPlayer" in (root/main).read_text()
assert native_dst.is_file()
print("Hybrid7: opt-in Compose mini-player, real Spotify bridge + transport, rollback switch, version 37")
