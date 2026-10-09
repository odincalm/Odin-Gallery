package com.example.telegram.restore

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import com.example.data.local.OdinDatabase
import com.example.data.local.TelegramBackupItem
import com.example.telegram.client.TelegramClientHolder
import com.example.telegram.client.TelegramSavedMessagesHelper
import com.example.telegram.model.DiscoveredCloudItem
import io.github.tdlibandroid.ktx.trackFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
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
            val client = TelegramClientHolder.getClient(context)

            // Check if TDLib already has the file downloaded locally
            val existingFile = try {
                TelegramClientHolder.send(context, TdApi.GetFile(item.telegramFileId))
            } catch (e: Exception) {
                null
            }

            val sourcePath = if (existingFile?.local?.isDownloadingCompleted == true && !existingFile.local.path.isNullOrBlank()) {
                existingFile.local.path
            } else {
                // Start downloading the original file in TDLib
                TelegramClientHolder.send(
                    context,
                    TdApi.DownloadFile(item.telegramFileId, 32, 0L, 0L, false)
                )

                // Track download until completed with a 90-second timeout
                val downloadedFile = withTimeoutOrNull(90_000L) {
                    client.trackFile(item.telegramFileId).first { file ->
                        file.local?.isDownloadingCompleted == true && !file.local.path.isNullOrBlank()
                    }
                } ?: return@withContext Result.failure(IllegalStateException("Download timed out for ${item.metadata.fileName}"))

                downloadedFile.local?.path
                    ?: return@withContext Result.failure(IllegalStateException("Downloaded file path missing"))
            }

            val sourceFile = File(sourcePath)
            if (!sourceFile.exists()) {
                return@withContext Result.failure(IllegalStateException("Downloaded file does not exist on disk"))
            }

            // Target destination directory: Pictures/OdinGallery or Movies/OdinGallery
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

            // Copy file content
            FileInputStream(sourceFile).use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }

            // Scan file with MediaScanner so Android Gallery indexes it immediately
            MediaScannerConnection.scanFile(
                context,
                arrayOf(destFile.absolutePath),
                arrayOf(if (isVideo) "video/*" else "image/*"),
                null
            )

            // Save record in local database
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
                    telegramFileId = item.telegramFileId,
                    status = "COMPLETED",
                    completedAt = System.currentTimeMillis()
                )
            )

            Result.success(destFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun restoreMediaItem(context: Context, mediaItem: com.example.model.MediaItem): Result<File> = withContext(Dispatchers.IO) {
        val fileId = mediaItem.cloudFileId
            ?: return@withContext Result.failure(IllegalStateException("Item has no Telegram cloud file ID"))
        val msgId = mediaItem.cloudMessageId ?: 0L

        val item = DiscoveredCloudItem(
            messageId = msgId,
            telegramFileId = fileId,
            metadata = com.example.telegram.model.CloudBackupMetadata(
                fileName = mediaItem.displayName,
                mediaType = if (mediaItem.isVideo) "VIDEO" else "IMAGE",
                sizeBytes = mediaItem.size,
                fileHash = mediaItem.cloudFileHash ?: "",
                timestamp = mediaItem.dateModified
            ),
            isAlreadyLocal = false
        )
        restoreItem(context, item)
    }
}
