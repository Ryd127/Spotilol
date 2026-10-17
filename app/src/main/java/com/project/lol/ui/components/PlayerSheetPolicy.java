package com.project.lol.ui.components;

/** Pure UI decisions. No playback, network, or Android dependencies. */
public final class PlayerSheetPolicy {
    private PlayerSheetPolicy() {}

    public static float clampOffset(float offset, float height) {
        if (!Float.isFinite(offset) || !Float.isFinite(height) || height <= 0f) return 0f;
        return Math.max(0f, Math.min(offset, height));
    }

    public static float fraction(float offset, float height) {
        if (!Float.isFinite(height) || height <= 0f) return 0f;
        return clampOffset(offset, height) / height;
    }

    public static boolean shouldDismiss(float offset, float height, float velocity,
                                        float density, boolean cancelled) {
        if (cancelled || !Float.isFinite(height) || height <= 0f) return false;
        float dp = Float.isFinite(density) && density > 0f ? density : 1f;
        float speed = Float.isFinite(velocity) ? velocity : 0f;
        float distance = clampOffset(offset, height);
        // Reversing the gesture upward means return, even after a long pull.
        if (speed < -800f * dp) return false;
        float threshold = Math.min(height * .5f, Math.max(72f * dp, Math.min(140f * dp, height * .22f)));
        return distance >= threshold || (distance >= 24f * dp && speed >= 1200f * dp);
    }

    public static float miniHeightDp(float fontScale) {
        float scale = Float.isFinite(fontScale) && fontScale > 0f ? fontScale : 1f;
        return scale <= 1.2f ? 56f : Math.max(68f, (float) Math.ceil(32f * scale + 16f));
    }

    public static float artworkSideDp(float contentWidth, float viewportHeight, float fontScale) {
        if (!Float.isFinite(contentWidth) || contentWidth <= 0f ||
                !Float.isFinite(viewportHeight) || viewportHeight <= 0f) return 0f;
        float scale = Float.isFinite(fontScale) && fontScale > 0f ? fontScale : 1f;
        // Reserve two title lines, artist, slider, labels and full-size touch targets.
        // Extremely short/large-text layouts scroll instead of shrinking controls.
        float chrome = Math.max(60f, 28f * scale) + 70f * scale + 16f * scale +
                Math.max(48f, 16f * scale) + 48f + 68f + 106f;
        float available = Math.max(120f, viewportHeight - chrome);
        return Math.min(365f, Math.min(contentWidth, available));
    }

    public static long seekPosition(long duration, float fraction) {
        if (duration <= 0 || !Float.isFinite(fraction)) return 0L;
        return Math.min(duration, Math.max(0L, (long) (duration * (double) Math.max(0f, Math.min(1f, fraction)))));
    }

    public static int progressAnimationMillis(long previous, long current, boolean playing) {
        // Never animate a seek/restart through the entire track, or animate while paused.
        if (!playing || previous < 0 || current < previous) return 0;
        long delta = current - previous;
        return delta > 0 && delta <= 2000 ? 200 : 0;
    }
}
