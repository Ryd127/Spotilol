package com.project.lol.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.project.lol.util.Logger

/**
 * Cold/fallback router for hardware media keys.
 *
 * Active MediaSessions still receive keys directly from Android. This receiver is only the
 * manifest fallback used by MediaButtonReceiver-style PendingIntents / cold media-button events.
 * Spotilol has two playback engines (web + offline), so a hard-wired receiver -> web service route
 * can steal controls from OfflineMediaService. Route to whichever engine is actually alive and
 * prefer the explicit Online/Offline mode selected by the user.
 */
class MediaButtonRouterReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MEDIA_BUTTON) return

        val offlineReady = OfflineMediaService.instance != null || OfflineMediaService.controller != null
        val webReady = MediaNotificationService.instance != null || MediaNotificationService.webView != null
        val offlineMode = context.getSharedPreferences("spotilol_prefs", Context.MODE_PRIVATE)
            .getBoolean("OfflineMode", false)

        val target = when {
            offlineMode && offlineReady -> OfflineMediaService::class.java
            !offlineMode && webReady -> MediaNotificationService::class.java
            // Transition fallback: if the preferred engine is not alive yet, use the only engine
            // that can actually consume the key instead of dropping it.
            offlineReady -> OfflineMediaService::class.java
            webReady -> MediaNotificationService::class.java
            else -> {
                Logger.w("media.button", "media key ignored: no live playback engine")
                return
            }
        }

        val forwarded = Intent(intent).apply {
            setClass(context, target)
            setPackage(context.packageName)
        }

        try {
            // Both targets are media-playback foreground services and immediately re-assert their
            // notification in onStartCommand/onCreate, satisfying background-start requirements.
            ContextCompat.startForegroundService(context, forwarded)
        } catch (e: Exception) {
            Logger.e("media.button", "failed to route media key to ${target.simpleName}", e)
        }
    }
}
