package com.project.lol.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import compose.icons.TablerIcons
import compose.icons.tablericons.Home
import compose.icons.tablericons.Search
import compose.icons.tablericons.Playlist
import compose.icons.tablericons.Plus
import compose.icons.tablericons.PlayerPlay
import compose.icons.tablericons.PlayerPause
import compose.icons.tablericons.PlayerSkipBack
import compose.icons.tablericons.PlayerSkipForward
import compose.icons.tablericons.Heart
import compose.icons.tablericons.ArrowLeft
import compose.icons.tablericons.ChevronDown
import compose.icons.tablericons.DotsVertical
import compose.icons.tablericons.Download
import compose.icons.tablericons.ArrowsShuffle
import compose.icons.tablericons.Repeat
import compose.icons.tablericons.Camera
import java.net.HttpURLConnection
import java.net.URL

private val green = Color(0xFF1ED760)
private val bg = Color(0xFF121212)
private val grey = Color(0xFFB3B3B3)
private val white = Color.White

data class MobileBrowseItem(
    val path: String,
    val title: String,
    val subtitle: String = "",
    val image: String = "",
    val kind: String = ""
) {
    companion object {
        fun parse(o: JSONObject) = MobileBrowseItem(
            path = o.optString("path", ""), title = o.optString("title", ""),
            subtitle = o.optString("subtitle", ""), image = o.optString("image", ""),
            kind = o.optString("kind", "")
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
                error = o.optString("error", "")
            )
        }
    }
}

