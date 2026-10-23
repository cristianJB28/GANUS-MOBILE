package com.ganus.mobile.feature.posts.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ganus.mobile.core.network.ApiResult
import com.ganus.mobile.core.network.NetworkError
import com.ganus.mobile.feature.posts.data.PostsRepository
import com.ganus.mobile.feature.posts.domain.Post
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface PostsUiState {
    data object Loading : PostsUiState
    data class Success(val posts: List<Post>) : PostsUiState
    data class Error(val error: NetworkError) : PostsUiState
}

@HiltViewModel
class PostsViewModel @Inject constructor(
    private val repository: PostsRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<PostsUiState>(PostsUiState.Loading)
    val uiState: StateFlow<PostsUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = PostsUiState.Loading
            _uiState.value = when (val result = repository.getPosts()) {
                is ApiResult.Success -> PostsUiState.Success(result.data)
                is ApiResult.Error -> PostsUiState.Error(result.error)
            }
        }
    }
}
