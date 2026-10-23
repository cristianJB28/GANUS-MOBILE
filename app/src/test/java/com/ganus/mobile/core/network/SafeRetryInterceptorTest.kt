package com.ganus.mobile.core.network

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class SafeRetryInterceptorTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `retries a GET after a transient server response`() {
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))
        val client = clientWithRetries()

        client.newCall(
            Request.Builder().url(server.url("/posts")).get().build(),
        ).execute().use { response ->
            assertEquals(200, response.code)
        }

        assertEquals(2, server.requestCount)
    }

    @Test
    fun `handles a malformed Retry-After header with the bounded fallback`() {
        server.enqueue(MockResponse().setResponseCode(503).setHeader("Retry-After", "-1"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))
        val client = clientWithRetries()

        client.newCall(
            Request.Builder().url(server.url("/posts")).get().build(),
        ).execute().use { response ->
            assertEquals(200, response.code)
        }

        assertEquals(2, server.requestCount)
    }

    @Test
    fun `does not retry a POST after a transient server response`() {
        server.enqueue(MockResponse().setResponseCode(503))
        val client = clientWithRetries()
        val request = Request.Builder()
            .url(server.url("/posts"))
            .post("payload".toRequestBody())
            .build()

        client.newCall(request).execute().use { response ->
            assertEquals(503, response.code)
        }

        assertEquals(1, server.requestCount)
    }

    private fun clientWithRetries(): OkHttpClient =
        OkHttpClient.Builder()
            .retryOnConnectionFailure(false)
            .addInterceptor(
                SafeRetryInterceptor(
                    maxRetries = 1,
                    baseDelayMs = 0,
                    maxDelayMs = 0,
                ),
            )
            .build()
}
