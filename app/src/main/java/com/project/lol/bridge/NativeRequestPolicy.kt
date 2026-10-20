package com.project.lol.bridge

import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink

/** Explicit user mutations must not be replayed after a disconnect or retryable HTTP response. */
internal object NativeRequestPolicy {
    fun oneShotClient(base: OkHttpClient): OkHttpClient =
        base.newBuilder().retryOnConnectionFailure(false).build()

    fun body(method: String, text: String?, type: MediaType?, oneShot: Boolean): RequestBody? {
        if (method == "GET" || method == "HEAD") {
            require(text.isNullOrEmpty())
            return null
        }
        val payload = when (method) {
            "POST", "PUT", "PATCH" -> (text ?: "").toRequestBody(type)
            else -> text?.toRequestBody(type)
        }
        if (!oneShot) return payload
        // Even an empty DELETE needs this marker: retryOnConnectionFailure alone does
        // not suppress a 503 Retry-After: 0 follow-up in OkHttp.
        val delegate = payload ?: "".toRequestBody(type)
        return object : RequestBody() {
            override fun contentType() = delegate.contentType()
            override fun contentLength() = delegate.contentLength()
            override fun isOneShot() = true
            override fun writeTo(sink: BufferedSink) = delegate.writeTo(sink)
        }
    }
}
