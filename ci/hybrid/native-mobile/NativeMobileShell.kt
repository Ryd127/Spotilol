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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private val mobileGreen = Color(0xFF1ED760)
private val mobileMuted = Color(0xFFB3B3B3)
private val mobileDark = Color(0xFF101010)

data class MobileBrowseItem(
    val path: String,
    val title: String,
    val subtitle: String = "",
    val image: String = "",
    val kind: String = ""
) {
    companion object {
        fun parse(o: JSONObject) = MobileBrowseItem(
            path = o.optString("path", ""),
            title = o.optString("title", ""),
            subtitle = o.optString("subtitle", ""),
            image = o.optString("image", ""),
            kind = o.optString("kind", "")
        )
    }
}

data class MobileBrowseState(
    val ready: Boolean = false,
    val path: String = "/",
    val title: String = "",
    val items: List<MobileBrowseItem> = emptyList(),
    val library: List<MobileBrowseItem> = emptyList(),
    val loadingLibrary: Boolean = false,
    val error: String = ""
) {
    companion object {
        fun parse(payload: String): MobileBrowseState {
            val o = JSONObject(payload)
            fun entries(k: String): List<MobileBrowseItem> {
                val array = o.optJSONArray(k) ?: return emptyList()
                return (0 until minOf(array.length(), 80)).mapNotNull { i ->
                    array.optJSONObject(i)?.let(MobileBrowseItem::parse)
                }
            }
            return MobileBrowseState(
                ready = o.optBoolean("ready"),
                path = o.optString("path", "/"),
                title = o.optString("title", ""),
                items = entries("items"),
                library = entries("library"),
                loadingLibrary = o.optBoolean("loadingLibrary"),
                error = o.optString("error", "")
            )
        }
    }
}

private object MobileArtwork {
    private val cache = LruCache<String, Bitmap>(50)
    suspend fun load(url: String): Bitmap? = withContext(Dispatchers.IO) {
        if (url.isBlank() || url.length > 800) return@withContext null
        val u = runCatching { URL(url) }.getOrNull() ?: return@withContext null
        val host = u.host.lowercase(java.util.Locale.ROOT)
        if (u.protocol != "https" || !(host == "scdn.co" || host.endsWith(".scdn.co")))
            return@withContext null
        cache.get(url)?.let { return@withContext it }
        var connection: HttpURLConnection? = null
        try {
            connection = (u.openConnection() as HttpURLConnection).apply {
                connectTimeout = 3500
                readTimeout = 3500
                instanceFollowRedirects = false
            }
            if (connection.responseCode != 200 || connection.contentLengthLong > 3_000_000L)
                return@withContext null
            val image = connection.inputStream.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 2 })
            }
            if (image != null) cache.put(url, image)
            image
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }
}

