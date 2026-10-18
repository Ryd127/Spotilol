package com.project.lol.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.abs

/** Small recomposition boundary: dragging the slider does not recompose the browse screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NativeSeekControls(track: NativeMiniTrack, onSeek: (Long) -> Unit) {
    val seekIdentity = Pair(track.uri, Triple(track.title, track.artist, track.duration))
    val currentIdentity = rememberUpdatedState(seekIdentity)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    key(seekIdentity) {
        var preview by remember { mutableStateOf<Float?>(null) }
        var requested by remember { mutableStateOf<Float?>(null) }
        LaunchedEffect(requested) {
            if (requested != null) { delay(1600); requested = null }
        }
        LaunchedEffect(track.position) {
            requested?.let {
                if (abs(PlayerSheetPolicy.seekPosition(track.duration, it) - track.position) < 1000) requested = null
            }
        }
        val raw = if (track.duration > 0) (track.position.toFloat() / track.duration).coerceIn(0f, 1f) else 0f
        val value = preview ?: requested ?: raw
        val sliderState = remember { SliderState(value) }
        sliderState.value = value
        Column(Modifier.fillMaxWidth()) {
            Slider(state = sliderState, enabled = track.duration > 0L,
                onValueChange = { preview = it },
                onValueChangeFinished = {
                    if (currentIdentity.value == seekIdentity && track.duration > 0L) {
                        preview?.let { requested = it; onSeek(PlayerSheetPolicy.seekPosition(track.duration, it)) }
                    }
                    preview = null
                },
                thumb = { Box(Modifier.size(12.dp).clip(CircleShape).background(Color.White)) },
                track = { state ->
                    Canvas(Modifier.fillMaxWidth().height(4.dp)) {
                        val left = if (rtl) size.width else 0f
                        val right = if (rtl) 0f else size.width
                        drawLine(Color.White.copy(alpha = .3f), Offset(left, size.height / 2),
                            Offset(right, size.height / 2), size.height, StrokeCap.Round)
                        drawLine(Color.White, Offset(left, size.height / 2),
                            Offset(left + (right - left) * state.value, size.height / 2), size.height, StrokeCap.Round)
                    }
                },
                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White),
                modifier = Modifier.fillMaxWidth().testTag("native-seek"))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                val position = if (preview != null || requested != null)
                    PlayerSheetPolicy.seekPosition(track.duration, value) else track.position
                Text(seekClock(position), color = Color(0xFFB3B3B3), fontSize = 11.sp)
                Text(seekClock(track.duration), color = Color(0xFFB3B3B3), fontSize = 11.sp)
            }
        }
    }
}

private fun seekClock(ms: Long): String {
    val seconds = ms.coerceAtLeast(0L) / 1000
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}
