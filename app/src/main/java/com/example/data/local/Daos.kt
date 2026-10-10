package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites WHERE isFavorite = 1")
    fun getAllFavorites(): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites WHERE isFavorite = 1")
    suspend fun getAllFavoritesSync(): List<FavoriteEntity>

    @Query("SELECT isFavorite FROM favorites WHERE originalMediaId = :mediaId LIMIT 1")
    fun isFavorite(mediaId: Long): Flow<Boolean?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(entity: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE originalMediaId = :mediaId")
    suspend fun deleteFavoriteByMediaId(mediaId: Long)

    @Query("DELETE FROM favorites WHERE originalMediaId IN (:mediaIds)")
    suspend fun deleteFavoritesByMediaIds(mediaIds: List<Long>)
}

@Dao
interface AlbumDao {
    @Query("SELECT * FROM custom_albums ORDER BY createdAt DESC")
    fun getAllAlbums(): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM custom_albums ORDER BY createdAt DESC")
    suspend fun getAllAlbumsSync(): List<AlbumEntity>

    @Query("SELECT * FROM album_media_cross_ref")
    fun getAllAlbumMediaCrossRefs(): Flow<List<AlbumMediaEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlbum(album: AlbumEntity): Long

    @Query("DELETE FROM custom_albums WHERE id = :albumId")
    suspend fun deleteAlbum(albumId: Long)

    @Query("SELECT * FROM album_media_cross_ref WHERE albumId = :albumId ORDER BY addedAt DESC")
    fun getMediaInAlbum(albumId: Long): Flow<List<AlbumMediaEntity>>

    @Query("SELECT * FROM album_media_cross_ref WHERE albumId = :albumId ORDER BY addedAt DESC")
    suspend fun getMediaInAlbumSync(albumId: Long): List<AlbumMediaEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlbumMedia(crossRef: AlbumMediaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlbumMediaBatch(crossRefs: List<AlbumMediaEntity>)

    @Query("DELETE FROM album_media_cross_ref WHERE albumId = :albumId AND mediaId = :mediaId")
    suspend fun removeAlbumMedia(albumId: Long, mediaId: Long)

    @Query("DELETE FROM album_media_cross_ref WHERE mediaId = :mediaId")
    suspend fun deleteMediaFromAllAlbums(mediaId: Long)

    @Query("DELETE FROM album_media_cross_ref WHERE mediaId IN (:mediaIds)")
    suspend fun deleteMediaFromAllAlbumsBatch(mediaIds: List<Long>)
}

@Dao
interface DeletedMediaDao {
    @Query("SELECT * FROM recently_deleted WHERE status = 'TRASHED' ORDER BY deletedTimestamp DESC")
    fun getAllDeleted(): Flow<List<DeletedMediaEntity>>

    @Query("SELECT * FROM recently_deleted WHERE status = 'TRASHED' ORDER BY deletedTimestamp DESC")
    suspend fun getAllDeletedSync(): List<DeletedMediaEntity>

    @Query("SELECT * FROM recently_deleted WHERE id = :id LIMIT 1")
    suspend fun getDeletedById(id: Long): DeletedMediaEntity?

