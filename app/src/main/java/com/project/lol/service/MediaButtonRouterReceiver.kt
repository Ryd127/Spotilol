package com.project.lol.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.project.lol.util.Logger

/** Cold media-key fallback; active MediaSessions retain direct Android routing. */
class MediaButtonRouterReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MEDIA_BUTTON) return
        val offline = OfflineMediaService.instance != null || OfflineMediaService.controller != null
        val online = MediaNotificationService.instance != null || MediaNotificationService.webView != null
        val offlineMode = context.getSharedPreferences("spotilol_prefs", Context.MODE_PRIVATE)
            .getBoolean("OfflineMode", false)
        val target = when {
            offlineMode && offline -> OfflineMediaService::class.java
            !offlineMode && online -> MediaNotificationService::class.java
            offline -> OfflineMediaService::class.java
            online -> MediaNotificationService::class.java
            else -> return
        }
        val forwarded = Intent(intent).apply {
            setClass(context, target)
            setPackage(context.packageName)
        }
        try { ContextCompat.startForegroundService(context, forwarded) }
        catch (error: Exception) { Logger.e("media.button", "media key routing failed", error) }
    }
}
