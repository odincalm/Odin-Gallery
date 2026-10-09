package com.example.telegram.data

import android.content.Context
import com.example.data.local.OdinDatabase
import com.example.data.local.TelegramBackupItem
import com.example.model.MediaItem
import com.example.telegram.model.BackupStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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
            if (hash.isNotBlank()) {
                val completedDuplicate = dao.getCompletedItemByHash(hash)
                if (completedDuplicate != null) {
                    // Already backed up under identical hash! Mark completed immediately without re-uploading
                    dao.insert(
                        TelegramBackupItem(
                            localMediaId = item.id,
                            uriString = item.uri.toString(),
                            filePath = null,
                            fileName = item.displayName,
                            mediaType = if (item.isVideo) "VIDEO" else "IMAGE",
                            sizeBytes = item.size,
                            fileHash = hash,
                            telegramMessageId = completedDuplicate.telegramMessageId,
                            telegramFileId = completedDuplicate.telegramFileId,
                            thumbnailFileId = completedDuplicate.thumbnailFileId,
                            thumbnailPath = completedDuplicate.thumbnailPath,
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
                fileHash = hash.ifBlank { "hash_${item.id}_${item.size}_${item.dateModified}" },
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

    fun syncWithCloud(onComplete: ((Int) -> Unit)? = null) {
        TelegramSyncManager.syncWithCloud(context, onComplete)
    }
}
