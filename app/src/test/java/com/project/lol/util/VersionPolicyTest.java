package com.project.lol.util;

import org.junit.Test;
import static org.junit.Assert.*;

public class VersionPolicyTest {
    @Test public void upstreamBaseDoesNotReplaceNativeBuild() {
        assertFalse(VersionPolicy.isNewer("1.2.4", "1.2.4-native18"));
        assertFalse(VersionPolicy.isNewer("v1.2.4", "1.2.4-native18-vivo"));
    }
    @Test public void realPatchUpdateIsRecognized() {
        assertTrue(VersionPolicy.isNewer("1.2.5", "1.2.4-native18"));
    }
    @Test public void olderVersionIsNotUpdate() {
        assertFalse(VersionPolicy.isNewer("1.2.3", "1.2.4-native18"));
    }
    @Test public void compareNumericallyNotLexically() {
        assertTrue(VersionPolicy.isNewer("1.10.0", "1.9.99"));
    }
    @Test public void buildMetadataAndZeroPaddingAreIgnored() {
        assertFalse(VersionPolicy.isNewer("v1.2.4+build", "1.2.4.0-native18"));
    }
    @Test public void malformedVersionsNeverTriggerNavigation() {
        assertFalse(VersionPolicy.isNewer("latest", "1.2.4"));
        assertFalse(VersionPolicy.isNewer("1.2.5", null));
        assertFalse(VersionPolicy.isNewer("9999999999999999999999999.0", "1.2.4"));
    }
}
