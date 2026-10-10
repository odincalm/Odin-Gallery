package com.example.telegram.client

import android.content.Context
import com.example.telegram.model.CloudBackupMetadata
import com.example.telegram.model.DiscoveredCloudItem
import org.drinkless.tdlib.TdApi

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
        if (captionText == null || !captionText.contains(TAG_PREFIX)) return null
        val lines = captionText.lines()
        var fileName = ""
        var mediaType = "IMAGE"
        var sizeBytes = 0L
        var fileHash = ""
        var timestamp = 0L

        for (line in lines) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("fn:") -> fileName = trimmed.removePrefix("fn:").trim()
                trimmed.startsWith("type:") -> mediaType = trimmed.removePrefix("type:").trim().uppercase()
                trimmed.startsWith("size:") -> sizeBytes = trimmed.removePrefix("size:").trim().toLongOrNull() ?: 0L
                trimmed.startsWith("hash:") -> fileHash = trimmed.removePrefix("hash:").trim()
                trimmed.startsWith("ts:") -> timestamp = trimmed.removePrefix("ts:").trim().toLongOrNull() ?: 0L
            }
        }
        if (fileName.isBlank() || fileHash.isBlank()) return null
        return CloudBackupMetadata(
            fileName = fileName,
            mediaType = mediaType,
            sizeBytes = sizeBytes,
            fileHash = fileHash,
            timestamp = timestamp
        )
    }

    suspend fun discoverBackups(context: Context): List<DiscoveredCloudItem> {
        val chatId = getSavedMessagesChatId(context)
        val discovered = mutableListOf<DiscoveredCloudItem>()
        var fromMessageId = 0L
        var hasMore = true
        val batchSize = 100

        while (hasMore) {
            val found = try {
                TelegramClientHolder.sendWithTimeout(
                    context,
                    TdApi.SearchChatMessages(
                        chatId,
                        null,
                        TAG_PREFIX,
                        null,
                        fromMessageId,
                        0,
                        batchSize,
                        null
                    ),
                    15_000L
                )
            } catch (e: Exception) {
                break
            }

            if (found.messages.isEmpty()) {
                hasMore = false
                break
            }

            for (message in found.messages) {
                var captionText: String? = null
                var fileId = 0

                when (val content = message.content) {
                    is TdApi.MessagePhoto -> {
                        captionText = content.caption?.text
                        val largestSize = content.photo?.sizes?.maxByOrNull { it.width * it.height }
                        fileId = largestSize?.photo?.id ?: 0
                    }
                    is TdApi.MessageVideo -> {
                        captionText = content.caption?.text
                        fileId = content.video?.video?.id ?: 0
                    }
                }

                val metadata = parseBackupCaption(captionText)
                if (metadata != null && fileId != 0) {
                    discovered.add(
                        DiscoveredCloudItem(
                            messageId = message.id,
                            telegramFileId = fileId,
                            metadata = metadata,
                            telegramDate = message.date
                        )
                    )
                }
            }

            val lastMsg = found.messages.lastOrNull()
            if (lastMsg != null && lastMsg.id != fromMessageId) {
                fromMessageId = lastMsg.id
            } else {
                hasMore = false
            }
        }
        return discovered
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
