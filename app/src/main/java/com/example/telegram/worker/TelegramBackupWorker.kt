package com.example.telegram.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.local.OdinDatabase
import com.example.telegram.client.TelegramAuthManager
import com.example.telegram.data.TelegramBackupPreferences
import com.example.telegram.data.TelegramNetworkUtil
import com.example.telegram.data.TelegramUploader
import com.example.telegram.model.NetworkPreference
import com.example.telegram.model.TelegramAuthState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class TelegramBackupWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = TelegramBackupPreferences(context)
        if (!prefs.isBackupEnabled.value) {
            return@withContext Result.success()
        }

        // 1. Verify network state respects preference
        if (!TelegramNetworkUtil.isUploadAllowed(context, prefs.networkPreference.value)) {
            return@withContext Result.retry()
        }

        // 2. Ensure Telegram client is initialized and wait up to 15s for authentication readiness
        TelegramAuthManager.init(context)
        val isReady = withTimeoutOrNull(15_000L) {
            TelegramAuthManager.authState.firstOrNull { it is TelegramAuthState.Ready || it is TelegramAuthState.Error }
        }

        if (isReady !is TelegramAuthState.Ready) {
            // Not yet logged in or temporary timeout
            return@withContext Result.retry()
        }

        val database = OdinDatabase.getInstance(context)
        val dao = database.telegramBackupDao()

        // 3. Reset any previously interrupted uploading items to pending
        dao.resetUploadingToPending()

        val pendingItems = dao.getPendingItems()
        if (pendingItems.isEmpty()) {
            return@withContext Result.success()
        }

        var hasFailures = false

        for (item in pendingItems) {
            if (isStopped) {
                return@withContext Result.retry()
            }

            // Check network before each file
            if (!TelegramNetworkUtil.isUploadAllowed(context, prefs.networkPreference.value)) {
                return@withContext Result.retry()
            }

            // Double check if this content hash is already confirmed in cloud (duplicate prevention)
            val alreadyCompleted = dao.getCompletedItemByHash(item.fileHash)
            if (alreadyCompleted != null && alreadyCompleted.telegramMessageId > 0) {
                dao.update(
                    item.copy(
                        status = "COMPLETED",
                        telegramMessageId = alreadyCompleted.telegramMessageId,
                        telegramFileId = alreadyCompleted.telegramFileId,
                        thumbnailFileId = alreadyCompleted.thumbnailFileId,
                        thumbnailPath = alreadyCompleted.thumbnailPath,
                        completedAt = System.currentTimeMillis()
                    )
                )
                continue
            }

            dao.updateStatus(item.id, "UPLOADING")

            val uri = item.uriString ?: continue
            val isVideo = item.mediaType == "VIDEO"
            val result = TelegramUploader.uploadMedia(
                context = context,
                uriString = uri,
                fileName = item.fileName,
                isVideo = isVideo,
                sizeBytes = item.sizeBytes,
                fileHash = item.fileHash
            )

            result.fold(
                onSuccess = { messageId ->
                    val updated = item.copy(
                        telegramMessageId = messageId,
                        status = "COMPLETED",
                        errorMessage = null,
                        completedAt = System.currentTimeMillis()
                    )
                    dao.update(updated)
                    prefs.updateLastBackupTime()
                },
                onFailure = { error ->
                    val newRetryCount = item.retryCount + 1
                    val newStatus = if (newRetryCount >= 3) "FAILED" else "PENDING"
                    val updated = item.copy(
                        status = newStatus,
                        retryCount = newRetryCount,
                        errorMessage = error.message ?: "Upload failed"
                    )
                    dao.update(updated)
                    hasFailures = true
                }
            )
        }

        if (hasFailures) {
            Result.retry()
        } else {
            Result.success()
        }
    }

    companion object {
        const val WORK_NAME = "odin_telegram_cloud_backup_work"

        fun enqueueBackup(context: Context, replaceExisting: Boolean = false) {
            val prefs = TelegramBackupPreferences(context)
            if (!prefs.isBackupEnabled.value) return

            val netPref = prefs.networkPreference.value

            val networkType = when (netPref) {
                NetworkPreference.WIFI_ONLY -> NetworkType.UNMETERED
                NetworkPreference.MOBILE_DATA -> NetworkType.CONNECTED
                NetworkPreference.WIFI_AND_MOBILE -> NetworkType.CONNECTED
            }

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(networkType)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<TelegramBackupWorker>()
                .setConstraints(constraints)
                .build()

            val policy = if (replaceExisting) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                policy,
                workRequest
            )
        }

        fun cancelBackup(context: Context) {
            try {
                WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
}
