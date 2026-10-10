package com.example.telegram.client

import android.content.Context
import android.util.Log
import com.example.telegram.model.CloudBackupMetadata
import com.example.telegram.model.DiscoveredCloudItem
import org.drinkless.tdlib.TdApi
import kotlin.math.abs

object TelegramSavedMessagesHelper {
    private const val TAG_PREFIX = "#ODIN_BACKUP"
    private var cachedSavedMessagesChatId: Long? = null

    suspend fun getSavedMessagesChatId(context: Context): Long {
        cachedSavedMessagesChatId?.let { return it }
        val me = TelegramClientHolder.send(context, TdApi.GetMe())
        val chat = TelegramClientHolder.send(context, TdApi.CreatePrivateChat(me.id, false))
        cachedSavedMessagesChatId = chat.id
        return chat.id
    }

    fun clearCachedChatId() {
        cachedSavedMessagesChatId = null
    }

    fun createBackupCaption(
        fileName: String,
        mediaType: String,
        sizeBytes: Long,
        fileHash: String,
        timestamp: Long = System.currentTimeMillis()
    ): String {
        return buildString {
            appendLine(TAG_PREFIX)
            appendLine("fn: $fileName")
            appendLine("type: $mediaType")
            appendLine("size: $sizeBytes")
            appendLine("hash: $fileHash")
            append("ts: $timestamp")
        }
    }

    fun parseBackupCaption(captionText: String?): CloudBackupMetadata? {
        if (captionText == null) return null
        val upperCaption = captionText.uppercase()
        val isOdinBackup = upperCaption.contains("#ODIN_BACKUP") ||
            upperCaption.contains("[ODIN_BACKUP]") ||
            upperCaption.contains("#ODIN")

        if (!isOdinBackup) return null

        val lines = captionText.lines()
        var fileName = ""
        var mediaType = ""
        var sizeBytes = 0L
        var fileHash = ""
        var timestamp = 0L

        for (line in lines) {
            val trimmed = line.trim()
            val lower = trimmed.lowercase()
            when {
                lower.startsWith("fn:") -> fileName = trimmed.substring(3).trim()
                lower.startsWith("filename:") -> fileName = trimmed.substring(9).trim()
                lower.startsWith("file:") -> fileName = trimmed.substring(5).trim()
                lower.startsWith("name:") -> fileName = trimmed.substring(5).trim()

                lower.startsWith("type:") -> mediaType = trimmed.substring(5).trim().uppercase()
                lower.startsWith("media:") -> mediaType = trimmed.substring(6).trim().uppercase()
                lower.startsWith("mediatype:") -> mediaType = trimmed.substring(10).trim().uppercase()

                lower.startsWith("size:") -> sizeBytes = trimmed.substring(5).trim().toLongOrNull() ?: 0L
                lower.startsWith("bytes:") -> sizeBytes = trimmed.substring(6).trim().toLongOrNull() ?: 0L
                lower.startsWith("length:") -> sizeBytes = trimmed.substring(7).trim().toLongOrNull() ?: 0L

                lower.startsWith("hash:") -> fileHash = trimmed.substring(5).trim()
                lower.startsWith("sha256:") -> fileHash = trimmed.substring(7).trim()
                lower.startsWith("checksum:") -> fileHash = trimmed.substring(9).trim()

                lower.startsWith("ts:") -> timestamp = trimmed.substring(3).trim().toLongOrNull() ?: 0L
                lower.startsWith("timestamp:") -> timestamp = trimmed.substring(10).trim().toLongOrNull() ?: 0L
                lower.startsWith("date:") -> timestamp = trimmed.substring(5).trim().toLongOrNull() ?: 0L
            }
        }

        if (fileName.isBlank() && fileHash.isBlank()) return null

        return CloudBackupMetadata(
            fileName = fileName.ifBlank { "odin_backup_file" },
            mediaType = if (mediaType == "VIDEO") "VIDEO" else "IMAGE",
            sizeBytes = sizeBytes,
            fileHash = fileHash,
            timestamp = timestamp
        )
    }

    data class ExtractedMediaInfo(
        val captionText: String?,
        val originalFileId: Int,
        val thumbnailFileId: Int,
        val metadata: CloudBackupMetadata,
        val mediaWidth: Int,
        val mediaHeight: Int,
        val mediaDuration: Long,
        val isOdinBackup: Boolean
    )

