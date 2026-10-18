package com.project.lol.ui.components

import android.webkit.WebView
import com.project.lol.security.WebSecurityPolicy
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.json.JSONTokener
import kotlin.coroutines.resume

/** Bounded command polling, only for explicit user actions; never retries a mutation. */
internal suspend fun performNativeAction(
    browser: WebView, request: NativeActionRequest, stillCurrent: () -> Boolean
): NativeActionReply {
    if (!stillCurrent() || !WebSecurityPolicy.isPlayer(browser.url)) return NativeActionReply()
    val result = withTimeoutOrNull(12500L) {
        val started = browser.nativeEval(NativeActionsJs.INSTALL + "\nwindow.splNativeAction(" + request.json() + ")")
        if (started != "true") return@withTimeoutOrNull NativeActionReply(code = "busy")
        while (stillCurrent() && WebSecurityPolicy.isPlayer(browser.url)) {
            val raw = browser.nativeEval("window.splNativeActionResult&&window.splNativeActionResult(" + JSONObject.quote(request.id) + ",true)")
            if (raw != null && raw != "null" && raw.length < 400000) {
                val decoded = runCatching { JSONTokener(raw).nextValue() as? String }.getOrNull()
                val reply = decoded?.let { runCatching { NativeActionReply.parse(it) }.getOrNull() }
                if (reply != null && reply.state != "pending") return@withTimeoutOrNull reply
            }
            delay(120L)
        }
        NativeActionReply(state = "unknown", code = "navigation")
    }
    return result ?: NativeActionReply(state = "unknown", code = "timeout")
}

private suspend fun WebView.nativeEval(code: String): String? = suspendCancellableCoroutine { continuation ->
    evaluateJavascript(code) { if (continuation.isActive) continuation.resume(it) }
}
