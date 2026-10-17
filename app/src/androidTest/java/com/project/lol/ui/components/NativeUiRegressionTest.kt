package com.project.lol.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.LruCache
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
import androidx.compose.ui.test.ExperimentalTestApi
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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Render/click/gesture tests with clearly synthetic data, never a personal Spotify session. */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class NativeUiRegressionTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val artistArt = "https://i.scdn.co/image/fixture-artist"
    private val albumArt = "https://i.scdn.co/image/fixture-album"
    private val artist = MobileBrowseItem("/artist/ArtistA", "Artist A", "Исполнитель", image = artistArt, kind = "artist")
    private val song = MobileBrowseItem("/track/1234567890123456789012", "Real song", "Artist A", image = albumArt, kind = "track")
    private val track = NativeMiniTrack(title = "Test track", artist = "Test artist", cover = albumArt, position = 60000,
        duration = 180000, playing = true)

    @Before fun generatedArtworkFixture() {
        // Deliberately labelled synthetic art; no network, copyrighted album asset or account data.
        val owner = Class.forName("com.project.lol.ui.components.NativeArtwork")
        val field = owner.getDeclaredField("cache").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val cache = field.get(null) as LruCache<String, Bitmap>
        listOf(artistArt to 0xFF285CAF.toInt(), albumArt to 0xFFAC2F69.toInt()).forEach { (url, color) ->
            val bitmap = Bitmap.createBitmap(640, 640, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(0f, 0f, 640f, 640f, color, 0xFF181C35.toInt(), Shader.TileMode.CLAMP)
            }
            canvas.drawRect(0f, 0f, 640f, 640f, paint)
            paint.shader = null; paint.color = android.graphics.Color.WHITE; paint.textSize = 64f
            canvas.drawText("FIXTURE", 60f, 340f, paint)
            cache.put(url, bitmap)
        }
    }

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
