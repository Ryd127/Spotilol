package com.project.lol.bridge

import android.app.Activity
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import com.project.lol.security.WebSecurityPolicy
import com.project.lol.security.BoundedInput
import java.util.concurrent.TimeUnit
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.widget.Toast
import com.project.lol.R
import com.project.lol.service.MediaNotificationService
import com.project.lol.webview.helpers.AdIdStore
import org.json.JSONArray
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import com.project.lol.offline.DownloadManager
import com.project.lol.spicy.SpicyLyricsClient
import com.project.lol.util.Logger
import com.project.lol.webview.helpers.NavToken

class SpotifyBridge(activityRef: WeakReference<Activity>) {

    companion object {
        private const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36"

        private val FILTERED_HEADERS = setOf(
            "x-requested-with",
            "sec-ch-ua-full-version-list",
            "sec-ch-ua-platform-version",
            "sec-ch-ua-arch",
            "sec-ch-ua-bitness",
            "sec-ch-ua-model"
        )

        private const val TAG = "bridge"
        private const val CALL = "bridge.call"
    }

    @Volatile
    private var activityRef = activityRef
    private val mainHandler = Handler(Looper.getMainLooper())

    fun rebind(ref: WeakReference<Activity>) {
        activityRef = ref
    }
    var onLoginDetected: (() -> Unit)? = null
    var onPlayLoaded: (() -> Unit)? = null
    var onMediaStatus: ((String) -> Unit)? = null
    var onMediaPosition: ((Long) -> Unit)? = null
    var onTimerDialogRequest: (() -> Unit)? = null
    var onVolumeDialogRequest: (() -> Unit)? = null
    var onEnterPipRequest: (() -> Unit)? = null
    var onEnterPipVideoRequest: ((Int, Int) -> Unit)? = null
    var onEnterVideoFullscreen: (() -> Unit)? = null
    var onExitVideoFullscreen: (() -> Unit)? = null
    var onEnterLyricsPipRequest: (() -> Unit)? = null
    var onDownloadTrack: ((String) -> Unit)? = null
    var onDownloadCollection: ((String) -> Unit)? = null
    var onPlayerExpanded: ((Boolean) -> Unit)? = null
    var onWideVideo: ((Boolean) -> Unit)? = null
    var onCtxMenuState: ((Boolean) -> Unit)? = null

    @JavascriptInterface
    fun loginDetected() {
        val activity = activityRef.get() ?: return
        Logger.i(TAG, "login detected")
        activity.getSharedPreferences("spotilol_prefs", Activity.MODE_PRIVATE)
            .edit()
            .putBoolean("LoggedIn", true)
            .apply()
        activity.runOnUiThread {
            onLoginDetected?.invoke()
        }
    }

    @JavascriptInterface
    fun deferMessage(msg: String?) {
        val activity = activityRef.get() ?: return
        if (msg == "adblock") return
        val display = when (msg) {
            "unlock" -> activity.getString(R.string.bridge_player_unlocked)
            "reload" -> activity.getString(R.string.bridge_reloading)
            else -> msg
        }
        Logger.d(CALL, "deferMessage: $display")
        activity.runOnUiThread {
            Toast.makeText(activity, display, Toast.LENGTH_SHORT).show()
        }
    }

    @JavascriptInterface
    fun isWoke(): Boolean {
        val activity = activityRef.get() ?: return true
        val visible = activity.window?.decorView?.visibility == View.VISIBLE
        Logger.v(CALL, "isWoke -> $visible")
        return visible
    }

    @JavascriptInterface
    fun wakeUp() {
        Logger.v(CALL, "wakeUp")
    }

    @JavascriptInterface
    fun wakeOff() {
        Logger.v(CALL, "wakeOff")
    }

    @JavascriptInterface
    fun cssInjected() {
        Logger.v(CALL, "cssInjected")
    }

    @JavascriptInterface
    fun dbg(level: String?, msg: String?) {
        if (!Logger.isEnabled()) return
        Logger.js(level, msg)
    }

    @JavascriptInterface
    fun clearDebugLog() {
        Logger.clear()
        Logger.d(CALL, "logger buffer cleared from js")
    }

    @JavascriptInterface
    fun recAdContentIds(json: String?) {
        val payload = json ?: return
        val arr = try { JSONArray(payload) } catch (e: Exception) { return }
        val ids = ArrayList<String>(arr.length())
        for (i in 0 until arr.length()) {
            val v = arr.optString(i, "")
            if (v.isNotEmpty()) ids.add(v)
        }
        if (ids.isEmpty()) return
        val token = NavToken.current()
        mainHandler.post {
            if (token != NavToken.current()) {
                Logger.d(CALL, "recAdContentIds: dropped ${ids.size} stale ids")
                return@post
            }
            if (AdIdStore.addAll(ids)) Logger.d(CALL, "recAdContentIds: ${ids.size}")
        }
    }

