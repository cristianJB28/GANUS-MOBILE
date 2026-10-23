package com.ganus.mobile.core.network

import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

interface AccessTokenProvider {
    fun getAccessToken(): String?
}

@Singleton
class InMemoryAccessTokenProvider @Inject constructor() : AccessTokenProvider {
    private val accessToken = AtomicReference<String?>(null)

    override fun getAccessToken(): String? = accessToken.get()

    fun setAccessToken(token: String?) {
        accessToken.set(token?.takeIf(String::isNotBlank))
    }

    fun clearAccessToken() {
        accessToken.set(null)
    }
}
