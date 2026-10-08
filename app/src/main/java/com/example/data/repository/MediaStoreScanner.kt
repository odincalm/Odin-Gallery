package com.example.data.repository

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.example.model.MediaGroup
import com.example.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class MediaStoreScanner(private val context: Context) {

    suspend fun queryAllMedia(): List<MediaItem> = withContext(Dispatchers.IO) {
        // Use LinkedHashMap keyed by MediaStore ID to strictly prevent any duplicate items
        val mediaMap = LinkedHashMap<Long, MediaItem>()
        val contentResolver = context.contentResolver

        // 1. Query Images
        val imageProjection = mutableListOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.DATA
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Images.Media.RELATIVE_PATH)
            }
        }.toTypedArray()

        try {
            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                imageProjection,
                null,
                null,
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                parseMediaCursor(cursor, isVideo = false, mediaMap)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Query Videos
        val videoProjection = mutableListOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.BUCKET_ID,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Video.Media.DATA
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Video.Media.RELATIVE_PATH)
            }
        }.toTypedArray()

        try {
            contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                videoProjection,
                null,
                null,
                "${MediaStore.Video.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                parseMediaCursor(cursor, isVideo = true, mediaMap)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Return sorted descending by dateAdded
        mediaMap.values.sortedByDescending { it.dateAdded }
    }

    private fun parseMediaCursor(cursor: Cursor, isVideo: Boolean, outMap: MutableMap<Long, MediaItem>) {
        val idCol = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
        val nameCol = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
        val mimeCol = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
        val dateAddedCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)
        val dateModCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
        val sizeCol = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
        val widthCol = cursor.getColumnIndex(MediaStore.MediaColumns.WIDTH)
        val heightCol = cursor.getColumnIndex(MediaStore.MediaColumns.HEIGHT)
        val bucketIdCol = cursor.getColumnIndex(MediaStore.MediaColumns.BUCKET_ID)
        val bucketNameCol = cursor.getColumnIndex(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
        val dataCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
        val pathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
        } else -1
        val durationCol = if (isVideo) cursor.getColumnIndex(MediaStore.Video.VideoColumns.DURATION) else -1

        val baseUri = if (isVideo) {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        while (cursor.moveToNext()) {
            try {
                val id = if (idCol >= 0) cursor.getLong(idCol) else continue
                val name = if (nameCol >= 0) cursor.getString(nameCol) ?: "Media_$id" else "Media_$id"
                val mime = if (mimeCol >= 0) cursor.getString(mimeCol) ?: (if (isVideo) "video/*" else "image/*") else (if (isVideo) "video/*" else "image/*")
                val dateAdded = if (dateAddedCol >= 0) cursor.getLong(dateAddedCol) else System.currentTimeMillis() / 1000
                val dateMod = if (dateModCol >= 0) cursor.getLong(dateModCol) else dateAdded
                val size = if (sizeCol >= 0) cursor.getLong(sizeCol) else 0L
                val width = if (widthCol >= 0) cursor.getInt(widthCol) else 0
                val height = if (heightCol >= 0) cursor.getInt(heightCol) else 0
                val duration = if (durationCol >= 0) cursor.getLong(durationCol) else 0L
                val bucketId = if (bucketIdCol >= 0) cursor.getString(bucketIdCol) else null
                val bucketName = if (bucketNameCol >= 0) cursor.getString(bucketNameCol) else null
                val dataPath = if (dataCol >= 0) cursor.getString(dataCol) else null
                val relPath = if (pathCol >= 0) cursor.getString(pathCol) else null

                val contentUri: Uri = ContentUris.withAppendedId(baseUri, id)

                // Deduplicate by ID
                outMap[id] = MediaItem(
                    id = id,
                    uri = contentUri,
                    displayName = name,
                    mimeType = mime,
                    dateAdded = dateAdded,
                    dateModified = dateMod,
                    size = size,
                    width = width,
                    height = height,
                    duration = duration,
                    isVideo = isVideo,
                    bucketId = bucketId,
                    bucketDisplayName = bucketName,
                    relativePath = relPath ?: dataPath,
                    isFavorite = false,
                    isHidden = false
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun checkMediaExistsInMediaStore(id: Long, isVideo: Boolean): Boolean = withContext(Dispatchers.IO) {
        val baseUri = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        try {
            context.contentResolver.query(
                baseUri,
                projection,
                "${MediaStore.MediaColumns._ID} = ?",
                arrayOf(id.toString()),
                null
            )?.use { cursor ->
                return@withContext cursor.moveToFirst()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        false
    }

    suspend fun findExistingMediaByPathOrName(
        displayName: String,
        relativePath: String?,
        isVideo: Boolean
    ): MediaItem? = withContext(Dispatchers.IO) {
        val baseUri = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT
        )
        try {
            context.contentResolver.query(
                baseUri,
                projection,
                "${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
                arrayOf(displayName),
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                    val contentUri = ContentUris.withAppendedId(baseUri, id)
                    return@withContext MediaItem(
                        id = id,
                        uri = contentUri,
                        displayName = displayName,
                        mimeType = if (isVideo) "video/*" else "image/*",
                        dateAdded = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)),
                        dateModified = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)),
                        size = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)),
                        width = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)),
                        height = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)),
                        isVideo = isVideo
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        null
    }

    companion object {
        fun groupMediaByDate(items: List<MediaItem>): List<MediaGroup> {
            if (items.isEmpty()) return emptyList()

            val tzOffset = TimeZone.getDefault().getOffset(System.currentTimeMillis()).toLong()
            val now = System.currentTimeMillis()
            val todayDay = (now + tzOffset) / 86_400_000L
            val yesterdayDay = todayDay - 1L

            val calendar = Calendar.getInstance()
            val currentYear = calendar.get(Calendar.YEAR)

            val fullDateFormatter = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
            val monthYearFormatter = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
            val dayDateFormatter = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())

            val result = ArrayList<MediaGroup>()
            var currentDay = Long.MIN_VALUE
            var currentGroupItems = ArrayList<MediaItem>()
            var currentFirstItemTime = 0L

            fun flushCurrentGroup() {
                if (currentGroupItems.isNotEmpty()) {
                    calendar.timeInMillis = currentFirstItemTime
                    val itemYear = calendar.get(Calendar.YEAR)
                    val isToday = (currentDay == todayDay)
                    val isYesterday = (currentDay == yesterdayDay)
                    val isThisYear = (itemYear == currentYear)

                    val dateObj = Date(currentFirstItemTime)
                    val title = when {
                        isToday -> "Today"
                        isYesterday -> "Yesterday"
                        isThisYear -> dayDateFormatter.format(dateObj)
                        else -> fullDateFormatter.format(dateObj)
                    }
                    val subtitle = monthYearFormatter.format(dateObj)

                    result.add(
                        MediaGroup(
                            title = title,
                            dateSubtitle = subtitle,
                            items = currentGroupItems
                        )
                    )
                }
            }

            for (item in items) {
                val timeMillis = if (item.dateAdded > 10_000_000_000L) item.dateAdded else item.dateAdded * 1000L
                val itemDay = (timeMillis + tzOffset) / 86_400_000L

                if (itemDay == currentDay) {
                    currentGroupItems.add(item)
                } else {
                    flushCurrentGroup()
                    currentDay = itemDay
                    currentFirstItemTime = timeMillis
                    currentGroupItems = ArrayList()
                    currentGroupItems.add(item)
                }
            }
            flushCurrentGroup()

            return result
        }

        // Automatic categorization helpers based on MediaStore metadata
        fun isScreenshotMedia(item: MediaItem): Boolean {
            if (item.isVideo) return false
            val bucket = item.bucketDisplayName?.lowercase() ?: ""
            val path = item.relativePath?.lowercase() ?: ""
            val name = item.displayName.lowercase()
            return bucket.contains("screenshot") || path.contains("screenshot") ||
                    name.contains("screenshot") || name.startsWith("screenshot") ||
                    name.startsWith("screen_shot") || name.startsWith("screen-shot")
        }

        fun isScreenRecordingMedia(item: MediaItem): Boolean {
            if (!item.isVideo) return false
            val bucket = item.bucketDisplayName?.lowercase() ?: ""
            val path = item.relativePath?.lowercase() ?: ""
            val name = item.displayName.lowercase()
            return bucket.contains("screen record") || bucket.contains("screenrecorder") ||
                    bucket.contains("screen_record") || bucket.contains("screen-record") ||
                    path.contains("screen record") || path.contains("screenrecord") ||
                    path.contains("screen_record") || path.contains("screen-record") ||
                    name.contains("screen_recording") || name.contains("screen-recording") ||
                    name.contains("screenrecorder") || name.contains("screen_record") ||
                    name.startsWith("screen_") || name.startsWith("screen-") || name.startsWith("screenrecorder")
        }

        fun isCameraMedia(item: MediaItem): Boolean {
            if (isScreenshotMedia(item) || isScreenRecordingMedia(item)) return false
            val bucket = item.bucketDisplayName?.lowercase() ?: ""
            val path = item.relativePath?.lowercase() ?: ""
            return bucket == "camera" || bucket.contains("camera") ||
                    path.contains("dcim/camera") || path.contains("/camera/") ||
                    (path.contains("dcim") && !path.contains("screenshot") && !path.contains("screen record") && !path.contains("screenrecord"))
        }

        fun isDownloadMedia(item: MediaItem): Boolean {
            val bucket = item.bucketDisplayName?.lowercase() ?: ""
            val path = item.relativePath?.lowercase() ?: ""
            val name = item.displayName.lowercase()
            return bucket.contains("download") || path.contains("download") || path.contains("/downloads") || name.contains("download")
        }
    }
}
