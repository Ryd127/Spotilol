package com.project.lol.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VivoOriginIslandTest {

    @Test
    fun progressPayloadUsesOriginOsProgressTemplate() {
        val extras = VivoOriginIsland.buildShowExtras(
            title = "Track",
            artist = "Artist",
            appLabel = "Spotilol",
            positionMs = 25_000L,
            durationMs = 100_000L,
            accentColor = 0xFF112233.toInt(),
        )

        assertEquals(VivoOriginIsland.OP_SHOW, extras.getInt("notification.superx.operation"))
        assertTrue(extras.getBoolean("notification.superx.showNotify"))
        assertEquals(VivoOriginIsland.SCENE, extras.getString("notification.superx.scene"))
        assertEquals(2, extras.getInt("notification.superx.template"))

        val infos = extras.getBundle("notification.superx.infos")!!
        assertEquals(25, infos.getInt("notification.superx.infos.progress"))

        val island = extras.getBundle("notification.superx.island")!!
        assertEquals(1, island.getInt("island.superx.leftTemplate"))
        assertEquals(
            VivoOriginIsland.RIGHT_TEMPLATE_PROGRESS,
            island.getInt("island.superx.rightTemplate"),
        )
        assertEquals(
            25,
            island.getBundle("island.superx.rightInfo")!!
                .getInt("island.superx.rightInfo.progressValue"),
        )
    }

    @Test
    fun zeroProgressFallsBackToMediaTextTemplate() {
        val extras = VivoOriginIsland.buildShowExtras(
            title = "Track",
            artist = "Artist",
            appLabel = "Spotilol",
            positionMs = 0L,
            durationMs = 100_000L,
            accentColor = 0xFF112233.toInt(),
        )

        assertEquals(1, extras.getInt("notification.superx.template"))
        val island = extras.getBundle("notification.superx.island")!!
        assertEquals(
            VivoOriginIsland.RIGHT_TEMPLATE_TEXT_ICON,
            island.getInt("island.superx.rightTemplate"),
        )
        assertEquals(
            "Artist",
            island.getBundle("island.superx.rightInfo")!!
                .getString("island.superx.rightInfo.content"),
        )
    }

    @Test
    fun endPayloadCarriesOnlyUnmountOperation() {
        val extras = VivoOriginIsland.buildEndExtras()
        assertEquals(VivoOriginIsland.OP_END, extras.getInt("notification.superx.operation"))
        assertFalse(extras.containsKey("notification.superx.scene"))
        assertEquals(1, extras.size())
    }
}
