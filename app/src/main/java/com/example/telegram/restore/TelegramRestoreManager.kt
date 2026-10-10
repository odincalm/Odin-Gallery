package com.example.telegram.restore

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import com.example.data.local.OdinDatabase
import com.example.data.local.TelegramBackupItem
import com.example.model.MediaItem
import com.example.telegram.client.TelegramClientHolder
import com.example.telegram.client.TelegramSavedMessagesHelper
import com.example.telegram.data.TelegramSyncManager
import com.example.telegram.model.CloudBackupMetadata
import com.example.telegram.model.DiscoveredCloudItem
import io.github.tdlibandroid.ktx.trackFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.drinkless.tdlib.TdApi
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object TelegramRestoreManager {

    suspend fun discoverEligibleBackups(context: Context): List<DiscoveredCloudItem> = withContext(Dispatchers.IO) {
        val discovered = TelegramSavedMessagesHelper.discoverBackups(context)
        val database = OdinDatabase.getInstance(context)
        val dao = database.telegramBackupDao()

        discovered.map { item ->
            val localItem = dao.getCompletedItemByHash(item.metadata.fileHash)
            item.copy(isAlreadyLocal = localItem != null)
        }
    }

    suspend fun restoreItem(context: Context, item: DiscoveredCloudItem): Result<File> = withContext(Dispatchers.IO) {
        try {
            var fileId = item.telegramFileId
            val msgId = item.messageId

            if ((fileId == 0) && msgId > 0L) {
                val resolved = TelegramSyncManager.resolveRemoteMessage(context, msgId)
                if (resolved != null) {
                    fileId = resolved.first
                }
            }

            if (fileId == 0) {
                return@withContext Result.failure(IllegalStateException("Invalid Telegram file ID for ${item.metadata.fileName}"))
            }

            val client = try {
                TelegramClientHolder.getClient(context)
            } catch (e: Exception) {
                null
            }

            var existingFile = try {
                TelegramClientHolder.send(context, TdApi.GetFile(fileId))
            } catch (e: Exception) {
                null
            }

            if ((existingFile == null || existingFile.id == 0) && msgId > 0L) {
                val resolved = TelegramSyncManager.resolveRemoteMessage(context, msgId)
                if (resolved != null) {
                    fileId = resolved.first
                    existingFile = try {
                        TelegramClientHolder.send(context, TdApi.GetFile(fileId))
                    } catch (e: Exception) {
                        null
                    }
                }
            }

            val sourcePath = if (existingFile?.local?.isDownloadingCompleted == true && !existingFile.local.path.isNullOrBlank()) {
                existingFile.local.path
            } else {
                TelegramClientHolder.send(
                    context,
                    TdApi.DownloadFile(fileId, 32, 0L, 0L, false)
                )

                var downloadedPath: String? = null

                if (client != null) {
                    try {
                        val flowResult = withTimeoutOrNull(90_000L) {
                            client.trackFile(fileId).firstOrNull { file ->
                                file.local?.isDownloadingCompleted == true && !file.local.path.isNullOrBlank()
                            }
                        }
                        downloadedPath = flowResult?.local?.path
                    } catch (e: Exception) {
                        // Fallback to polling
                    }
                }

                if (downloadedPath.isNullOrBlank()) {
                    downloadedPath = withTimeoutOrNull(90_000L) {
                        var path: String? = null
                        repeat(180) {
                            delay(500)
                            val f = try {
                                TelegramClientHolder.send(context, TdApi.GetFile(fileId))
                            } catch (e: Exception) {
                                null
                            }
                            if (f?.local?.isDownloadingCompleted == true && !f.local.path.isNullOrBlank()) {
                                path = f.local.path
                                return@withTimeoutOrNull path
                            }
                        }
                        path
                    }
                }

                downloadedPath ?: return@withContext Result.failure(IllegalStateException("Failed to download media file from Telegram"))
            }

            val sourceFile = File(sourcePath)
            if (!sourceFile.exists()) {
                return@withContext Result.failure(IllegalStateException("Downloaded file does not exist on disk"))
            }

            val isVideo = item.metadata.mediaType == "VIDEO"
            val targetFolder = if (isVideo) {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            } else {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            }
            val odinDir = File(targetFolder, "OdinGallery").apply { if (!exists()) mkdirs() }

            var destFile = File(odinDir, item.metadata.fileName)
            if (destFile.exists()) {
                val nameWithoutExt = item.metadata.fileName.substringBeforeLast(".")
                val ext = item.metadata.fileName.substringAfterLast(".", "")
                destFile = File(odinDir, "${nameWithoutExt}_restored_${System.currentTimeMillis()}.$ext")
            }

            FileInputStream(sourceFile).use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }

            MediaScannerConnection.scanFile(
                context,
                arrayOf(destFile.absolutePath),
                arrayOf(if (isVideo) "video/*" else "image/*"),
                null
            )

            val database = OdinDatabase.getInstance(context)
            val dao = database.telegramBackupDao()
            dao.insert(
                TelegramBackupItem(
                    localMediaId = System.currentTimeMillis(),
                    uriString = destFile.toURI().toString(),
                    filePath = destFile.absolutePath,
                    fileName = item.metadata.fileName,
                    mediaType = item.metadata.mediaType,
                    sizeBytes = item.metadata.sizeBytes,
                    fileHash = item.metadata.fileHash,
                    telegramMessageId = item.messageId,
                    telegramFileId = fileId,
                    status = "COMPLETED",
                    completedAt = System.currentTimeMillis()
                )
            )

            Result.success(destFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun restoreMediaItem(context: Context, mediaItem: MediaItem): Result<File> = withContext(Dispatchers.IO) {
        val msgId = mediaItem.cloudMessageId ?: 0L
        var fileId = mediaItem.cloudFileId ?: 0

        if (fileId == 0 && msgId > 0L) {
            val resolved = TelegramSyncManager.resolveRemoteMessage(context, msgId)
            if (resolved != null) {
                fileId = resolved.first
            }
        }

        if (fileId == 0 && msgId == 0L) {
            return@withContext Result.failure(IllegalStateException("Item has no Telegram cloud file ID or message ID"))
        }

        val item = DiscoveredCloudItem(
            messageId = msgId,
            telegramFileId = fileId,
            metadata = CloudBackupMetadata(
                fileName = mediaItem.displayName,
                mediaType = if (mediaItem.isVideo) "VIDEO" else "IMAGE",
                sizeBytes = mediaItem.size,
                fileHash = mediaItem.cloudFileHash ?: "",
                timestamp = mediaItem.dateModified
            ),
            telegramDate = (mediaItem.dateModified / 1000).toInt(),
            isAlreadyLocal = false
        )
        restoreItem(context, item)
    }
}
