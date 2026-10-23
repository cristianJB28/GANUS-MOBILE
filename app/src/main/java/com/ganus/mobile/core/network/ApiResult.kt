package com.ganus.mobile.core.network

sealed interface ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>
    data class Error(val error: NetworkError) : ApiResult<Nothing>
}
