package com.example.model

import android.net.Uri
import androidx.compose.runtime.Immutable

@Immutable
data class MediaItem(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
    val dateAdded: Long,      // In seconds (from MediaStore) or millis
    val dateModified: Long,
    val size: Long,
    val width: Int,
    val height: Int,
    val duration: Long = 0L,   // In milliseconds (for videos)
    val isVideo: Boolean = false,
    val bucketId: String? = null,
    val bucketDisplayName: String? = null,
    val relativePath: String? = null,
    val isFavorite: Boolean = false,
    val isHidden: Boolean = false
) {
    val durationFormatted: String
        get() {
            if (duration <= 0) return ""
            val totalSeconds = duration / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            val hours = minutes / 60
            return if (hours > 0) {
                val remainingMins = minutes % 60
                String.format("%d:%02d:%02d", hours, remainingMins, seconds)
            } else {
                String.format("%d:%02d", minutes, seconds)
            }
        }

    val sizeFormatted: String
        get() {
            val kb = size / 1024.0
            val mb = kb / 1024.0
            val gb = mb / 1024.0
            return when {
                gb >= 1.0 -> String.format("%.2f GB", gb)
                mb >= 1.0 -> String.format("%.1f MB", mb)
                kb >= 1.0 -> String.format("%.1f KB", kb)
                else -> "$size B"
            }
        }

    val resolutionFormatted: String
        get() = if (width > 0 && height > 0) "${width} × ${height}" else "Unknown"
}

@Immutable
data class MediaGroup(
    val title: String,
    val dateSubtitle: String? = null,
    val items: List<MediaItem>
)

enum class SystemAlbumType {
    ALL_PHOTOS,
    ALL_VIDEOS,
    VIDEOS,
    CAMERA,
    DOWNLOADS,
    SCREENSHOTS,
    SCREEN_RECORDINGS,
    FAVORITES,
    RECENTLY_ADDED,
    RECENTLY_DELETED,
    HIDDEN
}

@Immutable
data class AlbumItem(
    val id: Long,
    val title: String,
    val count: Int,
    val coverUri: Uri? = null,
    val systemType: SystemAlbumType? = null
)
