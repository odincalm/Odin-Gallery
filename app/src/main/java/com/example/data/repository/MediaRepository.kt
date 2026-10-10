package com.example.data.repository

import android.content.Context
import android.content.IntentSender
import android.net.Uri
import coil.Coil
import java.io.File
import com.example.data.local.AlbumEntity
import com.example.data.local.AlbumMediaEntity
import com.example.data.local.DeletedMediaEntity
import com.example.data.local.FavoriteEntity
import com.example.data.local.HiddenMediaEntity
import com.example.data.local.OdinDatabase
import com.example.data.local.PlaybackPositionEntity
import com.example.data.local.UserPrefEntity
import com.example.model.AlbumItem
import com.example.model.MediaItem
import com.example.model.SystemAlbumType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MediaRepository(private val context: Context) {

    private val db = OdinDatabase.getInstance(context)
    val favoriteDao = db.favoriteDao()
    val albumDao = db.albumDao()
    val deletedMediaDao = db.deletedMediaDao()
    val hiddenMediaDao = db.hiddenMediaDao()
    val playbackDao = db.playbackDao()
    val userPrefDao = db.userPrefDao()

    suspend fun setPreference(key: String, value: String) = withContext(Dispatchers.IO) {
        userPrefDao.setPreference(UserPrefEntity(key, value))
    }

    suspend fun getPreferenceSync(key: String): String? = withContext(Dispatchers.IO) {
        userPrefDao.getPreferenceSync(key)
    }

    val scanner = MediaStoreScanner(context)
    val recentlyDeletedManager = RecentlyDeletedManager(context, deletedMediaDao, scanner)

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Raw scanned media from device MediaStore (MediaStore is Source of Truth)
    private val _rawMediaFlow = MutableStateFlow<List<MediaItem>>(emptyList())
    val rawMediaFlow: StateFlow<List<MediaItem>> = _rawMediaFlow.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    // Real-time ContentObserver for MediaStore changes
    private val mediaObserver = MediaStoreObserver(context) {
        scanMedia()
    }

    init {
        mediaObserver.register()
        scanMedia()
    }

    fun cleanup() {
        mediaObserver.unregister()
    }

    fun scanMedia() {
        repositoryScope.launch {
            _isScanning.value = true
            try {
                val scanned = scanner.queryAllMedia()
                _rawMediaFlow.value = scanned
                reconcileStaleMetadata(scanned)

                // Enqueue new media for Telegram cloud backup if enabled
                try {
                    val backupPrefs = com.example.telegram.data.TelegramBackupPreferences(context)
                    if (backupPrefs.isBackupEnabled.value && backupPrefs.backupNewMediaAutomatically.value) {
                        val backupRepo = com.example.telegram.data.TelegramBackupRepository(context)
                        val enqueued = backupRepo.enqueueMediaItems(scanned)
                        if (enqueued > 0) {
                            com.example.telegram.worker.TelegramBackupWorker.enqueueBackup(context)
                        }
                    }
                } catch (ignored: Exception) {}
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isScanning.value = false
            }
        }
    }

    // Reconcile stale database records if files were removed externally
    private suspend fun reconcileStaleMetadata(scannedItems: List<MediaItem>) = withContext(Dispatchers.IO) {
        val validIds = scannedItems.map { it.id }.toSet()

        // 1. Favorites reconciliation
        val favorites = favoriteDao.getAllFavoritesSync()
        val orphanFavorites = favorites.filterNot { it.originalMediaId in validIds }.map { it.originalMediaId }
        if (orphanFavorites.isNotEmpty()) {
            favoriteDao.deleteFavoritesByMediaIds(orphanFavorites)
        }

        // 2. Hidden media reconciliation
        val hidden = hiddenMediaDao.getAllHiddenSync()
        val orphanHidden = hidden.filterNot { it.originalMediaId in validIds }.map { it.originalMediaId }
        if (orphanHidden.isNotEmpty()) {
            hiddenMediaDao.deleteHiddenByMediaIds(orphanHidden)
        }
    }

    // Filtered media: raw media MINUS recently deleted and MINUS hidden media, PLUS cloud-only items
    val activeMediaFlow: Flow<List<MediaItem>> = combine(
        _rawMediaFlow,
        favoriteDao.getAllFavorites(),
        deletedMediaDao.getAllDeleted(),
        hiddenMediaDao.getAllHidden(),
        db.telegramBackupDao().getAllCompletedFlow()
    ) { rawItems, favorites, deletedItems, hiddenItems, completedBackups ->
        val favoriteIds = favorites.map { it.originalMediaId }.toSet()
        val deletedIds = deletedItems.map { it.originalMediaId }.toSet()
        val hiddenIds = hiddenItems.map { it.originalMediaId }.toSet()

        val completedByMediaId = completedBackups.filter { it.localMediaId > 0 }.associateBy { it.localMediaId }
        val completedByHash = completedBackups.filter { it.fileHash.isNotBlank() }.associateBy { it.fileHash }

        val localScannedIds = mutableSetOf<Long>()
        val resultList = mutableListOf<MediaItem>()

        // 1. Process local scanned media
        for (item in rawItems) {
            if (item.id in deletedIds || item.id in hiddenIds) continue
            localScannedIds.add(item.id)

            val backupInfo = completedByMediaId[item.id] ?: (if (item.cloudFileHash != null) completedByHash[item.cloudFileHash] else null)
            val isFav = item.id in favoriteIds

            val updatedItem = if (backupInfo != null) {
                item.copy(
                    isFavorite = isFav,
                    isHidden = false,
                    isCloudSynced = true,
                    cloudMessageId = backupInfo.telegramMessageId,
                    cloudFileId = backupInfo.telegramFileId,
                    cloudThumbnailPath = backupInfo.thumbnailPath ?: item.cloudThumbnailPath,
                    cloudFileHash = backupInfo.fileHash
                )
            } else {
                item.copy(isFavorite = isFav, isHidden = false)
            }
            resultList.add(updatedItem)
        }

        // 2. Add cloud-only media (backed up from other devices or missing locally)
        // Never fabricate a content:// URI for missing files — only use a real local path.
        val deletedHashesOrNames = deletedItems.map { "${it.displayName}_${it.size}" }.toSet()
        val seenCloudHashes = mutableSetOf<String>()

        for (backup in completedBackups) {
            // Do not expose cloud items if they were locally deleted or hidden by the user
            if (backup.localMediaId > 0 && (backup.localMediaId in deletedIds || backup.localMediaId in hiddenIds)) continue
            val deletedKey = "${backup.fileName}_${backup.sizeBytes}"
            if (deletedKey in deletedHashesOrNames) continue

            // Skip blank-hash items for hash-based dedup only; never treat blank as a duplicate key
            if (backup.fileHash.isNotBlank()) {
                if (backup.fileHash in seenCloudHashes) continue
                seenCloudHashes.add(backup.fileHash)
            }

            val isLocalPresent = backup.localMediaId > 0 && backup.localMediaId in localScannedIds
            val isLocalHashPresent = backup.fileHash.isNotBlank() &&
                resultList.any { it.cloudFileHash == backup.fileHash && !it.isCloudOnly }

            if (!isLocalPresent && !isLocalHashPresent) {
                val cloudOnlyId = -100_000L - backup.id
                val mediaFile = backup.filePath?.let { File(it) }?.takeIf { it.exists() && it.length() > 0 }
                val thumbnailFile = backup.thumbnailPath?.let { File(it) }?.takeIf { it.exists() && it.length() > 0 }

                // Prefer real original, then valid thumbnail. Empty URI means "not materialized yet".
                val usableUri = when {
                    mediaFile != null -> Uri.fromFile(mediaFile)
                    thumbnailFile != null -> Uri.fromFile(thumbnailFile)
                    else -> Uri.EMPTY
                }

                // Normalize timestamp: backup metadata uses millis; MediaStore uses seconds
                val tsMillis = backup.dateModified
                val dateAddedSeconds = if (tsMillis > 10_000_000_000L) tsMillis / 1000L else tsMillis
                val dateModifiedSeconds = dateAddedSeconds

                val isFav = cloudOnlyId in favoriteIds

                val cloudItem = MediaItem(
                    id = cloudOnlyId,
                    uri = usableUri,
                    displayName = backup.fileName,
                    mimeType = if (backup.mediaType == "VIDEO") "video/mp4" else "image/jpeg",
                    dateAdded = dateAddedSeconds,
                    dateModified = dateModifiedSeconds,
                    size = backup.sizeBytes,
                    width = backup.width,
                    height = backup.height,
                    duration = backup.duration,
                    isVideo = backup.mediaType == "VIDEO",
                    isFavorite = isFav,
                    isHidden = false,
                    isCloudOnly = true,
                    isCloudSynced = true,
                    cloudMessageId = backup.telegramMessageId,
                    cloudFileId = backup.telegramFileId,
                    cloudThumbnailPath = backup.thumbnailPath,
                    cloudFileHash = backup.fileHash
                )
                resultList.add(cloudItem)
            }
        }

        // Sort by normalized seconds-based dateModified descending for consistent ordering
        resultList.sortedByDescending { item ->
            if (item.dateModified > 10_000_000_000L) item.dateModified / 1000L else item.dateModified
        }
    }.flowOn(Dispatchers.Default)

    // Hidden media flow (accessible strictly in the Hidden vault)
    val hiddenMediaFlow: Flow<List<MediaItem>> = combine(
        _rawMediaFlow,
        deletedMediaDao.getAllDeleted(),
        hiddenMediaDao.getAllHidden()
    ) { rawItems, deletedItems, hiddenItems ->
        val deletedIds = deletedItems.map { it.originalMediaId }.toSet()
        val hiddenIds = hiddenItems.map { it.originalMediaId }.toSet()

        rawItems
            .filter { it.id in hiddenIds && it.id !in deletedIds }
            .map { it.copy(isHidden = true) }
    }.flowOn(Dispatchers.Default)

    // Recently deleted flow
    val deletedMediaFlow: Flow<List<DeletedMediaEntity>> = deletedMediaDao.getAllDeleted()

    suspend fun toggleFavorite(mediaItem: MediaItem) = withContext(Dispatchers.IO) {
        val newFavState = !mediaItem.isFavorite
        if (newFavState) {
            favoriteDao.insertFavorite(
                FavoriteEntity(
                    originalMediaId = mediaItem.id,
                    uriString = mediaItem.uri.toString(),
                    isFavorite = true
                )
            )
        } else {
            favoriteDao.deleteFavoriteByMediaId(mediaItem.id)
        }
    }

    // Hidden Media Operations
    suspend fun hideMedia(items: List<MediaItem>) = withContext(Dispatchers.IO) {
        val entities = items.map {
            HiddenMediaEntity(
                originalMediaId = it.id,
                uriString = it.uri.toString(),
                hiddenAt = System.currentTimeMillis()
            )
        }
        hiddenMediaDao.insertHiddenBatch(entities)
        clearCoilCache()
    }

    suspend fun unhideMedia(items: List<MediaItem>) = withContext(Dispatchers.IO) {
        val ids = items.map { it.id }
        hiddenMediaDao.deleteHiddenByMediaIds(ids)
        clearCoilCache()
    }

    // Recently Deleted Operations
    /**
     * Moves local media to Recently Deleted (trash). Cloud-only items are removed from the
     * local cloud index only — Telegram Saved Messages are never deleted by this path.
     */
    suspend fun moveToRecentlyDeleted(
        items: List<MediaItem>,
        intentSenderRequester: suspend (IntentSender) -> Boolean
    ): Boolean {
        val localItems = items.filter { !it.isCloudOnly }
        val cloudOnlyItems = items.filter { it.isCloudOnly }

        var localSuccess = true
        if (localItems.isNotEmpty()) {
            localSuccess = recentlyDeletedManager.moveToRecentlyDeleted(localItems, intentSenderRequester)
            if (localSuccess) {
                val ids = localItems.map { it.id }
                favoriteDao.deleteFavoritesByMediaIds(ids)
                albumDao.deleteMediaFromAllAlbumsBatch(ids)
                hiddenMediaDao.deleteHiddenByMediaIds(ids)
            }
        }

        // Cloud-only: remove from local Telegram index and favorites only (local deletion intent)
        if (cloudOnlyItems.isNotEmpty()) {
            val dao = db.telegramBackupDao()
            for (item in cloudOnlyItems) {
                val messageId = item.cloudMessageId
                if (messageId != null && messageId > 0L) {
                    dao.deleteByMessageId(messageId)
                } else {
                    // Fallback: synthetic id maps to backup row as -100_000 - backup.id
                    val backupId = -item.id - 100_000L
                    if (backupId > 0) dao.deleteById(backupId)
                }
                favoriteDao.deleteFavoriteByMediaId(item.id)
                albumDao.deleteMediaFromAllAlbums(item.id)
            }
        }

        if (localSuccess || cloudOnlyItems.isNotEmpty()) {
            clearCoilCache()
            if (localItems.isNotEmpty()) scanMedia()
            return true
        }
        return false
    }

    suspend fun restoreDeletedItems(entities: List<DeletedMediaEntity>): Int {
        val count = recentlyDeletedManager.restoreItems(entities)
        if (count > 0) {
            clearCoilCache()
            scanMedia()
        }
        return count
    }

    suspend fun permanentlyDeleteItems(
        entities: List<DeletedMediaEntity>,
        intentSenderRequester: suspend (IntentSender) -> Boolean
    ): Int {
        val count = recentlyDeletedManager.permanentlyDeleteItems(entities, intentSenderRequester)
        val ids = entities.map { it.originalMediaId }
        favoriteDao.deleteFavoritesByMediaIds(ids)
        albumDao.deleteMediaFromAllAlbumsBatch(ids)
        hiddenMediaDao.deleteHiddenByMediaIds(ids)
        clearCoilCache()
        scanMedia()
        return count
    }

    suspend fun emptyRecentlyDeleted(
        intentSenderRequester: suspend (IntentSender) -> Boolean
    ): Boolean {
        val success = recentlyDeletedManager.emptyRecentlyDeleted(intentSenderRequester)
        if (success) {
            clearCoilCache()
            scanMedia()
        }
        return success
    }

    private fun clearCoilCache() {
        try {
            Coil.imageLoader(context).memoryCache?.clear()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Custom Albums
    suspend fun createCustomAlbum(name: String): Long = withContext(Dispatchers.IO) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return@withContext -1L
        albumDao.insertAlbum(AlbumEntity(name = trimmed))
    }

    suspend fun deleteCustomAlbum(albumId: Long) = withContext(Dispatchers.IO) {
        albumDao.deleteAlbum(albumId)
    }

    suspend fun addMediaToAlbum(albumId: Long, items: List<MediaItem>) = withContext(Dispatchers.IO) {
        val entities = items.map {
            AlbumMediaEntity(
                albumId = albumId,
                mediaId = it.id,
                uriString = it.uri.toString()
            )
        }
        albumDao.insertAlbumMediaBatch(entities)
    }

    suspend fun removeMediaFromAlbum(albumId: Long, mediaId: Long) = withContext(Dispatchers.IO) {
        albumDao.removeAlbumMedia(albumId, mediaId)
    }

    // Automatic Categorization & Summary Albums Flow
    val allAlbumsFlow: Flow<List<AlbumItem>> = combine(
        activeMediaFlow,
        deletedMediaDao.getAllDeleted(),
        hiddenMediaDao.getAllHidden(),
        albumDao.getAllAlbums(),
        albumDao.getAllAlbumMediaCrossRefs()
    ) { activeList, deletedList, hiddenList, customAlbums, allCrossRefs ->
        val list = mutableListOf<AlbumItem>()

        // 1. All Photos (Images)
        val photos = activeList.filter { !it.isVideo }
        list.add(
            AlbumItem(
                id = -1L,
                title = "All Photos",
                count = photos.size,
                coverUri = photos.firstOrNull()?.uri,
                systemType = SystemAlbumType.ALL_PHOTOS
            )
        )

        // 2. Videos (All accessible videos)
        val videos = activeList.filter { it.isVideo }
        list.add(
            AlbumItem(
                id = -2L,
                title = "Videos",
                count = videos.size,
                coverUri = videos.firstOrNull()?.uri,
                systemType = SystemAlbumType.VIDEOS
            )
        )

        // 3. Camera
        val cameraItems = activeList.filter { MediaStoreScanner.isCameraMedia(it) }
        list.add(
            AlbumItem(
                id = -6L,
                title = "Camera",
                count = cameraItems.size,
                coverUri = cameraItems.firstOrNull()?.uri,
                systemType = SystemAlbumType.CAMERA
            )
        )

        // 4. Downloads
        val downloadItems = activeList.filter { MediaStoreScanner.isDownloadMedia(it) }
        list.add(
            AlbumItem(
                id = -5L,
                title = "Downloads",
                count = downloadItems.size,
                coverUri = downloadItems.firstOrNull()?.uri,
                systemType = SystemAlbumType.DOWNLOADS
            )
        )

        // 5. Screenshots
        val screenshotItems = activeList.filter { MediaStoreScanner.isScreenshotMedia(it) }
        list.add(
            AlbumItem(
                id = -4L,
                title = "Screenshots",
                count = screenshotItems.size,
                coverUri = screenshotItems.firstOrNull()?.uri,
                systemType = SystemAlbumType.SCREENSHOTS
            )
        )

        // 6. Screen Recordings
        val screenRecordings = activeList.filter { MediaStoreScanner.isScreenRecordingMedia(it) }
        list.add(
            AlbumItem(
                id = -9L,
                title = "Screen Recordings",
                count = screenRecordings.size,
                coverUri = screenRecordings.firstOrNull()?.uri,
                systemType = SystemAlbumType.SCREEN_RECORDINGS
            )
        )

        // 7. Favorites
        val favorites = activeList.filter { it.isFavorite }
        list.add(
            AlbumItem(
                id = -3L,
                title = "Favorites",
                count = favorites.size,
                coverUri = favorites.firstOrNull()?.uri,
                systemType = SystemAlbumType.FAVORITES
            )
        )

        // 8. Recently Added (past 30 days)
        val thirtyDaysAgo = (System.currentTimeMillis() / 1000L) - (30L * 24 * 3600)
        val recentlyAdded = activeList.filter { it.dateAdded >= thirtyDaysAgo }
        list.add(
            AlbumItem(
                id = -7L,
                title = "Recently Added",
                count = recentlyAdded.size,
                coverUri = recentlyAdded.firstOrNull()?.uri,
                systemType = SystemAlbumType.RECENTLY_ADDED
            )
        )

        // 9. Hidden (Vault)
        list.add(
            AlbumItem(
                id = -10L,
                title = "Hidden",
                count = hiddenList.size,
                coverUri = null,
                systemType = SystemAlbumType.HIDDEN
            )
        )

        // 10. Recently Deleted
        list.add(
            AlbumItem(
                id = -8L,
                title = "Recently Deleted",
                count = deletedList.size,
                coverUri = null,
                systemType = SystemAlbumType.RECENTLY_DELETED
            )
        )

        // 11. Telegram Cloud Backup
        val cloudItems = activeList.filter { it.isCloudSynced || it.isCloudOnly }
        list.add(
            AlbumItem(
                id = -11L,
                title = "Telegram Cloud",
                count = cloudItems.size,
                coverUri = cloudItems.firstOrNull()?.uri,
                systemType = SystemAlbumType.TELEGRAM_CLOUD
            )
        )

        // Custom User Albums
        for (custom in customAlbums) {
            val mediaIds = allCrossRefs.filter { it.albumId == custom.id }.map { it.mediaId }.toSet()
            val matchedItems = activeList.filter { it.id in mediaIds }
            list.add(
                AlbumItem(
                    id = custom.id,
                    title = custom.name,
                    count = matchedItems.size,
                    coverUri = matchedItems.firstOrNull()?.uri,
                    systemType = null
                )
            )
        }

        list
    }.flowOn(Dispatchers.Default)

    fun getMediaForAlbum(album: AlbumItem): Flow<List<MediaItem>> {
        return combine(activeMediaFlow, hiddenMediaFlow, albumDao.getMediaInAlbum(album.id)) { activeList, hiddenList, crossRefs ->
            when (album.systemType) {
                SystemAlbumType.ALL_PHOTOS -> activeList.filter { !it.isVideo }
                SystemAlbumType.ALL_VIDEOS, SystemAlbumType.VIDEOS -> activeList.filter { it.isVideo }
                SystemAlbumType.CAMERA -> activeList.filter { MediaStoreScanner.isCameraMedia(it) }
                SystemAlbumType.DOWNLOADS -> activeList.filter { MediaStoreScanner.isDownloadMedia(it) }
                SystemAlbumType.SCREENSHOTS -> activeList.filter { MediaStoreScanner.isScreenshotMedia(it) }
                SystemAlbumType.SCREEN_RECORDINGS -> activeList.filter { MediaStoreScanner.isScreenRecordingMedia(it) }
                SystemAlbumType.FAVORITES -> activeList.filter { it.isFavorite }
                SystemAlbumType.RECENTLY_ADDED -> {
                    val thirtyDaysAgo = (System.currentTimeMillis() / 1000L) - (30L * 24 * 3600)
                    activeList.filter { it.dateAdded >= thirtyDaysAgo }
                }
                SystemAlbumType.HIDDEN -> hiddenList
                SystemAlbumType.RECENTLY_DELETED -> emptyList()
                SystemAlbumType.TELEGRAM_CLOUD -> activeList.filter { it.isCloudSynced || it.isCloudOnly }
                null -> {
                    val ids = crossRefs.map { it.mediaId }.toSet()
                    activeList.filter { it.id in ids }
                }
            }
        }.flowOn(Dispatchers.Default)
    }

    // Playback positions
    suspend fun savePlaybackPosition(uri: Uri, positionMs: Long, durationMs: Long) = withContext(Dispatchers.IO) {
        playbackDao.savePosition(
            PlaybackPositionEntity(
                mediaUriString = uri.toString(),
                positionMs = positionMs,
                durationMs = durationMs
            )
        )
    }

    suspend fun getPlaybackPosition(uri: Uri): Long = withContext(Dispatchers.IO) {
        playbackDao.getPosition(uri.toString())?.positionMs ?: 0L
    }
}
