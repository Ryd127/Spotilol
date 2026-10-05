package com.project.lol.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.webkit.WebView
import android.view.KeyEvent
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.project.lol.R
import androidx.media.MediaBrowserServiceCompat
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.media.session.MediaButtonReceiver
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.scale
import androidx.core.graphics.toColorInt
import com.project.lol.webview.helpers.AccentTheme
import com.project.lol.util.Logger
import com.project.lol.widget.WidgetSource
import com.project.lol.widget.WidgetState
import com.project.lol.widget.WidgetUpdater
import com.project.lol.webview.SpotifyWebViewSession
import java.lang.ref.WeakReference
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MediaNotificationService : MediaBrowserServiceCompat() {

    companion object {
        private const val TAG = "MediaNotifService"
        private const val CHANNEL_ID = "spotilol_media_playback"
        private const val NOTIFICATION_ID = 1
        private val mainHandler = Handler(Looper.getMainLooper())
        private const val MEDIA_ID_ROOT = "__ROOT__"

        const val ACTION_PLAY_PAUSE = "com.project.lol.ACTION_PLAY_PAUSE"
        const val ACTION_PAUSE = "com.project.lol.ACTION_PAUSE"
        const val ACTION_NEXT = "com.project.lol.ACTION_NEXT"
        const val ACTION_PREV = "com.project.lol.ACTION_PREV"
        const val ACTION_SHUFFLE = "com.project.lol.ACTION_SHUFFLE"
        const val ACTION_REPEAT = "com.project.lol.ACTION_REPEAT"
        const val ACTION_FAVORITE = "com.project.lol.ACTION_FAVORITE"
        const val ACTION_WIDGET_REFRESH = "com.project.lol.ACTION_WIDGET_REFRESH"

        private const val CUSTOM_ACTION_TOGGLE_FAV = "toggle_fav"
        private const val CUSTOM_ACTION_TOGGLE_SHUFFLE = "toggle_shuffle"
        private const val CUSTOM_ACTION_REPEAT = "toggle_repeat"

        private data class PendingBrowserResult(
            val key: String,
            val result: Result<MutableList<MediaBrowserCompat.MediaItem>>
        )

        // Keyed by a unique request token rather than parent/query text. Android Auto is allowed to
        // issue the same request again before the previous async WebView fetch finishes; without a
        // token, the first JS response could accidentally complete the newer Result.
        private val pendingCallbacks = ConcurrentHashMap<String, PendingBrowserResult>()
        private val pendingSearchCallbacks = ConcurrentHashMap<String, PendingBrowserResult>()
        private val browserRequestSeq = AtomicLong(0L)

        private const val MEDIA_ID_PLAYLISTS = "playlists"
        private const val MEDIA_ID_ALBUMS = "albums"
        private const val MEDIA_ID_ARTISTS = "artists"
        private const val MEDIA_ID_PODCASTS = "podcasts"

        private val PLAYBACK_ACTIONS: Long =
            PlaybackStateCompat.ACTION_PLAY or
            PlaybackStateCompat.ACTION_PAUSE or
            PlaybackStateCompat.ACTION_PLAY_PAUSE or
            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
            PlaybackStateCompat.ACTION_STOP or
            PlaybackStateCompat.ACTION_SEEK_TO or
            PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH

        private const val NOTIF_COLOR = 0xFFE0E0E0.toInt()
        private const val PLAYBACK_LOCK_RELEASE_GRACE_MS = 8_000L
        private const val PLAYBACK_LOCK_GUARD_MS = 30_000L
        private const val PLAYBACK_WAKE_LOCK_TIMEOUT_MS = 120_000L
        private const val PLAYBACK_COMMAND_LOCK_TIMEOUT_MS = 15_000L
        private const val COVER_TRANSITION_GRACE_MS = 1_200L
        private const val MEDIA_SESSION_ART_MAX_PX = 256
        private const val ART_TAG = "media.art"
        private const val COVER_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/150.0.0.0 Mobile Safari/537.36"

        var webView: WebView? = null
        var instance: MediaNotificationService? = null
        private var appContext: Context? = null

        @Volatile
        private var taskRemoved = false

        private fun jsString(value: String): String = org.json.JSONObject.quote(value)

        private fun sendEmptyResult(result: Result<MutableList<MediaBrowserCompat.MediaItem>>) {
            runCatching { result.sendResult(mutableListOf()) }
                .onFailure { Logger.w(TAG, "Failed to complete pending media browser result: ${it.message}") }
        }

        private fun drainPendingBrowserResults() {
            val browse = pendingCallbacks.values.toList()
            pendingCallbacks.clear()
            browse.forEach { sendEmptyResult(it.result) }

            val search = pendingSearchCallbacks.values.toList()
            pendingSearchCallbacks.clear()
            search.forEach { sendEmptyResult(it.result) }
        }

        private fun nextBrowserToken(prefix: Char): String =
            "$prefix${browserRequestSeq.incrementAndGet().toString(36)}"

        @JvmStatic
        fun onMediaItemsLoaded(parentId: String, requestToken: String, json: String) {
            val pending = pendingCallbacks.remove(requestToken) ?: return
            val result = pending.result
            if (pending.key != parentId) {
                Logger.w(TAG, "Ignoring mismatched browse response token=$requestToken parent=$parentId expected=${pending.key}")
                sendEmptyResult(result)
                return
            }
            val items = mutableListOf<MediaBrowserCompat.MediaItem>()
            if (json.isNotEmpty() && json != "null" && json != "[]") {
                try {
                    val jsonArray = org.json.JSONArray(json)
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        val name = obj.optString("name", appContext?.getString(R.string.notif_unknown_name) ?: "")
                        val id = obj.optString("id")
                        if (id.isEmpty()) continue
                        val image = obj.optString("image")
                        val artists = obj.optJSONArray("artists")
                        val isBrowsable = obj.optBoolean("browsable", false)
                        var sub = ""
                        if (artists != null && artists.length() > 0) {
                            val artistNames = (0 until artists.length()).map { artists.getString(it) }.joinToString(", ")
                            sub = appContext?.getString(R.string.notif_by_artist, artistNames) ?: artistNames
                        }
                        val desc = MediaDescriptionCompat.Builder()
                            .setMediaId(id)
                            .setTitle(name)
                            .setSubtitle(sub)
                            .setIconUri(if (image.isNotEmpty()) Uri.parse(image) else null)
                            .build()
                        val flags = if (isBrowsable) MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
                        else MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
                        items.add(MediaBrowserCompat.MediaItem(desc, flags))
                    }
                } catch (e: Exception) {
                    Logger.e(TAG, "Error parsing media items", e)
                }
            }
            val finalItems = items
            Handler(Looper.getMainLooper()).post { result.sendResult(finalItems) }
        }

        @JvmStatic
        fun onSearchCompleted(query: String, requestToken: String, json: String) {
            val pending = pendingSearchCallbacks.remove(requestToken) ?: return
            val result = pending.result
            if (pending.key != query) {
                Logger.w(TAG, "Ignoring mismatched search response token=$requestToken query=$query expected=${pending.key}")
                sendEmptyResult(result)
                return
            }
            val items = mutableListOf<MediaBrowserCompat.MediaItem>()
            if (json.isNotEmpty() && json != "null" && json != "[]") {
                try {
                    val jsonArray = org.json.JSONArray(json)
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        val name = obj.optString("name", appContext?.getString(R.string.notif_unknown_name) ?: "")
                        val id = obj.optString("id")
                        if (id.isEmpty()) continue
                        val image = obj.optString("image")
                        val type = obj.optString("type", "")
                        val artists = obj.optJSONArray("artists")
                        val artistText = if (artists != null && artists.length() > 0) {
                            (0 until artists.length()).map { artists.getString(it) }.joinToString(", ")
                        } else ""
                        val sub = when {
                            type.isNotEmpty() && artistText.isNotEmpty() -> "$type • $artistText"
                            type.isNotEmpty() -> type
                            artistText.isNotEmpty() -> appContext?.getString(R.string.notif_by_artist, artistText) ?: artistText
                            else -> ""
                        }
                        val isBrowsable = obj.optBoolean("browsable", false)
                        val desc = MediaDescriptionCompat.Builder()
                            .setMediaId(id)
                            .setTitle(name)
                            .setSubtitle(sub)
                            .setIconUri(if (image.isNotEmpty()) Uri.parse(image) else null)
                            .build()
                        val flags = if (isBrowsable) MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
                        else MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
                        items.add(MediaBrowserCompat.MediaItem(desc, flags))
                    }
                } catch (e: Exception) {
                    Logger.e(TAG, "Error parsing search results", e)
                }
            }
            val finalItems = items
            Handler(Looper.getMainLooper()).post { result.sendResult(finalItems) }
        }
    }

    private lateinit var mediaSession: MediaSessionCompat
    private var isPlaying = false
    private var isShuffle = false
    private var isSmartShuffle = false
    private var isShuffleAvailable = true
    private var isFavorite = false
    private var coverBitmap: Bitmap? = null
    private var currentTitle = ""
    private var currentArtist = ""
    private var currentAlbum = ""
    private var currentPosition: Long = 0L
    private var currentDuration: Long = 0L
    private var lastCoverUrl = ""
    private var coverBitmapTrackKey = ""
    private var coverRequestSeq = 0L
    private var lastActiveContextId: String? = null
    private var isRepeat = "false"
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    private val playbackLockReleaseRunnable = Runnable {
        if (!isPlaying) releasePlaybackLocksNow()
    }
    private val playbackLockGuardRunnable = object : Runnable {
        override fun run() {
            if (!isPlaying) return
            acquirePlaybackLocks()
        }
    }
    private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var lastWidgetPushAt = 0L

    private val actionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_PLAY_PAUSE -> dispatchPlaybackCommand(!isPlaying)
                ACTION_NEXT -> dispatchSkipCommand(next = true)
                ACTION_PREV -> dispatchSkipCommand(next = false)
                ACTION_SHUFFLE -> wakeAndRun("actToggleShuffle();")
                ACTION_REPEAT -> wakeAndRun("actRepeat();")
                ACTION_FAVORITE -> wakeAndRun("actAddToFav();")
                ACTION_WIDGET_REFRESH -> pushWidgetState(force = true)
            }
        }
    }

    private val audioBecomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                val prefs = getSharedPreferences("spotilol_prefs", MODE_PRIVATE)
                if (prefs.getBoolean("BtAutoPause", false)) autoPauseOnce("audio becoming noisy")
            }
        }
    }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val prefs = getSharedPreferences("spotilol_prefs", MODE_PRIVATE)
            when (intent.action) {
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    if (prefs.getBoolean("BtAutoPause", false)) autoPauseOnce("acl disconnected")
                }
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    if (prefs.getBoolean("BtAutoResume", false)) autoResumeOnce("acl connected")
                }
            }
        }
    }

    private val remoteOutputTypes = intArrayOf(
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_BLE_SPEAKER,
        AudioDeviceInfo.TYPE_HEARING_AID,
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_ACCESSORY
    )

    private val knownRouteIds = mutableSetOf<Int>()
    private var lastAutoPauseAt = 0L
    private var lastAutoResumeAt = 0L

    private val audioRouteCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            val fresh = addedDevices.filter { knownRouteIds.add(it.id) }
            if (fresh.isEmpty()) return
            val prefs = getSharedPreferences("spotilol_prefs", MODE_PRIVATE)
            if (!prefs.getBoolean("BtAutoResume", false)) return
            if (fresh.none { isRemoteOutput(it.type) }) return
            autoResumeOnce("route added: " + fresh.joinToString("/") { it.type.toString() })
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            knownRouteIds.removeAll(removedDevices.map { it.id }.toSet())
            if (!isPlaying) return
            val prefs = getSharedPreferences("spotilol_prefs", MODE_PRIVATE)
            if (!prefs.getBoolean("BtAutoPause", false)) return
            if (removedDevices.none { isRemoteOutput(it.type) }) return
            val stillRemote = getSystemService(AudioManager::class.java)
                .getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                .any { isRemoteOutput(it.type) }
            if (stillRemote) return
            autoPauseOnce("route removed: " + removedDevices.joinToString("/") { it.type.toString() })
        }
    }

    private fun isRemoteOutput(type: Int): Boolean = remoteOutputTypes.contains(type)

    private fun btLog(msg: String) {
        Logger.s("bt", msg)
    }

    private fun autoPauseOnce(source: String) {
        val now = System.currentTimeMillis()
        if (now - lastAutoPauseAt < 1500L) {
            btLog("pause skipped, duplicate trigger ($source)")
            return
        }
        lastAutoPauseAt = now
        btLog("pause trigger: $source (webView=${if (webView != null) "bound" else "null"})")
        pausePlayback()
    }

    private fun autoResumeOnce(source: String) {
        val now = System.currentTimeMillis()
        if (now - lastAutoResumeAt < 1500L) {
            btLog("resume skipped, duplicate trigger ($source)")
            return
        }
        lastAutoResumeAt = now
        btLog("resume trigger: $source")
        resumePlayback()
    }

    private var lastMediaStatusJson: String? = null
    private var firstHeadsetCallback = true
    private var accentCache = 0

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "PaletteSeed" || key == "MaterialYou") {
            accentCache = 0
            mainHandler.post {
                showNotification()
            }
        }
        if (key == "AndAuto") {
            val andAuto = getSharedPreferences("spotilol_prefs", MODE_PRIVATE)
                .getBoolean("AndAuto", true)
            if (andAuto) {
                lastMediaStatusJson?.let { updateFromMediaStatus(it) }
            }
        }
    }

    private val headsetReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_HEADSET_PLUG) {
                val state = intent.getIntExtra("state", -1)
                if (firstHeadsetCallback) {
                    firstHeadsetCallback = false
                    return
                }
                if (state == 1) {
                    val prefs = getSharedPreferences("spotilol_prefs", MODE_PRIVATE)
                    if (prefs.getBoolean("HpAutoResume", false)) autoResumeOnce("headset plugged")
                }
            }
        }
    }

    private fun accent(): Int {
        if (accentCache == 0) {
            accentCache = try {
                AccentTheme.resolveHex(this).toColorInt()
            } catch (_: Exception) {
                0xFFE0E0E0.toInt()
            }
        }
        return accentCache
    }

    private fun tintedIcon(resId: Int): IconCompat {
        val d = AppCompatResources.getDrawable(this, resId)!!.mutate()
        d.setTint(accent())
        val bmp = createBitmap(d.intrinsicWidth, d.intrinsicHeight)
        Canvas(bmp).also { canvas ->
            d.setBounds(0, 0, bmp.width, bmp.height)
            d.draw(canvas)
        }
        return IconCompat.createWithBitmap(bmp)
    }

    override fun onCreate() {
        super.onCreate()
        // Swipe-stop belongs to the service instance that received onTaskRemoved().
        // A later explicit start in the same process must not inherit that stale latch.
        taskRemoved = false
        // MediaBrowser Result objects belong to the previous service/client lifecycle. Never carry
        // detached results into a fresh service instance.
        drainPendingBrowserResults()
        instance = this
        appContext = applicationContext

        try {
            createNotificationChannel()
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to create notification channel", e)
        }

        // Enter foreground immediately. Spotilol has historically depended on doing this as early
        // as possible; the first notification may exist for a few milliseconds without a session
        // token, then is replaced below as soon as MediaSession is ready.
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotificationSafe(), getStartForegroundServiceType())
        } catch (e: Throwable) {
            Logger.e(TAG, "Failed to start foreground", e)
        }

        try {
            setupMediaSession()
            updatePlaybackState()
            updateMetadata()
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to setup media session", e)
        }

        // Re-assert the foreground notification with the real MediaSession token/state.
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotificationSafe(), getStartForegroundServiceType())
        } catch (e: Throwable) {
            Logger.e(TAG, "Failed to refresh foreground notification after MediaSession setup", e)
        }

        try {
            registerReceivers()
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to register receivers", e)
        }
        try {
            registerDisconnectReceivers()
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to register disconnect receivers", e)
        }
        getSharedPreferences("spotilol_prefs", MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(prefsListener)
        Logger.i(TAG, "media service ready: session, notification and receivers up")
    }

    @Suppress("DEPRECATION")
    private fun getStartForegroundServiceType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        } else {
            0
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Logger.d(TAG, "onStartCommand startId=$startId action=${intent?.action ?: "none"} taskRemoved=$taskRemoved")
        if (taskRemoved) {
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotificationSafe(), getStartForegroundServiceType())
        } catch (e: Throwable) {
            Logger.e(TAG, "Failed to re-assert foreground", e)
        }

        // Notification actions target this foreground service explicitly. Hardware headset keys keep
        // their standard ACTION_MEDIA_BUTTON path and are decoded by MediaButtonReceiver below.
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> dispatchPlaybackCommand(!isPlaying)
            ACTION_PAUSE -> dispatchPlaybackCommand(false)
            ACTION_NEXT -> dispatchSkipCommand(next = true)
            ACTION_PREV -> dispatchSkipCommand(next = false)
            ACTION_SHUFFLE -> wakeAndRun("actToggleShuffle();")
            ACTION_REPEAT -> wakeAndRun("actRepeat();")
            ACTION_FAVORITE -> wakeAndRun("actAddToFav();")
            ACTION_WIDGET_REFRESH -> pushWidgetState(force = true)
            Intent.ACTION_MEDIA_BUTTON -> {
                try {
                    MediaButtonReceiver.handleIntent(mediaSession, intent)
                } catch (e: Exception) {
                    Logger.e(TAG, "Failed to handle media button intent", e)
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot? {
        val andAuto = getSharedPreferences("spotilol_prefs", MODE_PRIVATE)
            .getBoolean("AndAuto", true)
        if (!andAuto) return null
        val extras = Bundle().apply {
            putBoolean("android.media.browse.SEARCH_SUPPORTED", true)
            putBoolean("android.media.browse.CONTENT_STYLE_SUPPORTED", true)
            putInt("android.media.browse.CONTENT_STYLE_BROWSABLE_HINT", 2)
            putInt("android.media.browse.CONTENT_STYLE_PLAYABLE_HINT", 1)
        }
        return BrowserRoot(MEDIA_ID_ROOT, extras)
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>
    ) {
        if (parentId == MEDIA_ID_ROOT) {
            val items = mutableListOf<MediaBrowserCompat.MediaItem>()
            items.add(createBrowsableItem(MEDIA_ID_PLAYLISTS, getString(com.project.lol.R.string.aa_playlists)))
            items.add(createBrowsableItem(MEDIA_ID_ALBUMS, getString(com.project.lol.R.string.aa_albums)))
            items.add(createBrowsableItem(MEDIA_ID_ARTISTS, getString(com.project.lol.R.string.aa_artists)))
            items.add(createBrowsableItem(MEDIA_ID_PODCASTS, getString(com.project.lol.R.string.aa_podcasts)))
            result.sendResult(items)
            return
        }
        if (parentId.startsWith("spotify:") || parentId == "your_library" ||
            parentId.contains("collection")
        ) {
            lastActiveContextId = parentId
        }
        result.detach()
        val requestToken = nextBrowserToken('b')
        val pending = PendingBrowserResult(parentId, result)
        pendingCallbacks[requestToken] = pending
        val dispatched = wakeAndRun(
            "if (typeof window.fetchMediaItems === 'function') " +
                "window.fetchMediaItems(${jsString(parentId)}, ${jsString(requestToken)});"
        )
        if (!dispatched) {
            if (pendingCallbacks.remove(requestToken, pending)) sendEmptyResult(result)
            return
        }
        mainHandler.postDelayed({
            if (pendingCallbacks.remove(requestToken, pending)) sendEmptyResult(result)
        }, 20_000L)
    }

    private fun createBrowsableItem(id: String, title: String): MediaBrowserCompat.MediaItem {
        val extras = Bundle().apply {
            putInt("android.media.browse.CONTENT_STYLE_BROWSABLE_HINT", 2)
            putInt("android.media.browse.CONTENT_STYLE_PLAYABLE_HINT", 1)
        }
        val desc = MediaDescriptionCompat.Builder()
            .setMediaId(id)
            .setTitle(title)
            .setExtras(extras)
            .build()
        return MediaBrowserCompat.MediaItem(desc, MediaBrowserCompat.MediaItem.FLAG_BROWSABLE)
    }

    override fun onSearch(
        query: String,
        extras: Bundle?,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>
    ) {
        if (query.isEmpty()) {
            result.sendResult(mutableListOf())
            return
        }
        result.detach()
        val requestToken = nextBrowserToken('s')
        val pending = PendingBrowserResult(query, result)
        pendingSearchCallbacks[requestToken] = pending
        val dispatched = wakeAndRun(
            "if (typeof window.searchMediaItems === 'function') " +
                "window.searchMediaItems(${jsString(query)}, ${jsString(requestToken)});"
        )
        if (!dispatched) {
            if (pendingSearchCallbacks.remove(requestToken, pending)) sendEmptyResult(result)
            return
        }
        mainHandler.postDelayed({
            if (pendingSearchCallbacks.remove(requestToken, pending)) sendEmptyResult(result)
        }, 20_000L)
    }

    private fun wakeAndRun(js: String): Boolean {
        val wv = webView
        if (wv == null) {
            Logger.w(TAG, "Media command dropped because WebView is not bound")
            return false
        }
        Handler(Looper.getMainLooper()).post {
            try {
                wv.resumeTimers()
                wv.onResume()
                wv.dispatchWindowVisibilityChanged(android.view.View.VISIBLE)
                wv.evaluateJavascript(js, null)
            } catch (e: Exception) {
                Logger.e(TAG, "Error waking WebView in wakeAndRun", e)
            }
        }
        return true
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_RUNNING_LOW) {
            coverRequestSeq++
            coverBitmap = null
            coverBitmapTrackKey = ""
            lastCoverUrl = ""
        }
    }

    override fun onDestroy() {
        releasePlaybackLocksNow()
        coverRequestSeq++
        coverBitmapTrackKey = ""
        lastCoverUrl = ""
        if (instance === this) {
            // Detached MediaBrowser results must always be completed, even if the service goes
            // away before the WebView answers.
            drainPendingBrowserResults()
            instance = null
        }
        try { unregisterReceiver(actionReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(bluetoothReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(audioBecomingNoisyReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(headsetReceiver) } catch (_: Exception) {}
        try { getSystemService(AudioManager::class.java).unregisterAudioDeviceCallback(audioRouteCallback) } catch (_: Exception) {}
        getSharedPreferences("spotilol_prefs", MODE_PRIVATE)
            .unregisterOnSharedPreferenceChangeListener(prefsListener)
        if (::mediaSession.isInitialized) {
            try { mediaSession.isActive = false } catch (_: Exception) {}
            try { mediaSession.release() } catch (_: Exception) {}
        }
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
            getSystemService(NotificationManager::class.java)
                .cancel(NOTIFICATION_ID)
        } catch (_: Exception) {}
        val ctx = applicationContext
        widgetScope.launch { runCatching { WidgetUpdater.push(ctx, WidgetState()) } }
        // A WebView deliberately retained while no Activity owns the foreground UI belongs to
        // this service. If the service itself ends (including SwipeStop), do not leak that player.
        SpotifyWebViewSession.destroyIfDetached()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_media_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notif_channel_media_description)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    @Suppress("DEPRECATION")
    private fun setupMediaSession() {
        mediaSession = MediaSessionCompat(this, "SpotilolSession").apply {
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            setCallback(MediaSessionCallback())
            isActive = true
        }
        sessionToken = mediaSession.sessionToken
    }

    private inner class MediaSessionCallback : MediaSessionCompat.Callback() {
        /**
         * Dedicated hardware media keys are handled explicitly so Bluetooth/wired devices do not
         * depend on vendor-specific toggle inference. The ambiguous PLAY_PAUSE/HEADSETHOOK keys
         * deliberately stay with MediaSessionCompat/framework handling, which preserves Android's
         * normal single/double-tap semantics on our minSdk (28+).
         */
        override fun onMediaButtonEvent(mediaButtonEvent: Intent): Boolean {
            val event = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                mediaButtonEvent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
            } else {
                @Suppress("DEPRECATION")
                mediaButtonEvent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
            } ?: return super.onMediaButtonEvent(mediaButtonEvent)

            val dedicated = when (event.keyCode) {
                KeyEvent.KEYCODE_MEDIA_PLAY,
                KeyEvent.KEYCODE_MEDIA_PAUSE,
                KeyEvent.KEYCODE_MEDIA_NEXT,
                KeyEvent.KEYCODE_MEDIA_PREVIOUS,
                KeyEvent.KEYCODE_MEDIA_STOP -> true
                else -> false
            }
            if (!dedicated) return super.onMediaButtonEvent(mediaButtonEvent)

            // A physical press produces DOWN + UP (and can produce repeated DOWN events when held).
            // Execute once on the first DOWN and consume the rest of that same press.
            if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount > 0) return true

            btLog("media key: ${event.keyCode}")
            when (event.keyCode) {
                KeyEvent.KEYCODE_MEDIA_PLAY -> requestPlaybackState(true)
                KeyEvent.KEYCODE_MEDIA_PAUSE -> requestPlaybackState(false)
                KeyEvent.KEYCODE_MEDIA_NEXT -> requestSkip(next = true)
                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> requestSkip(next = false)
                KeyEvent.KEYCODE_MEDIA_STOP -> requestPlaybackState(false)
            }
            return true
        }

        override fun onPrepare() {
            requestPlaybackState(true)
        }

        override fun onPrepareFromMediaId(mediaId: String?, extras: Bundle?) {
            onPlayFromMediaId(mediaId, extras)
        }

        override fun onPlay() {
            requestPlaybackState(true)
        }

        override fun onPlayFromSearch(query: String?, extras: Bundle?) {
            val q = query?.trim().orEmpty()
            if (q.isEmpty()) {
                requestPlaybackState(true)
                return
            }
            wakeAndRun(
                "if(typeof window.playSearchResult==='function')" +
                    "window.playSearchResult(${jsString(q)});"
            )
        }

        override fun onPause() {
            requestPlaybackState(false)
        }

        override fun onSkipToNext() {
            requestSkip(next = true)
        }

        override fun onSkipToPrevious() {
            requestSkip(next = false)
        }

        override fun onStop() {
            requestPlaybackState(false)
        }

        override fun onSeekTo(pos: Long) {
            wakeAndRun("actSeek($pos);")
        }

        override fun onCustomAction(action: String?, extras: Bundle?) {
            when (action) {
                CUSTOM_ACTION_TOGGLE_FAV, "ADDTOFAV_ACTION" -> wakeAndRun("actAddToFav();")
                CUSTOM_ACTION_TOGGLE_SHUFFLE, "SHUFFLE_ACTION" -> wakeAndRun("actToggleShuffle();")
                CUSTOM_ACTION_REPEAT, "REPEAT_ACTION" -> wakeAndRun("actRepeat();")
            }
        }

        override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
            val id = mediaId?.takeIf { it.isNotBlank() } ?: return
            val context = lastActiveContextId
            if (context != null) {
                wakeAndRun("playFromUri(${jsString(id)}, ${jsString(context)});")
            } else {
                wakeAndRun("playFromUri(${jsString(id)});")
            }
        }
    }

    private fun registerReceivers() {
        val filter = IntentFilter().apply {
            addAction(ACTION_PLAY_PAUSE)
            addAction(ACTION_NEXT)
            addAction(ACTION_PREV)
            addAction(ACTION_SHUFFLE)
            addAction(ACTION_REPEAT)
            addAction(ACTION_FAVORITE)
            addAction(ACTION_WIDGET_REFRESH)
        }
        ContextCompat.registerReceiver(
            this,
            actionReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun registerDisconnectReceivers() {
        val noisyFilter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(audioBecomingNoisyReceiver, noisyFilter, RECEIVER_EXPORTED)
            } else {
                registerReceiver(audioBecomingNoisyReceiver, noisyFilter)
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to register audio-becoming-noisy receiver", e)
        }

        val btFilter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(bluetoothReceiver, btFilter, RECEIVER_EXPORTED)
            } else {
                registerReceiver(bluetoothReceiver, btFilter)
            }
        } catch (e: Exception) {
            // BLUETOOTH_CONNECT can be denied on modern Android. Wired/noisy routing must still
            // keep working even if this optional receiver cannot be registered.
            Logger.e(TAG, "Failed to register Bluetooth receiver", e)
        }

        val hsFilter = IntentFilter(Intent.ACTION_HEADSET_PLUG)
        try {
            ContextCompat.registerReceiver(this, headsetReceiver, hsFilter, ContextCompat.RECEIVER_EXPORTED)
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to register headset receiver", e)
        }

        try {
            val am = getSystemService(AudioManager::class.java)
            knownRouteIds.clear()
            knownRouteIds.addAll(am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { it.id })
            am.registerAudioDeviceCallback(audioRouteCallback, mainHandler)
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to register audio route callback", e)
        }
    }

    private fun dispatchPlaybackCommand(play: Boolean) {
        if (::mediaSession.isInitialized) {
            try {
                if (play) mediaSession.controller.transportControls.play()
                else mediaSession.controller.transportControls.pause()
                return
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to dispatch playback command through MediaSession", e)
            }
        }
        requestPlaybackState(play)
    }

    private fun requestPlaybackState(play: Boolean) {
        // Queue exactly one JS command. PlaybackControls owns verification/retry.
        if (!wakeAndRun("actPlayPause($play);")) return
        if (isPlaying == play) return
        isPlaying = play
        updatePlaybackState()
        showNotification()
        pushWidgetState(force = true)
    }

    private fun dispatchSkipCommand(next: Boolean) {
        if (::mediaSession.isInitialized) {
            try {
                if (next) mediaSession.controller.transportControls.skipToNext()
                else mediaSession.controller.transportControls.skipToPrevious()
                return
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to dispatch skip command through MediaSession", e)
            }
        }
        requestSkip(next)
    }

    private fun requestSkip(next: Boolean) {
        wakeAndRun(if (next) "actSkipForward();" else "actSkipBack();")
    }

    private fun pausePlayback() {
        dispatchPlaybackCommand(false)
    }

    private fun resumePlayback() {
        dispatchPlaybackCommand(true)
    }

    fun updateFromMediaStatus(json: String) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { updateFromMediaStatus(json) }
            return
        }
        try {
            lastMediaStatusJson = json
            val obj = org.json.JSONObject(json)
            currentTitle = obj.optString("track", "")
            currentArtist = obj.optString("artist", "")
            currentAlbum = obj.optString("album", "")
            val coverUrl = obj.optString("cover", "")
            val coverTrackKey = currentCoverTrackKey()
            val hasCoverUrl = coverUrl.isNotEmpty() && coverUrl != "null"
            Logger.i(
                ART_TAG,
                "status track=${currentTitle.take(48)} cover=${if (hasCoverUrl) coverUrl.take(140) else "<empty>"} bitmap=${coverBitmap?.let { "${it.width}x${it.height}" } ?: "none"}"
            )

            if (hasCoverUrl) {
                if (coverUrl == lastCoverUrl && coverBitmap != null) {
                    // Same artwork (common inside one album): reuse the decoded bitmap immediately.
                    coverRequestSeq++
                    coverBitmapTrackKey = coverTrackKey
                } else if (coverUrl != lastCoverUrl || coverBitmap == null) {
                    // Keep the previous bitmap briefly while the new artwork is downloading. This
                    // avoids a grey-note flash that makes Origin Island lose its artwork palette.
                    val requestSeq = ++coverRequestSeq
                    lastCoverUrl = coverUrl
                    loadCoverArt(coverUrl, coverTrackKey, requestSeq)
                }
            } else {
                // Spotify often publishes title/artist a fraction of a second before its cover DOM
                // is mounted. Give that normal transition a short grace period instead of
                // immediately replacing valid artwork with an empty MediaSession image.
                lastCoverUrl = ""
                val requestSeq = ++coverRequestSeq
                mainHandler.postDelayed({
                    if (
                        instance === this &&
                        requestSeq == coverRequestSeq &&
                        currentCoverTrackKey() == coverTrackKey &&
                        lastCoverUrl.isEmpty() &&
                        coverBitmapTrackKey != coverTrackKey
                    ) {
                        coverBitmap = null
                        coverBitmapTrackKey = ""
                        updateMetadata()
                        showNotification()
                        pushWidgetState(force = true)
                    }
                }, COVER_TRANSITION_GRACE_MS)
            }

            isPlaying = obj.optBoolean("playing", false)
            isFavorite = obj.optBoolean("fav", false)
            isRepeat = obj.optString("repeat", "false")
            val shuffleVal = obj.optString("shuffle", "off")
            isShuffle = shuffleVal == "shuffle" || shuffleVal == "smart"
            isSmartShuffle = shuffleVal == "smart"
            isShuffleAvailable = shuffleVal != "disabled"
            currentDuration = obj.optLong("duration", 0L)
            currentPosition = obj.optLong("position", 0L)

            if (isPlaying) acquirePlaybackLocks() else schedulePlaybackLocksRelease()

            updatePlaybackState()
            updateMetadata()
            showNotification()
            pushWidgetState(force = true)
        } catch (e: Exception) {
            Logger.e(ART_TAG, "media status apply failed: ${e.message}", e)
        }
    }

    fun updatePlaybackPosition(position: Long) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { updatePlaybackPosition(position) }
            return
        }
        currentPosition = position
        updatePlaybackState()
        pushWidgetState()
    }

    private fun pushWidgetState(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastWidgetPushAt < 2000L) return
        if (!isPlaying && WidgetSource.get(this) == WidgetState.SOURCE_OFFLINE) return
        lastWidgetPushAt = now
        val state = WidgetState(
            title = currentTitle,
            artist = currentArtist,
            playing = isPlaying,
            favorite = isFavorite,
            shuffle = when {
                !isShuffleAvailable -> "disabled"
                isSmartShuffle -> "smart"
                isShuffle -> "shuffle"
                else -> "off"
            },
            repeat = isRepeat,
            position = currentPosition,
            duration = currentDuration,
            accent = accent(),
            source = WidgetState.SOURCE_WEB,
            cover = coverBitmap
        )
        val ctx = applicationContext
        widgetScope.launch { runCatching { WidgetUpdater.push(ctx, state) } }
    }

    private fun updatePlaybackState() {
        val favIcon = if (isFavorite) R.drawable.ic_favorite_filled else R.drawable.ic_favorite
        val shuffleIcon = when {
            isSmartShuffle -> R.drawable.ic_shuffle_smart_active
            isShuffle -> R.drawable.ic_shuffle_active
            else -> R.drawable.ic_shuffle
        }
        val repeatIcon = when (isRepeat) {
            "true" -> R.drawable.ic_repeat
            "mixed" -> R.drawable.ic_repeat_one
            else -> R.drawable.ic_repeat_off
        }
        val state = PlaybackStateCompat.Builder()
            .setActions(PLAYBACK_ACTIONS)
            .setState(
                if (isPlaying) PlaybackStateCompat.STATE_PLAYING
                else PlaybackStateCompat.STATE_PAUSED,
                currentPosition, if (isPlaying) 1f else 0f
            )
            .addCustomAction(
                CUSTOM_ACTION_TOGGLE_FAV,
                if (isFavorite) getString(R.string.notif_action_unlike) else getString(R.string.notif_action_like),
                favIcon
            )
            .addCustomAction(
                CUSTOM_ACTION_TOGGLE_SHUFFLE,
                when {
                    isSmartShuffle -> getString(R.string.notif_shuffle_disable_smart)
                    isShuffle -> getString(R.string.notif_shuffle_disable)
                    else -> getString(R.string.notif_shuffle_enable)
                },
                shuffleIcon
            )
            .addCustomAction(
                CUSTOM_ACTION_REPEAT,
                when (isRepeat) {
                    "true" -> getString(R.string.notif_repeat_disable)
                    "mixed" -> getString(R.string.notif_repeat_disable_one)
                    else -> getString(R.string.notif_repeat_enable)
                },
                repeatIcon
            )
            .build()
        if (::mediaSession.isInitialized) {
            try { mediaSession.setPlaybackState(state) } catch (_: Exception) {}
        }
    }

    private fun currentCoverTrackKey(): String =
        "$currentTitle\u0001$currentArtist\u0001$currentAlbum"

    private fun updateMetadata() {
        val builder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentTitle)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, currentArtist)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, currentAlbum)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, currentDuration)

        lastCoverUrl.takeIf { it.startsWith("https://") }?.let { artUri ->
            builder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, artUri)
            builder.putString(MediaMetadataCompat.METADATA_KEY_ART_URI, artUri)
            builder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI, artUri)
        }

        // Keep the full-size bitmap for NotificationCompat.setLargeIcon(), but use a bounded copy
        // for MediaSession metadata so OEM media renderers stay under Binder transaction limits.
        val sessionArt = coverBitmap?.let { bitmapForMediaSession(it) }
        sessionArt?.let { bmp ->
            builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bmp)
            builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, bmp)
        }

        if (::mediaSession.isInitialized) {
            try {
                val metadata = builder.build()
                mediaSession.setMetadata(metadata)
                val echoed = mediaSession.controller.metadata
                    ?.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART)
                Logger.i(
                    ART_TAG,
                    "MediaSession metadata set art=${sessionArt?.let { "${it.width}x${it.height}" } ?: "none"} echoed=${echoed?.let { "${it.width}x${it.height}" } ?: "none"} uri=${lastCoverUrl.take(100)}"
                )
            } catch (e: Exception) {
                Logger.e(ART_TAG, "MediaSession setMetadata failed: ${e.javaClass.simpleName}: ${e.message}", e)
            }
        }
    }

    private fun bitmapForMediaSession(source: Bitmap): Bitmap {
        val maxSide = maxOf(source.width, source.height)
        if (maxSide <= MEDIA_SESSION_ART_MAX_PX) return source
        val scale = MEDIA_SESSION_ART_MAX_PX.toFloat() / maxSide.toFloat()
        val width = (source.width * scale).toInt().coerceAtLeast(1)
        val height = (source.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, width, height, true)
    }

    private fun loadCoverArt(url: String, trackKey: String, requestSeq: Long) {
        Thread {
            var conn: HttpURLConnection? = null
            try {
                conn = URL(url).openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = true
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.setRequestProperty("User-Agent", COVER_USER_AGENT)
                conn.setRequestProperty("Referer", "https://open.spotify.com/")
                conn.setRequestProperty("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
                conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
                conn.connect()
                val code = conn.responseCode
                Logger.i(
                    ART_TAG,
                    "HTTP $code type=${conn.contentType ?: "?"} length=${conn.contentLengthLong} url=${url.take(120)}"
                )
                if (code !in 200..299) {
                    throw IllegalStateException("Cover HTTP $code")
                }
                val raw = conn.inputStream.use { BitmapFactory.decodeStream(it) }
                    ?: throw IllegalStateException("Cover decode returned null")
                val target = 512
                val scale = min(target.toFloat() / raw.width, target.toFloat() / raw.height)
                val w = (raw.width * scale).toInt().coerceAtLeast(1)
                val h = (raw.height * scale).toInt().coerceAtLeast(1)
                val scaled = Bitmap.createScaledBitmap(raw, w, h, true)
                Logger.i(ART_TAG, "decoded ${raw.width}x${raw.height} -> ${scaled.width}x${scaled.height}")
                if (scaled != raw) raw.recycle()
                mainHandler.post {
                    // A cover fetch can finish after another track has already won the race.
                    if (
                        instance !== this ||
                        requestSeq != coverRequestSeq ||
                        url != lastCoverUrl ||
                        trackKey != currentCoverTrackKey()
                    ) {
                        if (!scaled.isRecycled) scaled.recycle()
                        return@post
                    }
                    coverBitmap = scaled
                    coverBitmapTrackKey = trackKey
                    Logger.i(ART_TAG, "bitmap applied ${scaled.width}x${scaled.height} track=${currentTitle.take(48)}")
                    updateMetadata()
                    showNotification()
                    pushWidgetState(force = true)
                }
            } catch (e: Exception) {
                Logger.w(TAG, "Cover art load failed: ${e.message}")
                mainHandler.post {
                    if (
                        instance === this &&
                        requestSeq == coverRequestSeq &&
                        url == lastCoverUrl &&
                        trackKey == currentCoverTrackKey() &&
                        coverBitmapTrackKey != trackKey
                    ) {
                        coverBitmap = null
                        coverBitmapTrackKey = ""
                        updateMetadata()
                        showNotification()
                        pushWidgetState(force = true)
                    }
                }
            } finally {
                runCatching { conn?.disconnect() }
            }
        }.start()
    }

    private fun showNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotificationSafe())
    }

    private fun buildNotificationSafe(): Notification {
        return try {
            buildNotification()
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to build notification", e)
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.app_name))
                .setSmallIcon(R.drawable.ic_notification)
                .setOngoing(true)
                .build()
        }
    }

    private fun buildMediaStyle(@Suppress("UNUSED_PARAMETER") showShuffle: Boolean): MediaStyle {
        // Android MediaStyle supports at most three compact actions. Shuffle/favorite remain
        // available in the expanded notification exactly as before.
        val compact = intArrayOf(0, 1, 2)
        val style = MediaStyle()
            .setShowActionsInCompactView(*compact)
            .setShowCancelButton(true)
            // A cancel/close affordance must never toggle a paused player back to playing.
            .setCancelButtonIntent(getActionPendingIntent(ACTION_PAUSE))
        if (::mediaSession.isInitialized) {
            style.setMediaSession(mediaSession.sessionToken)
        }
        return style
    }

    private fun buildNotification(): Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val contentIntent = PendingIntent.getActivity(
            this, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val prevAction = NotificationCompat.Action.Builder(
            tintedIcon(R.drawable.ic_skip_prev), getString(R.string.notif_action_previous), getActionPendingIntent(ACTION_PREV)
        ).build()

        val playPauseAction = NotificationCompat.Action.Builder(
            tintedIcon(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
            if (isPlaying) getString(R.string.notif_action_pause) else getString(R.string.notif_action_play),
            getActionPendingIntent(ACTION_PLAY_PAUSE)
        ).build()

        val nextAction = NotificationCompat.Action.Builder(
            tintedIcon(R.drawable.ic_skip_next), getString(R.string.notif_action_next), getActionPendingIntent(ACTION_NEXT)
        ).build()

        val shuffleAction = NotificationCompat.Action.Builder(
            tintedIcon(
                when {
                    isSmartShuffle -> R.drawable.ic_shuffle_smart_active
                    isShuffle -> R.drawable.ic_shuffle_active
                    else -> R.drawable.ic_shuffle
                }
            ),
            when {
                isSmartShuffle -> getString(R.string.notif_shuffle_disable_smart)
                isShuffle -> getString(R.string.notif_shuffle_disable)
                else -> getString(R.string.notif_shuffle_enable)
            },
            getActionPendingIntent(ACTION_SHUFFLE)
        ).build()

        val favAction = NotificationCompat.Action.Builder(
            tintedIcon(if (isFavorite) R.drawable.ic_favorite_filled else R.drawable.ic_favorite),
            if (isFavorite) getString(R.string.notif_action_unlike) else getString(R.string.notif_action_like),
            getActionPendingIntent(ACTION_FAVORITE)
        ).build()

        val actions = mutableListOf<NotificationCompat.Action>()
        actions.add(prevAction)
        actions.add(playPauseAction)
        actions.add(nextAction)
        if (isShuffleAvailable) actions.add(shuffleAction)
        actions.add(favAction)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(currentTitle.ifEmpty { getString(R.string.app_name) })
            .setContentText(currentArtist)
            .setSubText(getString(R.string.app_name))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setColor(accent())
            .setStyle(buildMediaStyle(isShuffleAvailable))
        actions.forEach { builder.addAction(it) }

        coverBitmap?.let { builder.setLargeIcon(it) }
        Logger.i(
            ART_TAG,
            "notification build largeIcon=${coverBitmap?.let { "${it.width}x${it.height}" } ?: "none"} track=${currentTitle.take(48)}"
        )

        return builder.build()
    }

    private fun getActionPendingIntent(action: String): PendingIntent {
        val intent = Intent(this, MediaNotificationService::class.java).setAction(action)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(this, action.hashCode(), intent, flags)
        } else {
            PendingIntent.getService(this, action.hashCode(), intent, flags)
        }
    }

    /**
     * Keep the playback engine alive only while audio is active.
     *
     * The old implementation acquired one timed WakeLock for exactly one hour and
     * only checked whether the object reference was null. After the timeout expired
     * the reference stayed non-null, so long sessions could silently lose CPU
     * protection forever. We now renew a short bounded timeout while playback is healthy;
     * it self-releases if the player/service state machine stops making progress.
     */
    private fun acquirePlaybackLocks() {
        mainHandler.removeCallbacks(playbackLockReleaseRunnable)
        acquireWakeLock()
        acquireWifiLockIfNeeded()

        mainHandler.removeCallbacks(playbackLockGuardRunnable)
        mainHandler.postDelayed(playbackLockGuardRunnable, PLAYBACK_LOCK_GUARD_MS)
    }

    /**
     * Spotify can briefly report `playing=false` between tracks while the next item
     * is being prepared. Releasing the CPU/network locks at that exact moment makes
     * a screen-off transition unnecessarily fragile, so give genuine transitions a
     * short grace period. A confirmed new playing state cancels this release.
     */
    private fun schedulePlaybackLocksRelease() {
        mainHandler.removeCallbacks(playbackLockGuardRunnable)
        mainHandler.removeCallbacks(playbackLockReleaseRunnable)
        mainHandler.postDelayed(playbackLockReleaseRunnable, PLAYBACK_LOCK_RELEASE_GRACE_MS)
    }

    private fun acquireWakeLock() {
        try {
            var lock = wakeLock
            if (lock == null) {
                val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                lock = pm.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "spotilol:media_playback"
                ).apply { setReferenceCounted(false) }
                wakeLock = lock
            }
            // Refresh a bounded timeout on every guard pass. For a non-reference-counted
            // WakeLock Android replaces the previous timeout, so this remains continuous while
            // playback is healthy but still self-releases if our state machine dies unexpectedly.
            lock.acquire(PLAYBACK_WAKE_LOCK_TIMEOUT_MS)
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to acquire playback WakeLock", e)
        }
    }

    private fun isWifiTransportActive(): Boolean {
        return try {
            val cm = getSystemService(ConnectivityManager::class.java)
            val network = cm.activeNetwork ?: return false
            cm.getNetworkCapabilities(network)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        } catch (_: Exception) {
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun acquireWifiLockIfNeeded() {
        if (!isWifiTransportActive()) {
            releaseWifiLock()
            return
        }
        try {
            var lock = wifiLock
            if (lock == null) {
                val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                lock = wm.createWifiLock(
                    WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                    "spotilol:media_wifi"
                ).apply { setReferenceCounted(false) }
                wifiLock = lock
            }
            if (!lock.isHeld) lock.acquire()
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to acquire playback WifiLock", e)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            try { if (it.isHeld) it.release() } catch (_: Exception) {}
        }
        wakeLock = null
    }

    private fun releaseWifiLock() {
        wifiLock?.let {
            try { if (it.isHeld) it.release() } catch (_: Exception) {}
        }
        wifiLock = null
    }

    private fun releasePlaybackLocksNow() {
        mainHandler.removeCallbacks(playbackLockReleaseRunnable)
        mainHandler.removeCallbacks(playbackLockGuardRunnable)
        releaseWakeLock()
        releaseWifiLock()
    }

    /** Called by the existing JS bridge wake hooks around transport actions. */
    fun keepPlaybackAwake() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { keepPlaybackAwake() }
            return
        }
        val alreadyPlaying = isPlaying
        acquirePlaybackLocks()
        // A transport command can call wakeUp before Spotify has reported playing=true.
        // Do not leak a lock forever if that command fails or the WebView disappears.
        if (!alreadyPlaying) {
            mainHandler.removeCallbacks(playbackLockReleaseRunnable)
            mainHandler.postDelayed(playbackLockReleaseRunnable, PLAYBACK_COMMAND_LOCK_TIMEOUT_MS)
        }
    }

    /** Pair for [keepPlaybackAwake]; only releases after the normal transition grace. */
    fun allowPlaybackSleep() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { allowPlaybackSleep() }
            return
        }
        if (!isPlaying) schedulePlaybackLocksRelease()
    }

    /**
     * The Spotify renderer is the actual playback engine. If it dies, keeping an active
     * MediaSession/foreground notification and CPU/network locks would advertise a player that
     * no longer exists and can drain battery indefinitely. Shut only the media shell down; a
     * future MainActivity/WebView can start a fresh service normally.
     */
    fun onPlaybackEngineLost(reason: String) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { onPlaybackEngineLost(reason) }
            return
        }
        Logger.w(TAG, "playback engine lost: $reason")
        isPlaying = false
        releasePlaybackLocksNow()
        if (::mediaSession.isInitialized) {
            try { updatePlaybackState() } catch (_: Exception) {}
            try { mediaSession.isActive = false } catch (_: Exception) {}
        }
        try {
            getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        } catch (_: Exception) {}
        try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) {}
        stopSelf()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val stopOnSwipe = getSharedPreferences("spotilol_prefs", MODE_PRIVATE)
            .getBoolean("SwipeStop", true)
        if (stopOnSwipe) {
            taskRemoved = true
            if (::mediaSession.isInitialized) {
                try { mediaSession.isActive = false } catch (_: Exception) {}
            }
            releasePlaybackLocksNow()
            try {
                getSystemService(NotificationManager::class.java)
                    .cancel(NOTIFICATION_ID)
            } catch (_: Exception) {}
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }
}
