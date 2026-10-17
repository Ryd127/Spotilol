package com.project.lol.ui.components;

import java.net.URI;
import java.util.Locale;

/** Pure URL/decoding policy, shared by all native artwork surfaces. */
public final class ArtworkPolicy {
    private ArtworkPolicy() {}

    public static boolean isAllowed(String url) {
        if (url == null || url.length() > 850) return false;
        try {
            URI parsed = new URI(url);
            String host = parsed.getHost();
            if (!"https".equalsIgnoreCase(parsed.getScheme()) || parsed.getRawUserInfo() != null ||
                    host == null || (parsed.getPort() != -1 && parsed.getPort() != 443)) return false;
            host = host.toLowerCase(Locale.ROOT);
            String path = parsed.getPath().toLowerCase(Locale.ROOT);
            if (path.matches(".*(equaliz|placeholder|favicon|playing[-_.]).*")) return false;
            return host.equals("scdn.co") || host.endsWith(".scdn.co") ||
                    host.equals("spotifycdn.com") || host.endsWith(".spotifycdn.com");
        } catch (Exception ignored) { return false; }
    }

    public static int sampleSize(int width, int height) {
        if (width <= 0 || height <= 0 || (long) width * height > 40_000_000L) return 0;
        int sample = 1;
        while (Math.max(width, height) / sample > 1200) sample *= 2;
        return sample;
    }
}
