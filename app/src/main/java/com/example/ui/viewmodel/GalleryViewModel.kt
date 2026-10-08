package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.DeletedMediaEntity
import com.example.data.local.UserPrefEntity
import com.example.data.repository.MediaRepository
import com.example.data.repository.MediaStoreScanner
import com.example.model.AlbumItem
import com.example.model.MediaGroup
import com.example.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.security.MessageDigest
import kotlin.coroutines.resume

class GalleryViewModel(application: Application) : AndroidViewModel(application) {

    val repository = MediaRepository(application)

    private val _optimisticFavoriteIds = MutableStateFlow<Set<Long>>(emptySet())

    val activeMedia: StateFlow<List<MediaItem>> = combine(
        repository.activeMediaFlow,
        _optimisticFavoriteIds
    ) { items, toggledIds ->
        items.map { item ->
            val actualFav = item.isFavorite
            val isToggled = item.id in toggledIds
            val finalFav = if (isToggled) !actualFav else actualFav
            item.copy(isFavorite = finalFav)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val mediaGroups: StateFlow<List<MediaGroup>> = activeMedia
        .map { items ->
            MediaStoreScanner.groupMediaByDate(items)
        }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val albums: StateFlow<List<AlbumItem>> = repository.allAlbumsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val recentlyDeleted: StateFlow<List<DeletedMediaEntity>> = repository.deletedMediaFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val isScanning: StateFlow<Boolean> = repository.isScanning

    // Selection state for Photos / Albums
    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()

    private val _selectedMediaIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedMediaIds: StateFlow<Set<Long>> = _selectedMediaIds.asStateFlow()

    // Selection state for Recently Deleted
    private val _selectedDeletedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedDeletedIds: StateFlow<Set<Long>> = _selectedDeletedIds.asStateFlow()

    // Fullscreen Viewer state
    private val _viewerMediaList = MutableStateFlow<List<MediaItem>>(emptyList())
    val viewerMediaList: StateFlow<List<MediaItem>> = _viewerMediaList.asStateFlow()

    private val _viewerCurrentIndex = MutableStateFlow(0)
    val viewerCurrentIndex: StateFlow<Int> = _viewerCurrentIndex.asStateFlow()

    private val _isViewerOpen = MutableStateFlow(false)
    val isViewerOpen: StateFlow<Boolean> = _isViewerOpen.asStateFlow()

    // Dialog & Action sheet states
    private val _showDeleteConfirm = MutableStateFlow(false)
    val showDeleteConfirm: StateFlow<Boolean> = _showDeleteConfirm.asStateFlow()

    private val _pendingDeleteItems = MutableStateFlow<List<MediaItem>>(emptyList())
    val pendingDeleteItems: StateFlow<List<MediaItem>> = _pendingDeleteItems.asStateFlow()

    private val _showEmptyTrashConfirm = MutableStateFlow(false)
    val showEmptyTrashConfirm: StateFlow<Boolean> = _showEmptyTrashConfirm.asStateFlow()

    private val _showAddToAlbumSheet = MutableStateFlow(false)
    val showAddToAlbumSheet: StateFlow<Boolean> = _showAddToAlbumSheet.asStateFlow()

    private val _showCreateAlbumDialog = MutableStateFlow(false)
    val showCreateAlbumDialog: StateFlow<Boolean> = _showCreateAlbumDialog.asStateFlow()

    private val _infoSheetItem = MutableStateFlow<MediaItem?>(null)
    val infoSheetItem: StateFlow<MediaItem?> = _infoSheetItem.asStateFlow()

    // Local Search state
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchFilterType = MutableStateFlow("ALL") // "ALL", "PHOTOS", "VIDEOS", "FAVORITES"
    val searchFilterType: StateFlow<String> = _searchFilterType.asStateFlow()

    val searchResults: StateFlow<List<MediaItem>> = combine(
        activeMedia,
        _searchQuery,
        _searchFilterType
    ) { items, query, filterType ->
        val trimmed = query.trim().lowercase()
        items.filter { item ->
            // Type filter
            val matchesType = when (filterType) {
                "PHOTOS" -> !item.isVideo
                "VIDEOS" -> item.isVideo
                "FAVORITES" -> item.isFavorite
                else -> true
            }

            if (!matchesType) return@filter false

            // Query filter: matches filename, album/bucket, or relative path
            if (trimmed.isEmpty()) return@filter true

            item.displayName.lowercase().contains(trimmed) ||
                    (item.bucketDisplayName?.lowercase()?.contains(trimmed) == true) ||
                    (item.relativePath?.lowercase()?.contains(trimmed) == true)
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // User appearance preference ("SYSTEM", "LIGHT", "DARK")
    private val _themeMode = MutableStateFlow("SYSTEM")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    fun refresh() {
        repository.scanMedia()
    }

    fun toggleFavorite(item: MediaItem) {
        val currentToggled = _optimisticFavoriteIds.value.toMutableSet()
        if (currentToggled.contains(item.id)) {
            currentToggled.remove(item.id)
        } else {
            currentToggled.add(item.id)
        }
        _optimisticFavoriteIds.value = currentToggled

        viewModelScope.launch {
            repository.toggleFavorite(item)
            val freshToggled = _optimisticFavoriteIds.value.toMutableSet()
            freshToggled.remove(item.id)
            _optimisticFavoriteIds.value = freshToggled
        }
    }

    private var cachedPasswordHash: String? = null
    private val _hasHidePhotosPassword = MutableStateFlow(false)
    val hasHidePhotosPassword: StateFlow<Boolean> = _hasHidePhotosPassword.asStateFlow()

    private val _isHiddenVaultUnlocked = MutableStateFlow(false)
    val isHiddenVaultUnlocked: StateFlow<Boolean> = _isHiddenVaultUnlocked.asStateFlow()

    private val _isHiddenSelectionMode = MutableStateFlow(false)
    val isHiddenSelectionMode: StateFlow<Boolean> = _isHiddenSelectionMode.asStateFlow()

    private val _hiddenSelectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val hiddenSelectedIds: StateFlow<Set<Long>> = _hiddenSelectedIds.asStateFlow()

    val hiddenMedia = repository.hiddenMediaFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _showMoveDialog = MutableStateFlow(false)
    val showMoveDialog: StateFlow<Boolean> = _showMoveDialog.asStateFlow()

    init {
        viewModelScope.launch {
            cachedPasswordHash = repository.getPreferenceSync("hide_photos_password_hash")
            _hasHidePhotosPassword.value = !cachedPasswordHash.isNullOrEmpty()
        }
    }

    private fun hashPassword(password: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(password.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun setHidePhotosPassword(password: String) {
        viewModelScope.launch {
            val hash = hashPassword(password)
            cachedPasswordHash = hash
            repository.setPreference("hide_photos_password_hash", hash)
            _hasHidePhotosPassword.value = true
        }
    }

    fun unlockHiddenVault(password: String): Boolean {
        val hash = hashPassword(password)
        if (hash == cachedPasswordHash) {
            _isHiddenVaultUnlocked.value = true
            return true
        }
        return false
    }

    fun lockHiddenVault() {
        _isHiddenVaultUnlocked.value = false
        clearHiddenSelection()
    }

    fun hideSelectedMedia() {
        val selected = activeMedia.value.filter { it.id in _selectedMediaIds.value }
        if (selected.isEmpty()) return
        viewModelScope.launch {
            repository.hideMedia(selected)
            clearSelection()
        }
    }

    fun hideMediaFromUris(uris: List<Uri>) {
        viewModelScope.launch {
            val allRaw = repository.rawMediaFlow.value
            val matching = allRaw.filter { item -> item.uri in uris }
            if (matching.isNotEmpty()) {
                repository.hideMedia(matching)
            }
        }
    }

    fun unhideSelectedHidden() {
        val targets = hiddenMedia.value.filter { it.id in _hiddenSelectedIds.value }
        if (targets.isEmpty()) return
        viewModelScope.launch {
            repository.unhideMedia(targets)
            clearHiddenSelection()
        }
    }

    fun deleteSelectedHidden(allHidden: List<MediaItem>) {
        val targets = allHidden.filter { it.id in _hiddenSelectedIds.value }
        if (targets.isEmpty()) return
        viewModelScope.launch {
            repository.moveToRecentlyDeleted(targets) { true }
            repository.unhideMedia(targets)
            clearHiddenSelection()
        }
    }

    fun startHiddenSelection(id: Long) {
        _isHiddenSelectionMode.value = true
        _hiddenSelectedIds.value = setOf(id)
    }

    fun toggleHiddenSelection(id: Long) {
        val current = _hiddenSelectedIds.value.toMutableSet()
        if (current.contains(id)) {
            current.remove(id)
            if (current.isEmpty()) _isHiddenSelectionMode.value = false
        } else {
            current.add(id)
            _isHiddenSelectionMode.value = true
        }
        _hiddenSelectedIds.value = current
    }

    fun clearHiddenSelection() {
        _hiddenSelectedIds.value = emptySet()
        _isHiddenSelectionMode.value = false
    }

    fun openMoveDialog() {
        _showMoveDialog.value = true
    }

    fun closeMoveDialog() {
        _showMoveDialog.value = false
    }

    fun moveSelectedMediaToAlbum(albumId: Long) {
        viewModelScope.launch {
            val selected = activeMedia.value.filter { it.id in _selectedMediaIds.value }
            if (selected.isNotEmpty()) {
                repository.addMediaToAlbum(albumId, selected)
                clearSelection()
            }
            _showMoveDialog.value = false
        }
    }

    // Selection management
    fun startSelectionWith(mediaId: Long) {
        _isSelectionMode.value = true
        _selectedMediaIds.value = setOf(mediaId)
    }

    fun toggleSelection(mediaId: Long) {
        val current = _selectedMediaIds.value.toMutableSet()
        if (current.contains(mediaId)) {
            current.remove(mediaId)
            if (current.isEmpty()) {
                _isSelectionMode.value = false
            }
        } else {
            current.add(mediaId)
            _isSelectionMode.value = true
        }
        _selectedMediaIds.value = current
    }

    fun selectAll(allItems: List<MediaItem>) {
        _isSelectionMode.value = true
        _selectedMediaIds.value = allItems.map { it.id }.toSet()
    }

    fun clearSelection() {
        _selectedMediaIds.value = emptySet()
        _isSelectionMode.value = false
    }

    // Recently deleted selection
    fun toggleDeletedSelection(id: Long) {
        val current = _selectedDeletedIds.value.toMutableSet()
        if (current.contains(id)) {
            current.remove(id)
        } else {
            current.add(id)
        }
        _selectedDeletedIds.value = current
    }

    fun selectAllDeleted(allDeleted: List<DeletedMediaEntity>) {
        _selectedDeletedIds.value = allDeleted.map { it.id }.toSet()
    }

    fun clearDeletedSelection() {
        _selectedDeletedIds.value = emptySet()
    }

    var intentSenderRequester: (suspend (IntentSender) -> Boolean)? = null

    // Delete flow (Normal delete -> moves to Recently Deleted)
    fun requestDeleteSelected(allItems: List<MediaItem>) {
        val selected = allItems.filter { it.id in _selectedMediaIds.value }
        if (selected.isNotEmpty()) {
            _pendingDeleteItems.value = selected
            _showDeleteConfirm.value = true
        }
    }

    fun requestDeleteSingle(item: MediaItem) {
        _pendingDeleteItems.value = listOf(item)
        _showDeleteConfirm.value = true
    }

    fun confirmMoveToRecentlyDeleted() {
        val items = _pendingDeleteItems.value
        viewModelScope.launch {
            repository.moveToRecentlyDeleted(items) { intentSender ->
                suspendCancellableCoroutine { continuation ->
                    val requester = intentSenderRequester
                    if (requester != null) {
                        viewModelScope.launch {
                            val result = requester(intentSender)
                            if (continuation.isActive) continuation.resume(result)
                        }
                    } else {
                        if (continuation.isActive) continuation.resume(false)
                    }
                }
            }
            clearSelection()
            _showDeleteConfirm.value = false
            _pendingDeleteItems.value = emptyList()
            if (_isViewerOpen.value) {
                // If viewer is viewing the deleted item, navigate or close
                closeViewer()
            }
        }
    }

    fun dismissDeleteDialog() {
        _showDeleteConfirm.value = false
        _pendingDeleteItems.value = emptyList()
    }

    // Recently Deleted Operations
    fun restoreSelectedDeleted(allDeleted: List<DeletedMediaEntity>) {
        val targets = allDeleted.filter { it.id in _selectedDeletedIds.value }
        if (targets.isEmpty()) return
        viewModelScope.launch {
            repository.restoreDeletedItems(targets)
            clearDeletedSelection()
        }
    }

    fun restoreSingleDeleted(entity: DeletedMediaEntity) {
        viewModelScope.launch {
            repository.restoreDeletedItems(listOf(entity))
        }
    }

    fun permanentlyDeleteSelected(allDeleted: List<DeletedMediaEntity>) {
        val targets = allDeleted.filter { it.id in _selectedDeletedIds.value }
        if (targets.isEmpty()) return
        viewModelScope.launch {
            repository.permanentlyDeleteItems(targets) { intentSender ->
                suspendCancellableCoroutine { continuation ->
                    val requester = intentSenderRequester
                    if (requester != null) {
                        viewModelScope.launch {
                            val result = requester(intentSender)
                            if (continuation.isActive) continuation.resume(result)
                        }
                    } else {
                        if (continuation.isActive) continuation.resume(false)
                    }
                }
            }
            clearDeletedSelection()
        }
    }

    fun permanentlyDeleteSingle(entity: DeletedMediaEntity) {
        viewModelScope.launch {
            repository.permanentlyDeleteItems(listOf(entity)) { intentSender ->
                suspendCancellableCoroutine { continuation ->
                    val requester = intentSenderRequester
                    if (requester != null) {
                        viewModelScope.launch {
                            val result = requester(intentSender)
                            if (continuation.isActive) continuation.resume(result)
                        }
                    } else {
                        if (continuation.isActive) continuation.resume(false)
                    }
                }
            }
            clearDeletedSelection()
        }
    }

    fun requestEmptyTrash() {
        _showEmptyTrashConfirm.value = true
    }

    fun confirmEmptyTrash() {
        viewModelScope.launch {
            val allDeleted = repository.deletedMediaDao.getAllDeletedSync()
            repository.emptyRecentlyDeleted { intentSender ->
                suspendCancellableCoroutine { continuation ->
                    val requester = intentSenderRequester
                    if (requester != null) {
                        viewModelScope.launch {
                            val result = requester(intentSender)
                            if (continuation.isActive) continuation.resume(result)
                        }
                    } else {
                        if (continuation.isActive) continuation.resume(false)
                    }
                }
            }
            clearDeletedSelection()
            _showEmptyTrashConfirm.value = false
        }
    }

    fun dismissEmptyTrashDialog() {
        _showEmptyTrashConfirm.value = false
    }

    // Custom Albums
    fun openAddToAlbumSheet() {
        _showAddToAlbumSheet.value = true
    }

    fun closeAddToAlbumSheet() {
        _showAddToAlbumSheet.value = false
    }

    fun openCreateAlbumDialog() {
        _showCreateAlbumDialog.value = true
    }

    fun closeCreateAlbumDialog() {
        _showCreateAlbumDialog.value = false
    }

    fun createAlbum(name: String) {
        viewModelScope.launch {
            val albumId = repository.createCustomAlbum(name)
            if (albumId > 0 && _selectedMediaIds.value.isNotEmpty()) {
                val selected = activeMedia.value.filter { it.id in _selectedMediaIds.value }
                repository.addMediaToAlbum(albumId, selected)
                clearSelection()
            }
            _showCreateAlbumDialog.value = false
        }
    }

    fun addSelectedToAlbum(albumId: Long) {
        viewModelScope.launch {
            val selected = activeMedia.value.filter { it.id in _selectedMediaIds.value }
            repository.addMediaToAlbum(albumId, selected)
            clearSelection()
            _showAddToAlbumSheet.value = false
        }
    }

    fun deleteAlbum(albumId: Long) {
        viewModelScope.launch {
            repository.deleteCustomAlbum(albumId)
        }
    }

    // Info Sheet
    fun showInfoSheet(item: MediaItem) {
        _infoSheetItem.value = item
    }

    fun dismissInfoSheet() {
        _infoSheetItem.value = null
    }

    // Fullscreen Viewer
    fun openViewer(item: MediaItem, collection: List<MediaItem>) {
        _viewerMediaList.value = collection
        val index = collection.indexOfFirst { it.id == item.id }.coerceAtLeast(0)
        _viewerCurrentIndex.value = index
        _isViewerOpen.value = true
    }

    fun setViewerIndex(index: Int) {
        _viewerCurrentIndex.value = index
    }

    fun closeViewer() {
        _isViewerOpen.value = false
    }

    // Search
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSearchFilter(type: String) {
        _searchFilterType.value = type
    }

    // Settings
    fun setThemeMode(mode: String) {
        _themeMode.value = mode
    }

    // Sharing via Android Sharesheet
    fun shareMedia(context: Context, items: List<MediaItem>) {
        if (items.isEmpty()) return
        if (items.size == 1) {
            val item = items.first()
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = item.mimeType
                putExtra(Intent.EXTRA_STREAM, item.uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share ${item.displayName}"))
        } else {
            val uris = ArrayList(items.map { it.uri })
            val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share ${items.size} items"))
        }
    }
}
