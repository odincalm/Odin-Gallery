package com.example.data.repository

import android.content.Context
import android.net.Uri
import com.example.data.remote.GeminiApiService
import com.example.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AiRepository(
    private val apiService: GeminiApiService = GeminiApiService()
) {

    suspend fun describeMediaItem(context: Context, item: MediaItem): Result<String> = withContext(Dispatchers.IO) {
        if (item.isVideo) {
            return@withContext apiService.generateContent(
                prompt = "Provide a summary description of a video file named '${item.displayName}' with duration ${item.durationFormatted} for a gallery app."
            )
        }
        apiService.describeImage(
            context = context,
            imageUri = item.uri,
            prompt = "Analyze this photo for a mobile gallery app. Describe what is shown in 2-3 sentences."
        )
    }

    suspend fun searchMediaWithAi(
        query: String,
        activeMedia: List<MediaItem>
    ): Result<List<Long>> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            return@withContext Result.success(activeMedia.map { it.id })
        }

        val prompt = "Find media matching the search term: '$trimmed'.\n" +
                "Media items list:\n" +
                activeMedia.take(100).joinToString("\n") { "ID:${it.id}, Name:${it.displayName}, Type:${if (it.isVideo) "video" else "photo"}, Album:${it.bucketDisplayName ?: "root"}" } +
                "\nReturn ONLY a JSON array of matching ID numbers (e.g. [101, 102]). No other text."

        val res = apiService.generateContent(prompt)
        res.map { jsonText ->
            try {
                val arrayText = jsonText.substringAfter("[").substringBeforeLast("]")
                if (arrayText.isBlank()) return@map emptyList()
                arrayText.split(",")
                    .mapNotNull { it.trim().toLongOrNull() }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
}
