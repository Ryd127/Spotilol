package com.project.lol.ui.components

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.project.lol.ui.theme.SpotifyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Render/click/gesture tests with clearly synthetic data, never a personal Spotify session. */
@RunWith(AndroidJUnit4::class)
class NativeUiRegressionTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val artist = MobileBrowseItem("/artist/ArtistA", "Artist A", "Исполнитель", kind = "artist")
    private val song = MobileBrowseItem("/track/1234567890123456789012", "Real song", "Artist A", kind = "track")
    private val track = NativeMiniTrack(title = "Test track", artist = "Test artist", position = 60000,
        duration = 180000, playing = true)

    private fun screenshot(name: String) {
        rule.waitForIdle()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        val folder = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "ui-regression")
        check(folder.isDirectory || folder.mkdirs())
        File(folder, "$name.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }

    @Test fun artistTracksAndBackNavigationDoNotBecomeSidebarArtists() {
        var browse by mutableStateOf(MobileBrowseState(ready = true, items = listOf(artist), library = listOf(artist)))
        var played = ""
        rule.setContent {
            SpotifyTheme {
                NativeMobileShell(browse, track, onNavigate = { path ->
                    browse = if (path == artist.path) MobileBrowseState(ready = true, path = path,
                        title = "Artist A", items = listOf(song), library = listOf(artist))
                    else MobileBrowseState(ready = true, items = listOf(artist), library = listOf(artist))
                }, onPlay = { uri, _ -> played = uri }, onPrevious = {}, onToggle = {}, onNext = {},
                    onSeek = {}, onFavorite = {}, onShuffle = {}, onRepeat = {}, onOpenSettings = {}, onOpenWeb = {})
            }
        }
        screenshot("home-fixture")
        rule.onNodeWithText("Artist A").performClick()
        rule.onNodeWithText("Популярные").assertIsDisplayed()
        rule.onNodeWithText("Плейлист").assertDoesNotExist()
        screenshot("artist-fixture")
        rule.onNodeWithText("Real song").performClick()
        rule.runOnIdle { assertEquals("spotify:track:1234567890123456789012", played) }
        rule.onNodeWithContentDescription("Назад").performClick()
        rule.onNodeWithText("Real song").assertDoesNotExist()
    }

    @Test fun searchTypingProducesTheEncodedQuery() {
        val navigated = mutableListOf<String>()
        rule.setContent {
            SpotifyTheme {
                NativeMobileShell(MobileBrowseState(ready = true), track, onNavigate = { navigated.add(it) },
                    onPlay = { _, _ -> }, onPrevious = {}, onToggle = {}, onNext = {}, onSeek = {}, onFavorite = {},
                    onShuffle = {}, onRepeat = {}, onOpenSettings = {}, onOpenWeb = {})
            }
        }
        rule.onNodeWithText("Поиск").performClick()
        rule.onNodeWithTag("native-search-input").performTextInput("Дора & test")
        rule.waitUntil(5000) { navigated.lastOrNull() == "/search/%D0%94%D0%BE%D1%80%D0%B0%20%26%20test" }
        screenshot("search-fixture")
    }

    @Test fun expandedPlayerSurvivesStateRestoreAndCancelledDrag() {
        val restoration = StateRestorationTester(rule)
        restoration.setContent {
            SpotifyTheme {
                NativeMobileShell(MobileBrowseState(ready = true, items = listOf(artist)), track,
                    onNavigate = {}, onPlay = { _, _ -> }, onPrevious = {}, onToggle = {}, onNext = {}, onSeek = {},
                    onFavorite = {}, onShuffle = {}, onRepeat = {}, onOpenSettings = {}, onOpenWeb = {})
            }
        }
        rule.onNodeWithTag("native-mini-open").performClick()
        rule.onNodeWithTag("native-seek").assertIsDisplayed()
        screenshot("player-fixture")
        rule.onNodeWithText("СЕЙЧАС ИГРАЕТ").performTouchInput { down(center); moveBy(Offset(0f, 30f)); cancel() }
        rule.onNodeWithTag("native-player-sheet").assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        rule.onNodeWithTag("native-player-sheet").assertIsDisplayed()
        screenshot("player-restored-fixture")
    }

    @Test fun largeTextPlayerKeepsFooterReachable() {
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                SpotifyTheme {
                    NativeMobileShell(MobileBrowseState(ready = true), track, onNavigate = {}, onPlay = { _, _ -> },
                        onPrevious = {}, onToggle = {}, onNext = {}, onSeek = {}, onFavorite = {}, onShuffle = {},
                        onRepeat = {}, onOpenSettings = {}, onOpenWeb = {})
                }
            }
        }
        rule.onNodeWithTag("native-mini-open").performClick()
        rule.onNodeWithContentDescription("Очередь и текст").performScrollTo().assertIsDisplayed()
        screenshot("player-large-text-fixture")
    }
}
