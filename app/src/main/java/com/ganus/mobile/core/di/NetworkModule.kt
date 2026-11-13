package com.ganus.mobile.core.di

import com.ganus.mobile.BuildConfig
import com.ganus.mobile.core.network.AccessTokenProvider
import com.ganus.mobile.core.network.AndroidNetworkConnectivityObserver
import com.ganus.mobile.core.network.BearerTokenInterceptor
import com.ganus.mobile.core.network.DefaultHeadersInterceptor
import com.ganus.mobile.core.network.GanusApi
import com.ganus.mobile.core.network.NetworkConnectivityObserver
import com.ganus.mobile.core.network.PersistentAccessTokenProvider
import com.ganus.mobile.core.network.SafeRetryInterceptor
import com.ganus.mobile.feature.posts.data.PostsApi
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import javax.inject.Named
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkBindingsModule {
    @Binds
    @Singleton
    abstract fun bindAccessTokenProvider(
        implementation: PersistentAccessTokenProvider,
    ): AccessTokenProvider
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        tokenProvider: AccessTokenProvider,
    ): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .addInterceptor(DefaultHeadersInterceptor())
            .addInterceptor(BearerTokenInterceptor(tokenProvider))
            .addInterceptor(SafeRetryInterceptor())
            .build()

    @Provides
    @Singleton
    @Named("storageUploadClient")
    fun provideStorageUploadClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .callTimeout(90, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()

    @Provides
    @Singleton
    fun provideRetrofit(
        client: OkHttpClient,
        json: Json,
    ): Retrofit {
        val baseUrl = BuildConfig.API_BASE_URL
        require(baseUrl.startsWith("https://", ignoreCase = true)) {
            "API_BASE_URL must use HTTPS"
        }
        require(baseUrl.endsWith('/')) {
            "API_BASE_URL must end with '/'"
        }

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    fun providePostsApi(retrofit: Retrofit): PostsApi =
        retrofit.create(PostsApi::class.java)

    @Provides
    @Singleton
    fun provideGanusApi(retrofit: Retrofit): GanusApi =
        retrofit.create(GanusApi::class.java)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ConnectivityModule {
    @Binds
    @Singleton
    abstract fun bindNetworkConnectivityObserver(
        implementation: AndroidNetworkConnectivityObserver,
    ): NetworkConnectivityObserver
}
