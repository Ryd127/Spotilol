package com.project.lol.security;

import org.junit.Test;

import static org.junit.Assert.*;

/** Tests the policy used by OriginScopedBridge, not only the legacy URL helper. */
public class WebSecurityPolicyTest {
    private static final String PLAYER = "https://open.spotify.com";
    private static final String ACCOUNTS = "https://accounts.spotify.com";

    @Test public void newUiCommandsArePlayerOnlyAndMainFrameOnly() {
        for (String method : new String[]{"openVolumeDialog", "ctxMenuState", "downloadTrack", "recMediaStatus", "nFetch"}) {
            assertTrue(method, WebSecurityPolicy.isBridgeCall(PLAYER, true, method));
            assertFalse(method, WebSecurityPolicy.isBridgeCall(PLAYER, false, method));
            assertFalse(method, WebSecurityPolicy.isBridgeCall(ACCOUNTS, true, method));
            assertFalse(method, WebSecurityPolicy.isBridgeCall("https://accounts.google.com", true, method));
        }
        assertTrue(WebSecurityPolicy.isBridgeCall(ACCOUNTS, true, "loginDetected"));
        assertFalse(WebSecurityPolicy.isBridgeCall(PLAYER, true, "unknownCommand"));
    }

    @Test public void bridgeRequiresAnExactOriginRatherThanAPageUrl() {
        assertTrue(WebSecurityPolicy.isBridgeOrigin(PLAYER));
        assertTrue(WebSecurityPolicy.isBridgeOrigin(ACCOUNTS));
        for (String origin : new String[]{PLAYER + "/", PLAYER + "?x=1", PLAYER + "#fragment",
                "https://open.spotify.com.evil.example", "https://open.spotify.com@evil.example",
                "http://open.spotify.com", "https://open.spotify.com:8443", "null"}) {
            assertFalse(origin, WebSecurityPolicy.isBridgeOrigin(origin));
        }
    }

    @Test public void nativeFetchRejectsRedirectAndAuthorityTricks() {
        assertTrue(WebSecurityPolicy.isNativeFetch("https://gew4-spclient.spotify.com/connect-state/v1/player"));
        assertTrue(WebSecurityPolicy.isNativeFetch("https://api.spotify.com/v1/me"));
        assertTrue(WebSecurityPolicy.isNativeFetch("https://open.spotify.com:443/get_access_token"));
        for (String url : new String[]{"http://api.spotify.com/v1/me", "https://api.spotify.com:8443/v1/me",
                "https://api.spotify.com:0443/v1/me", "https://api.spotify.com./v1/me",
                "https://api.spotify.com.evil.example/v1/me", "https://evil.example@api.spotify.com/v1/me",
                "https://api.spotify.com/v1/me%0d%0aX-Test:x", "file:///data/local/tmp/x", "https://127.0.0.1/"}) {
            assertFalse(url, WebSecurityPolicy.isNativeFetch(url));
        }
        assertTrue(WebSecurityPolicy.isCookieHost("https://open.spotify.com/path"));
        assertFalse(WebSecurityPolicy.isCookieHost("https://api.spotify.com/path"));
    }

    @Test public void restoredCookiesCannotInjectAttributes() {
        assertEquals("sp_dc=value; Path=/; Secure; HttpOnly", WebSecurityPolicy.restoreCookie("sp_dc=value"));
        assertEquals("other=value; Path=/; Secure", WebSecurityPolicy.restoreCookie("other=value"));
        for (String cookie : new String[]{"sp_dc=value; Domain=evil.example", "Domain=evil.example",
                "Path=/", "bad name=value", "sp_dc=value\r\nSet-Cookie: x=y", "missing-equals"}) {
            assertNull(cookie, WebSecurityPolicy.restoreCookie(cookie));
        }
    }
}
