@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.project.lol.ui.components

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.json.JSONObject
import compose.icons.TablerIcons
import compose.icons.tablericons.Playlist
import compose.icons.tablericons.ArrowLeft
import compose.icons.tablericons.ChevronDown
import compose.icons.tablericons.Download
import compose.icons.tablericons.Repeat
import compose.icons.tablericons.User

private val green = Color(0xFF1ED760)
private val bg = Color(0xFF121212)
private val grey = Color(0xFFB3B3B3)
private val white = Color.White

data class MobileBrowseItem(
    val path: String,
    val title: String,
    val subtitle: String = "",
    val image: String = "",
    val kind: String = "",
    val color: String = ""
) {
    companion object {
        fun parse(o: JSONObject) = MobileBrowseItem(
            path = o.optString("path", ""), title = o.optString("title", ""),
            subtitle = o.optString("subtitle", ""), image = o.optString("image", ""),
            kind = o.optString("kind", ""), color = o.optString("color", "")
        )
    }
}
data class MobileSection(val title: String, val items: List<MobileBrowseItem>)

data class MobileBrowseState(
    val ready: Boolean = false,
    val path: String = "/",
    val title: String = "",
    val cover: String = "",
    val creator: String = "",
    val meta: String = "",
    val items: List<MobileBrowseItem> = emptyList(),
    val library: List<MobileBrowseItem> = emptyList(),
    val sections: List<MobileSection> = emptyList(),
    val loadingLibrary: Boolean = false,
    val loadingPage: Boolean = false,
    val error: String = ""
) {
    companion object {
        fun parse(payload: String): MobileBrowseState {
            val o = JSONObject(payload)
            fun itemsFrom(value: org.json.JSONArray?, limit: Int): List<MobileBrowseItem> {
                if (value == null) return emptyList()
                return (0 until minOf(value.length(), limit)).mapNotNull {
                    value.optJSONObject(it)?.let(MobileBrowseItem::parse)
                }
            }
            val source = o.optJSONArray("sections")
            val shelves = if (source == null) emptyList() else
                (0 until minOf(source.length(), 12)).mapNotNull {
                    source.optJSONObject(it)?.let { group ->
                        val members = itemsFrom(group.optJSONArray("items"), 30)
                        if (members.isEmpty()) null
                        else MobileSection(group.optString("title", ""), members)
                    }
                }
            return MobileBrowseState(
                ready = o.optBoolean("ready", false),
                path = o.optString("path", "/"),
                title = o.optString("title", ""),
                cover = o.optString("cover", ""),
                creator = o.optString("creator", ""),
                meta = o.optString("meta", ""),
                items = itemsFrom(o.optJSONArray("items"), 140),
                library = itemsFrom(o.optJSONArray("library"), 220),
                sections = shelves,
                loadingLibrary = o.optBoolean("loadingLibrary"),
                loadingPage = o.optBoolean("loadingPage", !o.optBoolean("ready", false)),
                error = o.optString("error", "")
            )
        }
    }
}

