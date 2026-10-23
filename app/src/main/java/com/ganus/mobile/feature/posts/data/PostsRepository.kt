package com.ganus.mobile.feature.posts.data

import com.ganus.mobile.core.network.ApiResult
import com.ganus.mobile.core.network.NetworkConnectivityObserver
import com.ganus.mobile.core.network.safeApiCall
import com.ganus.mobile.feature.posts.domain.Post
import javax.inject.Inject

interface PostsRepository {
    suspend fun getPosts(): ApiResult<List<Post>>
}

class DefaultPostsRepository @Inject constructor(
    private val api: PostsApi,
    private val connectivityObserver: NetworkConnectivityObserver,
) : PostsRepository {
    override suspend fun getPosts(): ApiResult<List<Post>> =
        safeApiCall(connectivityObserver) {
            api.getPosts()
        }
}
