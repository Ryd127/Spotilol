package com.project.lol.ui

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class LauncherResumeTest {
    @Test fun launcherReusesMainWithoutAnotherSplash() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        context.getSharedPreferences("spotilol_prefs", Context.MODE_PRIVATE).edit()
            .putBoolean("OnboardingDone", true).putBoolean("ServiceOn", false).putBoolean("OfflineMode", false).commit()
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        assertNotNull(launch)
        assertEquals(MainActivity::class.java.name, launch!!.component!!.className)
        val created = AtomicInteger()
        val splash = AtomicInteger()
        val callbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, state: Bundle?) {
                if (activity is MainActivity) created.incrementAndGet()
                if (activity is SplashActivity) splash.incrementAndGet()
            }
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        }
        val app = context.applicationContext as Application
        app.registerActivityLifecycleCallbacks(callbacks)
        try {
            ActivityScenario.launch<MainActivity>(launch).use { scenario ->
                scenario.onActivity { it.startActivity(context.packageManager.getLaunchIntentForPackage(context.packageName)) }
                instrumentation.waitForIdleSync()
                scenario.onActivity { assertEquals(1, created.get()); assertEquals(0, splash.get()) }
            }
        } finally { app.unregisterActivityLifecycleCallbacks(callbacks) }
    }
}
