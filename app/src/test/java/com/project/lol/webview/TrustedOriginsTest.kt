package com.project.lol.webview

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrustedOriginsTest {

    @Test
    fun spotifyDeepLinksAcceptOnlyExactOwnedHosts() {
        assertTrue(TrustedOrigins.isSpotifyDeepLink("https://open.spotify.com/track/123"))
        assertTrue(TrustedOrigins.isSpotifyDeepLink("https://spotify.link/abc"))
        assertFalse(TrustedOrigins.isSpotifyDeepLink("https://evilspotify.com/"))
        assertFalse(TrustedOrigins.isSpotifyDeepLink("https://spotify.com.evil.example/"))
        assertFalse(TrustedOrigins.isSpotifyDeepLink("http://open.spotify.com/track/123"))
        assertFalse(TrustedOrigins.isSpotifyDeepLink("https://spotify.com@evil.example/"))
    }

    @Test
    fun bridgeIsLimitedToSpotifyPagesThatNeedNativeAccess() {
        assertTrue(TrustedOrigins.isBridgeOrigin("https://open.spotify.com/"))
        assertTrue(TrustedOrigins.isBridgeOrigin("https://accounts.spotify.com/login"))
        assertFalse(TrustedOrigins.isBridgeOrigin("https://spotify.link/abc"))
        assertFalse(TrustedOrigins.isBridgeOrigin("https://accounts.google.com/"))
        assertFalse(TrustedOrigins.isBridgeOrigin("https://evilspotify.com/"))
    }

    @Test
    fun oauthAllowlistDoesNotUseSubstringMatching() {
        assertTrue(TrustedOrigins.isAllowedMainFrame("https://accounts.google.com/"))
        assertTrue(TrustedOrigins.isAllowedMainFrame("https://www.facebook.com/login"))
        assertTrue(TrustedOrigins.isAllowedMainFrame("https://appleid.apple.com/"))
        assertFalse(TrustedOrigins.isAllowedMainFrame("https://accounts.google.com.evil.example/"))
        assertFalse(TrustedOrigins.isAllowedMainFrame("https://evil.google.example/"))
    }

    @Test
    fun nativeFetchCannotBecomeGenericNetworkBridge() {
        assertTrue(TrustedOrigins.isNativeFetchTarget("https://gew4-spclient.spotify.com/connect-state/v1/player"))
        assertTrue(TrustedOrigins.isNativeFetchTarget("https://i.scdn.co/image/abc"))
        assertFalse(TrustedOrigins.isNativeFetchTarget("https://example.com/"))
        assertFalse(TrustedOrigins.isNativeFetchTarget("https://spotify.com.evil.example/"))
        assertFalse(TrustedOrigins.isNativeFetchTarget("file:///data/local/tmp/x"))
    }
}
