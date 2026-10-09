package com.example.telegram.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.telegram.model.NetworkPreference

object TelegramNetworkUtil {

    data class NetworkStatus(
        val isConnected: Boolean,
        val isWifi: Boolean,
        val isCellular: Boolean,
        val isUnmetered: Boolean
    )

    fun getNetworkStatus(context: Context): NetworkStatus {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return NetworkStatus(isConnected = false, isWifi = false, isCellular = false, isUnmetered = false)

        val activeNetwork = cm.activeNetwork ?: return NetworkStatus(isConnected = false, isWifi = false, isCellular = false, isUnmetered = false)
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return NetworkStatus(isConnected = false, isWifi = false, isCellular = false, isUnmetered = false)

        val isConnected = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val isWifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        val isCellular = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        val isUnmetered = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)

        return NetworkStatus(
            isConnected = isConnected,
            isWifi = isWifi,
            isCellular = isCellular,
            isUnmetered = isUnmetered
        )
    }

    /**
     * Evaluates if backup upload is permitted under the current active network
     * according to the user's selected preference.
     *
     * - WIFI_ONLY: Requires Wi-Fi or unmetered network. Disallows cellular metered.
     * - MOBILE_DATA: Allows cellular network only. Disallows Wi-Fi.
     * - WIFI_AND_MOBILE: Allows either Wi-Fi or cellular connected networks.
     */
    fun isUploadAllowed(context: Context, preference: NetworkPreference): Boolean {
        val status = getNetworkStatus(context)
        if (!status.isConnected) return false

        return when (preference) {
            NetworkPreference.WIFI_ONLY -> {
                // Must be on Wi-Fi or an unmetered connection
                status.isWifi || status.isUnmetered
            }
            NetworkPreference.MOBILE_DATA -> {
                // Mobile data preference: must be on cellular connection, not Wi-Fi
                status.isCellular
            }
            NetworkPreference.WIFI_AND_MOBILE -> {
                // Any active internet connection
                status.isConnected
            }
        }
    }
}
