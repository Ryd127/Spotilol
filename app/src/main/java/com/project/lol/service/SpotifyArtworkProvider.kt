package com.project.lol.service

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException
import java.security.MessageDigest

/**
 * Minimal read-only artwork provider used only for the Vivo/OriginOS compatibility test.
 *
 * Official Spotify exposes album artwork through
 * content://com.spotify.mobile.android.mediaapi/spotify%3Aimage%3A... URIs. Vivo can therefore
 * resolve the artwork outside the app process instead of relying only on Binder-carried bitmaps.
 *
 * This provider intentionally exposes only generated JPEG files from one dedicated cache
 * directory. It cannot traverse arbitrary paths and supports no writes.
 */
class SpotifyArtworkProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "com.spotify.mobile.android.mediaapi"

        private const val CACHE_DIR = "spotify_media_art"
        private const val MAX_CACHED_ARTWORK = 12
        private const val EXPORTED_ART_MAX_PX = 300
        private val TOKEN_RE = Regex("[A-Za-z0-9_-]{16,128}")

        fun publish(context: Context, bitmap: Bitmap, sourceUrl: String): Uri {
            val token = tokenFor(sourceUrl)
            val dir = File(context.cacheDir, CACHE_DIR)
            if (!dir.exists() && !dir.mkdirs()) {
                throw IllegalStateException("Unable to create artwork cache")
            }

            val target = File(dir, "$token.jpg")
            val temp = File(dir, ".$token.${System.nanoTime()}.tmp")
            val exported = bitmapForMaxSide(bitmap, EXPORTED_ART_MAX_PX)
            try {
                temp.outputStream().buffered().use { out ->
                    if (!exported.compress(Bitmap.CompressFormat.JPEG, 92, out)) {
                        throw IllegalStateException("JPEG compression failed")
                    }
                }
                if (!temp.renameTo(target)) {
                    temp.copyTo(target, overwrite = true)
                    temp.delete()
                }
                target.setLastModified(System.currentTimeMillis())
            } finally {
                if (exported !== bitmap && !exported.isRecycled) exported.recycle()
                if (temp.exists()) temp.delete()
            }

            prune(dir, keep = target)

            val uri = Uri.Builder()
                .scheme("content")
                .authority(AUTHORITY)
                .appendPath("spotify:image:$token")
                .appendQueryParameter("transformation", "NONE")
                .build()

            // Verify the exact URI through ContentResolver before advertising it in MediaSession.
            val firstByte = context.contentResolver.openInputStream(uri)?.use { it.read() } ?: -1
            if (firstByte < 0) throw IllegalStateException("ContentResolver self-check failed")
            return uri
        }

        private fun tokenFor(sourceUrl: String): String {
            val candidate = runCatching { Uri.parse(sourceUrl).lastPathSegment }
                .getOrNull()
                ?.substringBefore('?')
                ?.takeIf { TOKEN_RE.matches(it) }
            if (candidate != null) return candidate

            val digest = MessageDigest.getInstance("SHA-256")
                .digest(sourceUrl.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }.take(40)
        }

        private fun bitmapForMaxSide(source: Bitmap, maxPx: Int): Bitmap {
            val maxSide = maxOf(source.width, source.height)
            if (maxSide <= maxPx) return source
            val scale = maxPx.toFloat() / maxSide.toFloat()
            val width = (source.width * scale).toInt().coerceAtLeast(1)
            val height = (source.height * scale).toInt().coerceAtLeast(1)
            return Bitmap.createScaledBitmap(source, width, height, true)
        }

        private fun prune(dir: File, keep: File) {
            val files = dir.listFiles()
                ?.filter { it.isFile && it.extension.equals("jpg", ignoreCase = true) && it != keep }
                ?.sortedByDescending { it.lastModified() }
                .orEmpty()
            files.drop(MAX_CACHED_ARTWORK - 1).forEach { runCatching { it.delete() } }
        }
    }

    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String? =
        if (resolveFile(uri) != null) "image/jpeg" else null

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("Read-only provider")
        val file = resolveFile(uri) ?: throw FileNotFoundException("Unknown artwork URI")
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val file = resolveFile(uri) ?: return null
        val requested = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val supported = requested.filter {
            it == OpenableColumns.DISPLAY_NAME || it == OpenableColumns.SIZE
        }
        val cursor = MatrixCursor(supported.toTypedArray(), 1)
        val row = cursor.newRow()
        supported.forEach {
            when (it) {
                OpenableColumns.DISPLAY_NAME -> row.add(file.name)
                OpenableColumns.SIZE -> row.add(file.length())
            }
        }
        return cursor
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0

    private fun resolveFile(uri: Uri): File? {
        if (uri.scheme != "content" || uri.authority != AUTHORITY) return null
        val decoded = uri.lastPathSegment ?: return null
        if (!decoded.startsWith("spotify:image:")) return null
        val token = decoded.removePrefix("spotify:image:")
        if (!TOKEN_RE.matches(token)) return null

        val ctx = context ?: return null
        val dir = File(ctx.cacheDir, CACHE_DIR)
        val file = File(dir, "$token.jpg")

        // Token validation above guarantees a single filename, but keep a canonical-path check too.
        val safe = runCatching {
            file.canonicalFile.parentFile == dir.canonicalFile
        }.getOrDefault(false)
        return file.takeIf { safe && it.isFile }
    }
}
