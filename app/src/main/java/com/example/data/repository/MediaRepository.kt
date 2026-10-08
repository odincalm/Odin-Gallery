package com.example.data.repository

import android.content.Context
import android.content.IntentSender
import android.net.Uri
import coil.Coil
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

    // Filtered media: raw media MINUS recently deleted and MINUS hidden media
    val activeMediaFlow: Flow<List<MediaItem>> = combine(
        _rawMediaFlow,
        favoriteDao.getAllFavorites(),
        deletedMediaDao.getAllDeleted(),
        hiddenMediaDao.getAllHidden()
    ) { rawItems, favorites, deletedItems, hiddenItems ->
        val favoriteIds = favorites.map { it.originalMediaId }.toSet()
        val deletedIds = deletedItems.map { it.originalMediaId }.toSet()
        val hiddenIds = hiddenItems.map { it.originalMediaId }.toSet()

        rawItems
            .filterNot { it.id in deletedIds || it.id in hiddenIds }
            .map { item ->
                item.copy(isFavorite = item.id in favoriteIds, isHidden = false)
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
    suspend fun moveToRecentlyDeleted(
        items: List<MediaItem>,
        intentSenderRequester: suspend (IntentSender) -> Boolean
    ): Boolean {
        val success = recentlyDeletedManager.moveToRecentlyDeleted(items, intentSenderRequester)
        if (success) {
            val ids = items.map { it.id }
            favoriteDao.deleteFavoritesByMediaIds(ids)
            albumDao.deleteMediaFromAllAlbumsBatch(ids)
            hiddenMediaDao.deleteHiddenByMediaIds(ids)
            clearCoilCache()
            scanMedia()
        }
        return success
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
