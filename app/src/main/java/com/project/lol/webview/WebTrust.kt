package com.project.lol.webview

import android.net.Uri
import java.util.Locale

/**
 * Central trust policy for URLs that can reach Spotilol's privileged WebView / JS bridge.
 *
 * Keep host matching exact-or-subdomain. Plain endsWith("spotify.com") is unsafe because
 * hosts such as evilspotify.com would otherwise be accepted.
 */
object WebTrust {
    private fun normalizeHost(host: String?): String? =
        host?.trim()?.trimEnd('.')?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }

    fun hostMatches(host: String?, root: String): Boolean {
        val h = normalizeHost(host) ?: return false
        val r = root.lowercase(Locale.ROOT)
        return h == r || h.endsWith(".$r")
    }

    fun httpsHost(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true)) return null
        return normalizeHost(uri.host)
    }

    fun isSpotifyHost(host: String?): Boolean = hostMatches(host, "spotify.com")

    fun isSpotifyUrl(url: String?): Boolean = isSpotifyHost(httpsHost(url))

    fun isOpenSpotifyUrl(url: String?): Boolean =
        httpsHost(url) == "open.spotify.com"

    fun isSpotifyDeepLinkUrl(url: String?): Boolean {
        val host = httpsHost(url) ?: return false
        return host == "spotify.link" || isSpotifyHost(host)
    }

    fun isOAuthUrl(url: String?): Boolean {
        val host = httpsHost(url) ?: return false
        return hostMatches(host, "google.com") ||
            hostMatches(host, "facebook.com") ||
            hostMatches(host, "apple.com")
    }

    fun isAllowedMainFrameUrl(url: String?): Boolean {
        val host = httpsHost(url) ?: return false
        return host == "spotify.link" ||
            isSpotifyHost(host) ||
            hostMatches(host, "google.com") ||
            hostMatches(host, "facebook.com") ||
            hostMatches(host, "apple.com")
    }

    fun isAllowedNativeFetchUrl(url: String?): Boolean {
        val host = httpsHost(url) ?: return false
        return isSpotifyHost(host) ||
            hostMatches(host, "scdn.co") ||
            hostMatches(host, "spotifycdn.com")
    }
}
