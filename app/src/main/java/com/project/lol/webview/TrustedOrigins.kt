package com.project.lol.webview

import java.net.URI
import java.util.Locale

/**
 * Central trust policy for URLs that are allowed to stay inside Spotilol's privileged WebView.
 *
 * The JS bridge is intentionally limited to Spotify-owned origins. OAuth providers may stay in
 * the WebView so login keeps working, but they never receive the native AndBridge object.
 */
object TrustedOrigins {

    private val bridgeHosts = setOf(
        "spotify.com",
        "www.spotify.com",
        "open.spotify.com",
        "play.spotify.com",
        "accounts.spotify.com"
    )

    private val deepLinkHosts = setOf(
        "spotify.com",
        "www.spotify.com",
        "open.spotify.com",
        "play.spotify.com",
        "spotify.link"
    )

    private fun httpsHost(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return runCatching {
            val uri = URI(url)
            if (!uri.scheme.equals("https", ignoreCase = true)) return null
            uri.host?.lowercase(Locale.ROOT)
        }.getOrNull()
    }

    fun isSpotifyHost(host: String?): Boolean {
        val value = host?.lowercase(Locale.ROOT) ?: return false
        return value == "spotify.com" || value.endsWith(".spotify.com")
    }

    fun isSpotifyWebUrl(url: String?): Boolean {
        val host = httpsHost(url) ?: return false
        return isSpotifyHost(host) || host == "spotify.link"
    }

    fun isSpotifyDeepLink(url: String?): Boolean {
        val host = httpsHost(url) ?: return false
        return host in deepLinkHosts
    }

    fun isBridgeOrigin(url: String?): Boolean {
        val host = httpsHost(url) ?: return false
        return host in bridgeHosts
    }

    fun isGoogleOrigin(url: String?): Boolean {
        val host = httpsHost(url) ?: return false
        return host == "google.com" ||
            host.endsWith(".google.com")
    }

    fun isOAuthOrigin(url: String?): Boolean {
        val host = httpsHost(url) ?: return false
        return isSpotifyHost(host) ||
            host == "spotify.link" ||
            host == "google.com" ||
            host.endsWith(".google.com") ||
            host == "facebook.com" ||
            host.endsWith(".facebook.com") ||
            host == "appleid.apple.com" ||
            host.endsWith(".apple.com")
    }

    fun isAllowedMainFrame(url: String?): Boolean = isOAuthOrigin(url)

    fun isNativeFetchTarget(url: String?): Boolean {
        val host = httpsHost(url) ?: return false
        return isSpotifyHost(host) ||
            host == "scdn.co" ||
            host.endsWith(".scdn.co") ||
            host == "spotifycdn.com" ||
            host.endsWith(".spotifycdn.com")
    }
}
