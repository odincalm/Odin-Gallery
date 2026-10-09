package com.example

import com.example.telegram.client.TelegramSavedMessagesHelper
import com.example.telegram.model.NetworkPreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramBackupTest {

    @Test
    fun testBackupCaptionFormattingAndParsing() {
        val fileName = "IMG_2026_VACATION.jpg"
        val mediaType = "IMAGE"
        val sizeBytes = 3_452_100L
        val fileHash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        val timestamp = 1791123456789L

        val caption = TelegramSavedMessagesHelper.createBackupCaption(
            fileName = fileName,
            mediaType = mediaType,
            sizeBytes = sizeBytes,
            fileHash = fileHash,
            timestamp = timestamp
        )

        assertTrue("Caption must contain #ODIN_BACKUP marker", caption.contains("#ODIN_BACKUP"))

        val parsed = TelegramSavedMessagesHelper.parseBackupCaption(caption)
        assertNotNull("Parsed metadata should not be null", parsed)
        assertEquals(fileName, parsed!!.fileName)
        assertEquals(mediaType, parsed.mediaType)
        assertEquals(sizeBytes, parsed.sizeBytes)
        assertEquals(fileHash, parsed.fileHash)
        assertEquals(timestamp, parsed.timestamp)
    }

    @Test
    fun testOrdinaryTelegramMessagesIgnored() {
        val userNotes = "Remember to buy groceries on Tuesday!"
        val parsedNote = TelegramSavedMessagesHelper.parseBackupCaption(userNotes)
        assertNull("User personal notes must not be identified as backup", parsedNote)

        val regularPhotoCaption = "Sunset at the beach 🌅"
        val parsedCaption = TelegramSavedMessagesHelper.parseBackupCaption(regularPhotoCaption)
        assertNull("Ordinary photo captions must not be identified as backup", parsedCaption)

        val nullCaption = TelegramSavedMessagesHelper.parseBackupCaption(null)
        assertNull("Null caption must be null", nullCaption)
    }

    @Test
    fun testVideoBackupCaptionFormattingAndParsing() {
        val fileName = "VID_2026_BIRTHDAY.mp4"
        val mediaType = "VIDEO"
        val sizeBytes = 154_230_000L
        val fileHash = "a1b2c3d4e5f67890123456789abcdef0123456789abcdef0123456789abcdef0"
        val timestamp = 1791999999999L

        val caption = TelegramSavedMessagesHelper.createBackupCaption(
            fileName = fileName,
            mediaType = mediaType,
            sizeBytes = sizeBytes,
            fileHash = fileHash,
            timestamp = timestamp
        )

        val parsed = TelegramSavedMessagesHelper.parseBackupCaption(caption)
        assertNotNull(parsed)
        assertEquals("VIDEO", parsed!!.mediaType)
        assertEquals(fileName, parsed.fileName)
        assertEquals(sizeBytes, parsed.sizeBytes)
    }

    @Test
    fun testNetworkPreferenceLabels() {
        assertEquals("Wi-Fi only", NetworkPreference.WIFI_ONLY.label)
        assertEquals("Mobile data", NetworkPreference.MOBILE_DATA.label)
        assertEquals("Wi-Fi and mobile data", NetworkPreference.WIFI_AND_MOBILE.label)
    }
}
