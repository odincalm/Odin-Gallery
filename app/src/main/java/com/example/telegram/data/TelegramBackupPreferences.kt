package com.example.telegram.data

import android.content.Context
import android.content.SharedPreferences
import com.example.telegram.model.NetworkPreference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TelegramBackupPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _isBackupEnabled = MutableStateFlow(prefs.getBoolean(KEY_BACKUP_ENABLED, false))
    val isBackupEnabled: StateFlow<Boolean> = _isBackupEnabled.asStateFlow()

    private val _networkPreference = MutableStateFlow(
        NetworkPreference.entries.find { it.name == prefs.getString(KEY_NETWORK_PREF, NetworkPreference.WIFI_ONLY.name) }
            ?: NetworkPreference.WIFI_ONLY
    )
    val networkPreference: StateFlow<NetworkPreference> = _networkPreference.asStateFlow()

    private val _backupPhotos = MutableStateFlow(prefs.getBoolean(KEY_BACKUP_PHOTOS, true))
    val backupPhotos: StateFlow<Boolean> = _backupPhotos.asStateFlow()

    private val _backupVideos = MutableStateFlow(prefs.getBoolean(KEY_BACKUP_VIDEOS, true))
    val backupVideos: StateFlow<Boolean> = _backupVideos.asStateFlow()

    private val _backupExistingMedia = MutableStateFlow(prefs.getBoolean(KEY_BACKUP_EXISTING, true))
    val backupExistingMedia: StateFlow<Boolean> = _backupExistingMedia.asStateFlow()

    private val _backupNewMediaAutomatically = MutableStateFlow(prefs.getBoolean(KEY_BACKUP_NEW_AUTO, true))
    val backupNewMediaAutomatically: StateFlow<Boolean> = _backupNewMediaAutomatically.asStateFlow()

    private val _lastBackupTime = MutableStateFlow(prefs.getLong(KEY_LAST_BACKUP, 0L))
    val lastBackupTime: StateFlow<Long> = _lastBackupTime.asStateFlow()

    fun setBackupEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BACKUP_ENABLED, enabled).apply()
        _isBackupEnabled.value = enabled
    }

    fun setNetworkPreference(pref: NetworkPreference) {
        prefs.edit().putString(KEY_NETWORK_PREF, pref.name).apply()
        _networkPreference.value = pref
    }

    fun setBackupPhotos(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BACKUP_PHOTOS, enabled).apply()
        _backupPhotos.value = enabled
    }

    fun setBackupVideos(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BACKUP_VIDEOS, enabled).apply()
        _backupVideos.value = enabled
    }

    fun setBackupExistingMedia(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BACKUP_EXISTING, enabled).apply()
        _backupExistingMedia.value = enabled
    }

    fun setBackupNewMediaAutomatically(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BACKUP_NEW_AUTO, enabled).apply()
        _backupNewMediaAutomatically.value = enabled
    }

    fun updateLastBackupTime(timestamp: Long = System.currentTimeMillis()) {
        prefs.edit().putLong(KEY_LAST_BACKUP, timestamp).apply()
        _lastBackupTime.value = timestamp
    }

    companion object {
        private const val PREFS_NAME = "odin_telegram_backup_prefs"
        private const val KEY_BACKUP_ENABLED = "backup_enabled"
        private const val KEY_NETWORK_PREF = "network_preference"
        private const val KEY_BACKUP_PHOTOS = "backup_photos"
        private const val KEY_BACKUP_VIDEOS = "backup_videos"
        private const val KEY_BACKUP_EXISTING = "backup_existing"
        private const val KEY_BACKUP_NEW_AUTO = "backup_new_auto"
        private const val KEY_LAST_BACKUP = "last_backup_timestamp"
    }
}
