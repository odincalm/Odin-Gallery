package com.example.telegram.data

import android.content.Context
import com.example.data.local.OdinDatabase
import com.example.data.local.TelegramBackupItem
import com.example.telegram.client.TelegramAuthManager
import com.example.telegram.client.TelegramClientHolder
import com.example.telegram.client.TelegramSavedMessagesHelper
import com.example.telegram.model.TelegramAuthState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.drinkless.tdlib.TdApi

object TelegramSyncManager {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncedItemsCount = MutableStateFlow(0)
    val syncedItemsCount: StateFlow<Int> = _syncedItemsCount.asStateFlow()

    private var updatesJob: Job? = null
    private var isObserving = false

    fun init(context: Context) {
        startListeningForUpdates(context)
        syncWithCloud(context)
    }

    private fun startListeningForUpdates(context: Context) {
        if (isObserving) return
        isObserving = true

        updatesJob = scope.launch {
            val database = OdinDatabase.getInstance(context)
            val dao = database.telegramBackupDao()

            TelegramClientHolder.getUpdates(context).collect { update ->
                when (update) {
                    is TdApi.UpdateNewMessage -> {
                        val message = update.message
                        try {
                            val savedChatId = TelegramSavedMessagesHelper.getSavedMessagesChatId(context)
                            if (message.chatId == savedChatId) {
                                processSingleCloudMessage(context, message, dao)
                            }
                        } catch (ignored: Exception) {}
                    }
                    is TdApi.UpdateDeleteMessages -> {
                        try {
                            val savedChatId = TelegramSavedMessagesHelper.getSavedMessagesChatId(context)
                            if (update.chatId == savedChatId) {
                                for (msgId in update.messageIds) {
                                    dao.deleteByMessageId(msgId)
                                }
                            }
                        } catch (ignored: Exception) {}
                    }
                    is TdApi.UpdateFile -> {
                        // If a thumbnail finished downloading, update its local disk path
                        val file = update.file
                        if (file.local?.isDownloadingCompleted == true && !file.local.path.isNullOrBlank()) {
                            val items = dao.getCompletedItems()
                            val matching = items.firstOrNull { it.thumbnailFileId == file.id }
                            if (matching != null && matching.thumbnailPath != file.local.path) {
                                dao.updateThumbnailPath(matching.id, file.local.path)
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Incrementally synchronizes Saved Messages to discover Odin Gallery backups.
     * Reconciles the local Room index without redownloading heavy full-size media files.
     */
    fun syncWithCloud(context: Context, onComplete: ((Int) -> Unit)? = null) {
        if (_isSyncing.value) return

        scope.launch {
            _isSyncing.value = true
            var discoveredCount = 0

            try {
                // Ensure auth state is Ready
                TelegramAuthManager.init(context)
                val isReady = withTimeoutOrNull(8000L) {
                    TelegramAuthManager.authState.first { it is TelegramAuthState.Ready }
                }

                if (isReady == null) {
                    _isSyncing.value = false
                    onComplete?.invoke(0)
                    return@launch
                }

                val database = OdinDatabase.getInstance(context)
                val dao = database.telegramBackupDao()
                val chatId = TelegramSavedMessagesHelper.getSavedMessagesChatId(context)

                var fromMessageId = 0L
                var hasMore = true
                val batchSize = 100

                while (hasMore) {
                    val searchResult = try {
                        TelegramClientHolder.sendWithTimeout(
                            context,
                            TdApi.SearchChatMessages(
                                chatId,
                                null,
                                "#ODIN_BACKUP",
                                null,
                                fromMessageId,
                                0,
                                batchSize,
                                null
                            ),
                            15_000L
                        )
                    } catch (e: Exception) {
                        break
                    }

                    if (searchResult.messages.isEmpty()) {
                        hasMore = false
                        break
                    }

                    for (message in searchResult.messages) {
                        val added = processSingleCloudMessage(context, message, dao)
                        if (added) discoveredCount++
                    }

                    val lastMsg = searchResult.messages.lastOrNull()
                    if (lastMsg != null && lastMsg.id != fromMessageId) {
                        fromMessageId = lastMsg.id
                    } else {
                        hasMore = false
                    }
                }

                _syncedItemsCount.value = dao.getCompletedCount()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isSyncing.value = false
                onComplete?.invoke(discoveredCount)
            }
        }
    }

    private suspend fun processSingleCloudMessage(
        context: Context,
        message: TdApi.Message,
        dao: com.example.data.local.TelegramBackupDao
    ): Boolean = withContext(Dispatchers.IO) {
        var captionText: String? = null
        var originalFileId = 0
        var thumbnailFileId = 0
        var mediaWidth = 0
        var mediaHeight = 0
        var mediaDuration = 0L

        when (val content = message.content) {
            is TdApi.MessagePhoto -> {
                captionText = content.caption?.text
                val largest = content.photo?.sizes?.maxByOrNull { it.width * it.height }
                originalFileId = largest?.photo?.id ?: 0
                mediaWidth = largest?.width ?: 0
                mediaHeight = largest?.height ?: 0

                // Thumbnail: smallest photo size
                val smallest = content.photo?.sizes?.minByOrNull { it.width * it.height }
                thumbnailFileId = smallest?.photo?.id ?: 0
            }
            is TdApi.MessageVideo -> {
                captionText = content.caption?.text
                originalFileId = content.video?.video?.id ?: 0
                thumbnailFileId = content.video?.thumbnail?.file?.id ?: 0
                mediaWidth = content.video?.width ?: 0
                mediaHeight = content.video?.height ?: 0
                mediaDuration = (content.video?.duration ?: 0) * 1000L
            }
            else -> return@withContext false
        }

        val metadata = TelegramSavedMessagesHelper.parseBackupCaption(captionText)
            ?: return@withContext false

        // Check if message ID is already registered
        val existingByMsg = dao.getItemByMessageId(message.id)
        if (existingByMsg != null) {
            // Already indexed! Ensure thumbnail is requested if missing
            if (existingByMsg.thumbnailPath.isNullOrBlank() && thumbnailFileId != 0) {
                requestThumbnailDownload(context, thumbnailFileId)
            }
            return@withContext false
        }

        // Check if hash matches an existing local pending/completed item
        val existingByHash = dao.getItemByHash(metadata.fileHash)
        if (existingByHash != null) {
            // Update the existing item to completed with the confirmed Telegram message ID
            val updated = existingByHash.copy(
                telegramMessageId = message.id,
                telegramFileId = originalFileId,
                thumbnailFileId = thumbnailFileId,
                status = "COMPLETED",
                completedAt = metadata.timestamp,
                width = if (existingByHash.width > 0) existingByHash.width else mediaWidth,
                height = if (existingByHash.height > 0) existingByHash.height else mediaHeight,
                duration = if (existingByHash.duration > 0) existingByHash.duration else mediaDuration
            )
            dao.update(updated)
            if (thumbnailFileId != 0) {
                requestThumbnailDownload(context, thumbnailFileId)
            }
            return@withContext true
        }

        // It is a cloud item (e.g. from another device or restored account)
        val cloudItem = TelegramBackupItem(
            localMediaId = 0L,
            uriString = null,
            filePath = null,
            fileName = metadata.fileName,
            mediaType = metadata.mediaType,
            sizeBytes = metadata.sizeBytes,
            fileHash = metadata.fileHash,
            telegramMessageId = message.id,
            telegramFileId = originalFileId,
            thumbnailFileId = thumbnailFileId,
            thumbnailPath = null,
            status = "COMPLETED",
            dateModified = metadata.timestamp,
            width = mediaWidth,
            height = mediaHeight,
            duration = mediaDuration,
            queuedAt = metadata.timestamp,
            completedAt = metadata.timestamp
        )
        dao.insert(cloudItem)

        // Asynchronously request thumbnail download (lightweight preview)
        if (thumbnailFileId != 0) {
            requestThumbnailDownload(context, thumbnailFileId)
        }

        return@withContext true
    }

    private fun requestThumbnailDownload(context: Context, fileId: Int) {
        scope.launch {
            try {
                TelegramClientHolder.send(
                    context,
                    TdApi.DownloadFile(fileId, 1, 0, 0, false)
                )
            } catch (ignored: Exception) {}
        }
    }
}
