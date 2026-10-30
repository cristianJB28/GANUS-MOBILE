package com.ganus.mobile.core.network

import java.io.IOException
import java.io.InterruptedIOException
import okhttp3.Interceptor
import okhttp3.Response

class DefaultHeadersInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response =
        chain.proceed(
            chain.request()
                .newBuilder()
                .header("Accept", "application/json")
                .build(),
        )
}

class BearerTokenInterceptor(
    private val tokenProvider: AccessTokenProvider,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val requestBuilder = chain.request().newBuilder()
        tokenProvider.getAccessToken()
            ?.takeIf(String::isNotBlank)
            ?.let { requestBuilder.header("Authorization", "Bearer $it") }
        return chain.proceed(requestBuilder.build())
    }
}

class SafeRetryInterceptor(
    private val maxRetries: Int = 2,
    private val baseDelayMs: Long = 300,
    private val maxDelayMs: Long = 1_500,
) : Interceptor {
    init {
        require(maxRetries >= 0)
        require(baseDelayMs >= 0)
        require(maxDelayMs >= 0)
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.method !in SAFE_METHODS || maxRetries == 0) {
            return chain.proceed(request)
        }

        var retries = 0
        while (true) {
            if (chain.call().isCanceled()) {
                throw IOException("HTTP call was cancelled")
            }

            val response = try {
                chain.proceed(request)
            } catch (exception: IOException) {
                if (retries >= maxRetries || chain.call().isCanceled()) {
                    throw exception
                }
                waitBeforeRetry(chain, exponentialDelay(retries))
                retries++
                continue
            }

            if (retries >= maxRetries || response.code !in RETRYABLE_STATUS_CODES) {
                return response
            }

            val delay = retryAfterDelay(response.header("Retry-After"))
                ?: exponentialDelay(retries)
            response.close()
            waitBeforeRetry(chain, delay)
            retries++
        }
    }

    private fun exponentialDelay(retry: Int): Long {
        val multiplier = 1L shl retry.coerceAtMost(30)
        return if (baseDelayMs > maxDelayMs / multiplier) {
            maxDelayMs
        } else {
            baseDelayMs * multiplier
        }
    }

    private fun retryAfterDelay(header: String?): Long? {
        val seconds = header?.toLongOrNull()?.takeIf { it >= 0 } ?: return null
        if (seconds > Long.MAX_VALUE / 1_000) {
            return maxDelayMs
        }
        return (seconds * 1_000).coerceAtMost(maxDelayMs)
    }

    private fun waitBeforeRetry(chain: Interceptor.Chain, delayMs: Long) {
        if (chain.call().isCanceled()) {
            throw IOException("HTTP call was cancelled")
        }
        try {
            Thread.sleep(delayMs)
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            throw InterruptedIOException("Retry delay was interrupted").apply {
                initCause(exception)
            }
        }
    }

    private companion object {
        val SAFE_METHODS = setOf("GET", "HEAD", "OPTIONS")
        val RETRYABLE_STATUS_CODES = setOf(408, 429, 500, 502, 503, 504)
    }
}
