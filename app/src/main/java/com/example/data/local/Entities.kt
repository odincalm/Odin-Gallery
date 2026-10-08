package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "favorites",
    indices = [Index(value = ["originalMediaId"], unique = true)]
)
data class FavoriteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val originalMediaId: Long,
    val uriString: String,
    val isFavorite: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "custom_albums")
data class AlbumEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "album_media_cross_ref",
    primaryKeys = ["albumId", "mediaId"]
)
data class AlbumMediaEntity(
    val albumId: Long,
    val mediaId: Long,
    val uriString: String,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "recently_deleted")
data class DeletedMediaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val originalMediaId: Long,
    val originalUriString: String,
    val originalPath: String?,
    val displayName: String,
    val mimeType: String,
    val size: Long,
    val duration: Long,
    val width: Int,
    val height: Int,
    val dateAdded: Long,
    val relativePath: String?,
    val deletedTimestamp: Long = System.currentTimeMillis(),
    val trashFilePath: String, // Local storage path where backup is retained
    val status: String = "TRASHED" // State machine: TRASHED, RESTORED, PERMANENTLY_DELETED
) {
    // 30 days retention policy: days remaining calculation
    val daysRemaining: Int
        get() {
            val millisInDay = 24 * 60 * 60 * 1000L
            val elapsed = System.currentTimeMillis() - deletedTimestamp
            val remaining = 30 - (elapsed / millisInDay)
            return remaining.coerceAtLeast(0).toInt()
        }
}

@Entity(
    tableName = "hidden_media",
    indices = [Index(value = ["originalMediaId"], unique = true)]
)
data class HiddenMediaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val originalMediaId: Long,
    val uriString: String,
    val hiddenAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "playback_positions")
data class PlaybackPositionEntity(
    @PrimaryKey val mediaUriString: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "user_preferences")
data class UserPrefEntity(
    @PrimaryKey val key: String,
    val value: String
)
