package com.ganus.mobile.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface NetworkConnectivityObserver {
    val isConnected: StateFlow<Boolean>
}

@Singleton
class AndroidNetworkConnectivityObserver @Inject constructor(
    @ApplicationContext context: Context,
) : NetworkConnectivityObserver {
    private val connectivityManager =
        context.getSystemService(ConnectivityManager::class.java)

    private val _isConnected = MutableStateFlow(isCurrentlyConnected())
    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _isConnected.value = hasValidatedInternet(
                connectivityManager.getNetworkCapabilities(network),
            )
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities,
        ) {
            _isConnected.value = hasValidatedInternet(networkCapabilities)
        }

        override fun onLost(network: Network) {
            _isConnected.value = isCurrentlyConnected()
        }
    }

    init {
        connectivityManager.registerDefaultNetworkCallback(callback)
        _isConnected.value = isCurrentlyConnected()
    }

    private fun isCurrentlyConnected(): Boolean =
        connectivityManager.activeNetwork
            ?.let(connectivityManager::getNetworkCapabilities)
            ?.let(::hasValidatedInternet)
            ?: false

    private fun hasValidatedInternet(capabilities: NetworkCapabilities?): Boolean =
        capabilities?.let {
            it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                it.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } ?: false
}
