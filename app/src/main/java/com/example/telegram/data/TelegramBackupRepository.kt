package com.example.telegram.data

import android.content.Context
import com.example.data.local.OdinDatabase
import com.example.data.local.TelegramBackupItem
import com.example.data.repository.MediaRepository
import com.example.data.repository.MediaStoreScanner
import com.example.model.MediaItem
import com.example.telegram.client.TelegramSavedMessagesHelper
import com.example.telegram.model.AuditReport
import com.example.telegram.model.BackupStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class TelegramBackupRepository(private val context: Context) {
    private val database = OdinDatabase.getInstance(context)
    val dao = database.telegramBackupDao()
    val preferences = TelegramBackupPreferences(context)

    fun getBackupStats(): Flow<BackupStats> {
        return combine(
            dao.getPendingCountFlow(),
            dao.getFailedCountFlow(),
            dao.getCompletedCountFlow()
        ) { pending, failed, completed ->
            BackupStats(
                pendingCount = pending,
                failedCount = failed,
                completedCount = completed
            )
        }
    }

    fun getAllCompletedFlow(): Flow<List<TelegramBackupItem>> = dao.getAllCompletedFlow()

    suspend fun auditPipeline(): AuditReport = withContext(Dispatchers.IO) {
        val scanner = MediaStoreScanner(context)
        val localItems = try {
            scanner.queryAllMedia()
        } catch (e: Exception) {
            emptyList()
        }

        val localDiscovered = localItems.size
        val uniqueLocalIdentities = localItems.map { it.id }.toSet().size

        val pending = dao.getPendingItems().size
        val completed = dao.getCompletedCount()
        val failed = dao.getFailedItems().size

        val historyAudit = try {
            TelegramSavedMessagesHelper.auditSavedMessagesHistory(context)
        } catch (e: Exception) {
            TelegramSavedMessagesHelper.HistoryAuditResult(
                pagesFetched = 0,
                messagesExamined = 0,
                oldestMessageId = 0L,
                newestMessageId = 0L,
                photosDiscovered = 0,
                videosDiscovered = 0,
                documentsDiscovered = 0,
                odinBackupMessagesFound = 0,
                validMetadataParsed = 0,
                validRemoteMappings = 0,
                duplicateRemoteMessages = 0,
                skippedSummary = emptyMap(),
                discoveredItems = emptyList()
            )
        }

        val mediaRepo = MediaRepository(context)
        val galleryVisible = try {
            mediaRepo.activeMediaFlow.first().size
        } catch (e: Exception) {
            0
        } finally {
            mediaRepo.cleanup()
        }

        AuditReport(
            localDiscoveredCount = localDiscovered,
            uniqueLocalIdentitiesCount = uniqueLocalIdentities,
            pendingQueueCount = pending,
            completedQueueCount = completed,
            failedQueueCount = failed,
            pagesFetched = historyAudit.pagesFetched,
            messagesExamined = historyAudit.messagesExamined,
            oldestMessageId = historyAudit.oldestMessageId,
            newestMessageId = historyAudit.newestMessageId,
            photosDiscovered = historyAudit.photosDiscovered,
            videosDiscovered = historyAudit.videosDiscovered,
            documentsDiscovered = historyAudit.documentsDiscovered,
            totalMediaDiscovered = historyAudit.discoveredItems.size,
            odinBackupMessagesFound = historyAudit.odinBackupMessagesFound,
            validMetadataParsed = historyAudit.validMetadataParsed,
            validRemoteMappings = historyAudit.validRemoteMappings,
            galleryVisibleCount = galleryVisible,
            duplicateRemoteMessages = historyAudit.duplicateRemoteMessages,
            skippedSummary = historyAudit.skippedSummary
        )
    }

    suspend fun enqueueMediaItems(items: List<MediaItem>): Int = withContext(Dispatchers.IO) {
        val backupPhotos = preferences.backupPhotos.value
        val backupVideos = preferences.backupVideos.value

        val eligible = items.filter { item ->
            if (item.isVideo) backupVideos else backupPhotos
        }

        var enqueuedCount = 0
        for (item in eligible) {
            // Fast check: MediaStore ID match
            val existing = dao.getItemByMediaId(item.id)
            if (existing != null && (existing.status == "COMPLETED" || existing.status == "PENDING" || existing.status == "UPLOADING")) {
                continue
            }

            // Content identity check via SHA-256
            val hash = TelegramFileUtil.computeSha256(context, item.uri)
            val finalHash = hash.ifBlank { "hash_${item.id}_${item.size}_${item.dateModified}" }

            val existingByHash = dao.getItemByHash(finalHash)
            if (existingByHash != null) {
                if (existingByHash.status == "PENDING" || existingByHash.status == "UPLOADING") {
                    continue
                }
                if (existingByHash.status == "COMPLETED") {
                    // Already backed up under identical hash! Mark completed immediately without re-uploading
                    dao.insert(
                        TelegramBackupItem(
                            localMediaId = item.id,
                            uriString = item.uri.toString(),
                            filePath = null,
                            fileName = item.displayName,
                            mediaType = if (item.isVideo) "VIDEO" else "IMAGE",
                            sizeBytes = item.size,
                            fileHash = finalHash,
                            telegramMessageId = existingByHash.telegramMessageId,
                            telegramFileId = existingByHash.telegramFileId,
                            thumbnailFileId = existingByHash.thumbnailFileId,
                            thumbnailPath = existingByHash.thumbnailPath,
                            status = "COMPLETED",
                            dateModified = item.dateModified,
                            width = item.width,
                            height = item.height,
                            duration = item.duration,
                            completedAt = System.currentTimeMillis()
                        )
                    )
                    continue
                }
            }

            val backupItem = TelegramBackupItem(
                localMediaId = item.id,
                uriString = item.uri.toString(),
                filePath = null,
                fileName = item.displayName,
                mediaType = if (item.isVideo) "VIDEO" else "IMAGE",
                sizeBytes = item.size,
                fileHash = finalHash,
                status = "PENDING",
                dateModified = item.dateModified,
                width = item.width,
                height = item.height,
                duration = item.duration
            )
            dao.insert(backupItem)
            enqueuedCount++
        }
        enqueuedCount
    }

    suspend fun retryFailed(): Int = withContext(Dispatchers.IO) {
        dao.resetFailedItemsToPending()
    }

    suspend fun resetInterruptedUploads(): Int = withContext(Dispatchers.IO) {
        dao.resetUploadingToPending()
    }

    suspend fun clearLocalBackupRecords() = withContext(Dispatchers.IO) {
        dao.clearAll()
    }

    suspend fun getPendingItems(): List<TelegramBackupItem> = withContext(Dispatchers.IO) {
        dao.getPendingItems()
    }

    fun syncWithCloud(forceFullRescan: Boolean = false, onComplete: ((Int) -> Unit)? = null) {
        TelegramSyncManager.syncWithCloud(context, forceFullRescan, onComplete)
    }
}
