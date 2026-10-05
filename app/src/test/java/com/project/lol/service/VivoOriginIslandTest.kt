package com.project.lol.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VivoOriginIslandTest {

    @Test
    fun progressTemplateIsUsedOnlyForMeaningfulProgress() {
        assertTrue(VivoOriginIsland.usesProgressTemplate(25_000L, 100_000L))
        assertFalse(VivoOriginIsland.usesProgressTemplate(0L, 100_000L))
        assertFalse(VivoOriginIsland.usesProgressTemplate(100_000L, 100_000L))
        assertFalse(VivoOriginIsland.usesProgressTemplate(1L, 0L))
    }

    @Test
    fun progressPercentIsClamped() {
        assertEquals(25, VivoOriginIsland.progressPercent(25_000L, 100_000L))
        assertEquals(0, VivoOriginIsland.progressPercent(-1L, 100_000L))
        assertEquals(100, VivoOriginIsland.progressPercent(150_000L, 100_000L))
        assertEquals(0, VivoOriginIsland.progressPercent(5_000L, 0L))
    }

    @Test
    fun wireConstantsStayOnKnownOriginOsValues() {
        assertEquals(0, VivoOriginIsland.OP_SHOW)
        assertEquals(2, VivoOriginIsland.OP_END)
        assertEquals("TRAIN", VivoOriginIsland.SCENE)
        assertEquals(2, VivoOriginIsland.RIGHT_TEMPLATE_PROGRESS)
        assertEquals(4, VivoOriginIsland.RIGHT_TEMPLATE_TEXT_ICON)
    }
}