@Composable
private fun MobileArt(url: String, modifier: Modifier) {
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = url) {
        value = MobileArtwork.load(url)
    }
    Box(modifier.clip(RoundedCornerShape(7.dp)).background(Color(0xFF303030)), contentAlignment = Alignment.Center) {
        Crossfade(targetState = bitmap, animationSpec = tween(220), label = "albumCover") { img ->
            if (img == null) Text("♫", color = mobileMuted, fontSize = 24.sp)
            else Image(img.asImageBitmap(), contentDescription = null,
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
}

private fun mobileTime(ms: Long): String {
    val seconds = (ms.coerceAtLeast(0L) / 1000L).toInt()
    return (seconds / 60).toString() + ":" + (seconds % 60).toString().padStart(2, '0')
}

/**
 * Native foreground; the secured WebView remains mounted as the only Spotify player.
 * All transport and browsing commands go through the existing WebView context.
 */
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
    var query by rememberSaveable { mutableStateOf("") }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var returnPath by remember { mutableStateOf("/") }
    val detail = listOf("/playlist/", "/album/", "/artist/", "/collection/", "/show/").any { browse.path.startsWith(it) }
    val rows = if(tab == "Library" && !detail) browse.library else browse.items

    LaunchedEffect(query, tab) {
        if (tab == "Search") {
            delay(480)
            onNavigate(if(query.isBlank()) "/search" else "/search/" + java.net.URLEncoder.encode(query.trim(), "UTF-8").replace("+", "%20"))
        }
    }

    BackHandler(enabled = expanded || detail || tab != "Home") {
        when {
            expanded -> expanded = false
            detail -> onNavigate(returnPath)
            else -> { tab = "Home"; onNavigate("/") }
        }
    }

    Box(Modifier.fillMaxSize().background(mobileDark)) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().height(62.dp).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically) {
                if(detail) Text("‹", color = Color.White, fontSize = 36.sp,
                    modifier = Modifier.clickable { onNavigate(returnPath) }.padding(end = 16.dp))
                Text(
                    when {
                        detail -> browse.title.ifBlank { "Music" }
                        tab == "Home" -> "Good listening"
                        tab == "Search" -> "Search"
                        else -> "Your Library"
                    },
                    modifier = Modifier.weight(1f), fontSize = if(detail) 21.sp else 26.sp,
                    fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text("⚙", color = Color.White, fontSize = 25.sp,
                    modifier = Modifier.clickable(onClick = onOpenSettings).padding(8.dp))
            }
            if(tab == "Search" && !detail) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it.take(120) },
                    singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                    placeholder = { Text("What do you want to listen to?", color = mobileMuted) },
                    shape = RoundedCornerShape(10.dp)
                )
            }
            if(tab == "Home" && !detail) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("All", "Music").forEach { chip ->
                        Box(Modifier.clip(CircleShape).background(if (chip == "All") mobileGreen else Color(0xFF252525))
                            .clickable { onNavigate("/") }.padding(horizontal = 17.dp, vertical = 7.dp)) {
                            Text(chip, fontSize = 13.sp, color = if(chip == "All") Color.Black else Color.White)
                        }
                    }
                }
            }
            if(detail) Text(browse.title, fontSize = 23.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 17.dp, vertical = 10.dp), color = Color.White)
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 25.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if(rows.isEmpty()) {
                    item {
                        Text(
                            when {
                                tab == "Library" && browse.loadingLibrary -> "Loading your library…"
                                browse.error.isNotBlank() -> browse.error
                                else -> "Spotify Web is loading your music…"
                            },
                            modifier = Modifier.padding(22.dp), color = mobileMuted, fontSize = 14.sp
                        )
                    }
                }
                items(rows, key = { it.path + "|" + it.title }) { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp))
                            .clickable {
                                if(row.kind == "track" || row.path.startsWith("/track/")) {
                                    val id = row.path.substringAfterLast("/")
                                    if(id.matches(Regex("[A-Za-z0-9]{10,30}"))) {
                                        val context = when {
                                            browse.path.startsWith("/album/") -> "spotify:album:" + browse.path.substringAfterLast("/")
                                            browse.path.startsWith("/playlist/") -> "spotify:playlist:" + browse.path.substringAfterLast("/")
                                            browse.path.startsWith("/collection/") -> "spotify:collection:tracks"
                                            else -> ""
                                        }
                                        onPlay("spotify:track:" + id, context)
                                    }
                                } else {
                                    returnPath = browse.path
                                    onNavigate(row.path)
                                }
                            }.padding(horizontal = 4.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MobileArt(row.image, Modifier.size(if (detail) 48.dp else 57.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(row.title, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                                color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(row.subtitle.ifBlank { row.kind }, fontSize = 12.sp, color = mobileMuted,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text("⋮", fontSize = 25.sp, color = mobileMuted,
                            modifier = Modifier.padding(horizontal = 9.dp))
                    }
                }
            }
            if(track.title.isNotBlank()) {
                val fraction = if(track.duration > 0L) (track.position.toFloat() / track.duration).coerceIn(0f, 1f) else 0f
                Column(Modifier.padding(horizontal = 8.dp).clip(RoundedCornerShape(11.dp))
                    .background(Color(0xFF35473F))) {
                    Row(Modifier.fillMaxWidth().height(66.dp).padding(start = 7.dp, end = 3.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        MobileArt(track.cover, Modifier.size(50.dp))
                        Column(Modifier.weight(1f).clickable { expanded = true }
                            .padding(start = 10.dp)) {
                            Crossfade(targetState = track.title, animationSpec = tween(170), label = "title") { title ->
                                Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text(track.artist, color = Color.White.copy(alpha = .74f),
                                fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = onToggle, modifier = Modifier.size(44.dp)) {
                            Text(if(track.playing) "❚❚" else "▶", color = Color.White, fontSize = 20.sp)
                        }
                        IconButton(onClick = onNext, modifier = Modifier.size(43.dp)) {
                            Text("⏭", color = Color.White, fontSize = 20.sp)
                        }
                    }
                    LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth().height(2.dp),
                        color = Color.White, trackColor = Color.White.copy(alpha = .18f))
                }
            }
            Row(Modifier.fillMaxWidth().height(66.dp).background(Color(0xFF121212)),
                horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                listOf("Home" to "⌂", "Search" to "⌕", "Library" to "▣", "Web" to "☰").forEach { pair ->
                    val label = pair.first
                    Column(Modifier.weight(1f).clickable {
                        when(label) {
                            "Web" -> onOpenWeb()
                            else -> {
                                tab = label
                                onNavigate(when(label) { "Home" -> "/"; "Library" -> "/collection/tracks"; else -> "/search" })
                            }
                        }
                    }.padding(vertical = 5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(pair.second, color = if(tab == label) Color.White else mobileMuted, fontSize = 26.sp)
                        Text(label, color = if(tab == label) Color.White else mobileMuted, fontSize = 10.sp)
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = slideInVertically(animationSpec = tween(300)) { it } + fadeIn(tween(180)),
            exit = slideOutVertically(animationSpec = tween(250)) { it } + fadeOut(tween(180)),
            modifier = Modifier.fillMaxSize()
        ) {
            var swipe by remember { mutableStateOf(0f) }
            val fraction = if(track.duration > 0L) (track.position.toFloat() / track.duration).coerceIn(0f, 1f) else 0f
            Box(Modifier.fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF426A58), Color(0xFF172B23), mobileDark)))
                .pointerInput(Unit) {
                    detectVerticalDragGestures(onDragEnd = {
                        if(swipe > 100f) expanded = false
                        swipe = 0f
                    }, onVerticalDrag = { change, delta ->
                        swipe += delta; change.consume()
                    })
                }
            ) {
                Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 19.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(Modifier.fillMaxWidth().height(55.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("⌄", color = Color.White, fontSize = 32.sp,
                            modifier = Modifier.clickable { expanded = false }.padding(end = 16.dp))
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("PLAYING FROM SPOTILOL", color = Color.White.copy(alpha = .7f),
                                fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text("Now Playing", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Text("⋮", color = Color.White, fontSize = 27.sp)
                    }
                    Spacer(Modifier.weight(.5f))
                    MobileArt(track.cover, Modifier.fillMaxWidth().height(310.dp))
                    Spacer(Modifier.weight(.6f))
                    Row(Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Crossfade(track.title, animationSpec = tween(190), label = "trackName") { name ->
                                Text(name, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                            }
                            Text(track.artist, color = Color.White.copy(alpha = .74f), fontSize = 15.sp, maxLines = 2)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    var draggingSeek by remember(track.title) { mutableStateOf<Float?>(null) }
                    Slider(
                        value = draggingSeek ?: fraction,
                        onValueChange = { draggingSeek = it },
                        onValueChangeFinished = {
                            draggingSeek?.let { onSeek((it * track.duration).toLong()) }
                            draggingSeek = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(mobileTime(track.position), color = mobileMuted, fontSize = 12.sp)
                        Text(mobileTime(track.duration), color = mobileMuted, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly) {
                        IconButton(onClick = onPrevious, modifier = Modifier.size(65.dp)) {
                            Text("⏮", color = Color.White, fontSize = 30.sp)
                        }
                        Box(Modifier.size(72.dp).clip(CircleShape).background(Color.White)
                            .clickable(onClick = onToggle), contentAlignment = Alignment.Center) {
                            Text(if(track.playing) "❚❚" else "▶", color = Color.Black, fontSize = 29.sp, fontWeight = FontWeight.Bold)
                        }
                        IconButton(onClick = onNext, modifier = Modifier.size(65.dp)) {
                            Text("⏭", color = Color.White, fontSize = 30.sp)
                        }
                    }
                    Spacer(Modifier.weight(.7f))
                }
            }
        }
    }
}
