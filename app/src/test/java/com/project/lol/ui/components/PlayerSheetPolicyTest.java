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

    @Test public void artworkReservesControlsInShortAndLargeTextLayouts() {
        assertEquals(363f, PlayerSheetPolicy.artworkSideDp(363, 800, 1), 0f);
        assertEquals(284f, PlayerSheetPolicy.artworkSideDp(363, 700, 1), 0f);
        assertEquals(120f, PlayerSheetPolicy.artworkSideDp(750, 360, 1), 0f);
        assertTrue(PlayerSheetPolicy.artworkSideDp(363, 800, 2) <
                PlayerSheetPolicy.artworkSideDp(363, 800, 1));
        assertEquals(90f, PlayerSheetPolicy.artworkSideDp(90, 360, 1), 0f);
        assertEquals(365f, PlayerSheetPolicy.artworkSideDp(750, 1600, 1), 0f);
        assertEquals(0f, PlayerSheetPolicy.artworkSideDp(Float.NaN, 800, 1), 0f);
        assertEquals(0f, PlayerSheetPolicy.artworkSideDp(360, Float.POSITIVE_INFINITY, 1), 0f);
    }

    @Test public void seekTargetsStayInsideKnownDuration() {
        assertEquals(90000L, PlayerSheetPolicy.seekPosition(180000, .5f));
        assertEquals(0L, PlayerSheetPolicy.seekPosition(180000, -.3f));
        assertEquals(180000L, PlayerSheetPolicy.seekPosition(180000, 1.2f));
        assertEquals(0L, PlayerSheetPolicy.seekPosition(0, .5f));
        assertEquals(0L, PlayerSheetPolicy.seekPosition(-1, .5f));
        assertEquals(0L, PlayerSheetPolicy.seekPosition(180000, Float.NaN));
    }

    @Test public void artworkBoundsHoldAcrossViewportAndFontMatrix() {
        for (float width : new float[]{90, 240, 320, 411, 800}) {
            for (float height : new float[]{320, 640, 800, 1000}) {
                for (float scale : new float[]{.85f, 1f, 1.3f, 2f, 3f}) {
                    float side = PlayerSheetPolicy.artworkSideDp(width, height, scale);
                    assertTrue(side > 0 && side <= width && side <= 365f);
                    assertTrue(side >= Math.min(120f, width));
                }
            }
        }
    }
}
