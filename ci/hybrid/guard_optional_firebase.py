from pathlib import Path

root = Path("app/src/main/java/com/project/lol")
def edit(relative, old, new, expected=1):
    p = root / relative
    s = p.read_text()
    if s.count(old) != expected:
        raise RuntimeError(f"Unexpected source for {p}: {old[:50]}")
    p.write_text(s.replace(old,new))

edit("ui/SplashActivity.kt", "        FirebaseCrashlytics.getInstance()", """        if (com.google.firebase.FirebaseApp.getApps(this).isNotEmpty()) {
            runCatching { FirebaseCrashlytics.getInstance() }
        }""")
edit("ui/SplashActivity.kt",
     "if (Telemetry.isEnabled(this)) {",
     "if (Telemetry.isEnabled(this) && com.google.firebase.FirebaseApp.getApps(this).isNotEmpty()) {")
edit("ui/MainActivity.kt",
     "private val analytics: FirebaseAnalytics by lazy { FirebaseAnalytics.getInstance(this) }",
     """private val analytics: FirebaseAnalytics? by lazy {
        if (com.google.firebase.FirebaseApp.getApps(this).isNotEmpty())
            runCatching { FirebaseAnalytics.getInstance(this) }.getOrNull()
        else null
    }""")
edit("ui/MainActivity.kt", "analytics.logEvent(", "analytics?.logEvent(", 6)
edit("util/Telemetry.kt",
     """    fun apply(context: Context, enabled: Boolean) {
        FirebaseAnalytics.getInstance(context).setAnalyticsCollectionEnabled(enabled)
        FirebasePerformance.getInstance().setPerformanceCollectionEnabled(enabled)
    }""",
     """    fun apply(context: Context, enabled: Boolean) {
        if (com.google.firebase.FirebaseApp.getApps(context).isEmpty()) return
        runCatching { FirebaseAnalytics.getInstance(context).setAnalyticsCollectionEnabled(enabled) }
        runCatching { FirebasePerformance.getInstance().setPerformanceCollectionEnabled(enabled) }
    }""")
p = Path("app/build.gradle.kts")
s = p.read_text()
for before, after in [
    ('versionCode = 31','versionCode = 32'),
    ('versionName = "1.2.0-vivo-hybrid1"','versionName = "1.2.0-vivo-hybrid2"')
]:
    assert s.count(before) == 1
    s = s.replace(before, after)
p.write_text(s)
print("Firebase-safe hybrid2 startup prepared")
