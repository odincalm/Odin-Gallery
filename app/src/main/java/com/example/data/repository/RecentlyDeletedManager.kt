package com.example.data.repository

import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.data.local.DeletedMediaDao
import com.example.data.local.DeletedMediaEntity
import com.example.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class RecentlyDeletedManager(
    private val context: Context,
    private val deletedMediaDao: DeletedMediaDao,
    private val scanner: MediaStoreScanner
) {
    private val restoreMutex = Mutex()

    private val trashDirectory: File
        get() = File(context.filesDir, "recently_deleted").apply {
            if (!exists()) mkdirs()
        }

    suspend fun moveToRecentlyDeleted(
        items: List<MediaItem>,
        intentSenderRequester: suspend (IntentSender) -> Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        if (items.isEmpty()) return@withContext true

        // 1. Copy bytes into private trash directory first (before deleting from MediaStore)
        val backedUpItems = mutableListOf<Pair<MediaItem, File>>()
        for (item in items) {
            try {
                val extension = item.displayName.substringAfterLast('.', if (item.isVideo) "mp4" else "jpg")
                val trashFileName = "trash_${item.id}_${System.currentTimeMillis()}.$extension"
                val trashFile = File(trashDirectory, trashFileName)

                var copied = false
                try {
                    context.contentResolver.openInputStream(item.uri)?.use { inputStream ->
                        FileOutputStream(trashFile).use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    copied = true
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                if (copied && trashFile.exists() && trashFile.length() > 0) {
                    backedUpItems.add(Pair(item, trashFile))
                } else {
                    if (trashFile.exists()) trashFile.delete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (backedUpItems.isEmpty()) return@withContext false

        val urisToDelete = backedUpItems.map { it.first.uri }
        val failedUris = mutableListOf<Uri>()

        for (uri in urisToDelete) {
            try {
                val count = context.contentResolver.delete(uri, null, null)
                if (count <= 0) {
                    failedUris.add(uri)
                }
            } catch (e: SecurityException) {
                failedUris.add(uri)
            } catch (e: Exception) {
                e.printStackTrace()
                failedUris.add(uri)
            }
        }

        if (failedUris.isNotEmpty()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    val pendingIntent = MediaStore.createDeleteRequest(context.contentResolver, failedUris)
                    val userConfirmed = intentSenderRequester(pendingIntent.intentSender)
                    if (!userConfirmed) {
                        // User cancelled: clean up backup files and abort
                        for ((_, trashFile) in backedUpItems) {
                            if (trashFile.exists()) trashFile.delete()
                        }
                        return@withContext false
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    for ((_, trashFile) in backedUpItems) {
                        if (trashFile.exists()) trashFile.delete()
                    }
                    return@withContext false
                }
            } else {
                for ((_, trashFile) in backedUpItems) {
                    if (trashFile.exists()) trashFile.delete()
                }
                return@withContext false
            }
        }

        // 2. Prepare metadata with TRASHED state and insert into database
        val entitiesToInsert = mutableListOf<DeletedMediaEntity>()
        for ((item, trashFile) in backedUpItems) {
            val entity = DeletedMediaEntity(
                originalMediaId = item.id,
                originalUriString = item.uri.toString(),
                originalPath = item.relativePath,
                displayName = item.displayName,
                mimeType = item.mimeType,
                size = if (trashFile.exists()) trashFile.length() else item.size,
                duration = item.duration,
                width = item.width,
                height = item.height,
                dateAdded = item.dateAdded,
                relativePath = item.relativePath,
                deletedTimestamp = System.currentTimeMillis(),
                trashFilePath = trashFile.absolutePath,
                status = "TRASHED"
            )
            entitiesToInsert.add(entity)
        }

        if (entitiesToInsert.isNotEmpty()) {
            deletedMediaDao.insertDeletedBatch(entitiesToInsert)
        }
        true
    }

    suspend fun restoreItems(entities: List<DeletedMediaEntity>): Int = withContext(Dispatchers.IO) {
        restoreMutex.withLock {
            var restoredCount = 0

            for (entity in entities) {
                try {
                    // Verify item is still currently in TRASHED state in database (idempotency check)
                    val freshEntity = deletedMediaDao.getDeletedById(entity.id)
                    if (freshEntity == null || freshEntity.status != "TRASHED") {
                        continue
                    }

                    val isVideo = entity.mimeType.startsWith("video", ignoreCase = true)
                    val trashFile = File(entity.trashFilePath)

                    // Step 1: Check whether the original media still exists in MediaStore
                    val originalStillInMediaStore = scanner.checkMediaExistsInMediaStore(entity.originalMediaId, isVideo)

                    if (originalStillInMediaStore) {
                        // CRITICAL: Original file is already physically in MediaStore!
                        // DO NOT create another physical copy. Reconcile by clearing trash record.
                        if (trashFile.exists()) {
                            trashFile.delete()
                        }
                        deletedMediaDao.deleteById(entity.id)
                        restoredCount++
                        continue
                    }

                    // Step 2: Check whether media with same display name already exists in destination
                    val alreadyExisting = scanner.findExistingMediaByPathOrName(entity.displayName, entity.relativePath, isVideo)
                    if (alreadyExisting != null) {
                        // Media already exists in destination! Reconcile without duplicating.
                        if (trashFile.exists()) {
                            trashFile.delete()
                        }
                        deletedMediaDao.deleteById(entity.id)
                        restoredCount++
                        continue
                    }

                    // Step 3: Media is not in MediaStore. Restore from the preserved backup.
                    if (!trashFile.exists()) {
                        deletedMediaDao.deleteById(entity.id)
                        continue
                    }

                    val targetCollection = if (isVideo) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                        } else {
                            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                        }
                    } else {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                        } else {
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                        }
                    }

                    val defaultDir = if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
                    val relDir = entity.relativePath ?: defaultDir

                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, entity.displayName)
                        put(MediaStore.MediaColumns.MIME_TYPE, entity.mimeType)
                        put(MediaStore.MediaColumns.DATE_ADDED, entity.dateAdded)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            put(MediaStore.MediaColumns.RELATIVE_PATH, relDir)
                            put(MediaStore.MediaColumns.IS_PENDING, 1)
                        }
                    }

                    val newUri = context.contentResolver.insert(targetCollection, contentValues)
                    if (newUri != null) {
                        context.contentResolver.openOutputStream(newUri)?.use { outputStream ->
                            FileInputStream(trashFile).use { inputStream ->
                                inputStream.copyTo(outputStream)
                            }
                        }

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            contentValues.clear()
                            contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                            context.contentResolver.update(newUri, contentValues, null, null)
                        }

                        // Remove backup and delete database entry
                        trashFile.delete()
                        deletedMediaDao.deleteById(entity.id)
                        restoredCount++
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            restoredCount
        }
    }

    suspend fun permanentlyDeleteItems(
        entities: List<DeletedMediaEntity>,
        intentSenderRequester: suspend (IntentSender) -> Boolean
    ): Int = withContext(Dispatchers.IO) {
        if (entities.isEmpty()) return@withContext 0

        // 1. Identify which items still exist in MediaStore
        val itemsStillInMediaStore = mutableListOf<Pair<DeletedMediaEntity, Uri>>()
        for (entity in entities) {
            val isVideo = entity.mimeType.startsWith("video", ignoreCase = true)
            val exists = scanner.checkMediaExistsInMediaStore(entity.originalMediaId, isVideo)
            val uri = Uri.parse(entity.originalUriString)
            if (exists) {
                itemsStillInMediaStore.add(Pair(entity, uri))
            }
        }

        val urisToDelete = itemsStillInMediaStore.map { it.second }
        if (urisToDelete.isNotEmpty()) {
            val failedUris = mutableListOf<Uri>()
            for (uri in urisToDelete) {
                try {
                    val count = context.contentResolver.delete(uri, null, null)
                    if (count <= 0) {
                        failedUris.add(uri)
                    }
                } catch (e: SecurityException) {
                    failedUris.add(uri)
                } catch (e: Exception) {
                    e.printStackTrace()
                    failedUris.add(uri)
                }
            }

            if (failedUris.isNotEmpty()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    try {
                        val pendingIntent = MediaStore.createDeleteRequest(context.contentResolver, failedUris)
                        val userConfirmed = intentSenderRequester(pendingIntent.intentSender)
                        if (!userConfirmed) {
                            // User cancelled permanent deletion request
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        // 2. Verify deletion and remove database records & trash backup files for successfully deleted items
        var deletedCount = 0
        for (entity in entities) {
            val isVideo = entity.mimeType.startsWith("video", ignoreCase = true)
            val stillExists = scanner.checkMediaExistsInMediaStore(entity.originalMediaId, isVideo)

            if (!stillExists) {
                // MediaStore item is successfully gone! Now safe to delete backup and DB record.
                try {
                    val trashFile = File(entity.trashFilePath)
                    if (trashFile.exists()) {
                        trashFile.delete()
                    }
                    deletedMediaDao.deleteById(entity.id)
                    deletedCount++
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else {
                // Item still exists in MediaStore (deletion failed or user cancelled).
                // Do NOT remove Room record!
            }
        }
        deletedCount
    }

    suspend fun emptyRecentlyDeleted(intentSenderRequester: suspend (IntentSender) -> Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val all = deletedMediaDao.getAllDeletedSync()
            permanentlyDeleteItems(all, intentSenderRequester)
            trashDirectory.listFiles()?.forEach { if (!it.exists() || it.length() == 0L || deletedMediaDao.getAllDeletedSync().isEmpty()) it.delete() }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