    @Query("SELECT * FROM recently_deleted WHERE originalMediaId = :mediaId LIMIT 1")
    suspend fun getDeletedByMediaId(mediaId: Long): DeletedMediaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeleted(entity: DeletedMediaEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeletedBatch(entities: List<DeletedMediaEntity>)

    @Query("UPDATE recently_deleted SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)

    @Query("UPDATE recently_deleted SET status = :status WHERE originalMediaId = :mediaId")
    suspend fun updateStatusByMediaId(mediaId: Long, status: String)

    @Query("DELETE FROM recently_deleted WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM recently_deleted WHERE originalMediaId = :mediaId")
    suspend fun deleteByOriginalMediaId(mediaId: Long)

    @Query("DELETE FROM recently_deleted WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM recently_deleted")
    suspend fun clearAll()
}

@Dao
interface HiddenMediaDao {
    @Query("SELECT * FROM hidden_media ORDER BY hiddenAt DESC")
    fun getAllHidden(): Flow<List<HiddenMediaEntity>>

    @Query("SELECT * FROM hidden_media ORDER BY hiddenAt DESC")
    suspend fun getAllHiddenSync(): List<HiddenMediaEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM hidden_media WHERE originalMediaId = :mediaId)")
    fun isHidden(mediaId: Long): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHidden(entity: HiddenMediaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHiddenBatch(entities: List<HiddenMediaEntity>)

    @Query("DELETE FROM hidden_media WHERE originalMediaId = :mediaId")
    suspend fun deleteHiddenByMediaId(mediaId: Long)

    @Query("DELETE FROM hidden_media WHERE originalMediaId IN (:mediaIds)")
    suspend fun deleteHiddenByMediaIds(mediaIds: List<Long>)

    @Query("DELETE FROM hidden_media")
    suspend fun clearAll()
}

@Dao
interface PlaybackDao {
    @Query("SELECT * FROM playback_positions WHERE mediaUriString = :uriString LIMIT 1")
    suspend fun getPosition(uriString: String): PlaybackPositionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePosition(entity: PlaybackPositionEntity)

    @Query("DELETE FROM playback_positions WHERE mediaUriString = :uriString")
    suspend fun deletePosition(uriString: String)
}

@Dao
interface UserPrefDao {
    @Query("SELECT value FROM user_preferences WHERE `key` = :key LIMIT 1")
    fun getPreference(key: String): Flow<String?>

    @Query("SELECT value FROM user_preferences WHERE `key` = :key LIMIT 1")
    suspend fun getPreferenceSync(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setPreference(entity: UserPrefEntity)
}

@Dao
interface TelegramBackupDao {
    @Query("SELECT * FROM telegram_backup_items ORDER BY queuedAt DESC")
    fun getAllBackupItemsFlow(): Flow<List<TelegramBackupItem>>

    @Query("SELECT * FROM telegram_backup_items WHERE status = 'COMPLETED' ORDER BY completedAt DESC, queuedAt DESC")
    fun getAllCompletedFlow(): Flow<List<TelegramBackupItem>>

    @Query("SELECT * FROM telegram_backup_items WHERE status = 'PENDING' ORDER BY queuedAt ASC")
    suspend fun getPendingItems(): List<TelegramBackupItem>

    @Query("SELECT * FROM telegram_backup_items WHERE status = 'FAILED' ORDER BY queuedAt ASC")
    suspend fun getFailedItems(): List<TelegramBackupItem>

    @Query("SELECT * FROM telegram_backup_items WHERE status = 'COMPLETED'")
    suspend fun getCompletedItems(): List<TelegramBackupItem>

    @Query("SELECT * FROM telegram_backup_items WHERE localMediaId = :mediaId LIMIT 1")
    suspend fun getItemByMediaId(mediaId: Long): TelegramBackupItem?

    @Query("SELECT * FROM telegram_backup_items WHERE telegramMessageId = :messageId LIMIT 1")
    suspend fun getItemByMessageId(messageId: Long): TelegramBackupItem?

    @Query("SELECT * FROM telegram_backup_items WHERE fileHash = :hash LIMIT 1")
    suspend fun getItemByHash(hash: String): TelegramBackupItem?

    @Query("SELECT * FROM telegram_backup_items WHERE fileHash = :hash AND status = 'COMPLETED' LIMIT 1")
    suspend fun getCompletedItemByHash(hash: String): TelegramBackupItem?

    @Query("SELECT COUNT(*) FROM telegram_backup_items WHERE status = 'PENDING'")
    fun getPendingCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM telegram_backup_items WHERE status = 'FAILED'")
    fun getFailedCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM telegram_backup_items WHERE status = 'COMPLETED'")
    fun getCompletedCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM telegram_backup_items WHERE status = 'COMPLETED'")
    suspend fun getCompletedCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: TelegramBackupItem): Long

    @Transaction
    suspend fun insertBatch(items: List<TelegramBackupItem>) {
        for (item in items) {
            val existing = if (item.telegramMessageId > 0L) {
                getItemByMessageId(item.telegramMessageId)
            } else if (item.fileHash.isNotBlank()) {
                getItemByHash(item.fileHash)
            } else if (item.localMediaId > 0L) {
                getItemByMediaId(item.localMediaId)
            } else {
                null
            }

            if (existing != null) {
                update(existing.copy(
                    telegramMessageId = if (item.telegramMessageId > 0L) item.telegramMessageId else existing.telegramMessageId,
                    telegramFileId = if (item.telegramFileId != 0) item.telegramFileId else existing.telegramFileId,
                    thumbnailFileId = if (item.thumbnailFileId != 0) item.thumbnailFileId else existing.thumbnailFileId,
                    thumbnailPath = item.thumbnailPath ?: existing.thumbnailPath,
                    status = item.status,
                    completedAt = item.completedAt ?: existing.completedAt,
                    width = if (item.width > 0) item.width else existing.width,
                    height = if (item.height > 0) item.height else existing.height,
                    duration = if (item.duration > 0) item.duration else existing.duration
                ))
            } else {
                insert(item)
            }
        }
    }

    @Update
    suspend fun update(item: TelegramBackupItem)

    @Query("UPDATE telegram_backup_items SET thumbnailPath = :thumbnailPath WHERE id = :id")
    suspend fun updateThumbnailPath(id: Long, thumbnailPath: String)

    @Query("UPDATE telegram_backup_items SET status = :status, errorMessage = :errorMessage WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, errorMessage: String? = null)

    @Query("UPDATE telegram_backup_items SET status = 'PENDING' WHERE status = 'UPLOADING'")
    suspend fun resetUploadingToPending(): Int

    @Query("UPDATE telegram_backup_items SET status = 'PENDING', retryCount = 0, errorMessage = null WHERE status = 'FAILED'")
    suspend fun resetFailedItemsToPending(): Int

    @Query("DELETE FROM telegram_backup_items WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM telegram_backup_items WHERE telegramMessageId = :messageId")
    suspend fun deleteByMessageId(messageId: Long)

    @Query("DELETE FROM telegram_backup_items WHERE localMediaId = :mediaId")
    suspend fun deleteByMediaId(mediaId: Long)

    @Query("DELETE FROM telegram_backup_items")
    suspend fun clearAll()
}