    @JavascriptInterface
    fun playLoaded() {
        Logger.i(TAG, "play loaded, web player ready")
        MediaNotificationService.onPlayerLoaded()
        activityRef.get()?.runOnUiThread {
            onPlayLoaded?.invoke()
        }
    }

    @JavascriptInterface
    fun recMediaPosition(position: Long) {
        Logger.v(CALL, "position=$position")
        onMediaPosition?.invoke(position)
        MediaNotificationService.instance?.updatePlaybackPosition(position)
    }

    @JavascriptInterface
    fun recMediaStatus(json: String?) {
        json?.let {
            Logger.d(CALL, "media status (${it.length} chars): ${it.take(180)}")
            onMediaStatus?.invoke(it)
            MediaNotificationService.instance?.updateFromMediaStatus(it)
        }
    }

    @JavascriptInterface
    fun onMediaItemsLoaded(parentId: String?, json: String?) {
        Logger.d(CALL, "media items parent=$parentId size=${json?.length ?: 0}")
        parentId?.let { MediaNotificationService.onMediaItemsLoaded(it, json ?: "[]") }
    }

    @JavascriptInterface
    fun onSearchCompleted(query: String?, json: String?) {
        Logger.d(CALL, "search completed query=$query size=${json?.length ?: 0}")
        query?.let { MediaNotificationService.onSearchCompleted(it, json ?: "[]") }
    }

    @JavascriptInterface
    fun manageTShut(enabled: Boolean) {
        Logger.v(CALL, "manageTShut=$enabled")
    }

    @JavascriptInterface
    fun manageTSleep(enabled: Boolean) {
        Logger.v(CALL, "manageTSleep=$enabled")
    }

    @JavascriptInterface
    fun recAccountName(name: String) {
        val activity = activityRef.get() ?: return
        val trimmed = name.trim()
        if (trimmed.isNotEmpty()) {
            Logger.i(TAG, "account name: $trimmed")
            activity.getSharedPreferences("spotilol_prefs", Activity.MODE_PRIVATE)
                .edit()
                .putString("CurrentAccountName", trimmed)
                .apply()
        }
    }

    @JavascriptInterface
    fun openTimerDialog() {
        val activity = activityRef.get() ?: return
        Logger.d(CALL, "openTimerDialog")
        activity.runOnUiThread {
            onTimerDialogRequest?.invoke()
        }
    }

    @JavascriptInterface
    fun openVolumeDialog() {
        val activity = activityRef.get() ?: return
        Logger.d(CALL, "openVolumeDialog")
        activity.runOnUiThread {
            onVolumeDialogRequest?.invoke()
        }
    }

    @JavascriptInterface
    fun playerExpanded(expanded: Boolean) {
        val activity = activityRef.get() ?: return
        Logger.v(CALL, "playerExpanded=$expanded")
        activity.runOnUiThread {
            onPlayerExpanded?.invoke(expanded)
        }
    }

    @JavascriptInterface
    fun wideVideo(showing: Boolean) {
        val activity = activityRef.get() ?: return
        Logger.v(CALL, "wideVideo=$showing")
        activity.runOnUiThread {
            onWideVideo?.invoke(showing)
        }
    }

    @JavascriptInterface
    fun ctxMenuState(open: Boolean) {
        val activity = activityRef.get() ?: return
        Logger.v(CALL, "ctxMenuState=$open")
        activity.runOnUiThread {
            onCtxMenuState?.invoke(open)
        }
    }

    @JavascriptInterface
    fun enterPip() {
        val activity = activityRef.get() ?: return
        Logger.i(CALL, "enterPip")
        activity.runOnUiThread {
            onEnterPipRequest?.invoke()
        }
    }

    @JavascriptInterface
    fun enterPipVideo(w: Int, h: Int) {
        val activity = activityRef.get() ?: return
        Logger.i(CALL, "enterPipVideo ${w}x$h")
        activity.runOnUiThread {
            onEnterPipVideoRequest?.invoke(w, h)
        }
    }

    @JavascriptInterface
    fun enterVideoFullscreen() {
        val activity = activityRef.get() ?: return
        Logger.i(CALL, "enterVideoFullscreen")
        activity.runOnUiThread {
            onEnterVideoFullscreen?.invoke()
        }
    }

    @JavascriptInterface
    fun exitVideoFullscreen() {
        val activity = activityRef.get() ?: return
        Logger.i(CALL, "exitVideoFullscreen")
        activity.runOnUiThread {
            onExitVideoFullscreen?.invoke()
        }
    }

    @JavascriptInterface
    fun enterLyricsPip() {
        val activity = activityRef.get() ?: return
        Logger.i(CALL, "enterLyricsPip")
        activity.runOnUiThread {
            onEnterLyricsPipRequest?.invoke()
        }
    }

    @JavascriptInterface
    fun downloadTrack(json: String?) {
        Logger.i(CALL, "downloadTrack (${json?.length ?: 0} chars)")
        json?.let { onDownloadTrack?.invoke(it) }
    }

