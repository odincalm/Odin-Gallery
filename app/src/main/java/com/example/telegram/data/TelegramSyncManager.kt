package com.example.telegram.data

import android.content.Context
import android.util.Log
import com.example.data.local.OdinDatabase
import com.example.data.local.TelegramBackupDao
import com.example.data.local.TelegramBackupItem
import com.example.telegram.client.TelegramAuthManager
import com.example.telegram.client.TelegramClientHolder
import com.example.telegram.client.TelegramSavedMessagesHelper
import com.example.telegram.model.TelegramAuthState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.drinkless.tdlib.TdApi
import kotlin.math.abs

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
                        val file = update.file
                        val local = file.local
                        if (local != null && local.isDownloadingCompleted && !local.path.isNullOrBlank()) {
                            val path = local.path
                            val items = dao.getCompletedItems()
                            val matching = items.firstOrNull { it.thumbnailFileId == file.id }
                                ?: items.firstOrNull { it.telegramFileId == file.id && it.thumbnailPath.isNullOrBlank() }
                            if (matching != null && matching.thumbnailPath != path) {
                                if (matching.thumbnailFileId == file.id || matching.thumbnailPath.isNullOrBlank()) {
                                    dao.updateThumbnailPath(matching.id, path)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun syncWithCloud(context: Context, forceFullRescan: Boolean = false, onComplete: ((Int) -> Unit)? = null) {
        if (_isSyncing.value) return

        scope.launch {
            _isSyncing.value = true
            var discoveredCount = 0

            try {
                TelegramAuthManager.init(context)
                val isReady = withTimeoutOrNull(8000L) {
                    TelegramAuthManager.authState.firstOrNull { it is TelegramAuthState.Ready || it is TelegramAuthState.Error }
                }

                if (isReady !is TelegramAuthState.Ready) {
                    _isSyncing.value = false
                    onComplete?.invoke(0)
                    return@launch
                }

                val database = OdinDatabase.getInstance(context)
                val dao = database.telegramBackupDao()
                val chatId = TelegramSavedMessagesHelper.getSavedMessagesChatId(context)
                val prefs = TelegramBackupPreferences(context)

                if (forceFullRescan) {
                    prefs.resetScanState()
                }

                var fromMessageId = if (forceFullRescan) 0L else prefs.getScanCursorMessageId()
                var hasMore = true
                val batchSize = 100

                Log.d("TelegramSync", "Starting sync. forceFullRescan=$forceFullRescan, initialFromMsgId=$fromMessageId")

                while (hasMore) {
                    Log.d("TelegramSync", "Syncing page starting fromMessageId=$fromMessageId")
                    val history = try {
                        TelegramClientHolder.sendWithTimeout(
                            context,
                            TdApi.GetChatHistory(
                                chatId,
                                fromMessageId,
                                0,
                                batchSize,
                                false
                            ),
                            20_000L
                        )
                    } catch (e: Exception) {
                        Log.e("TelegramSync", "Failed to fetch history: ${e.message}")
                        break
                    }

                    val rawMessages = history.messages.orEmpty()
                    Log.d("TelegramSync", "Received ${rawMessages.size} messages")

                    if (rawMessages.isEmpty()) {
                        hasMore = false
                        prefs.setScanCompleted(true)
                        break
                    }

                    val messages = if (fromMessageId != 0L && rawMessages.firstOrNull()?.id == fromMessageId) {
                        rawMessages.drop(1)
                    } else {
                        rawMessages.toList()
                    }

                    if (messages.isEmpty()) {
                        hasMore = false
                        prefs.setScanCompleted(true)
                        break
                    }

                    var addedInPage = 0
                    for (message in messages) {
                        val added = processSingleCloudMessage(context, message, dao)
                        if (added) {
                            addedInPage++
                            discoveredCount++
                        }
                    }
                    Log.d("TelegramSync", "Processed page. Added $addedInPage new items. Total so far: $discoveredCount")

                    val lastMsgId = messages.last().id
                    if (lastMsgId != 0L && lastMsgId != fromMessageId) {
                        fromMessageId = lastMsgId
                        prefs.setScanCursorMessageId(fromMessageId)
                    } else {
                        hasMore = false
                        prefs.setScanCompleted(true)
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

    suspend fun resolveRemoteMessage(
        context: Context,
        messageId: Long
    ): Pair<Int, Int>? = withContext(Dispatchers.IO) {
        if (messageId <= 0L) return@withContext null
        try {
            val chatId = TelegramSavedMessagesHelper.getSavedMessagesChatId(context)
            val message = TelegramClientHolder.sendWithTimeout(
                context,
                TdApi.GetMessage(chatId, messageId),
                10_000L
            )

            var originalFileId = 0
            var thumbnailFileId = 0
            var mediaWidth = 0
            var mediaHeight = 0
            var mediaDuration = 0L

            when (val content = message.content) {
                is TdApi.MessagePhoto -> {
                    val sizes = content.photo?.sizes.orEmpty()
                    val largest = sizes.maxByOrNull { it.width * it.height }
                    originalFileId = largest?.photo?.id ?: 0
                    mediaWidth = largest?.width ?: 0
                    mediaHeight = largest?.height ?: 0

                    val mid = sizes
                        .filter { it.width in 100..640 || it.height in 100..640 }
                        .minByOrNull { abs(it.width - 320) + abs(it.height - 320) }
                    val smallest = sizes.filter { it.width > 0 && it.height > 0 }.minByOrNull { it.width * it.height }
                    thumbnailFileId = (mid ?: smallest)?.photo?.id ?: 0
                }
                is TdApi.MessageVideo -> {
                    originalFileId = content.video?.video?.id ?: 0
                    thumbnailFileId = content.video?.thumbnail?.file?.id ?: 0
                    mediaWidth = content.video?.width ?: 0
                    mediaHeight = content.video?.height ?: 0
                    mediaDuration = (content.video?.duration ?: 0) * 1000L
                }
                is TdApi.MessageDocument -> {
                    originalFileId = content.document?.document?.id ?: 0
                    thumbnailFileId = content.document?.thumbnail?.file?.id ?: 0
                }
            }

            if (originalFileId != 0) {
                val dao = OdinDatabase.getInstance(context).telegramBackupDao()
                val existing = dao.getItemByMessageId(messageId)
                if (existing != null) {
                    dao.update(
                        existing.copy(
                            telegramFileId = originalFileId,
                            thumbnailFileId = if (thumbnailFileId != 0) thumbnailFileId else existing.thumbnailFileId,
                            width = if (mediaWidth > 0) mediaWidth else existing.width,
                            height = if (mediaHeight > 0) mediaHeight else existing.height,
                            duration = if (mediaDuration > 0) mediaDuration else existing.duration
                        )
                    )
                }
                return@withContext Pair(originalFileId, thumbnailFileId)
            }
        } catch (e: Exception) {
            Log.w("TelegramSyncManager", "resolveRemoteMessage failed for messageId=$messageId: ${e.message}")
        }
        return@withContext null
    }

    private suspend fun processSingleCloudMessage(
        context: Context,
        message: TdApi.Message,
        dao: TelegramBackupDao
    ): Boolean = withContext(Dispatchers.IO) {
        val mediaInfo = TelegramSavedMessagesHelper.extractMediaInfo(message)
            ?: return@withContext false

        val metadata = mediaInfo.metadata
        val originalFileId = mediaInfo.originalFileId
        val thumbnailFileId = mediaInfo.thumbnailFileId
        val mediaWidth = mediaInfo.mediaWidth
        val mediaHeight = mediaInfo.mediaHeight
        val mediaDuration = mediaInfo.mediaDuration

        val resolvedHash = metadata.fileHash.ifBlank { "msg_${message.id}" }
        val isSyntheticHash = resolvedHash.startsWith("msg_") || resolvedHash.startsWith("legacy_msg_") || resolvedHash.startsWith("hash_")
        val resolvedName = if (metadata.fileName == "odin_backup_file") {
            "odin_media_${message.id}.${if (metadata.mediaType == "VIDEO") "mp4" else "jpg"}"
        } else metadata.fileName
        val resolvedTs = if (metadata.timestamp == 0L) message.date * 1000L else metadata.timestamp

        var initialThumbPath: String? = null
        if (thumbnailFileId != 0) {
            try {
                val tf = TelegramClientHolder.send(context, TdApi.GetFile(thumbnailFileId))
                if (tf.local?.isDownloadingCompleted == true && !tf.local.path.isNullOrBlank()) {
                    initialThumbPath = tf.local.path
                }
            } catch (ignored: Exception) {}
        }

        val existingByMsg = dao.getItemByMessageId(message.id)
        if (existingByMsg != null) {
            val updated = existingByMsg.copy(
                telegramFileId = if (originalFileId != 0) originalFileId else existingByMsg.telegramFileId,
                thumbnailFileId = if (thumbnailFileId != 0) thumbnailFileId else existingByMsg.thumbnailFileId,
                thumbnailPath = initialThumbPath ?: existingByMsg.thumbnailPath,
                status = "COMPLETED",
                completedAt = resolvedTs,
                width = if (existingByMsg.width > 0) existingByMsg.width else mediaWidth,
                height = if (existingByMsg.height > 0) existingByMsg.height else mediaHeight,
                duration = if (existingByMsg.duration > 0) existingByMsg.duration else mediaDuration
            )
            dao.update(updated)
            if (thumbnailFileId != 0 && updated.thumbnailPath.isNullOrBlank()) {
                requestThumbnailDownload(context, thumbnailFileId)
            }
            return@withContext false
        }

        if (!isSyntheticHash && resolvedHash.isNotBlank()) {
            val existingByHash = dao.getItemByHash(resolvedHash)
            if (existingByHash != null) {
                val updated = existingByHash.copy(
                    telegramMessageId = message.id,
                    telegramFileId = originalFileId,
                    thumbnailFileId = thumbnailFileId,
                    thumbnailPath = initialThumbPath ?: existingByHash.thumbnailPath,
                    status = "COMPLETED",
                    completedAt = resolvedTs,
                    width = if (existingByHash.width > 0) existingByHash.width else mediaWidth,
                    height = if (existingByHash.height > 0) existingByHash.height else mediaHeight,
                    duration = if (existingByHash.duration > 0) existingByHash.duration else mediaDuration
                )
                dao.update(updated)
                if (thumbnailFileId != 0 && updated.thumbnailPath.isNullOrBlank()) {
                    requestThumbnailDownload(context, thumbnailFileId)
                }
                return@withContext true
            }
        }

        val cloudItem = TelegramBackupItem(
            localMediaId = 0L,
            uriString = null,
            filePath = null,
            fileName = resolvedName,
            mediaType = metadata.mediaType,
            sizeBytes = metadata.sizeBytes,
            fileHash = resolvedHash,
            telegramMessageId = message.id,
            telegramFileId = originalFileId,
            thumbnailFileId = thumbnailFileId,
            thumbnailPath = initialThumbPath,
            status = "COMPLETED",
            dateModified = resolvedTs,
            width = mediaWidth,
            height = mediaHeight,
            duration = mediaDuration,
            queuedAt = resolvedTs,
            completedAt = resolvedTs
        )
        dao.insert(cloudItem)

        if (thumbnailFileId != 0 && initialThumbPath == null) {
            requestThumbnailDownload(context, thumbnailFileId)
        }

        return@withContext true
    }

    private fun requestThumbnailDownload(context: Context, fileId: Int) {
        if (fileId == 0) return
        scope.launch {
            try {
                TelegramClientHolder.send(
                    context,
                    TdApi.DownloadFile(fileId, 32, 0, 0, false)
                )
                var downloadedPath: String? = null
                repeat(12) {
                    delay(300)
                    val f = TelegramClientHolder.send(context, TdApi.GetFile(fileId))
                    if (f.local?.isDownloadingCompleted == true && !f.local.path.isNullOrBlank()) {
                        downloadedPath = f.local.path
                        return@repeat
                    }
                }
                if (!downloadedPath.isNullOrBlank()) {
                    val dao = OdinDatabase.getInstance(context).telegramBackupDao()
                    val matching = dao.getCompletedItems().firstOrNull { it.thumbnailFileId == fileId || it.telegramFileId == fileId }
                    if (matching != null && matching.thumbnailPath != downloadedPath) {
                        dao.updateThumbnailPath(matching.id, downloadedPath)
                    }
                }
            } catch (e: Exception) {
                Log.w("TelegramSync", "Thumbnail download request failed for fileId=$fileId: ${e.message}")
            }
        }
    }

    suspend fun ensureThumbnail(context: Context, thumbnailFileId: Int, backupRowId: Long): String? {
        if (thumbnailFileId == 0) return null
        return withContext(Dispatchers.IO) {
            try {
                val existing = TelegramClientHolder.send(context, TdApi.GetFile(thumbnailFileId))
                if (existing.local?.isDownloadingCompleted == true && !existing.local.path.isNullOrBlank()) {
                    val path = existing.local.path
                    if (backupRowId > 0) {
                        OdinDatabase.getInstance(context).telegramBackupDao()
                            .updateThumbnailPath(backupRowId, path)
                    }
                    return@withContext path
                }
                TelegramClientHolder.send(
                    context,
                    TdApi.DownloadFile(thumbnailFileId, 16, 0, 0, true)
                )
                val downloaded = withTimeoutOrNull(30_000L) {
                    var result: TdApi.File? = null
                    repeat(60) {
                        delay(500)
                        val f = TelegramClientHolder.send(context, TdApi.GetFile(thumbnailFileId))
                        if (f.local?.isDownloadingCompleted == true && !f.local.path.isNullOrBlank()) {
                            result = f
                            return@withTimeoutOrNull f
                        }
                    }
                    result
                }
                val path = downloaded?.local?.path
                if (!path.isNullOrBlank() && backupRowId > 0) {
                    OdinDatabase.getInstance(context).telegramBackupDao()
                        .updateThumbnailPath(backupRowId, path)
                }
                path
            } catch (e: Exception) {
                Log.w("TelegramSync", "ensureThumbnail failed: ${e.message}")
                null
            }
        }
    }
}
