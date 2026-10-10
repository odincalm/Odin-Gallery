package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.MediaStoreScanner
import com.example.model.MediaItem
import com.example.telegram.client.TelegramAuthManager
import com.example.telegram.client.TelegramClientHolder
import com.example.telegram.client.TelegramSavedMessagesHelper
import com.example.telegram.data.TelegramBackupPreferences
import com.example.telegram.data.TelegramBackupRepository
import com.example.telegram.data.TelegramFileUtil
import com.example.telegram.data.TelegramUploader
import com.example.telegram.model.BackupStats
import com.example.telegram.model.DiscoveredCloudItem
import com.example.telegram.model.NetworkPreference
import com.example.telegram.model.TelegramAuthState
import com.example.telegram.restore.TelegramRestoreManager
import com.example.telegram.worker.TelegramBackupWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TelegramBackupViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    val repository = TelegramBackupRepository(context)
    val preferences = repository.preferences

    // Telegram authentication state
    val authState: StateFlow<TelegramAuthState> = TelegramAuthManager.authState

    // Backup stats from Room DB
    val backupStats: StateFlow<BackupStats> = repository.getBackupStats().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = BackupStats()
    )

    // Preference flows
    val isBackupEnabled: StateFlow<Boolean> = preferences.isBackupEnabled
    val networkPreference: StateFlow<NetworkPreference> = preferences.networkPreference
    val backupPhotos: StateFlow<Boolean> = preferences.backupPhotos
    val backupVideos: StateFlow<Boolean> = preferences.backupVideos
    val backupExistingMedia: StateFlow<Boolean> = preferences.backupExistingMedia
    val backupNewMediaAutomatically: StateFlow<Boolean> = preferences.backupNewMediaAutomatically
    val lastBackupTime: StateFlow<Long> = preferences.lastBackupTime

    // Dialog & UI states
    private val _isActionLoading = MutableStateFlow(false)
    val isActionLoading: StateFlow<Boolean> = _isActionLoading.asStateFlow()

    private val _userFeedbackMessage = MutableStateFlow<String?>(null)
    val userFeedbackMessage: StateFlow<String?> = _userFeedbackMessage.asStateFlow()

    // Test upload state
    private val _testUploadStatus = MutableStateFlow<String?>(null)
    val testUploadStatus: StateFlow<String?> = _testUploadStatus.asStateFlow()

    private val _isTestingUpload = MutableStateFlow(false)
    val isTestingUpload: StateFlow<Boolean> = _isTestingUpload.asStateFlow()

    // Restore state
    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    private val _discoveredItems = MutableStateFlow<List<DiscoveredCloudItem>>(emptyList())
    val discoveredItems: StateFlow<List<DiscoveredCloudItem>> = _discoveredItems.asStateFlow()

    private val _isRestoring = MutableStateFlow(false)
    val isRestoring: StateFlow<Boolean> = _isRestoring.asStateFlow()

    private val _restoreProgress = MutableStateFlow(0 to 0) // current to total
    val restoreProgress: StateFlow<Pair<Int, Int>> = _restoreProgress.asStateFlow()

    init {
        try {
            TelegramAuthManager.init(context)
        } catch (ignored: Throwable) {}
    }

    fun connectTelegram() {
        TelegramAuthManager.init(context)
    }

    fun sendPhoneNumber(phoneNumber: String) {
        viewModelScope.launch {
            _isActionLoading.value = true
            try {
                val res = TelegramAuthManager.sendPhoneNumber(context, phoneNumber)
                res.onFailure { error ->
                    _userFeedbackMessage.value = error.message ?: "Failed to send phone number"
                }
            } catch (e: Throwable) {
                _userFeedbackMessage.value = e.message ?: "An unexpected error occurred"
            } finally {
                _isActionLoading.value = false
            }
        }
    }

    fun sendCode(code: String) {
        viewModelScope.launch {
            _isActionLoading.value = true
            try {
                val res = TelegramAuthManager.sendCode(context, code)
                res.onFailure { error ->
                    _userFeedbackMessage.value = error.message ?: "Verification failed"
                }
            } catch (e: Throwable) {
                _userFeedbackMessage.value = e.message ?: "An unexpected error occurred"
            } finally {
                _isActionLoading.value = false
            }
        }
    }

    fun sendPassword(password: String) {
        viewModelScope.launch {
            _isActionLoading.value = true
            try {
                val res = TelegramAuthManager.sendPassword(context, password)
                res.onFailure { error ->
                    _userFeedbackMessage.value = error.message ?: "Password check failed"
                }
            } catch (e: Throwable) {
                _userFeedbackMessage.value = e.message ?: "An unexpected error occurred"
            } finally {
                _isActionLoading.value = false
            }
        }
    }

    fun logOut() {
        viewModelScope.launch {
            _isActionLoading.value = true
            try {
                TelegramBackupWorker.cancelBackup(context)
                TelegramAuthManager.logOut(context)
            } catch (e: Throwable) {
                _userFeedbackMessage.value = e.message ?: "Logout failed"
            } finally {
                _isActionLoading.value = false
            }
        }
    }

    fun cancelAuthentication() {
        TelegramAuthManager.cancelAuthentication()
        _userFeedbackMessage.value = null
    }

    fun resetError() {
        TelegramAuthManager.resetError()
        _userFeedbackMessage.value = null
    }

    fun clearFeedbackMessage() {
        _userFeedbackMessage.value = null
    }

    fun setBackupEnabled(enabled: Boolean, allMedia: List<MediaItem> = emptyList()) {
        preferences.setBackupEnabled(enabled)
        if (enabled) {
            viewModelScope.launch {
                if (preferences.backupExistingMedia.value) {
                    val mediaList = if (allMedia.isNotEmpty()) {
                        allMedia
                    } else {
                        val scanner = MediaStoreScanner(context)
                        withContext(Dispatchers.IO) { scanner.queryAllMedia() }
                    }
                    repository.enqueueMediaItems(mediaList)
                }
                TelegramBackupWorker.enqueueBackup(context)
            }
        } else {
            TelegramBackupWorker.cancelBackup(context)
        }
    }

    fun setNetworkPreference(pref: NetworkPreference) {
        preferences.setNetworkPreference(pref)
        if (isBackupEnabled.value) {
            TelegramBackupWorker.enqueueBackup(context)
        }
    }

    fun setBackupPhotos(enabled: Boolean) {
        preferences.setBackupPhotos(enabled)
    }

    fun setBackupVideos(enabled: Boolean) {
        preferences.setBackupVideos(enabled)
    }

    fun setBackupExistingMedia(enabled: Boolean) {
        preferences.setBackupExistingMedia(enabled)
    }

    fun setBackupNewMediaAutomatically(enabled: Boolean) {
        preferences.setBackupNewMediaAutomatically(enabled)
    }

    fun backupExistingMediaNow(allMedia: List<MediaItem>) {
        viewModelScope.launch {
            _isActionLoading.value = true
            try {
                val count = repository.enqueueMediaItems(allMedia)
                TelegramBackupWorker.enqueueBackup(context)
                _userFeedbackMessage.value = "Enqueued $count items for Telegram backup"
            } catch (e: Throwable) {
                _userFeedbackMessage.value = "Failed to enqueue backup: ${e.message}"
            } finally {
                _isActionLoading.value = false
            }
        }
    }

    fun retryFailedUploads() {
        viewModelScope.launch {
            _isActionLoading.value = true
            try {
                val count = repository.retryFailed()
                TelegramBackupWorker.enqueueBackup(context)
                _userFeedbackMessage.value = "Retrying $count failed items"
            } catch (e: Throwable) {
                _userFeedbackMessage.value = "Failed to retry: ${e.message}"
            } finally {
                _isActionLoading.value = false
            }
        }
    }

    fun testUploadSampleMedia(item: MediaItem) {
        viewModelScope.launch {
            _isTestingUpload.value = true
            _testUploadStatus.value = "Preparing test upload..."
            try {
                val hash = TelegramFileUtil.computeSha256(context, item.uri)
                _testUploadStatus.value = "Uploading to Saved Messages..."
                val result = TelegramUploader.uploadMedia(
                    context = context,
                    uriString = item.uri.toString(),
                    fileName = item.displayName,
                    isVideo = item.isVideo,
                    sizeBytes = item.size,
                    fileHash = hash.ifBlank { "test_hash_${item.id}" }
                )
                result.fold(
                    onSuccess = { messageId ->
                        _testUploadStatus.value = "Upload confirmed! Saved Messages ID #$messageId"
                        preferences.updateLastBackupTime()
                    },
                    onFailure = { error ->
                        _testUploadStatus.value = "Test upload failed: ${error.message}"
                    }
                )
            } catch (e: Throwable) {
                _testUploadStatus.value = "Test upload failed: ${e.message}"
            } finally {
                _isTestingUpload.value = false
            }
        }
    }

    fun clearTestUploadStatus() {
        _testUploadStatus.value = null
    }

    fun discoverCloudBackups() {
        viewModelScope.launch {
            _isDiscovering.value = true
            try {
                val items = TelegramRestoreManager.discoverEligibleBackups(context)
                _discoveredItems.value = items
                if (items.isEmpty()) {
                    _userFeedbackMessage.value = "No Odin Gallery backups found in Saved Messages."
                } else {
                    val newCount = items.count { !it.isAlreadyLocal }
                    _userFeedbackMessage.value = "Found ${items.size} backups ($newCount new to restore)"
                }
            } catch (e: Exception) {
                _userFeedbackMessage.value = "Discovery error: ${e.message}"
            } finally {
                _isDiscovering.value = false
            }
        }
    }

    fun restoreDiscoveredItems() {
        val itemsToRestore = _discoveredItems.value.filter { !it.isAlreadyLocal }
        if (itemsToRestore.isEmpty()) {
            _userFeedbackMessage.value = "All discovered backups are already restored on this device."
            return
        }

        viewModelScope.launch {
            _isRestoring.value = true
            try {
                val total = itemsToRestore.size
                var successCount = 0

                for ((index, item) in itemsToRestore.withIndex()) {
                    _restoreProgress.value = (index + 1) to total
                    val result = TelegramRestoreManager.restoreItem(context, item)
                    if (result.isSuccess) {
                        successCount++
                    }
                }

                _userFeedbackMessage.value = "Restored $successCount of $total items successfully!"
                discoverCloudBackups()
            } catch (e: Throwable) {
                _userFeedbackMessage.value = "Restore error: ${e.message}"
            } finally {
                _isRestoring.value = false
            }
        }
    }

    fun deleteCloudBackups(onComplete: () -> Unit) {
        viewModelScope.launch {
            _isActionLoading.value = true
            try {
                val items = TelegramSavedMessagesHelper.discoverBackups(context)
                val messageIds = items.map { it.messageId }.toLongArray()
                if (messageIds.isNotEmpty()) {
                    TelegramSavedMessagesHelper.deleteCloudBackups(context, messageIds)
                }
                repository.clearLocalBackupRecords()
                _discoveredItems.value = emptyList()
                _userFeedbackMessage.value = "Deleted ${messageIds.size} backup messages from Saved Messages."
                onComplete()
            } catch (e: Exception) {
                _userFeedbackMessage.value = "Failed to delete cloud backups: ${e.message}"
            } finally {
                _isActionLoading.value = false
            }
        }
    }
}
