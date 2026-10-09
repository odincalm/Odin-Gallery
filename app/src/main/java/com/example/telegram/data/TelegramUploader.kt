package com.example.telegram.data

import android.content.Context
import com.example.telegram.client.TelegramClientHolder
import com.example.telegram.client.TelegramSavedMessagesHelper
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withTimeoutOrNull
import org.drinkless.tdlib.TdApi
import java.io.File

object TelegramUploader {

    suspend fun uploadMedia(
        context: Context,
        uriString: String,
        fileName: String,
        isVideo: Boolean,
        sizeBytes: Long,
        fileHash: String
    ): Result<Long> {
        val file = TelegramFileUtil.getOrPrepareLocalFilePath(context, uriString, fileName)
            ?: return Result.failure(IllegalStateException("Local file not accessible or expired: $fileName"))

        val isTempFile = file.absolutePath.contains("tdlib_upload")

        try {
            val chatId = TelegramSavedMessagesHelper.getSavedMessagesChatId(context)
            val caption = TelegramSavedMessagesHelper.createBackupCaption(
                fileName = fileName,
                mediaType = if (isVideo) "VIDEO" else "IMAGE",
                sizeBytes = sizeBytes,
                fileHash = fileHash
            )

            val formattedCaption = TdApi.FormattedText(caption, emptyArray())
            val inputFile = TdApi.InputFileLocal(file.absolutePath)

            val inputContent: TdApi.InputMessageContent = if (isVideo) {
                val inputVideo = TdApi.InputVideo(
                    inputFile,
                    null, // thumbnail
                    null, // cover
                    0,    // startTimestamp
                    IntArray(0),
                    0,    // duration
                    0,    // width
                    0,    // height
                    true  // supportsStreaming
                )
                TdApi.InputMessageVideo(
                    inputVideo,
                    formattedCaption,
                    false, // showCaptionAboveMedia
                    null,  // selfDestructType
                    false  // hasSpoiler
                )
            } else {
                val inputPhoto = TdApi.InputPhoto(
                    inputFile,
                    null, // thumbnail
                    null, // video
                    IntArray(0),
                    0,    // width
                    0     // height
                )
                TdApi.InputMessagePhoto(
                    inputPhoto,
                    formattedCaption,
                    false, // showCaptionAboveMedia
                    null,  // selfDestructType
                    false  // hasSpoiler
                )
            }

            val sendMessageFunction = TdApi.SendMessage(
                chatId,
                null, // topicId
                null, // replyTo
                null, // options
                null, // replyMarkup
                inputContent
            )

            val sentMessage = TelegramClientHolder.send(context, sendMessageFunction)

            // If message was already confirmed sent immediately (sendingState is null)
            if (sentMessage.sendingState == null) {
                return Result.success(sentMessage.id)
            }

            // Otherwise, wait for TDLib upload confirmation with a 120-second timeout
            val confirmedId = withTimeoutOrNull(120_000L) {
                try {
                    TelegramClientHolder.getUpdates(context)
                        .filterIsInstance<TdApi.Update>()
                        .mapNotNull { update ->
                            when (update) {
                                is TdApi.UpdateMessageSendSucceeded -> {
                                    if (update.oldMessageId == sentMessage.id) update.message.id else null
                                }
                                is TdApi.UpdateMessageSendFailed -> {
                                    if (update.oldMessageId == sentMessage.id) {
                                        throw IllegalStateException("Upload failed: ${update.error.message ?: "Unknown TDLib error"}")
                                    } else null
                                }
                                else -> null
                            }
                        }
                        .first()
                } catch (e: Exception) {
                    if (e is IllegalStateException) throw e
                    null
                }
            } ?: sentMessage.id

            return Result.success(confirmedId)
        } catch (e: Exception) {
            return Result.failure(e)
        } finally {
            // ONLY delete the temporary copy after upload has completed or failed!
            if (isTempFile) {
                try {
                    file.delete()
                } catch (ignored: Exception) {}
            }
        }
    }
}
