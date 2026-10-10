package com.example

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.remote.GeminiApiService
import com.example.data.repository.AiRepository
import com.example.model.MediaItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AiRepositoryTest {

    @Test
    fun testUnconfiguredApiKeyReturnsSafeError() = runBlocking {
        val service = GeminiApiService()
        val result = service.generateContent(
            prompt = "Hello AI",
            apiKeyOverride = "" // Empty override to simulate missing key
        )

        assertTrue("Unconfigured key must return failure Result", result.isFailure)
        val error = result.exceptionOrNull()
        assertNotNull(error)
        assertTrue(
            "Error message must inform user about missing GEMINI_API_KEY",
            error!!.message?.contains("GEMINI_API_KEY") == true
        )
    }

    @Test
    fun testAiSearchPromptFormatting() = runBlocking {
        val aiRepo = AiRepository()
        val items = listOf(
            MediaItem(
                id = 101L,
                uri = Uri.EMPTY,
                displayName = "sunset.jpg",
                mimeType = "image/jpeg",
                dateAdded = 1000L,
                dateModified = 1000L,
                size = 2048L,
                width = 1080,
                height = 1920,
                isVideo = false,
                bucketDisplayName = "Vacation"
            ),
            MediaItem(
                id = 102L,
                uri = Uri.EMPTY,
                displayName = "beach_party.mp4",
                mimeType = "video/mp4",
                dateAdded = 2000L,
                dateModified = 2000L,
                size = 102400L,
                width = 1920,
                height = 1080,
                isVideo = true,
                bucketDisplayName = "Vacation"
            )
        )

        // Empty search query returns all item IDs immediately without network call
        val emptyResult = aiRepo.searchMediaWithAi("", items)
        assertTrue(emptyResult.isSuccess)
        assertEquals(2, emptyResult.getOrNull()?.size)
        assertEquals(listOf(101L, 102L), emptyResult.getOrNull())
    }
}
