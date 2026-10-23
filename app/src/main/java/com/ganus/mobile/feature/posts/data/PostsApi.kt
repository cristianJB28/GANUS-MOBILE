package com.ganus.mobile.feature.posts.data

import com.ganus.mobile.feature.posts.domain.Post
import retrofit2.http.GET

interface PostsApi {
    @GET("posts")
    suspend fun getPosts(): List<Post>
}
