package com.ganus.mobile.core.network

import java.io.IOException
import kotlinx.serialization.SerializationException

sealed interface NetworkError {
    data class Http(val statusCode: Int, val message: String?) : NetworkError
    data object NoInternet : NetworkError
    data object Timeout : NetworkError
    data class Serialization(val cause: SerializationException) : NetworkError
    data class Connection(val cause: IOException) : NetworkError
    data class Unexpected(val cause: Exception) : NetworkError
}
