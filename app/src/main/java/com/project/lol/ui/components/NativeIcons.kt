package com.project.lol.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Original code for conventional transport symbols, not extracted Spotify assets. */
internal object NativeIcons {
    private fun outline(name: String, width: Float = 1.8f, draw: PathBuilder.() -> Unit) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = null, stroke = SolidColor(Color.White), strokeLineWidth = width,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = draw)
        }.build()

    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        val k = r * .55228475f
        moveTo(cx + r, cy); curveTo(cx + r, cy + k, cx + k, cy + r, cx, cy + r)
        curveTo(cx - k, cy + r, cx - r, cy + k, cx - r, cy)
        curveTo(cx - r, cy - k, cx - k, cy - r, cx, cy - r)
        curveTo(cx + k, cy - r, cx + r, cy - k, cx + r, cy); close()
    }
    val Home = outline("Home") {
        moveTo(3f, 10f); lineTo(12f, 3f); lineTo(21f, 10f); lineTo(21f, 21f)
        lineTo(15f, 21f); lineTo(15f, 14f); lineTo(9f, 14f); lineTo(9f, 21f); lineTo(3f, 21f); close()
    }
    val HomeSelected = ImageVector.Builder("Home selected", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.White), pathFillType = PathFillType.EvenOdd) {
            moveTo(2f, 10f); lineTo(12f, 2f); lineTo(22f, 10f); lineTo(22f, 22f); lineTo(2f, 22f); close()
            moveTo(9f, 14f); lineTo(15f, 14f); lineTo(15f, 22f); lineTo(9f, 22f); close()
        }
    }.build()
    val Search = outline("Search") { circle(10.5f, 10.5f, 7.5f); moveTo(16f, 16f); lineTo(22f, 22f) }
    val SearchSelected = outline("Search selected", 2.8f) { circle(10.5f, 10.5f, 7.5f); moveTo(16f, 16f); lineTo(22f, 22f) }
    val Plus = outline("Create") { moveTo(12f, 3f); lineTo(12f, 21f); moveTo(3f, 12f); lineTo(21f, 12f) }
    val PlusCircle = outline("Save") {
        circle(12f, 12f, 9.5f); moveTo(12f, 7f); lineTo(12f, 17f); moveTo(7f, 12f); lineTo(17f, 12f)
    }
    val CheckCircle = ImageVector.Builder("Saved", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.White), pathFillType = PathFillType.EvenOdd) {
            circle(12f, 12f, 10f)
            moveTo(6f, 12f); lineTo(8f, 10f); lineTo(11f, 13f); lineTo(16f, 8f)
            lineTo(18f, 10f); lineTo(11f, 17f); close()
        }
    }.build()
    val Shuffle = outline("Shuffle") {
        moveTo(3f, 5f); lineTo(5f, 5f); curveTo(10f, 5f, 12f, 19f, 18f, 19f); lineTo(21f, 19f)
        moveTo(18f, 16f); lineTo(21f, 19f); lineTo(18f, 22f)
        moveTo(3f, 19f); lineTo(5f, 19f); curveTo(7f, 19f, 8f, 17f, 10f, 14f)
        moveTo(14f, 9f); curveTo(15f, 6f, 17f, 5f, 19f, 5f); lineTo(21f, 5f)
        moveTo(18f, 2f); lineTo(21f, 5f); lineTo(18f, 8f)
    }
    val PlaylistAdd = outline("Playlist") {
        moveTo(3f, 5f); lineTo(15f, 5f); moveTo(3f, 10f); lineTo(15f, 10f)
        moveTo(3f, 15f); lineTo(10f, 15f); moveTo(18f, 13f); lineTo(18f, 21f)
        moveTo(14f, 17f); lineTo(22f, 17f)
    }
    val Close = outline("Close") { moveTo(5f, 5f); lineTo(19f, 19f); moveTo(19f, 5f); lineTo(5f, 19f) }
    val More = ImageVector.Builder("More", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.White)) { circle(12f, 4f, 1.6f); circle(12f, 12f, 1.6f); circle(12f, 20f, 1.6f) }
    }.build()
    val LibraryOutline = outline("Library") {
        moveTo(3f, 3f); lineTo(3f, 21f); moveTo(9f, 3f); lineTo(9f, 21f)
        moveTo(15f, 3f); lineTo(21f, 20f)
    }
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