private object ArtCache {
    private val cache = LruCache<String, Bitmap>(48)
    suspend fun load(url: String): Bitmap? = withContext(Dispatchers.IO) {
        if (!url.startsWith("https://") || url.length > 850) return@withContext null
        val uri = runCatching { URL(url) }.getOrNull() ?: return@withContext null
        val host = uri.host.lowercase(java.util.Locale.ROOT)
        if (!(host == "scdn.co" || host.endsWith(".scdn.co") ||
                host == "spotifycdn.com" || host.endsWith(".spotifycdn.com")))
            return@withContext null
        cache.get(url)?.let { return@withContext it }
        var conn: HttpURLConnection? = null
        try {
            conn = (uri.openConnection() as HttpURLConnection).apply {
                connectTimeout = 3500
                readTimeout = 3500
                instanceFollowRedirects = false
            }
            if (conn.responseCode != 200 || conn.contentLengthLong > 3_000_000L) return@withContext null
            val opt = BitmapFactory.Options().apply { inSampleSize = 2 }
            val bitmap = conn.inputStream.use { BitmapFactory.decodeStream(it, null, opt) }
            if (bitmap != null) cache.put(url, bitmap)
            bitmap
        } catch (_: Exception) { null }
        finally { conn?.disconnect() }
    }
}
@Composable
private fun Cover(url: String, modifier: Modifier = Modifier, round: Boolean = false) {
    val bitmap by produceState<Bitmap?>(null, url) { value = ArtCache.load(url) }
    val shape = if (round) CircleShape else RoundedCornerShape(5.dp)
    Box(modifier.clip(shape).background(Color(0xFF303030)), contentAlignment = Alignment.Center) {
        Crossfade(targetState = bitmap, animationSpec = tween(230), label = "art") { art ->
            if (art != null) Image(art.asImageBitmap(), contentDescription = null,
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Text("♫", color = grey, fontSize = 25.sp)
        }
    }
}
@Composable
private fun CoverTone(url: String): Color {
    val art by produceState<Bitmap?>(null, url) { value = ArtCache.load(url) }
    return remember(art) {
        if (art == null) Color(0xFF47313D)
        else runCatching {
            val w = art!!.width; val h = art!!.height
            var r = 0L; var g = 0L; var b = 0L; var count = 0
            for (y in 1..4) for (x in 1..4) {
                val pix = art!!.getPixel(w * x / 5, h * y / 5)
                r += android.graphics.Color.red(pix); g += android.graphics.Color.green(pix)
                b += android.graphics.Color.blue(pix); count++
            }
            Color((r / count).toInt(), (g / count).toInt(), (b / count).toInt())
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
    IconButton(onClick = onClick, modifier = modifier.size(45.dp)) {
        Icon(icon, contentDescription = title, tint = color, modifier = Modifier.size(size.dp))
    }
}
@Composable
private fun Avatar(onClick: () -> Unit) {
    Box(Modifier.size(39.dp).clip(CircleShape).background(Color(0xFFFF673D))
        .clickable(onClick = onClick),
        contentAlignment = Alignment.Center) {
        Text("R", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Color(0xFF151515))
    }
}
@Composable
private fun Pill(label: String, checked: Boolean, onClick: () -> Unit) {
    val fill by animateColorAsState(
        targetValue = if (checked) green else Color(0xFF282828),
        animationSpec = tween(160), label = "chipFill"
    )
    val ink by animateColorAsState(
        targetValue = if (checked) Color.Black else white,
        animationSpec = tween(160), label = "chipText"
    )
    Box(Modifier.clip(CircleShape).background(fill)
        .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 11.dp)) {
        Text(label, color = ink, fontSize = 13.sp, maxLines = 1)
    }
}
private fun progress(track: NativeMiniTrack): Float =
    if (track.duration > 0) (track.position.toFloat() / track.duration.toFloat()).coerceIn(0f, 1f) else 0f
private fun clock(ms: Long): String {
    val secs = (ms.coerceAtLeast(0) / 1000).toInt()
    return (secs / 60).toString() + ":" + (secs % 60).toString().padStart(2, '0')
}
private fun playable(item: MobileBrowseItem): Boolean = item.kind == "track" || item.path.startsWith("/track/")

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
    onOpenSettings: () -> Unit,
    onOpenWeb: () -> Unit
) {
    var tab by rememberSaveable { mutableStateOf("Home") }
    var search by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("Все") }
    var libraryFilter by rememberSaveable { mutableStateOf("") }
    var nowPlaying by rememberSaveable { mutableStateOf(false) }
    var previousPath by remember { mutableStateOf("/") }
    val detail = (browse.path.startsWith("/playlist/") || browse.path.startsWith("/album/") ||
        browse.path.startsWith("/artist/") || browse.path.startsWith("/show/") ||
        browse.path.startsWith("/collection/"))
    val isBrowse = !detail
    val tone = CoverTone(track.cover)

    fun clickItem(item: MobileBrowseItem) {
        if (playable(item)) {
            val id = item.path.substringAfterLast("/")
            if (id.matches(Regex("[a-zA-Z0-9]{15,30}"))) {
                val context = when {
                    browse.path.startsWith("/album/") -> "spotify:album:" + browse.path.substringAfterLast("/")
                    browse.path.startsWith("/playlist/") -> "spotify:playlist:" + browse.path.substringAfterLast("/")
                    browse.path == "/collection/tracks" -> "spotify:collection:tracks"
                    else -> ""
                }
                onPlay("spotify:track:" + id, context)
            }
        } else {
            previousPath = browse.path
            onNavigate(item.path)
        }
    }

    LaunchedEffect(search, tab) {
        if (tab == "Search") {
            delay(420)
            onNavigate(if (search.isBlank()) "/search"
                else "/search/" + java.net.URLEncoder.encode(search.trim(), "UTF-8").replace("+", "%20"))
        }
    }
    BackHandler(enabled = nowPlaying || detail || tab != "Home") {
        when {
            nowPlaying -> nowPlaying = false
            detail -> onNavigate(previousPath)
            else -> { tab = "Home"; onNavigate("/") }
        }
    }

    Box(Modifier.fillMaxSize().background(bg)) {
        Column(Modifier.fillMaxSize()) {
            when {
                detail -> {
                    Row(Modifier.fillMaxWidth().padding(start = 14.dp, top = 7.dp, end = 14.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        ToolIcon(TablerIcons.ArrowLeft, "Назад", { onNavigate(previousPath) })
                        Spacer(Modifier.weight(1f))
                        ToolIcon(TablerIcons.DotsVertical, "Дополнительно", onOpenWeb)
                    }
                }
                tab == "Home" -> {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Avatar(onOpenSettings)
                        Spacer(Modifier.width(4.dp))
                        listOf("Все","Клипы","Музыка","Подкасты").forEach { name ->
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
                        ToolIcon(TablerIcons.Camera, "Камера и дополнительные функции", onOpenWeb)
                    }
                    Row(Modifier.padding(horizontal = 17.dp).fillMaxWidth().height(52.dp)
                        .clip(RoundedCornerShape(5.dp)).background(white).padding(horizontal = 13.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(TablerIcons.Search, null, tint = Color.Black, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(10.dp))
                        BasicTextField(value = search, onValueChange = { search = it.take(120) },
                            singleLine = true, textStyle = androidx.compose.ui.text.TextStyle(
                                color = Color(0xFF171717), fontSize = 16.sp, fontWeight = FontWeight.SemiBold
                            ), modifier = Modifier.weight(1f),
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
                        Text("Моя медиатека", color = white, fontSize = 26.sp,
                            fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        ToolIcon(TablerIcons.Search, "Поиск", { tab = "Search"; onNavigate("/search") })
                        ToolIcon(TablerIcons.Plus, "Создать", onOpenWeb)
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
            Crossfade(
                targetState = tab + "|" + browse.path,
                animationSpec = tween(200), label = "mobilePage",
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) { page ->
                val pageTag = Modifier.fillMaxWidth().testTag(page)
                when {
                    detail -> {
                        val artUrl = browse.cover.ifBlank { browse.library.firstOrNull { it.path == browse.path }?.image.orEmpty() }
                        val detailTone = CoverTone(artUrl)
                        LazyColumn(modifier = pageTag, contentPadding = PaddingValues(bottom = 20.dp)) {
                            item {
                                Column(Modifier.fillMaxWidth().background(
                                    Brush.verticalGradient(listOf(detailTone.copy(alpha = .80f), bg))
                                ).padding(horizontal = 17.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Spacer(Modifier.height(8.dp))
                                    Cover(artUrl, Modifier.size(235.dp))
                                    Spacer(Modifier.height(19.dp))
                                    Text(browse.title.ifBlank { "Плейлист" }, color = white, fontSize = 26.sp,
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
                                        ToolIcon(TablerIcons.Heart, "Добавить в медиатеку", onOpenWeb, grey)
                                        ToolIcon(TablerIcons.Download, "Скачать", onOpenWeb, grey)
                                        ToolIcon(TablerIcons.DotsVertical, "Меню", onOpenWeb, grey)
                                        Spacer(Modifier.weight(1f))
                                        ToolIcon(TablerIcons.ArrowsShuffle, "Перемешать", onOpenWeb, grey)
                                        Box(Modifier.size(55.dp).clip(CircleShape).background(green)
                                            .clickable { onToggle() }, contentAlignment = Alignment.Center) {
                                            Icon(if(track.playing) TablerIcons.PlayerPause else TablerIcons.PlayerPlay,
                                                "Воспроизвести", modifier = Modifier.size(29.dp), tint = Color.Black)
                                        }
                                    }
                                }
                            }
                            items(browse.items, key = { it.path + it.title }) { item ->
                                TrackRow(item, { clickItem(item) }, onOpenWeb)
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
                            items(rows, key = { it.path }) { item -> LibraryRow(item) { clickItem(item) } }
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
                                items(tags.chunked(2)) { group ->
                                    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        group.forEachIndexed { index, entry ->
                                            val shade = listOf(
                                                Color(0xFFCF087C),Color(0xFF037967),
                                                Color(0xFF7225D5),Color(0xFF777777),
                                                Color(0xFF35789B),Color(0xFFAE6429)
                                            )[(entry.title.hashCode() and Int.MAX_VALUE)%6]
                                            Box(Modifier.weight(1f).height(128.dp)
                                                .clip(RoundedCornerShape(8.dp)).background(shade)
                                                .clickable { clickItem(entry) }) {
                                                Text(entry.title, color = white, fontWeight = FontWeight.Bold,
                                                    fontSize = 16.sp, modifier = Modifier.padding(12.dp).fillMaxWidth(.85f),
                                                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                                                Cover(entry.image,
                                                    Modifier.size(78.dp).align(Alignment.BottomEnd))
                                            }
                                        }
                                        if (group.size == 1) Spacer(Modifier.weight(1f))
                                    }
                                }
                            } else {
                                items(browse.items, key = { it.path + it.title }) { item ->
                                    TrackRow(item, { clickItem(item) }, onOpenWeb)
                                }
                            }
                        }
                    }
                    else -> {
                        val shortcuts = browse.items.filter { !playable(it) }.take(8)
                            .ifEmpty { browse.library.take(8) }
                        val browseSections = browse.sections
                        LazyColumn(modifier = pageTag,
                            contentPadding = PaddingValues(start = 16.dp, bottom = 20.dp)) {
                            if (filter == "Все" || filter == "Музыка") {
                                items(shortcuts.chunked(2)) { pair ->
                                    Row(Modifier.fillMaxWidth().padding(end = 16.dp, bottom = 8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        pair.forEach { item ->
                                            Row(Modifier.weight(1f).height(57.dp)
                                                .clip(RoundedCornerShape(7.dp))
                                                .background(Color(0xFF292929)).clickable { clickItem(item) },
                                                verticalAlignment = Alignment.CenterVertically) {
                                                Cover(item.image, Modifier.size(57.dp))
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
                            if (browseSections.isEmpty() && shortcuts.isNotEmpty()) {
                                item { Shelf("Рекомендуем тебе", browse.items.drop(8).take(15),
                                    onClick = { clickItem(it) }) }
                            }
                            items(browseSections) { section ->
                                Shelf(section.title, section.items, onClick = { clickItem(it) })
                            }
                            if (browse.items.isEmpty() && browse.library.isEmpty()) {
                                item { Text("Подгружаем рекомендации…",
                                    color = grey, modifier = Modifier.padding(22.dp)) }
                            }
                        }
                    }
                }
            }

            if (track.title.isNotBlank()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(tone.copy(alpha = .70f))
                    .height(66.dp).padding(start = 7.dp, end = 5.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Cover(track.cover, Modifier.size(51.dp))
                    Column(Modifier.weight(1f).clickable { nowPlaying = true }
                        .padding(start = 10.dp)) {
                        Crossfade(track.title, animationSpec = tween(170), label = "miniTrackTitle") { title ->
                            Text(title, color = white, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(track.artist, color = white.copy(alpha = .70f), fontSize = 11.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    ToolIcon(TablerIcons.Heart, "Нравится", onOpenWeb, white, 23)
                    ToolIcon(if (track.playing) TablerIcons.PlayerPause else TablerIcons.PlayerPlay,
                        "Пауза или воспроизведение", onToggle, white, 26)
                    ToolIcon(TablerIcons.PlayerSkipForward, "Следующий трек", onNext, white, 26)
                }
                LinearProgressIndicator(progress = { progress(track) },
                    modifier = Modifier.padding(horizontal = 12.dp).fillMaxWidth().height(2.dp),
                    color = white.copy(alpha = .92f), trackColor = white.copy(alpha = .17f))
            }
            Row(Modifier.fillMaxWidth().height(62.dp).background(Color(0xFF101010)),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically) {
                listOf("Главная","Поиск","Моя медиатека","Создать").forEachIndexed { index, label ->
                    val icon = when(index) { 0 -> TablerIcons.Home; 1 -> TablerIcons.Search;
                        2 -> TablerIcons.Playlist; else -> TablerIcons.Plus }
                    val selected = (index == 0 && tab == "Home") ||
                        (index == 1 && tab == "Search") ||
                        (index == 2 && tab == "Library")
                    Column(Modifier.weight(1f).clickable {
                        when (index) {
                            0 -> { tab = "Home"; onNavigate("/") }
                            1 -> { tab = "Search"; onNavigate("/search") }
                            2 -> { tab = "Library"; onNavigate("/") }
                            else -> onOpenWeb()
                        }
                    }, horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(icon, label, tint = if(selected) white else grey,
                            modifier = Modifier.size(26.dp))
                        Spacer(Modifier.height(4.dp))
                        Text(label, color = if(selected) white else grey, fontSize = 10.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        AnimatedVisibility(visible = nowPlaying,
            enter = slideInVertically(tween(260)) { it } + fadeIn(tween(160)),
            exit = slideOutVertically(tween(220)) { it } + fadeOut(tween(180)),
            modifier = Modifier.fillMaxSize()) {
            var swipe by remember { mutableStateOf(0f) }
            Box(Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(tone, tone.copy(alpha = .5f), Color(0xFF171018)))
            ).pointerInput(Unit) {
                detectVerticalDragGestures(onDragEnd = {
                    if (swipe > 95f) nowPlaying = false
                    swipe = 0f
                }, onVerticalDrag = { change, dy -> swipe += dy; change.consume() })
            }) {
                Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                    Row(Modifier.fillMaxWidth().height(60.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        ToolIcon(TablerIcons.ChevronDown, "Закрыть", { nowPlaying = false }, white, 28)
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("СЕЙЧАС ИГРАЕТ", color = white.copy(alpha = .8f),
                                fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            Text(browse.title.ifBlank { "Моя музыка" }, color = white,
                                fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        ToolIcon(TablerIcons.DotsVertical, "Меню", onOpenWeb, white)
                    }
                    Spacer(Modifier.weight(.3f))
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val side = maxWidth.coerceAtMost(365.dp)
                        Cover(track.cover, Modifier.size(side).align(Alignment.Center))
                    }
                    Spacer(Modifier.weight(.6f))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Crossfade(track.title, animationSpec = tween(190), label = "nowPlayingTitle") { name ->
                                Text(name, color = white, fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold, maxLines = 2,
                                    overflow = TextOverflow.Ellipsis)
                            }
                            Text(track.artist, color = white.copy(alpha = .72f), fontSize = 15.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        ToolIcon(TablerIcons.Heart, "Добавить в медиатеку", onOpenWeb, white, 27)
                    }
                    Spacer(Modifier.height(18.dp))
                    var seeking by remember(track.title) { mutableStateOf<Float?>(null) }
                    Slider(value = seeking ?: progress(track),
                        onValueChange = { seeking = it },
                        onValueChangeFinished = {
                            seeking?.let { onSeek((track.duration.toFloat()*it).toLong()) }
                            seeking = null
                        },
                        colors = SliderDefaults.colors(thumbColor = white,
                            activeTrackColor = white, inactiveTrackColor = white.copy(alpha = .4f)),
                        modifier = Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(clock(track.position), color = grey, fontSize = 11.sp)
                        Text(clock(track.duration), color = grey, fontSize = 11.sp)
                    }
                    Spacer(Modifier.height(20.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        ToolIcon(TablerIcons.ArrowsShuffle, "Перемешать", onOpenWeb, white, 26)
                        ToolIcon(TablerIcons.PlayerSkipBack, "Предыдущий", onPrevious, white, 32)
                        Box(Modifier.size(68.dp).clip(CircleShape).background(white)
                            .clickable(onClick = onToggle), contentAlignment = Alignment.Center) {
                            Icon(if(track.playing) TablerIcons.PlayerPause else TablerIcons.PlayerPlay,
                                "Пауза или воспроизведение", tint = Color.Black,
                                modifier = Modifier.size(34.dp))
                        }
                        ToolIcon(TablerIcons.PlayerSkipForward, "Следующий", onNext, white, 32)
                        ToolIcon(TablerIcons.Repeat, "Повтор", onOpenWeb, white, 26)
                    }
                    Spacer(Modifier.weight(.45f))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("♫  Подключено к Spotilol", color = green, fontSize = 12.sp)
                        ToolIcon(TablerIcons.Playlist, "Очередь и текст", onOpenWeb, white)
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun TrackRow(item: MobileBrowseItem, onClick: () -> Unit, onMore: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Cover(item.image, Modifier.size(54.dp), item.kind == "artist")
        Column(Modifier.weight(1f).padding(start = 13.dp)) {
            Text(item.title, color = white, fontSize = 15.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            Text(item.subtitle, color = grey, fontSize = 12.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }
        ToolIcon(TablerIcons.DotsVertical, "Действия с треком", onMore, grey, 21)
    }
}
@Composable
private fun LibraryRow(item: MobileBrowseItem, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(81.dp).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically) {
        Cover(item.image, Modifier.size(63.dp), item.kind == "artist")
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
private fun Shelf(title: String, items: List<MobileBrowseItem>, onClick: (MobileBrowseItem) -> Unit) {
    if (items.isEmpty()) return
    Column(Modifier.padding(top = 31.dp, bottom = 14.dp)) {
        Text(title, color = white, fontSize = 23.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(end = 16.dp, bottom = 15.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(end = 16.dp)) {
            items(items) { item ->
                Column(Modifier.width(158.dp).clickable { onClick(item) }) {
                    Cover(item.image, Modifier.size(158.dp), item.kind == "artist")
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
