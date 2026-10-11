package com.project.lol.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** Display state only. The existing Spotify Web/MediaSession engine retains playback ownership. */
data class NativeMiniTrack(
    val title: String = "",
    val artist: String = "",
    val cover: String = "",
    val playing: Boolean = false,
    val position: Long = 0L,
    val duration: Long = 0L
)

private object MiniArtwork {
    private val cache = LruCache<String, Bitmap>(20)

    suspend fun get(url: String): Bitmap? = withContext(Dispatchers.IO) {
        if (url.isEmpty() || url.length > 800) return@withContext null
        val uri = runCatching { URL(url) }.getOrNull() ?: return@withContext null
        val host = uri.host.lowercase(java.util.Locale.ROOT)
        if (uri.protocol != "https" || !(host == "scdn.co" || host.endsWith(".scdn.co")))
            return@withContext null
        cache.get(url)?.let { return@withContext it }
        var connection: HttpURLConnection? = null
        try {
            connection = (uri.openConnection() as HttpURLConnection).apply {
                connectTimeout = 3000
                readTimeout = 3000
                instanceFollowRedirects = false
            }
            if (connection.responseCode != 200) return@withContext null
            if (connection.contentLengthLong > 2_500_000L) return@withContext null
            val options = BitmapFactory.Options().apply { inSampleSize = 4 }
            val bitmap = connection.inputStream.use { BitmapFactory.decodeStream(it, null, options) }
            if (bitmap != null) cache.put(url, bitmap)
            bitmap
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }
}

@Composable
fun NativeMiniPlayer(
    track: NativeMiniTrack,
    onPrevious: () -> Unit,
    onTogglePlayback: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = track.cover) {
        value = MiniArtwork.get(track.cover)
    }
    val image = remember(bitmap) { bitmap?.asImageBitmap() }
    val fraction = if (track.duration > 0L)
        (track.position.toFloat() / track.duration.toFloat()).coerceIn(0f, 1f)
    else 0f
    val animatedFraction by animateFloatAsState(
        targetValue = fraction, animationSpec = tween(350), label = "mini-track-progress"
    )
    val panel = Color(0xFF35473F)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(panel)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(70.dp).padding(start = 8.dp, end = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF29352F)),
                contentAlignment = Alignment.Center
            ) {
                Crossfade(targetState = image, animationSpec = tween(220), label = "mini-art") { art ->
                    if (art != null) {
                        Image(
                            bitmap = art, contentDescription = "Album artwork",
                            contentScale = ContentScale.Crop, modifier = Modifier.size(52.dp)
                        )
                    } else {
                        Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                            Text("♫", color = Color.White.copy(alpha = 0.72f), fontSize = 27.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            Crossfade(
                targetState = track.title to track.artist,
                animationSpec = tween(durationMillis = 190),
                label = "mini-track-details",
                modifier = Modifier.weight(1f)
            ) { details ->
                Column(verticalArrangement = Arrangement.Center) {
                    Text(
                        text = details.first, color = Color.White, fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold, maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = details.second, color = Color.White.copy(alpha = 0.74f),
                        fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(
                onClick = onPrevious,
                modifier = Modifier.size(35.dp).semantics { contentDescription = "Previous track" }
            ) {
                Text("⏮", color = Color.White, fontSize = 19.sp)
            }
            IconButton(
                onClick = onTogglePlayback,
                modifier = Modifier.size(41.dp).semantics { contentDescription = "Play or pause" }
            ) {
                Crossfade(targetState = track.playing, animationSpec = tween(140), label = "mini-play") { playing ->
                    Text(if (playing) "❚❚" else "▶", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
            IconButton(
                onClick = onNext,
                modifier = Modifier.size(35.dp).semantics { contentDescription = "Next track" }
            ) {
                Text("⏭", color = Color.White, fontSize = 19.sp)
            }
        }
        LinearProgressIndicator(
            progress = { animatedFraction },
            modifier = Modifier.fillMaxWidth().height(2.dp),
            color = Color.White.copy(alpha = 0.9f),
            trackColor = Color.White.copy(alpha = 0.18f),
        )
    }
}
