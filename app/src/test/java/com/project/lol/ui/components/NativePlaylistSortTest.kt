package com.project.lol.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class NativePlaylistSortTest {
    private val items = listOf(
        MobileBrowseItem("/track/z", "Alpha", "Zebra"),
        MobileBrowseItem("/track/a", "Zulu", "Alpha"),
        MobileBrowseItem("/track/b", "Beta", "Alpha"),
        MobileBrowseItem("/track/m", "Missing", "   "),
        MobileBrowseItem("/track/duet", "Duet", "Beta, Zebra")
    )
    private fun paths(order: NativePlaylistOrder) = orderNativeTracks(items, order).map { it.path.substringAfterLast('/') }

    @Test fun artistIsSeparateFromTitleAndTiesAreStable() {
        assertEquals(listOf("a", "b", "duet", "z", "m"), paths(NativePlaylistOrder.ARTIST_ASC))
        assertEquals(listOf("z", "duet", "a", "b", "m"), paths(NativePlaylistOrder.ARTIST_DESC))
        assertEquals(listOf("z", "b", "duet", "m", "a"), paths(NativePlaylistOrder.TITLE_ASC))
        assertEquals(listOf("z", "a", "b", "m", "duet"), items.map { it.path.substringAfterLast('/') })
    }

    @Test fun originalAndUnknownPreferenceDoNotReorderOrCopy() {
        assertSame(items, orderNativeTracks(items, NativePlaylistOrder.ORIGINAL))
        assertEquals(NativePlaylistOrder.ORIGINAL, NativePlaylistOrder.parse("unknown"))
        assertEquals(NativePlaylistOrder.ORIGINAL, NativePlaylistOrder.parse(null))
        assertEquals(NativePlaylistOrder.ARTIST_DESC, NativePlaylistOrder.parse("ARTIST_DESC"))
    }
}
