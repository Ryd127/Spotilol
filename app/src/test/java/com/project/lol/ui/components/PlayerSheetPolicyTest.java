package com.project.lol.ui.components;

import org.junit.Test;
import static org.junit.Assert.*;

public class PlayerSheetPolicyTest {
    @Test public void cancelledGestureAlwaysReturns() {
        assertFalse(PlayerSheetPolicy.shouldDismiss(700, 800, 9000, 1, true));
    }
    @Test public void smallPullReturnsButLongPullDismisses() {
        assertFalse(PlayerSheetPolicy.shouldDismiss(80, 800, 0, 1, false));
        assertTrue(PlayerSheetPolicy.shouldDismiss(150, 800, 0, 1, false));
    }
    @Test public void fastSwipeNeedsActualTravel() {
        assertFalse(PlayerSheetPolicy.shouldDismiss(5, 800, 3000, 1, false));
        assertTrue(PlayerSheetPolicy.shouldDismiss(30, 800, 1500, 1, false));
    }
    @Test public void upwardReleaseReversesDismissal() {
        assertFalse(PlayerSheetPolicy.shouldDismiss(300, 800, -1000, 1, false));
    }
    @Test public void sameDpGestureWorksAcrossDensities() {
        for (float density : new float[]{1, 2, 3}) {
            assertFalse(PlayerSheetPolicy.shouldDismiss(80*density, 800*density, 0, density, false));
            assertTrue(PlayerSheetPolicy.shouldDismiss(150*density, 800*density, 0, density, false));
            assertTrue(PlayerSheetPolicy.shouldDismiss(30*density, 1500*density, 1500*density, density, false));
        }
    }
    @Test public void invalidGeometryAndOffsetsAreBounded() {
        assertFalse(PlayerSheetPolicy.shouldDismiss(100, 0, 9000, 1, false));
        assertFalse(PlayerSheetPolicy.shouldDismiss(100, Float.NaN, 9000, 1, false));
        assertEquals(0f, PlayerSheetPolicy.clampOffset(Float.NaN, 800), 0f);
        assertEquals(0f, PlayerSheetPolicy.clampOffset(-50, 800), 0f);
        assertEquals(800f, PlayerSheetPolicy.clampOffset(1000, 800), 0f);
        assertEquals(.5f, PlayerSheetPolicy.fraction(400, 800), 0f);
    }
    @Test public void compactAndLargeFontMetricsFitTwoLines() {
        assertEquals(56f, PlayerSheetPolicy.miniHeightDp(1), 0f);
        assertEquals(56f, PlayerSheetPolicy.miniHeightDp(1.2f), 0f);
        assertEquals(68f, PlayerSheetPolicy.miniHeightDp(1.3f), 0f);
        assertEquals(80f, PlayerSheetPolicy.miniHeightDp(2), 0f);
        assertEquals(112f, PlayerSheetPolicy.miniHeightDp(3), 0f);
    }
    @Test public void seeksRestartsAndPausedProgressDoNotTween() {
        assertEquals(0, PlayerSheetPolicy.progressAnimationMillis(1000, 60000, true));
        assertEquals(0, PlayerSheetPolicy.progressAnimationMillis(60000, 1000, true));
        assertEquals(0, PlayerSheetPolicy.progressAnimationMillis(1000, 1800, false));
        assertEquals(0, PlayerSheetPolicy.progressAnimationMillis(1000, 1000, true));
    }
    @Test public void ordinaryProgressGetsShortVisualSmoothing() {
        assertEquals(200, PlayerSheetPolicy.progressAnimationMillis(1000, 1850, true));
        assertEquals(200, PlayerSheetPolicy.progressAnimationMillis(1000, 3000, true));
    }
}
