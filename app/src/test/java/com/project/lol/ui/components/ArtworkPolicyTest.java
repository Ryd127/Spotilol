package com.project.lol.ui.components;

import org.junit.Test;
import static org.junit.Assert.*;

public class ArtworkPolicyTest {
    @Test public void acceptsOnlyOwnedHttpsArtworkHosts() {
        assertTrue(ArtworkPolicy.isAllowed("https://i.scdn.co/image/album"));
        assertTrue(ArtworkPolicy.isAllowed("https://misc.scdn.co/liked-songs/liked-songs-640.png"));
        assertFalse(ArtworkPolicy.isAllowed("https://scdn.co.evil.invalid/image"));
        assertFalse(ArtworkPolicy.isAllowed("http://i.scdn.co/image/a"));
        assertFalse(ArtworkPolicy.isAllowed("https://user@i.scdn.co/image/a"));
        assertFalse(ArtworkPolicy.isAllowed("https://i.scdn.co:444/image/a"));
        assertFalse(ArtworkPolicy.isAllowed("file:///data/local/image"));
    }
    @Test public void playbackGlyphsAreNotAlbumCovers() {
        assertFalse(ArtworkPolicy.isAllowed("https://open.spotifycdn.com/cdn/images/equalizer-animated.gif"));
        assertFalse(ArtworkPolicy.isAllowed("https://i.scdn.co/placeholder.png"));
    }
    @Test public void preservesSmallAndNormalCoversInsteadOfBlurringThem() {
        assertEquals(1, ArtworkPolicy.sampleSize(64, 64));
        assertEquals(1, ArtworkPolicy.sampleSize(640, 640));
        assertEquals(1, ArtworkPolicy.sampleSize(1024, 1024));
        assertEquals(2, ArtworkPolicy.sampleSize(2400, 2400));
    }
    @Test public void malformedAndExcessiveImagesAreRejected() {
        assertEquals(0, ArtworkPolicy.sampleSize(-1, 640));
        assertEquals(0, ArtworkPolicy.sampleSize(10000, 10000));
        assertEquals(0, ArtworkPolicy.sampleSize(Integer.MAX_VALUE, Integer.MAX_VALUE));
    }
}
