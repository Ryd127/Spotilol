package com.project.lol.webview

import android.app.Activity
import android.content.MutableContextWrapper
import android.os.Looper
import android.view.ViewGroup
import android.webkit.WebView
import com.project.lol.bridge.SpotifyBridge
import com.project.lol.service.MediaNotificationService
import com.project.lol.util.Logger
import java.lang.ref.WeakReference

/**
 * Keeps the single Spotify WebView alive across MainActivity recreation/background detachment
 * while the media service still owns playback. SwipeStop itself is enforced by the service's
 * onTaskRemoved() callback. The same WebView instance,
 * JS bridge and injected player state are reused; no second Spotify player is created.
 *
 * WebView is always created with a MutableContextWrapper. While attached, the wrapper points
 * at the current Activity so UI/WebChrome behavior stays identical. While detached it points
 * at applicationContext, avoiding retention of a destroyed Activity.
 */
object SpotifyWebViewSession {
    private const val TAG = "wv.session"

    data class Attached(
        val webView: WebView,
        val bridge: SpotifyBridge,
        val webViewClient: SpotifyWebViewClient,
        val chromeClient: SpotifyWebChromeClient
    )

    private var webView: WebView? = null
    private var bridge: SpotifyBridge? = null
    private var webViewClient: SpotifyWebViewClient? = null
    private var chromeClient: SpotifyWebChromeClient? = null
    private var contextWrapper: MutableContextWrapper? = null
    private var owner: WeakReference<Activity>? = null

    private fun checkMainThread() {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "SpotifyWebViewSession must be used on the main thread"
        }
    }

    fun bridgeFor(activity: Activity): SpotifyBridge {
        checkMainThread()
        val existing = bridge
        if (existing != null) {
            existing.bindActivity(activity)
            owner = WeakReference(activity)
            return existing
        }
        return SpotifyBridge(WeakReference(activity)).also {
            bridge = it
            owner = WeakReference(activity)
        }
    }

    fun attach(activity: Activity): Attached? {
        checkMainThread()
        val wv = webView ?: return null
        val b = bridge ?: return null
        val client = webViewClient ?: return null
        val chrome = chromeClient ?: return null
        val wrapper = contextWrapper ?: return null

        (wv.parent as? ViewGroup)?.removeView(wv)
        wrapper.baseContext = activity
        b.bindActivity(activity)
        owner = WeakReference(activity)
        MediaNotificationService.webView = wv
        Logger.i(TAG, "reattached retained Spotify WebView")
        return Attached(wv, b, client, chrome)
    }

    fun register(
        activity: Activity,
        webView: WebView,
        bridge: SpotifyBridge,
        webViewClient: SpotifyWebViewClient,
        chromeClient: SpotifyWebChromeClient
    ) {
        checkMainThread()
        val wrapper = webView.context as? MutableContextWrapper
            ?: error("Spotify WebView must use MutableContextWrapper")

        this.webView = webView
        this.bridge = bridge
        this.webViewClient = webViewClient
        this.chromeClient = chromeClient
        this.contextWrapper = wrapper
        this.owner = WeakReference(activity)
        bridge.bindActivity(activity)
        webViewClient.onSessionInvalidated = { invalidated -> invalidate(invalidated) }
        MediaNotificationService.webView = webView
        Logger.i(TAG, "registered Spotify WebView session")
    }

    /**
     * Detaches UI-only references while preserving the real Spotify WebView/player.
     * Returns true only when the current Activity actually owned a live session.
     */
    fun detachForBackground(activity: Activity): Boolean {
        checkMainThread()
        val wv = webView ?: return false
        if (owner?.get() !== activity) return false

        (wv.parent as? ViewGroup)?.removeView(wv)
        bridge?.clearUiCallbacks()
        bridge?.unbindActivity(activity)
        webViewClient?.clearUiCallbacks()
        chromeClient?.clearUiCallbacks()
        chromeClient?.cleanupChildWindow()
        contextWrapper?.baseContext = activity.applicationContext
        owner = null

        // Intentionally keep the same WebView bound to the media service.
        MediaNotificationService.webView = wv
        Logger.i(TAG, "detached Spotify WebView for background playback")
        return true
    }

    /**
     * Called before a deliberate WebView teardown (service off, account switch, etc.).
     * Returns false if a stale Activity tries to destroy a session already rebound to a newer one.
     */
    fun releaseForDestroy(activity: Activity, target: WebView): Boolean {
        checkMainThread()
        if (webView !== target) return true

        val currentOwner = owner?.get()
        if (currentOwner != null && currentOwner !== activity) {
            Logger.w(TAG, "ignored destroy request from stale Activity")
            return false
        }

        bridge?.clearUiCallbacks()
        if (currentOwner != null) bridge?.unbindActivity(currentOwner)
        webViewClient?.onSessionInvalidated = null
        webViewClient?.cleanup()
        chromeClient?.cleanup()
        contextWrapper?.baseContext = activity.applicationContext
        clearReferences(target)
        Logger.i(TAG, "released Spotify WebView session for destroy")
        return true
    }

    /**
     * If the foreground media service dies while no Activity owns the retained player, the
     * session has no legitimate lifetime left. Tear it down instead of leaking a WebView.
     */
    fun destroyIfDetached(): Boolean {
        checkMainThread()
        val target = webView ?: return false
        if (owner?.get() != null) return false

        bridge?.clearUiCallbacks()
        webViewClient?.onSessionInvalidated = null
        webViewClient?.cleanup()
        chromeClient?.cleanup()
        try { target.stopLoading() } catch (_: Exception) {}
        try { target.removeJavascriptInterface("AndBridge") } catch (_: Exception) {}
        try { (target.parent as? ViewGroup)?.removeView(target) } catch (_: Exception) {}
        try { target.removeAllViews() } catch (_: Exception) {}
        clearReferences(target)
        try { target.destroy() } catch (_: Exception) {}
        Logger.i(TAG, "destroyed detached Spotify WebView after media service stop")
        return true
    }

    /** Renderer-process death already makes the WebView unusable; never reattach it. */
    private fun invalidate(target: WebView) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            target.post { invalidate(target) }
            return
        }
        if (webView !== target) return

        val currentOwner = owner?.get()
        // The renderer is the real audio engine. Tell the media shell before dropping the
        // session references so it cannot keep stale PLAYING state or playback locks alive.
        MediaNotificationService.instance?.onPlaybackEngineLost("WebView renderer process gone")
        bridge?.clearUiCallbacks()
        if (currentOwner != null) bridge?.unbindActivity(currentOwner)
        chromeClient?.cleanup()
        clearReferences(target)
        Logger.w(TAG, "invalidated Spotify WebView session after renderer loss")
    }

    private fun clearReferences(target: WebView) {
        if (MediaNotificationService.webView === target) {
            MediaNotificationService.webView = null
        }
        webView = null
        bridge = null
        webViewClient = null
        chromeClient = null
        contextWrapper = null
        owner = null
    }
}
