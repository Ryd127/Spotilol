from pathlib import Path
import re

base = Path("app/src/main/java/com/project/lol")
snapshots = Path("ci/hybrid/playback-stability")
for name, required in [
    ("PlayerCore.kt", "splReadPlayingStateInfo"),
    ("PlaybackControls.kt", "splPlaybackReconcileSuperseded"),
]:
    src = snapshots / name
    dst = base / "webview/injections" / name
    data = src.read_text(encoding="utf-8")
    assert required in data and 'const val CONTENT = """' in data
    dst.write_text(data, encoding="utf-8")
    print(f"Playback core: restored verified {name} from stable 1.1.9")

svc_path = base / "service/MediaNotificationService.kt"
svc = svc_path.read_text(encoding="utf-8")
wake_before = """                wv.resumeTimers()
                wv.onResume()
                wv.dispatchWindowVisibilityChanged(android.view.View.VISIBLE)
                wv.evaluateJavascript(js, null)"""
wake_after = """                // Foreground WebView is already running. Forcing onResume() and
                // visibility changes for every transport action can trigger UI churn.
                if (!wv.isShown) {
                    wv.resumeTimers()
                    wv.onResume()
                    wv.dispatchWindowVisibilityChanged(android.view.View.VISIBLE)
                }
                wv.evaluateJavascript(js, null)"""
assert svc.count(wake_before) == 1, "WebView command dispatcher changed upstream"
svc = svc.replace(wake_before, wake_after)
svc_path.write_text(svc, encoding="utf-8")
print("WebView: avoid redundant foreground resume/visibility events")

gradle_path = Path("app/build.gradle.kts")
gradle = gradle_path.read_text(encoding="utf-8")
for before, after in [
    ('versionCode = 32', 'versionCode = 33'),
    ('versionName = "1.2.0-vivo-hybrid2"', 'versionName = "1.2.0-vivo-hybrid3"'),
]:
    assert gradle.count(before) == 1, f"Expected version marker missing: {before}"
    gradle = gradle.replace(before, after)
gradle_path.write_text(gradle, encoding="utf-8")

assert "splPauseRetry" not in (base / "webview/injections/PlaybackControls.kt").read_text()
assert "splPlaybackVerify" in (base / "webview/injections/PlaybackControls.kt").read_text()
print("Hybrid3 compatibility checks: PASS")
