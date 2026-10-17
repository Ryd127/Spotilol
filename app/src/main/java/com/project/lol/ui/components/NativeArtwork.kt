package com.project.lol.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import com.project.lol.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/** One bounded artwork pipeline for mini-player, expanded player and browser cards. */
internal object NativeArtwork {
    private const val MAX_BYTES = 3_000_000
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val slots = Semaphore(4)
    private val lock = Any()
    private val pending = mutableMapOf<String, Deferred<Bitmap?>>()
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }

    suspend fun load(url: String): Bitmap? {
        if (!ArtworkPolicy.isAllowed(url)) return null
        cache.get(url)?.let { return it }
        val task = synchronized(lock) {
            pending[url] ?: if (pending.size >= 64) null else {
                scope.async(start = CoroutineStart.LAZY) {
                    slots.withPermit { cache.get(url) ?: download(url)?.also { cache.put(url, it) } }
                }.also { work ->
                    pending[url] = work
                    work.invokeOnCompletion { synchronized(lock) { if (pending[url] === work) pending.remove(url) } }
                }
            }
        } ?: return null
        task.start()
        // Composition cancellation stops only this waiter, not other users of the same cover.
        return task.await()
    }

    private fun download(url: String): Bitmap? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 3500
                readTimeout = 3500
                instanceFollowRedirects = false
            }
            if (connection.responseCode != 200 || connection.contentLengthLong > MAX_BYTES) {
                Logger.d("nativeArt", "image unavailable: host=${URL(url).host} status=${connection.responseCode}")
                return null
            }
            val bytes = connection.inputStream.use { stream ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > MAX_BYTES) return null
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val sample = ArtworkPolicy.sampleSize(bounds.outWidth, bounds.outHeight)
            if (sample == 0) return null
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
                inSampleSize = sample
            })?.also { Logger.v("nativeArt", "decoded ${it.width}x${it.height} host=${URL(url).host}") }
        } catch (_: Exception) {
            Logger.d("nativeArt", "image request failed: host=${runCatching { URL(url).host }.getOrDefault("invalid")}")
            null
        } finally {
            connection?.disconnect()
        }
    }
}
