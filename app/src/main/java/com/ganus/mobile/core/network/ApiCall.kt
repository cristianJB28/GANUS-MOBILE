package com.ganus.mobile.core.network

import java.io.IOException
import java.io.InterruptedIOException
import java.net.UnknownHostException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import retrofit2.HttpException

suspend fun <T> safeApiCall(
    connectivityObserver: NetworkConnectivityObserver,
    apiCall: suspend () -> T,
): ApiResult<T> {
    if (!connectivityObserver.isConnected.value) {
        return ApiResult.Error(NetworkError.NoInternet)
    }

    return try {
        ApiResult.Success(apiCall())
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: HttpException) {
        ApiResult.Error(
            NetworkError.Http(
                statusCode = exception.code(),
                message = exception.message,
            ),
        )
    } catch (exception: InterruptedIOException) {
        ApiResult.Error(NetworkError.Timeout)
    } catch (exception: UnknownHostException) {
        ApiResult.Error(NetworkError.NoInternet)
    } catch (exception: SerializationException) {
        ApiResult.Error(NetworkError.Serialization(exception))
    } catch (exception: IOException) {
        if (connectivityObserver.isConnected.value) {
            ApiResult.Error(NetworkError.Connection(exception))
        } else {
            ApiResult.Error(NetworkError.NoInternet)
        }
    } catch (exception: Exception) {
        ApiResult.Error(NetworkError.Unexpected(exception))
    }
}
