package com.ganus.mobile.feature.posts.data

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PostsRepositoryModule {
    @Binds
    @Singleton
    abstract fun bindPostsRepository(
        implementation: DefaultPostsRepository,
    ): PostsRepository
}
