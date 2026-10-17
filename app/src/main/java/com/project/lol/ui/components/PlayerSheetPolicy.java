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

    public static int progressAnimationMillis(long previous, long current, boolean playing) {
        // Never animate a seek/restart through the entire track, or animate while paused.
        if (!playing || previous < 0 || current < previous) return 0;
        long delta = current - previous;
        return delta > 0 && delta <= 2000 ? 200 : 0;
    }
}
