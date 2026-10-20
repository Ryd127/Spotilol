package com.project.lol.bridge

import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test

class NativeRequestPolicyTest {
    @Test fun oneShotBodyPreservesPayloadAndReadRequestsStayBodyless() {
        val body = NativeRequestPolicy.body("POST", "{\"name\":\"One\"}", "application/json".toMediaType(), true)!!
        val buffer = Buffer()
        body.writeTo(buffer)
        assertEquals("{\"name\":\"One\"}", buffer.readUtf8())
        assertTrue(body.isOneShot())
        assertEquals(14L, body.contentLength())
        assertTrue(NativeRequestPolicy.body("DELETE", null, null, true)!!.isOneShot())
        assertNull(NativeRequestPolicy.body("GET", null, null, true))
        assertNull(NativeRequestPolicy.body("DELETE", null, null, false))
    }

    @Test fun playlistPostDoesNotRepeatAfter503RetryAfterZero() =
        checkHttpFollowUp("POST", true, 503, 1)

    @Test fun unsaveDeleteDoesNotRepeatAfter503RetryAfterZero() =
        checkHttpFollowUp("DELETE", true, 503, 1)

    @Test fun legacyTransportKeepsItsExistingFollowUpPolicy() =
        checkHttpFollowUp("POST", false, 201, 2)

    private fun checkHttpFollowUp(method: String, oneShot: Boolean, expectedCode: Int, expectedCount: Int) {
        // Loopback server only: no account, Spotify host or real authorization.
        val count = AtomicInteger()
        val failure = AtomicReference<Throwable?>()
        val server = ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"))
        val worker = thread(isDaemon = true, name = "one-shot-http-fixture") {
            try {
                repeat(2) {
                    server.accept().use { socket ->
                        socket.soTimeout = 5000
                        val reader = socket.getInputStream().bufferedReader(Charsets.US_ASCII)
                        require(!reader.readLine().isNullOrEmpty())
                        var length = 0
                        while (true) {
                            val line = reader.readLine() ?: error("Incomplete fixture request")
                            if (line.isEmpty()) break
                            if (line.startsWith("Content-Length:", ignoreCase = true)) length = line.substringAfter(':').trim().toInt()
                        }
                        repeat(length) { require(reader.read() != -1) }
                        val first = count.incrementAndGet() == 1
                        val reply = if (first) "503 Service Unavailable\r\nRetry-After: 0" else "201 Created"
                        socket.getOutputStream().apply {
                            write("HTTP/1.1 $reply\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray(Charsets.US_ASCII))
                            flush()
                        }
                    }
                }
            } catch (e: SocketException) {
                if (!server.isClosed) failure.set(e)
            } catch (e: Throwable) { failure.set(e) }
        }
        val base = OkHttpClient.Builder().followRedirects(false).callTimeout(5, TimeUnit.SECONDS).build()
        val client = if (oneShot) NativeRequestPolicy.oneShotClient(base) else base
        try {
            val body = NativeRequestPolicy.body(method, null, null, oneShot)
            val request = Request.Builder().url("http://127.0.0.1:${server.localPort}/")
                .method(method, body).build()
            client.newCall(request).execute().use { assertEquals(expectedCode, it.code) }
            assertEquals(expectedCount, count.get())
        } finally {
            server.close()
            worker.join(2000)
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdownNow()
        }
        failure.get()?.let { throw AssertionError("Loopback fixture failed", it) }
    }
}
