package com.ganus.mobile.core.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class ApiCallTest {
    private val connectivityObserver = object : NetworkConnectivityObserver {
        override val isConnected = MutableStateFlow(true)
    }

    @Test
    fun `maps HTTP failures to a typed network error`() = runBlocking {
        val response = Response.error<String>(
            404,
            "not found".toResponseBody(),
        )

        val result = safeApiCall(connectivityObserver) {
            throw HttpException(response)
        }

        assertEquals(
            ApiResult.Error(NetworkError.Http(404, "HTTP 404 Response.error()")),
            result,
        )
    }

    @Test
    fun `rethrows coroutine cancellation`() {
        assertThrows(CancellationException::class.java) {
            runBlocking {
                safeApiCall(connectivityObserver) {
                    throw CancellationException("cancelled")
                }
            }
        }
    }
}
