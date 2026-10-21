package com.project.lol.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
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
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
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
import org.junit.Assert.assertTrue
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
    private val artist = MobileBrowseItem("/artist/5234567890123456789012", "Artist A", "Исполнитель", image = artistArt, kind = "artist")
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

    private fun screenshot(name: String, tag: String? = null) {
        rule.waitForIdle()
        val bitmap = (if (tag == null) rule.onRoot() else rule.onNodeWithTag(tag)).captureToImage().asAndroidBitmap()
        val folder = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "ui-regression")
        check(folder.isDirectory || folder.mkdirs())
        File(folder, "$name.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        // Gradle removes test apps after execution. Retain synthetic QA images outside app data.
        // This runs only in androidTest on an isolated emulator, never in the release application.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/SpotilolUiRegression")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = checkNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
            checkNotNull(resolver.openOutputStream(uri)).use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
            values.clear(); values.put(MediaStore.Images.Media.IS_PENDING, 0)
            check(resolver.update(uri, values, null, null) == 1)
        }
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
        var browse by mutableStateOf(MobileBrowseState(ready = true))
        var played = ""
        rule.setContent {
            SpotifyTheme {
                NativeMobileShell(browse, track, onNavigate = {
                    navigated.add(it)
                    browse = MobileBrowseState(ready = true, path = it, items = if (it.startsWith("/search/")) listOf(song) else emptyList())
                },
                    onPlay = { uri, _ -> played = uri }, onPrevious = {}, onToggle = {}, onNext = {}, onSeek = {}, onFavorite = {},
                    onShuffle = {}, onRepeat = {}, onOpenSettings = {}, onOpenWeb = {})
            }
        }
        rule.onNodeWithText("Поиск").performClick()
        rule.onNodeWithTag("native-search-input").performTextInput("Дора & test")
        rule.waitUntil(5000) { navigated.lastOrNull() == "/search/%D0%94%D0%BE%D1%80%D0%B0%20%26%20test" }
        rule.onNodeWithText(song.title).assertIsDisplayed()
        screenshot("search-fixture")
        rule.onNodeWithText(song.title).performClick()
        rule.runOnIdle { assertEquals(mediaUri(song.path), played) }
    }

    @Test fun createTabUsesNativeFormAndCreatesOnlyOnceAfterAnExplicitName() {
        val requests = mutableListOf<NativeActionRequest>()
        var finish: ((NativeActionReply) -> Unit)? = null
        var created = ""
        var web = 0
        rule.setContent { SpotifyTheme {
            NativeMobileShell(MobileBrowseState(ready = true), track, onNavigate = { created = it },
                onPlay = { _, _ -> }, onPrevious = {}, onToggle = {}, onNext = {}, onSeek = {}, onFavorite = {},
                onShuffle = {}, onRepeat = {}, onOpenSettings = {}, onOpenWeb = { web++ },
                onAction = { request, result -> requests.add(request); finish = result })
        } }
        rule.onNodeWithTag("native-tab-3").performClick()
        rule.onNodeWithText("Совместный плейлист").assertIsDisplayed()
        screenshot("create-menu-fixture", "native-actions-sheet")
        rule.onNodeWithText("Плейлист").performClick()
        rule.onNodeWithTag("native-playlist-name").performTextInput("My test playlist")
        screenshot("create-name-fixture", "native-actions-sheet")
        rule.onNodeWithText("Создать плейлист").performClick()
        rule.runOnIdle {
            assertEquals(1, requests.size)
            assertEquals("createPlaylist", requests.single().kind)
            assertEquals("My test playlist", requests.single().name)
            assertEquals(false, requests.single().collaborative)
            assertEquals(0, web)
            finish!!(NativeActionReply(state = "success", uri = "spotify:playlist:1234567890123456789012"))
        }
        rule.waitUntil { created == "/playlist/1234567890123456789012" }
        rule.runOnIdle { assertEquals(1, requests.size) }
    }

    @Test fun savedIndicatorRequiresConfirmationAndKeepsItsOriginalTrackTarget() {
        lateinit var actions: NativeLibraryActions
        val requests = mutableListOf<NativeActionRequest>()
        val callbacks = mutableListOf<(NativeActionReply) -> Unit>()
        var current by mutableStateOf(song)
        rule.setContent { SpotifyTheme {
            actions = rememberNativeLibraryActions { request, callback -> requests.add(request); callbacks.add(callback) }
            NativeSaveButton(actions, current)
            NativeActionsSheet(actions, {}, {})
        } }
        rule.onNodeWithTag("save:" + song.path).performClick().assertIsNotEnabled()
        rule.runOnIdle {
            actions.favorite(song) // A second queued tap must not send another request.
            assertEquals(1, requests.size)
            assertEquals(mediaUri(song.path), requests.single().uri)
            assertEquals(null, actions.saved[mediaUri(song.path)])
            current = song.copy(path = "/track/2234567890123456789012", title = "Another song")
            callbacks.single()(NativeActionReply(state = "success", uri = mediaUri(song.path), saved = true))
            assertEquals(true, actions.saved[mediaUri(song.path)])
            assertEquals(false, actions.isSaved(current))
            current = song
        }
        rule.onNodeWithContentDescription("Сохранено: действия").performClick()
        rule.onNodeWithText("Удалить из медиатеки").assertIsDisplayed()
        rule.onNodeWithText("Добавить в плейлист").assertIsDisplayed()
        screenshot("saved-actions-fixture", "native-actions-sheet")
        rule.onNodeWithText("Удалить из медиатеки").performClick()
        rule.runOnIdle {
            assertEquals("setSaved", requests.last().kind)
            assertEquals(false, requests.last().saved)
            callbacks.last()(NativeActionReply(state = "success", uri = mediaUri(song.path), saved = false))
            assertEquals(false, actions.isSaved(song))
        }
    }

    @Test fun playlistPickerAddsSelectedSongAndUnknownCreationDoesNotRetry() {
        lateinit var actions: NativeLibraryActions
        val requests = mutableListOf<NativeActionRequest>()
        val callbacks = mutableListOf<(NativeActionReply) -> Unit>()
        val destination = MobileBrowseItem("/playlist/1234567890123456789012", "My destination", "My playlist", albumArt, "playlist")
        rule.setContent { SpotifyTheme {
            actions = rememberNativeLibraryActions { request, callback -> requests.add(request); callbacks.add(callback) }
            NativeActionsSheet(actions, {}, {})
        } }
        rule.runOnIdle { actions.choosePlaylist(song) }
        rule.runOnIdle {
            assertEquals("playlists", requests.single().kind)
            callbacks.single()(NativeActionReply(state = "success", playlists = listOf(destination)))
        }
        screenshot("playlist-picker-fixture", "native-actions-sheet")
        rule.onNodeWithTag("native-playlist-filter").performTextInput("destination")
        rule.onNodeWithText("My destination").performClick()
        rule.runOnIdle {
            assertEquals("addToPlaylist", requests.last().kind)
            assertEquals(mediaUri(song.path), requests.last().uri)
            assertEquals(mediaUri(destination.path), requests.last().target)
            callbacks.last()(NativeActionReply(state = "success", uri = mediaUri(song.path)))
            assertEquals("", actions.mode)
            actions.createForm(collaborative = true)
            actions.name = "Uncertain playlist"
            actions.create { error("Must not navigate on an unconfirmed creation") }
            callbacks.last()(NativeActionReply(state = "unknown", code = "timeout"))
            actions.create { error("Must not retry") }
            assertEquals(1, requests.count { it.kind == "createPlaylist" })
            assertEquals(true, requests.last().collaborative)
        }
        rule.onNodeWithText("Создать плейлист").assertIsNotEnabled()
        rule.onNodeWithTag("native-action-error").assertIsDisplayed()
    }

    @Test fun nativeHomeFiltersAndAlbumSaveDoNotChangeUnrelatedTargets() {
        val album = MobileBrowseItem("/album/3234567890123456789012", "Music album", kind = "album")
        val podcast = MobileBrowseItem("/show/4234567890123456789012", "Podcast series", kind = "show")
        var browse by mutableStateOf(MobileBrowseState(ready = true, items = listOf(album, podcast)))
        val requests = mutableListOf<NativeActionRequest>()
        rule.setContent { SpotifyTheme {
            NativeMobileShell(browse, track, onNavigate = {
                browse = MobileBrowseState(ready = true, path = it, title = album.title)
            }, onPlay = { _, _ -> }, onPrevious = {}, onToggle = {}, onNext = {}, onSeek = {}, onFavorite = {},
                onShuffle = {}, onRepeat = {}, onOpenSettings = {}, onOpenWeb = {},
                onAction = { request, result -> requests.add(request); result(NativeActionReply(state = "error", code = "permission")) })
        } }
        rule.onNodeWithTag("native-tab-0").assertIsSelected()
        rule.onNodeWithText("Подкасты").performClick()
        rule.onNodeWithText(album.title).assertDoesNotExist()
        rule.onNodeWithText(podcast.title).assertIsDisplayed()
        rule.onNodeWithText("Музыка").performClick()
        rule.onNodeWithText(podcast.title).assertDoesNotExist()
        rule.onNodeWithText(album.title).performClick()
        rule.onNodeWithTag("save:" + album.path).performClick()
        rule.runOnIdle {
            assertEquals(mediaUri(album.path), requests.last().uri)
            assertEquals("setSaved", requests.last().kind)
        }
        rule.onNodeWithTag("native-action-message").assertIsDisplayed()
        rule.onNodeWithTag("save:" + album.path).assertIsEnabled()
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

    @Test fun seekTapCommitsOneBoundedRequestAndTrackChangeDoesNotRepeatIt() {
        val requests = mutableListOf<Long>()
        var current by mutableStateOf(track)
        rule.setContent { SpotifyTheme { NativeSeekControls(current) { requests.add(it) } } }
        rule.onNodeWithTag("native-seek").performTouchInput {
            down(Offset(center.x * 1.5f, center.y)); up()
        }
        rule.runOnIdle {
            assertEquals(1, requests.size)
            assertTrue(requests.single() in 110000L..160000L)
            // Same display text and duration, different recording: URI must reset the seek preview.
            current = track.copy(uri = "spotify:track:2234567890123456789012", position = 0L)
        }
        rule.waitForIdle()
        rule.onNodeWithText("0:00").assertIsDisplayed()
        rule.runOnIdle { assertEquals(1, requests.size) }
    }

    @Test fun nativeArtistSortSelectsRestoresAndKeepsOriginalData() {
        val restoration = StateRestorationTester(rule)
        var selected by mutableStateOf(NativePlaylistOrder.ORIGINAL)
        val tracks = listOf(song.copy(subtitle = "Zebra"), song.copy(path = "/track/2234567890123456789012", subtitle = "Alpha"))
        restoration.setContent {
            SpotifyTheme {
                NativePlaylistSortButton("/playlist/fixture", selected) { selected = it }
            }
        }
        rule.onNodeWithTag("native-sort-open").performClick()
        rule.onNodeWithTag("native-sort-ARTIST_ASC").performScrollTo().performClick()
        rule.runOnIdle {
            assertEquals(NativePlaylistOrder.ARTIST_ASC, selected)
            assertEquals("Alpha", orderNativeTracks(tracks, selected).first().subtitle)
            assertEquals("Zebra", tracks.first().subtitle)
        }
        rule.onNodeWithTag("native-sort-open").performClick()
        rule.onNodeWithTag("native-sort-ARTIST_ASC").assertIsSelected()
        screenshot("artist-sort-fixture", "native-sort-sheet")
        restoration.emulateSavedInstanceStateRestore()
        rule.onNodeWithTag("native-sort-ARTIST_ASC").performScrollTo().assertIsSelected()
        rule.onNodeWithTag("native-sort-ORIGINAL").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(NativePlaylistOrder.ORIGINAL, selected) }
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