@Composable
internal fun Cover(
    url: String, modifier: Modifier = Modifier,
    round: Boolean = false, isLiked: Boolean = false
) {
    val bitmap by produceState<Bitmap?>(null, url, isLiked) {
        value = if (isLiked) null else NativeArtwork.load(url)
    }
    val shape = if (round) CircleShape else RoundedCornerShape(5.dp)
    Box(modifier.clip(shape).background(Color(0xFF303030)), contentAlignment = Alignment.Center) {
        if (isLiked) {
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(
                listOf(Color(0xFF4320A7), Color(0xFF806AE3), Color(0xFFA8E4D4))
            )), contentAlignment = Alignment.Center) {
                Text("♥", color = white, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            Crossfade(targetState = bitmap, animationSpec = tween(230), label = "art") { art ->
                if (art != null) Image(art.asImageBitmap(), contentDescription = null,
                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Box(Modifier.fillMaxSize().background(Brush.linearGradient(
                    listOf(Color(0xFF333333), Color(0xFF242424))
                )), contentAlignment = Alignment.Center) {
                    Text("♫", color = grey, fontSize = 23.sp)
                }
            }
        }
    }
}

private fun artworkFor(item: MobileBrowseItem, library: List<MobileBrowseItem>): String {
    if (item.image.isNotBlank()) return item.image
    return library.firstOrNull { it.path == item.path && it.image.isNotBlank() }?.image.orEmpty()
}
@Composable
private fun CoverTone(url: String): Color {
    val art by produceState<Bitmap?>(null, url) { value = NativeArtwork.load(url) }
    return remember(art) {
        if (art == null) Color(0xFF47313D)
        else runCatching {
            val img = art!!
            // Prefer a saturated cover colour over the grey average of a collage.
            val hsv = FloatArray(3)
            var best = floatArrayOf(280f, .42f, .35f)
            var score = -1f
            for (y in 1..4) for (x in 1..4) {
                android.graphics.Color.colorToHSV(
                    img.getPixel(img.width * x / 5, img.height * y / 5), hsv
                )
                val next = hsv[1] * (.4f + hsv[2]) -
                    kotlin.math.abs(hsv[2] - .55f) * .14f
                if (next > score) {
                    score = next
                    best = floatArrayOf(hsv[0], hsv[1].coerceAtLeast(.35f), hsv[2])
                }
            }
            best[1] = best[1].coerceIn(.34f, .76f)
            best[2] = best[2].coerceIn(.24f, .35f)
            Color(android.graphics.Color.HSVToColor(best))
        }.getOrDefault(Color(0xFF47313D))
    }
}
@Composable
private fun ToolIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit,
    color: Color = white,
    size: Int = 25,
    modifier: Modifier = Modifier
) {
    val interactions = remember { MutableInteractionSource() }
    IconButton(onClick = onClick, interactionSource = interactions,
        modifier = modifier.size(48.dp).nativePressScale(interactions)) {
        Icon(icon, contentDescription = title, tint = color, modifier = Modifier.size(size.dp))
    }
}
@Composable
private fun Avatar(onClick: () -> Unit) {
    Box(Modifier.size(32.dp).clip(CircleShape).background(Color(0xFFFF673D))
        .clickable(onClick = onClick),
        contentAlignment = Alignment.Center) {
        Icon(TablerIcons.User, contentDescription = "Профиль", tint = Color(0xFF151515), modifier = Modifier.size(24.dp))
    }
}
@Composable
private fun Pill(label: String, checked: Boolean, onClick: () -> Unit) {
    val interactions = remember { MutableInteractionSource() }
    val fill by animateColorAsState(
        targetValue = if (checked) green else Color(0xFF282828),
        animationSpec = tween(200, easing = NativeChromeMotion.SurfaceEasing), label = "chipFill"
    )
    val ink by animateColorAsState(
        targetValue = if (checked) Color.Black else white,
        animationSpec = tween(200, easing = NativeChromeMotion.SurfaceEasing), label = "chipText"
    )
    Box(Modifier.nativePressScale(interactions).clip(CircleShape).background(fill)
        .clickable(interactionSource = interactions, indication = null, role = Role.Button, onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 11.dp)) {
        Text(label, color = ink, fontSize = 13.sp, maxLines = 1)
    }
}
private fun progress(track: NativeMiniTrack): Float =
    if (track.duration > 0) (track.position.toFloat() / track.duration.toFloat()).coerceIn(0f, 1f) else 0f