    fun extractMediaInfo(message: TdApi.Message): ExtractedMediaInfo? {
        var captionText: String? = null
        var originalFileId = 0
        var thumbnailFileId = 0
        var mediaWidth = 0
        var mediaHeight = 0
        var mediaDuration = 0L

        var defaultFileName = ""
        var defaultMediaType = "IMAGE"
        var defaultSizeBytes = 0L

        when (val content = message.content) {
            is TdApi.MessagePhoto -> {
                captionText = content.caption?.text
                val sizes = content.photo?.sizes.orEmpty()
                val largest = sizes.maxByOrNull { it.width * it.height }
                originalFileId = largest?.photo?.id ?: 0
                mediaWidth = largest?.width ?: 0
                mediaHeight = largest?.height ?: 0
                defaultSizeBytes = largest?.photo?.expectedSize?.toLong() ?: largest?.photo?.size?.toLong() ?: 0L

                val mid = sizes
                    .filter { it.width in 100..640 || it.height in 100..640 }
                    .minByOrNull { abs(it.width - 320) + abs(it.height - 320) }
                val smallest = sizes.filter { it.width > 0 && it.height > 0 }.minByOrNull { it.width * it.height }
                thumbnailFileId = (mid ?: smallest)?.photo?.id ?: 0

                defaultFileName = "photo_${message.id}.jpg"
                defaultMediaType = "IMAGE"
            }
            is TdApi.MessageVideo -> {
                captionText = content.caption?.text
                originalFileId = content.video?.video?.id ?: 0
                thumbnailFileId = content.video?.thumbnail?.file?.id ?: 0
                mediaWidth = content.video?.width ?: 0
                mediaHeight = content.video?.height ?: 0
                mediaDuration = (content.video?.duration ?: 0) * 1000L
                defaultSizeBytes = content.video?.video?.expectedSize?.toLong() ?: content.video?.video?.size?.toLong() ?: 0L

                defaultFileName = content.video?.fileName?.ifBlank { "video_${message.id}.mp4" } ?: "video_${message.id}.mp4"
                defaultMediaType = "VIDEO"
            }
            is TdApi.MessageDocument -> {
                captionText = content.caption?.text
                originalFileId = content.document?.document?.id ?: 0
                thumbnailFileId = content.document?.thumbnail?.file?.id ?: 0
                defaultSizeBytes = content.document?.document?.expectedSize?.toLong() ?: content.document?.document?.size?.toLong() ?: 0L

                val mime = content.document?.mimeType.orEmpty().lowercase()
                val name = content.document?.fileName.orEmpty().lowercase()
                val isVideo = mime.startsWith("video/") || name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".mov") || name.endsWith(".webm") || name.endsWith(".avi")
                val isImage = mime.startsWith("image/") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".webp") || name.endsWith(".heic") || name.endsWith(".gif")

                if (!isVideo && !isImage && parseBackupCaption(captionText) == null) {
                    return null
                }

                defaultMediaType = if (isVideo) "VIDEO" else "IMAGE"
                defaultFileName = content.document?.fileName?.ifBlank { "doc_${message.id}.${if (isVideo) "mp4" else "jpg"}" } ?: "doc_${message.id}.${if (isVideo) "mp4" else "jpg"}"
            }
            else -> return null
        }

        val parsedMeta = parseBackupCaption(captionText)
        val isOdinBackup = parsedMeta != null

        val finalMetadata = parsedMeta ?: CloudBackupMetadata(
            fileName = defaultFileName,
            mediaType = defaultMediaType,
            sizeBytes = defaultSizeBytes,
            fileHash = "msg_${message.id}",
            timestamp = message.date * 1000L
        )

        return ExtractedMediaInfo(
            captionText = captionText,
            originalFileId = originalFileId,
            thumbnailFileId = thumbnailFileId,
            metadata = finalMetadata,
            mediaWidth = mediaWidth,
            mediaHeight = mediaHeight,
            mediaDuration = mediaDuration,
            isOdinBackup = isOdinBackup
        )
    }

    suspend fun discoverBackups(context: Context): List<DiscoveredCloudItem> {
        val audit = auditSavedMessagesHistory(context)
        return audit.discoveredItems
    }

    data class HistoryAuditResult(
        val pagesFetched: Int,
        val messagesExamined: Int,
        val oldestMessageId: Long,
        val newestMessageId: Long,
        val photosDiscovered: Int,
        val videosDiscovered: Int,
        val documentsDiscovered: Int,
        val odinBackupMessagesFound: Int,
        val validMetadataParsed: Int,
        val validRemoteMappings: Int,
        val duplicateRemoteMessages: Int,
        val skippedSummary: Map<String, Int>,
        val discoveredItems: List<DiscoveredCloudItem>
    )

    suspend fun auditSavedMessagesHistory(context: Context): HistoryAuditResult {
        val chatId = getSavedMessagesChatId(context)
        val discovered = mutableListOf<DiscoveredCloudItem>()
        val seenMessageIds = mutableSetOf<Long>()
        val seenHashes = mutableSetOf<String>()

        var pagesFetched = 0
        var messagesExamined = 0
        var odinBackupMessagesFound = 0
        var validMetadataParsed = 0
        var validRemoteMappings = 0
        var duplicateRemoteMessages = 0

        var oldestMessageId = Long.MAX_VALUE
        var newestMessageId = 0L
        var photosDiscovered = 0
        var videosDiscovered = 0
        var documentsDiscovered = 0

        val skippedSummary = mutableMapOf(
            "not_odin_backup" to 0,
            "no_media_content" to 0,
            "duplicate_message_id" to 0
        )

        var fromMessageId = 0L
        var hasMore = true
        val batchSize = 100

        Log.d("TelegramDiscovery", "Starting audit for Saved Messages (chatId=$chatId)")

        while (hasMore) {
            Log.d("TelegramDiscovery", "Fetching page ${pagesFetched + 1} fromMessageId=$fromMessageId")
            val history = try {
                TelegramClientHolder.sendWithTimeout(
                    context,
                    TdApi.GetChatHistory(
                        chatId,
                        fromMessageId,
                        0,
                        batchSize,
                        false
                    ),
                    20_000L
                )
            } catch (e: Exception) {
                Log.e("TelegramDiscovery", "Failed to fetch history at page ${pagesFetched + 1}: ${e.message}")
                break
            }

            pagesFetched++
            val rawMessages = history.messages.orEmpty()
            Log.d("TelegramDiscovery", "Received ${rawMessages.size} messages from TDLib")

            if (rawMessages.isEmpty()) {
                Log.d("TelegramDiscovery", "End of history reached (empty page)")
                hasMore = false
                break
            }

            val messages = if (fromMessageId != 0L && rawMessages.firstOrNull()?.id == fromMessageId) {
                rawMessages.drop(1)
            } else {
                rawMessages.toList()
            }

            if (messages.isEmpty()) {
                Log.d("TelegramDiscovery", "End of history reached (all messages in page already processed or empty after drop)")
                hasMore = false
                break
            }

            Log.d("TelegramDiscovery", "Processing ${messages.size} unique messages in this page")

            for (message in messages) {
                messagesExamined++

                if (message.id > newestMessageId) newestMessageId = message.id
                if (message.id < oldestMessageId) oldestMessageId = message.id

                if (!seenMessageIds.add(message.id)) {
                    skippedSummary["duplicate_message_id"] = (skippedSummary["duplicate_message_id"] ?: 0) + 1
                    continue
                }

                val mediaInfo = extractMediaInfo(message)
                if (mediaInfo == null) {
                    skippedSummary["no_media_content"] = (skippedSummary["no_media_content"] ?: 0) + 1
                    continue
                }

                when (message.content) {
                    is TdApi.MessagePhoto -> photosDiscovered++
                    is TdApi.MessageVideo -> videosDiscovered++
                    is TdApi.MessageDocument -> documentsDiscovered++
                }

                if (mediaInfo.isOdinBackup) {
                    odinBackupMessagesFound++
                    validMetadataParsed++
                }

                val metadata = mediaInfo.metadata
                val resolvedHash = metadata.fileHash.ifBlank { "legacy_msg_${message.id}" }
                val resolvedName = if (metadata.fileName == "odin_backup_file") {
                    "odin_media_${message.id}.${if (metadata.mediaType == "VIDEO") "mp4" else "jpg"}"
                } else metadata.fileName
                val resolvedTs = if (metadata.timestamp == 0L) message.date * 1000L else metadata.timestamp

                val finalMetadata = metadata.copy(
                    fileName = resolvedName,
                    fileHash = resolvedHash,
                    timestamp = resolvedTs
                )

                if (resolvedHash.isNotBlank() && !resolvedHash.startsWith("msg_") && !seenHashes.add(resolvedHash)) {
                    duplicateRemoteMessages++
                }

                validRemoteMappings++
                discovered.add(
                    DiscoveredCloudItem(
                        messageId = message.id,
                        telegramFileId = mediaInfo.originalFileId,
                        metadata = finalMetadata,
                        telegramDate = message.date
                    )
                )
            }

            val lastMsgId = messages.last().id
            if (lastMsgId != 0L && lastMsgId != fromMessageId) {
                Log.d("TelegramDiscovery", "Page ${pagesFetched} complete. Last message ID: $lastMsgId. Total discovered so far: ${discovered.size}")
                fromMessageId = lastMsgId
            } else {
                Log.d("TelegramDiscovery", "End of history reached (lastMsgId same as fromMessageId or 0)")
                hasMore = false
            }
        }

        val safeOldest = if (oldestMessageId == Long.MAX_VALUE) 0L else oldestMessageId

        Log.i("TelegramDiscovery", "Audit complete. Pages: $pagesFetched, Messages Examined: $messagesExamined, Discovered: ${discovered.size}")
        Log.i("TelegramDiscovery", "Skipped: $skippedSummary")

        return HistoryAuditResult(
            pagesFetched = pagesFetched,
            messagesExamined = messagesExamined,
            oldestMessageId = safeOldest,
            newestMessageId = newestMessageId,
            photosDiscovered = photosDiscovered,
            videosDiscovered = videosDiscovered,
            documentsDiscovered = documentsDiscovered,
            odinBackupMessagesFound = odinBackupMessagesFound,
            validMetadataParsed = validMetadataParsed,
            validRemoteMappings = validRemoteMappings,
            duplicateRemoteMessages = duplicateRemoteMessages,
            skippedSummary = skippedSummary,
            discoveredItems = discovered
        )
    }

    suspend fun deleteCloudBackups(context: Context, messageIds: LongArray): Boolean {
        if (messageIds.isEmpty()) return true
        val chatId = getSavedMessagesChatId(context)
        TelegramClientHolder.send(
            context,
            TdApi.DeleteMessages(chatId, messageIds, true)
        )
        return true
    }
}