    @Suppress("unused")
    @JavascriptInterface
    fun downloadCollection(json: String?) {
        Logger.i(CALL, "downloadCollection (${json?.length ?: 0} chars)")
        json?.let { onDownloadCollection?.invoke(it) }
    }

    @Suppress("unused")
    @JavascriptInterface
    fun skipDownload() {
        Logger.i(CALL, "skipDownload")
        DownloadManager.skipCurrent()
    }

    @Suppress("unused")
    @JavascriptInterface
    fun cancelDownload() {
        Logger.i(CALL, "cancelDownload")
        DownloadManager.cancelAll()
    }

    private val fetchClient by lazy {
        OkHttpClient.Builder()
            .followRedirects(false).followSslRedirects(false)
            .connectTimeout(10, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS).build()
    }
    private val oneShotFetchClient by lazy { NativeRequestPolicy.oneShotClient(fetchClient) }

    fun nFetch(url: String, optsJson: String?): String {
        return try {
            require(WebSecurityPolicy.isNativeFetch(url)) { "Untrusted native request target" }
            require((optsJson?.length ?: 0) <= 1_048_576) { "Request too large" }
            val opts = if (optsJson.isNullOrBlank()) JSONObject() else JSONObject(optsJson)
            val method = opts.optString("method", "GET").uppercase(Locale.ROOT)
            require(method in setOf("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"))
            val body = if (opts.has("body") && !opts.isNull("body")) opts.getString("body") else null
            require((body?.toByteArray(Charsets.UTF_8)?.size ?: 0) <= 1_048_576)
            val headers = opts.optJSONObject("headers") ?: JSONObject()
            require(headers.length() <= 64)
            val builder = Request.Builder().url(url)
            val blocked = setOf("cookie", "cookie2", "host", "origin", "referer", "connection",
                "content-length", "transfer-encoding", "proxy-authorization", "proxy-connection",
                "te", "trailer", "upgrade", "accept-encoding", "user-agent", "x-requested-with")
            var headerBytes = 0
            headers.keys().forEach { key ->
                val lower = key.lowercase(Locale.ROOT)
                if (lower !in blocked && !lower.startsWith("sec-") && !headers.isNull(key)) {
                    val value = headers.getString(key)
                    headerBytes += key.length + value.length
                    require(headerBytes <= 16_384)
                    builder.header(key, value)
                }
            }
            builder.header("User-Agent", DESKTOP_UA)
                .header("sec-ch-ua-platform", "\"Windows\"")
                .header("sec-ch-ua-mobile", "?0")
                .header("sec-ch-ua", "\"Chromium\";v=\"150\", \"Google Chrome\";v=\"150\"")
                .header("Origin", "https://open.spotify.com")
                .header("Referer", "https://open.spotify.com/")
            if (WebSecurityPolicy.isCookieHost(url)) {
                CookieManager.getInstance().getCookie(url)?.takeIf { it.isNotEmpty() }?.let { builder.header("Cookie", it) }
            }
            val oneShot = opts.optBoolean("noRetry", false)
            val requestBody = NativeRequestPolicy.body(method, body,
                headers.optString("Content-Type").toMediaTypeOrNull(), oneShot)
            val client = if (oneShot) oneShotFetchClient else fetchClient
            client.newCall(builder.method(method, requestBody).build()).execute().use { response ->
                require(response.code !in setOf(300, 301, 302, 303, 305, 307, 308)) { "Native redirects are not permitted" }
                if (WebSecurityPolicy.isCookieHost(url)) {
                    response.headers.values("Set-Cookie").forEach { CookieManager.getInstance().setCookie(url, it) }
                    CookieManager.getInstance().flush()
                }
                val responseBody = response.body.byteStream().use { BoundedInput.readUtf8(it, 4_194_304) }
                val responseHeaders = JSONObject()
                response.headers.names().forEach { key ->
                    if (!key.equals("Set-Cookie", true) && !key.equals("Set-Cookie2", true)) {
                        responseHeaders.put(key, response.header(key))
                    }
                }
                JSONObject().put("status", response.code).put("body", responseBody)
                    .put("headers", responseHeaders).toString()
            }
        } catch (_: Exception) {
            Logger.w(TAG, "native request rejected or failed")
            "{\"status\":0,\"body\":\"Native request rejected or failed\",\"headers\":{}}"
        }
    }

    @Suppress("unused")
    @JavascriptInterface
    fun spicyLyrics(trackId: String?, title: String?, artist: String?, duration: Int): String {
        val id = trackId?.trim().orEmpty()
        if (id.length != 22) return "null"
        return try {
            val payload = SpicyLyricsClient.fetch(id, title?.trim(), artist?.trim(), duration)
            Logger.d(
                TAG,
                "spicyLyrics $id [${title ?: "-"}] dur=$duration -> ${if (payload == null) "miss" else "${payload.length} bytes"}"
            )
            payload ?: "null"
        } catch (e: Exception) {
            Logger.e(TAG, "spicyLyrics failed $id", e)
            "null"
        }
    }
}