@Composable
private fun NativeTransportBar(
    track: NativeMiniTrack, tone: Color, onOpen: () -> Unit,
    actions: NativeLibraryActions, item: MobileBrowseItem, onToggle: () -> Unit
) {
    val height = PlayerSheetPolicy.miniHeightDp(LocalDensity.current.fontScale).dp
    Box(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 8.dp)) {
        Box(Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(8.dp)).background(tone)
            .testTag("native-transport-bar")) {
            Row(Modifier.fillMaxSize().testTag("native-mini-open").clickable(role = Role.Button, onClick = onOpen)
                .padding(start = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Cover(track.cover, Modifier.size(40.dp).clip(RoundedCornerShape(4.dp)))
                Column(Modifier.weight(1f).padding(start = 12.dp, end = 4.dp)) {
                    Crossfade(track.title, animationSpec = tween(170), label = "miniTrackTitle") { title ->
                        Text(title, color = white, fontFamily = FontFamily.SansSerif,
                            fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(track.artist, color = white.copy(alpha = .70f), fontFamily = FontFamily.SansSerif,
                        fontSize = 13.sp, lineHeight = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                NativeSaveButton(actions, item, track.favorite)
                ToolIcon(if(track.playing) NativeIcons.Pause else NativeIcons.Play,
                    "Пауза или воспроизведение", onToggle, white, 26)
            }
            // Track identity creates a fresh visual anchor; a seek never sweeps through the song.
            key(track.uri, track.title, track.artist, track.duration) {
                val previous = remember { longArrayOf(track.position) }
                val millis = remember(track.position, track.playing) {
                    PlayerSheetPolicy.progressAnimationMillis(previous[0], track.position, track.playing)
                }
                SideEffect { previous[0] = track.position }
                val fraction = animateFloatAsState(progress(track), tween(millis, easing = LinearEasing),
                    label = "miniProgress")
                Canvas(Modifier.align(Alignment.BottomCenter).padding(horizontal = 8.dp)
                    .fillMaxWidth().height(2.dp).progressSemantics(progress(track))) {
                    drawRect(white.copy(alpha = .17f))
                    drawRect(white.copy(alpha = .92f), size = Size(size.width * fraction.value.coerceIn(0f, 1f), size.height))
                }
            }
        }
    }
}
private fun clock(ms: Long): String {
    val secs = (ms.coerceAtLeast(0) / 1000).toInt()
    return (secs / 60).toString() + ":" + (secs % 60).toString().padStart(2, '0')
}
private fun playable(item: MobileBrowseItem): Boolean = item.kind == "track" || item.kind == "episode" ||
    item.path.startsWith("/track/") || item.path.startsWith("/episode/")

@Composable
fun NativeMobileShell(
    browse: MobileBrowseState,
    track: NativeMiniTrack,
    onNavigate: (String) -> Unit,
    onPlay: (String, String) -> Unit,
    onPrevious: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onFavorite: () -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenWeb: () -> Unit,
    onRetry: () -> Unit = {},
    onAction: (NativeActionRequest, (NativeActionReply) -> Unit) -> Unit = { _, result -> result(NativeActionReply()) },
    onOpenWebTarget: (String) -> Unit = { onOpenWeb() }
) {
    var tab by rememberSaveable { mutableStateOf("Home") }
    var search by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("Все") }
    var libraryFilter by rememberSaveable { mutableStateOf("") }
    var nowPlaying by rememberSaveable { mutableStateOf(false) }
    var backPaths by rememberSaveable { mutableStateOf(listOf<String>()) }
    var startedContext by rememberSaveable { mutableStateOf("") }
    var expectedPath by rememberSaveable { mutableStateOf(browse.path) }
    var userNavigated by remember { mutableStateOf(false) }
    fun navigateTo(path: String) { userNavigated = true; expectedPath = path; onNavigate(path) }
    fun goBack() {
        val target = backPaths.lastOrNull() ?: "/"
        backPaths = backPaths.dropLast(1)
        navigateTo(target)
    }
    LaunchedEffect(browse.path) { if (!userNavigated) expectedPath = browse.path }
    val detail = (expectedPath.startsWith("/playlist/") || expectedPath.startsWith("/album/") ||
        expectedPath.startsWith("/artist/") || expectedPath.startsWith("/show/") ||
        expectedPath.startsWith("/collection/"))
    val isBrowse = !detail
    val tone = CoverTone(track.cover)
    val actions = rememberNativeLibraryActions(onAction)
    val playingItem = MobileBrowseItem(
        path = if (mediaUri(track.uri).isBlank()) "" else "/" + track.uri.substringAfter(':').replace(':', '/'),
        title = track.title, subtitle = track.artist, image = track.cover,
        kind = if (track.uri.startsWith("spotify:episode:")) "episode" else "track")
    val pageItem = MobileBrowseItem(browse.path, browse.title, browse.creator, browse.cover,
        kind = browse.path.split('/').getOrNull(1).orEmpty())
    val sortPrefs = LocalContext.current.getSharedPreferences("spotilol_prefs", android.content.Context.MODE_PRIVATE)
    var sortEnabled by remember { mutableStateOf(sortPrefs.getBoolean("PlaylistSortEnabled", true)) }
    val sortPrefKey = "NativePlaylistSort:${browse.path}"
    var playlistOrder by remember(browse.path) {
        mutableStateOf(NativePlaylistOrder.parse(sortPrefs.getString(sortPrefKey, null)))
    }
    DisposableEffect(sortPrefs, sortPrefKey) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { prefs, changed ->
            if (changed == "PlaylistSortEnabled") sortEnabled = prefs.getBoolean("PlaylistSortEnabled", true)
            if (changed == sortPrefKey) playlistOrder = NativePlaylistOrder.parse(prefs.getString(sortPrefKey, null))
        }
        sortPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { sortPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val canSort = sortEnabled && (browse.path.startsWith("/playlist/") || browse.path.startsWith("/album/") ||
        browse.path == "/collection/tracks")
    val orderedTracks = remember(browse.items, canSort, playlistOrder) {
        orderNativeTracks(browse.items, if (canSort) playlistOrder else NativePlaylistOrder.ORIGINAL)
    }
    LaunchedEffect(track.uri) { if (track.uri.isNotBlank()) actions.readSaved(playingItem) }
    LaunchedEffect(browse.path) { if (mediaUri(browse.path).isNotBlank()) actions.readSaved(pageItem) }
    fun showMenu(item: MobileBrowseItem) {
        val uri = mediaUri(item.path)
        actions.menu(item, browse.library.any { it.path == item.path } || (uri == track.uri && track.favorite))
    }

    fun clickItem(item: MobileBrowseItem) {
        if (playable(item)) {
            val id = item.path.substringAfterLast("/")
            if (id.matches(Regex("[a-zA-Z0-9]{15,30}"))) {
                val context = when {
                    browse.path.startsWith("/album/") -> "spotify:album:" + browse.path.substringAfterLast("/")
                    browse.path.startsWith("/playlist/") -> "spotify:playlist:" + browse.path.substringAfterLast("/")
                    browse.path.startsWith("/artist/") -> "spotify:artist:" + browse.path.substringAfterLast("/")
                    browse.path == "/collection/tracks" -> "spotify:collection:tracks"
                    else -> ""
                }
                startedContext = browse.path
                onPlay("spotify:" + (if (item.kind == "episode") "episode:" else "track:") + id, context)
            }
        } else {
            backPaths = backPaths + expectedPath
            navigateTo(item.path)
        }
    }

    LaunchedEffect(search, tab) {
        if (tab == "Search") {
            delay(420)
            navigateTo(if (search.isBlank()) "/search"
                else "/search/" + java.net.URLEncoder.encode(search.trim(), "UTF-8").replace("+", "%20"))
        }
    }
    BackHandler(enabled = nowPlaying || detail || tab != "Home") {
        when {
            nowPlaying -> nowPlaying = false
            detail -> goBack()
            else -> { tab = "Home"; navigateTo("/") }
        }
    }

    ProvideTextStyle(LocalTextStyle.current.copy(fontFamily = FontFamily.SansSerif)) {
    Box(Modifier.fillMaxSize().background(bg)) {
        Column(Modifier.fillMaxSize().then(
            if (nowPlaying) Modifier.clearAndSetSemantics { } else Modifier
        )) {
            when {
                detail -> {
                    Row(Modifier.fillMaxWidth().padding(start = 14.dp, top = 7.dp, end = 14.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        ToolIcon(TablerIcons.ArrowLeft, "Назад", { goBack() })
                        Spacer(Modifier.weight(1f))
                        ToolIcon(NativeIcons.More, "Дополнительно", { showMenu(pageItem) })
                    }
                }
                tab == "Home" -> {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Avatar(onOpenSettings)
                        Spacer(Modifier.width(4.dp))
                        listOf("Все","Музыка","Подкасты").forEach { name ->
                            Pill(name, name == filter) { filter = name }
                        }
                    }
                }
                tab == "Search" -> {
                    Row(Modifier.fillMaxWidth().padding(start = 17.dp, top = 12.dp, end = 12.dp, bottom = 20.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Avatar(onOpenSettings)
                        Spacer(Modifier.width(16.dp))
                        Text("Поиск", modifier = Modifier.weight(1f),
                            fontSize = 27.sp, fontWeight = FontWeight.Bold, color = white)
                    }
                    Row(Modifier.padding(horizontal = 17.dp).fillMaxWidth().height(52.dp)
                        .clip(RoundedCornerShape(5.dp)).background(white).padding(horizontal = 13.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(NativeIcons.Search, null, tint = Color.Black, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(10.dp))
                        BasicTextField(value = search, onValueChange = { search = it.take(120) },
                            singleLine = true, textStyle = androidx.compose.ui.text.TextStyle(
                                color = Color(0xFF171717), fontSize = 16.sp, fontWeight = FontWeight.SemiBold
                            ), modifier = Modifier.weight(1f).testTag("native-search-input"),
                            decorationBox = { inner ->
                                Box {
                                    if (search.isBlank()) Text("Что хочешь послушать?",
                                        color = Color(0xFF555555), fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold)
                                    inner()
                                }
                            })
                    }
                    Spacer(Modifier.height(22.dp))
                }
                else -> {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp, end = 10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Avatar(onOpenSettings)
                        Spacer(Modifier.width(14.dp))
                        Text("Моя медиатека", color = white, fontSize = 23.sp,
                            fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        ToolIcon(NativeIcons.Search, "Поиск", { tab = "Search"; navigateTo("/search") })
                        ToolIcon(NativeIcons.Plus, "Создать", { actions.createMenu() })
                    }
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                        .padding(horizontal = 17.dp, vertical = 15.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Плейлисты","Исполнители","Альбомы").forEach { label ->
                            Pill(label, libraryFilter == label) { libraryFilter = if (libraryFilter == label) "" else label }
                        }
                    }
                }
            }

            // A screen transition changes only the native visuals. Playback remains in WebView.
            NativePageFrame(
                pageKey = tab + "|" + expectedPath,
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
                val page = tab + "|" + expectedPath
                val pageTag = Modifier.fillMaxWidth().testTag(page)
                when {
                    browse.path != expectedPath || (!browse.ready && browse.items.isEmpty() && browse.library.isEmpty()) -> {
                        BrowseFeedback(loading = true, error = false, onRetry = onRetry)
                    }
                    detail -> {
                        val artUrl = browse.cover.ifBlank { browse.library.firstOrNull { it.path == browse.path }?.image.orEmpty() }
                        val detailTone = CoverTone(artUrl)
                        LazyColumn(modifier = pageTag, contentPadding = PaddingValues(bottom = 20.dp)) {
                            item {
                                Column(Modifier.fillMaxWidth().background(
                                    Brush.verticalGradient(listOf(detailTone.copy(alpha = .80f), bg))
                                ).padding(horizontal = 17.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Spacer(Modifier.height(8.dp))
                                    Cover(artUrl, if (browse.path.startsWith("/artist/"))
                                        Modifier.fillMaxWidth().aspectRatio(1.5f) else Modifier.size(235.dp),
                                        isLiked = browse.path == "/collection/tracks")
                                    Spacer(Modifier.height(19.dp))
                                    Text(browse.title.ifBlank { if (browse.loadingPage) "Загрузка…" else "" }, color = white,
                                        fontSize = if (browse.path.startsWith("/artist/")) 32.sp else 26.sp,
                                        fontWeight = FontWeight.ExtraBold, modifier = Modifier.fillMaxWidth())
                                    Spacer(Modifier.height(7.dp))
                                    if (browse.creator.isNotBlank()) {
                                        Text(browse.creator, color = white, modifier = Modifier.fillMaxWidth(),
                                            fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                        Spacer(Modifier.height(7.dp))
                                    }
                                    Text(browse.meta.ifBlank {
                                        if (browse.path.startsWith("/album/")) "Альбом" else
                                            if (browse.path.startsWith("/artist/")) "Исполнитель" else "Плейлист"
                                    }, color = grey, modifier = Modifier.fillMaxWidth(), fontSize = 13.sp)
                                    Spacer(Modifier.height(15.dp))
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        if (mediaUri(browse.path).isNotBlank()) NativeSaveButton(actions, pageItem,
                                            fallback = browse.library.any { it.path == browse.path })
                                        if (browse.path.startsWith("/album/") || browse.path.startsWith("/playlist/") || browse.path == "/collection/tracks")
                                            ToolIcon(TablerIcons.Download, "Загрузить через веб-плеер", { onOpenWebTarget(browse.path) }, grey)
                                        ToolIcon(NativeIcons.More, "Меню", { showMenu(pageItem) }, grey)
                                        Spacer(Modifier.weight(1f))
                                        ToolIcon(NativeIcons.Shuffle, "Перемешать", onShuffle, if (track.shuffle) green else grey)
                                        Box(Modifier.size(55.dp).clip(CircleShape).background(green)
                                            .clickable {
                                                if (startedContext == browse.path && track.title.isNotBlank()) onToggle()
                                                else orderedTracks.firstOrNull(::playable)?.let(::clickItem)
                                            }, contentAlignment = Alignment.Center) {
                                            Icon(if(track.playing && startedContext == browse.path) NativeIcons.Pause else NativeIcons.Play,
                                                "Воспроизвести", modifier = Modifier.size(29.dp), tint = Color.Black)
                                        }
                                    }
                                }
                            }
                            if (browse.loadingPage || browse.error.isNotBlank()) {
                                item { BrowseFeedback(browse.loadingPage, browse.error.isNotBlank(), onRetry) }
                            } else if (browse.items.isEmpty()) {
                                item { Text("Пока нет доступных треков", color = grey, modifier = Modifier.padding(20.dp)) }
                            }
                            if (browse.path.startsWith("/artist/") && browse.items.isNotEmpty()) {
                                item { Text("Популярные", color = white, fontSize = 21.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) }
                            }
                            if (canSort && browse.items.isNotEmpty()) {
                                item {
                                    NativePlaylistSortButton(browse.path, playlistOrder) {
                                        playlistOrder = it
                                        sortPrefs.edit().putString(sortPrefKey, it.name).apply()
                                    }
                                }
                            }
                            items(orderedTracks, key = { it.path + it.title }) { item ->
                                TrackRow(item, { clickItem(item) }, { showMenu(item) })
                            }
                        }
                    }
                    tab == "Library" -> {
                        val source = browse.library
                        val rows = when (libraryFilter) {
                            "Плейлисты" -> source.filter { it.kind == "playlist" || it.kind == "collection" }
                            "Исполнители" -> source.filter { it.kind == "artist" }
                            "Альбомы" -> source.filter { it.kind == "album" }
                            else -> source
                        }
                        LazyColumn(modifier = pageTag, contentPadding = PaddingValues(
                            start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp
                        )) {
                            item { Text("↕  Недавние", color = grey, fontSize = 13.sp,
                                modifier = Modifier.padding(vertical = 14.dp)) }
                            if (browse.loadingLibrary && rows.isEmpty()) {
                                item { Text("Загружаем медиатеку…", color = grey,
                                    modifier = Modifier.padding(14.dp)) }
                            }
                            items(rows, key = { it.path }) { item -> LibraryRow(item, { showMenu(item) }) { clickItem(item) } }
                        }
                    }
                    tab == "Search" -> {
                        LazyColumn(modifier = pageTag, contentPadding = PaddingValues(
                            horizontal = 16.dp, vertical = 10.dp
                        )) {
                            if (search.isBlank()) {
                                item { Text("Открой новое для себя",
                                    color = white, fontWeight = FontWeight.Bold, fontSize = 22.sp,
                                    modifier = Modifier.padding(vertical = 18.dp)) }
                                val tags = browse.items.filter { it.kind == "genre" || it.kind == "section" }
                                if (tags.isNotEmpty()) {
                                    item {
                                        LazyRow(
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            contentPadding = PaddingValues(end = 12.dp)
                                        ) {
                                            items(tags.take(3), key = { it.path }) { entry ->
                                                Box(
                                                    Modifier.width(144.dp).height(181.dp)
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .background(Color(0xFF282828))
                                                        .clickable { clickItem(entry) }
                                                ) {
                                                    if (entry.image.isNotBlank()) {
                                                        Cover(entry.image, Modifier.fillMaxSize())
                                                    } else {
                                                        Box(Modifier.fillMaxSize().background(
                                                            Brush.linearGradient(listOf(
                                                                Color(0xFF58406D), Color(0xFF162828)
                                                            ))
                                                        ))
                                                    }
                                                    Box(Modifier.fillMaxSize().background(
                                                        Brush.verticalGradient(listOf(
                                                            Color.Transparent, Color.Black.copy(alpha = .68f)
                                                        ))
                                                    ))
                                                    Text(
                                                        entry.title, color = white,
                                                        fontSize = 16.sp, fontWeight = FontWeight.Bold,
                                                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.align(Alignment.BottomStart)
                                                            .padding(11.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(Modifier.height(23.dp))
                                        Text("Все категории", color = white, fontSize = 21.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(bottom = 15.dp))
                                    }
                                }
                                items(tags.drop(3).chunked(2)) { group ->
                                    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        group.forEachIndexed { index, entry ->
                                            // Prefer Spotify's card colour from the Web DOM.
                                            // These named fallbacks match the screenshot examples.
                                            val shade = runCatching {
                                                Color(android.graphics.Color.parseColor(entry.color))
                                            }.getOrElse {
                                                when (entry.title.lowercase(java.util.Locale.ROOT)) {
                                                    "музыка" -> Color(0xFFE00B85)
                                                    "подкасты" -> Color(0xFF006F58)
                                                    "мероприятия" -> Color(0xFF7800C8)
                                                    "фитнес" -> Color(0xFF777777)
                                                    "поп" -> Color(0xFFBA0777)
                                                    else -> Color(0xFF325989)
                                                }
                                            }
                                            Box(Modifier.weight(1f).height(128.dp)
                                                .clip(RoundedCornerShape(8.dp)).background(shade)
                                                .clickable { clickItem(entry) }) {
                                                Text(entry.title, color = white, fontWeight = FontWeight.Bold,
                                                    fontSize = 16.sp, modifier = Modifier.padding(12.dp).fillMaxWidth(.85f),
                                                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                                                if (entry.image.isNotBlank()) {
                                                    Cover(entry.image,
                                                        Modifier.size(88.dp).align(Alignment.BottomEnd)
                                                            .offset(x = 12.dp, y = 12.dp).rotate(-15f))
                                                } else {
                                                    Box(
                                                        Modifier.size(75.dp).align(Alignment.BottomEnd)
                                                            .offset(x = 20.dp, y = 17.dp).rotate(-15f)
                                                            .clip(RoundedCornerShape(12.dp))
                                                            .background(Brush.linearGradient(
                                                                listOf(white.copy(alpha = .22f),
                                                                    Color.Black.copy(alpha = .13f))
                                                            ))
                                                    )
                                                }
                                            }
                                        }
                                        if (group.size == 1) Spacer(Modifier.weight(1f))
                                    }
                                }
                            } else {
                                if (browse.loadingPage || browse.error.isNotBlank()) {
                                    item { BrowseFeedback(browse.loadingPage, browse.error.isNotBlank(), onRetry) }
                                } else if (browse.items.isEmpty()) {
                                    item { Text("Ничего не найдено", color = grey, modifier = Modifier.padding(16.dp)) }
                                }
                                items(browse.items, key = { it.path + it.title }) { item ->
                                    TrackRow(item, { clickItem(item) }, { showMenu(item) })
                                }
                            }
                        }
                    }
                    else -> {
                        fun matchesFilter(item: MobileBrowseItem) = when (filter) {
                            "Подкасты" -> item.kind in listOf("show", "episode")
                            "Музыка" -> item.kind in listOf("track", "artist", "album", "playlist", "collection")
                            else -> true
                        }
                        val shortcuts = browse.items.filter { it.kind !in listOf("genre", "section", "user") && matchesFilter(it) }.take(8)
                            .ifEmpty { browse.library.filter(::matchesFilter).take(8) }
                        val browseSections = browse.sections.map { it.copy(items = it.items.filter(::matchesFilter)) }.filter { it.items.isNotEmpty() }
                        LazyColumn(modifier = pageTag,
                            contentPadding = PaddingValues(start = 16.dp, bottom = 20.dp)) {
                            if (shortcuts.isNotEmpty()) {
                                items(shortcuts.chunked(2)) { pair ->
                                    Row(Modifier.fillMaxWidth().padding(end = 16.dp, bottom = 8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        pair.forEach { item ->
                                            Row(Modifier.weight(1f).height(57.dp)
                                                .clip(RoundedCornerShape(7.dp))
                                                .background(Color(0xFF292929)).combinedClickable(
                                                    onClick = { clickItem(item) }, onLongClick = { showMenu(item) }),
                                                verticalAlignment = Alignment.CenterVertically) {
                                                Cover(artworkFor(item, browse.library), Modifier.size(57.dp),
                                                    isLiked = item.path == "/collection/tracks")
                                                Text(item.title, color = white, fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold, maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.padding(horizontal = 9.dp))
                                            }
                                        }
                                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                            val fallbackShelf = browse.items.filter(::matchesFilter)
                                .filter { candidate -> shortcuts.none { it.path == candidate.path } }.take(15)
                            if (browseSections.isEmpty() && fallbackShelf.isNotEmpty()) {
                                item { Shelf("Рекомендуем тебе", fallbackShelf,
                                    library = browse.library, onClick = { clickItem(it) }, onMore = { showMenu(it) }) }
                            }
                            items(browseSections) { section ->
                                Shelf(section.title, section.items,
                                    library = browse.library, onClick = { clickItem(it) }, onMore = { showMenu(it) })
                            }
                            if (filter != "Все" && shortcuts.isEmpty() && browseSections.isEmpty()) {
                                item { Text("В этой подборке пока нет такого контента", color = grey, modifier = Modifier.padding(22.dp)) }
                            } else if (browse.items.isEmpty() && browse.library.isEmpty()) {
                                item { Text("Подгружаем рекомендации…",
                                    color = grey, modifier = Modifier.padding(22.dp)) }
                            }
                        }
                    }
                }
            }

            if (track.title.isNotBlank()) {
                NativeTransportBar(track, tone, { nowPlaying = true }, actions, playingItem, onToggle)
            }
            Row(Modifier.fillMaxWidth().height(62.dp).background(Color(0xFF101010)),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically) {
                listOf("Главная","Поиск","Моя медиатека","Создать").forEachIndexed { index, label ->
                    val selected = (index == 0 && tab == "Home") ||
                        (index == 1 && tab == "Search") ||
                        (index == 2 && tab == "Library") ||
                        (index == 3 && actions.mode in listOf("create", "name") && !actions.fromPicker)
                    val icon = when(index) { 0 -> if (selected) NativeIcons.HomeSelected else NativeIcons.Home
                        1 -> if (selected) NativeIcons.SearchSelected else NativeIcons.Search
                        2 -> if (selected) NativeIcons.Library else NativeIcons.LibraryOutline
                        else -> NativeIcons.Plus }
                    Column(Modifier.weight(1f).heightIn(min = 56.dp).testTag("native-tab-$index").selectable(selected, role = Role.Tab, onClick = {
                        when (index) {
                            0 -> { backPaths = emptyList(); tab = "Home"; navigateTo("/") }
                            1 -> { backPaths = emptyList(); tab = "Search"; navigateTo("/search") }
                            2 -> { backPaths = emptyList(); tab = "Library"; navigateTo("/") }
                            else -> actions.createMenu()
                        }
                    }), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(icon, label, tint = if(selected) white else grey,
                            modifier = Modifier.size(24.dp))
                        Spacer(Modifier.height(4.dp))
                        Text(label, color = if(selected) white else grey, fontSize = 10.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        NativePlayerSheet(visible = nowPlaying, onDismiss = { nowPlaying = false }) { closePlayer, dragHandle ->
            BoxWithConstraints(Modifier.fillMaxSize().background(bg).background(
                Brush.verticalGradient(listOf(tone, tone.copy(alpha = .5f), Color(0xFF171018)))
            ).semantics { paneTitle = "Сейчас играет"; isTraversalGroup = true }) {
                val fontScale = LocalDensity.current.fontScale
                val contentWidth = (maxWidth - 48.dp).coerceAtLeast(0.dp)
                val side = PlayerSheetPolicy.artworkSideDp(
                    contentWidth.value, maxHeight.value, fontScale
                ).dp
                val viewportHeight = maxHeight
                // Keep touch targets full-size. Short screens and large fonts get scrolling,
                // not controls pushed below a fixed-height column.
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                    .heightIn(min = viewportHeight).padding(horizontal = 24.dp)) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).then(dragHandle),
                        verticalAlignment = Alignment.CenterVertically) {
                        ToolIcon(TablerIcons.ChevronDown, "Закрыть", closePlayer, white, 28)
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("СЕЙЧАС ИГРАЕТ", color = white.copy(alpha = .8f),
                                fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            Text(browse.title.ifBlank { "Моя музыка" }, color = white,
                                fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        ToolIcon(NativeIcons.More, "Меню", { showMenu(playingItem) }, white)
                    }
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth().then(dragHandle)) {
                        Cover(track.cover, Modifier.size(side).align(Alignment.Center))
                    }
                    Spacer(Modifier.height(24.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Crossfade(track.title, animationSpec = tween(190), label = "nowPlayingTitle") { name ->
                                Text(name, color = white, fontFamily = FontFamily.SansSerif, fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold, maxLines = 2,
                                    overflow = TextOverflow.Ellipsis)
                            }
                            Text(track.artist, color = white.copy(alpha = .72f), fontFamily = FontFamily.SansSerif, fontSize = 15.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        NativeSaveButton(actions, playingItem, track.favorite)
                    }
                    Spacer(Modifier.height(18.dp))
                    NativeSeekControls(track, onSeek)
                    Spacer(Modifier.height(20.dp))
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                        .widthIn(min = contentWidth),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        ToolIcon(NativeIcons.Shuffle, "Перемешать", onShuffle, if(track.shuffle) green else white, 26)
                        ToolIcon(NativeIcons.Previous, "Предыдущий", onPrevious, white, 28)
                        val playInteractions = remember { MutableInteractionSource() }
                        Box(Modifier.size(68.dp).nativePressScale(playInteractions).clip(CircleShape).background(white)
                            .clickable(interactionSource = playInteractions, indication = null,
                                role = Role.Button, onClick = onToggle), contentAlignment = Alignment.Center) {
                            Icon(if(track.playing) NativeIcons.Pause else NativeIcons.Play,
                                "Пауза или воспроизведение", tint = Color.Black,
                                modifier = Modifier.size(34.dp))
                        }
                        ToolIcon(NativeIcons.Next, "Следующий", onNext, white, 28)
                        ToolIcon(TablerIcons.Repeat, "Повтор", onRepeat, if(track.repeat != "false") green else white, 26)
                    }
                    Spacer(Modifier.height(20.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("♫  Подключено к Spotilol", color = green, fontSize = 12.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        ToolIcon(TablerIcons.Playlist, "Очередь и текст", onOpenWeb, white)
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
        NativeActionMessage(actions, Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp))
        NativeActionsSheet(actions, onCreated = { path -> backPaths = backPaths + expectedPath; navigateTo(path) },
            onOpenWeb = onOpenWebTarget)
    }
    }
}

@Composable
private fun BrowseFeedback(loading: Boolean, error: Boolean, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(24.dp), color = green, strokeWidth = 2.dp)
            Spacer(Modifier.height(12.dp))
        }
        Text(if (error) "Не удалось загрузить. Проверь соединение." else "Загружаем музыку…", color = grey, fontSize = 14.sp)
        if (error && !loading) Text("Повторить", color = green, fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable(onClick = onRetry).padding(16.dp))
    }
}

@Composable
private fun TrackRow(item: MobileBrowseItem, onClick: () -> Unit, onMore: () -> Unit) {
    Row(Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onMore)
        .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Cover(item.image, Modifier.size(48.dp), item.kind == "artist")
        Column(Modifier.weight(1f).padding(start = 13.dp)) {
            Text(item.title, color = white, fontSize = 15.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            Text(item.subtitle, color = grey, fontSize = 12.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }
        ToolIcon(NativeIcons.More, "Действия с треком", onMore, grey, 21)
    }
}
@Composable
private fun LibraryRow(item: MobileBrowseItem, onMore: () -> Unit, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 68.dp).combinedClickable(onClick = onClick, onLongClick = onMore),
        verticalAlignment = Alignment.CenterVertically) {
        Cover(item.image, Modifier.size(56.dp), item.kind == "artist",
            isLiked = item.path == "/collection/tracks")
        Column(Modifier.weight(1f).padding(start = 13.dp)) {
            Text(item.title, color = white, fontSize = 16.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(3.dp))
            Text(item.subtitle.ifBlank { "Плейлист" }, color = grey, fontSize = 13.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
@Composable
private fun Shelf(
    title: String, items: List<MobileBrowseItem>,
    library: List<MobileBrowseItem>,
    onClick: (MobileBrowseItem) -> Unit,
    onMore: (MobileBrowseItem) -> Unit
) {
    if (items.isEmpty()) return
    Column(Modifier.padding(top = 31.dp, bottom = 14.dp)) {
        Text(title, color = white, fontSize = 23.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(end = 16.dp, bottom = 15.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(end = 16.dp)) {
            items(items) { item ->
                Column(Modifier.width(158.dp).combinedClickable(onClick = { onClick(item) }, onLongClick = { onMore(item) })) {
                    Cover(artworkFor(item, library), Modifier.size(158.dp),
                        round = item.kind == "artist",
                        isLiked = item.path == "/collection/tracks")
                    Spacer(Modifier.height(9.dp))
                    Text(item.title, color = white, fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold, maxLines = 2,
                        overflow = TextOverflow.Ellipsis)
                    if (item.subtitle.isNotBlank()) Text(item.subtitle, color = grey, fontSize = 12.sp,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
