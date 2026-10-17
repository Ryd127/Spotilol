package com.project.lol.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Original code for conventional transport symbols, not extracted Spotify assets. */
internal object NativeIcons {
    val Play = ImageVector.Builder("Play", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black)) { moveTo(7f, 3f); lineTo(22f, 12f); lineTo(7f, 21f); close() }
    }.build()
    val Pause = ImageVector.Builder("Pause", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(6f, 4f); lineTo(10f, 4f); lineTo(10f, 20f); lineTo(6f, 20f); close()
            moveTo(14f, 4f); lineTo(18f, 4f); lineTo(18f, 20f); lineTo(14f, 20f); close()
        }
    }.build()
    val Next = ImageVector.Builder("Next", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(4f, 4f); lineTo(17f, 12f); lineTo(4f, 20f); close()
            moveTo(18f, 4f); lineTo(21f, 4f); lineTo(21f, 20f); lineTo(18f, 20f); close()
        }
    }.build()
    val Previous = ImageVector.Builder("Previous", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(20f, 4f); lineTo(7f, 12f); lineTo(20f, 20f); close()
            moveTo(3f, 4f); lineTo(6f, 4f); lineTo(6f, 20f); lineTo(3f, 20f); close()
        }
    }.build()
    val Library = ImageVector.Builder("Library", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(3f, 3f); lineTo(5f, 3f); lineTo(5f, 21f); lineTo(3f, 21f); close()
            moveTo(9f, 3f); lineTo(11f, 3f); lineTo(11f, 21f); lineTo(9f, 21f); close()
            moveTo(14f, 4f); lineTo(16f, 3f); lineTo(22f, 20f); lineTo(20f, 21f); close()
        }
    }.build()
}
