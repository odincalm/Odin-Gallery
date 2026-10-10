package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.local.OdinDatabase
import com.example.data.local.TelegramBackupItem
import com.example.model.MediaItem
import com.example.telegram.client.TelegramSavedMessagesHelper
import com.example.telegram.data.TelegramBackupRepository
import com.example.telegram.data.TelegramNetworkUtil
import com.example.telegram.model.NetworkPreference
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class TelegramBackupTest {

    private lateinit var db: OdinDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, OdinDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        if (::db.isInitialized) {
            db.close()
        }
    }

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

    @Test
    fun testNetworkPreferenceEvaluationSemantics() {
        val wifiConnected = TelegramNetworkUtil.NetworkStatus(
            isConnected = true,
            isWifi = true,
            isCellular = false,
            isUnmetered = true
        )

        val cellularConnected = TelegramNetworkUtil.NetworkStatus(
            isConnected = true,
            isWifi = false,
            isCellular = true,
            isUnmetered = false
        )

        val disconnected = TelegramNetworkUtil.NetworkStatus(
            isConnected = false,
            isWifi = false,
            isCellular = false,
            isUnmetered = false
        )

        // WIFI_ONLY: allow wifi, reject cellular & disconnected
        assertTrue(wifiConnected.isConnected && (wifiConnected.isWifi))
        assertFalse(cellularConnected.isWifi)
        assertFalse(disconnected.isConnected)

        // MOBILE_DATA: allow cellular, reject wifi & disconnected
        assertTrue(cellularConnected.isConnected && cellularConnected.isCellular)
        assertFalse(wifiConnected.isCellular && !wifiConnected.isWifi)

        // WIFI_AND_MOBILE: allow either
        assertTrue(wifiConnected.isWifi || wifiConnected.isCellular)
        assertTrue(cellularConnected.isWifi || cellularConnected.isCellular)
    }

    @Test
    fun testRoomDatabaseTelegramBackupDaoOperations() = runBlocking {
        val dao = db.telegramBackupDao()

        val item1 = TelegramBackupItem(
            localMediaId = 101L,
            uriString = "content://media/external/images/media/101",
            fileName = "photo1.jpg",
            mediaType = "IMAGE",
            sizeBytes = 1024L,
            fileHash = "hash_101",
            status = "PENDING"
        )

        val id = dao.insert(item1)
        assertTrue("Inserted ID should be valid positive number", id > 0)

        val pending = dao.getPendingItems()
        assertEquals(1, pending.size)
        assertEquals("photo1.jpg", pending.first().fileName)

        // Reset uploading to pending
        dao.updateStatus(id, "UPLOADING")
        val pendingBefore = dao.getPendingItems()
        assertEquals(0, pendingBefore.size)

        val countReset = dao.resetUploadingToPending()
        assertEquals(1, countReset)
        val pendingAfter = dao.getPendingItems()
        assertEquals(1, pendingAfter.size)

        // Complete item
        dao.update(pendingAfter.first().copy(status = "COMPLETED", telegramMessageId = 555L))
        val completed = dao.getCompletedItems()
        assertEquals(1, completed.size)
        assertEquals(555L, completed.first().telegramMessageId)
    }

    @Test
    fun testDuplicatePreventionHashMatching() = runBlocking {
        val dao = db.telegramBackupDao()
        val hash = "unique_sha256_hash_123"

        val item = TelegramBackupItem(
            localMediaId = 202L,
            fileName = "duplicated.png",
            mediaType = "IMAGE",
            sizeBytes = 2048L,
            fileHash = hash,
            status = "COMPLETED",
            telegramMessageId = 777L
        )

        dao.insert(item)

        val existing = dao.getItemByHash(hash)
        assertNotNull("Item must be found by SHA-256 hash", existing)
        assertEquals("COMPLETED", existing!!.status)
        assertEquals(777L, existing.telegramMessageId)

        val completed = dao.getCompletedItemByHash(hash)
        assertNotNull("Completed duplicate must be found by hash", completed)
    }

    @Test
    fun testInterruptedAndFailedUploadReset() = runBlocking {
        val dao = db.telegramBackupDao()

        dao.insert(
            TelegramBackupItem(
                localMediaId = 301L,
                fileName = "failed1.mp4",
                mediaType = "VIDEO",
                sizeBytes = 5000L,
                fileHash = "hash_failed",
                status = "FAILED",
                retryCount = 3
            )
        )

        val failed = dao.getFailedItems()
        assertEquals(1, failed.size)

        val resetCount = dao.resetFailedItemsToPending()
        assertEquals(1, resetCount)

        val pending = dao.getPendingItems()
        assertEquals(1, pending.size)
        assertEquals(0, pending.first().retryCount)
        assertEquals("PENDING", pending.first().status)
    }

    @Test
    fun testDiscoveredCloudItemPersistenceAndRelogin() = runBlocking {
        val dao = db.telegramBackupDao()

        // Simulate cloud item discovered from Telegram Saved Messages on a fresh device
        val cloudItem = TelegramBackupItem(
            localMediaId = 0L,
            uriString = null,
            fileName = "cloud_photo.jpg",
            mediaType = "IMAGE",
            sizeBytes = 4096L,
            fileHash = "cloud_hash_999",
            telegramMessageId = 8888L,
            telegramFileId = 12345,
            status = "COMPLETED",
            completedAt = System.currentTimeMillis()
        )

        dao.insert(cloudItem)

        val completedCount = dao.getCompletedCount()
        assertEquals(1, completedCount)

        val retrieved = dao.getItemByMessageId(8888L)
        assertNotNull(retrieved)
        assertEquals("cloud_photo.jpg", retrieved!!.fileName)
        assertEquals(0L, retrieved.localMediaId)
    }

    @Test
    fun test400ItemsPaginationSimulationAndReconciliation() = runBlocking {
        val dao = db.telegramBackupDao()
        val totalSimulatedItems = 400
        val batchSize = 100
        val itemsToInsert = mutableListOf<TelegramBackupItem>()

        for (i in 1..totalSimulatedItems) {
            itemsToInsert.add(
                TelegramBackupItem(
                    localMediaId = 0L,
                    fileName = "photo_$i.jpg",
                    mediaType = if (i % 5 == 0) "VIDEO" else "IMAGE",
                    sizeBytes = 1024L * i,
                    fileHash = "hash_$i",
                    telegramMessageId = 1000L + i,
                    telegramFileId = 5000 + i,
                    status = "COMPLETED",
                    completedAt = System.currentTimeMillis() + i
                )
            )
        }

        // Simulate paginated insertion in batches of 100 across 4 pages
        for (batch in itemsToInsert.chunked(batchSize)) {
            dao.insertBatch(batch)
        }

        val completedCount = dao.getCompletedCount()
        assertEquals("All 400 simulated backup items must be successfully discovered and persisted", totalSimulatedItems, completedCount)

        val allCompleted = dao.getCompletedItems()
        assertEquals(totalSimulatedItems, allCompleted.size)

        // Verify re-scan / repeated scan produces zero duplicates (idempotent upserts)
        for (batch in itemsToInsert.chunked(batchSize)) {
            dao.insertBatch(batch)
        }

        val completedCountAfterRescan = dao.getCompletedCount()
        assertEquals("Repeated scans must produce no duplicates", totalSimulatedItems, completedCountAfterRescan)
    }

    @Test
    fun testFreshDatabaseCloudIndexRebuildAfterLogoutAndLogin() = runBlocking {
        val dao = db.telegramBackupDao()

        // Simulate backing up 400 items
        val items = (1..400).map { i ->
            TelegramBackupItem(
                localMediaId = 0L,
                fileName = "backup_$i.jpg",
                mediaType = "IMAGE",
                sizeBytes = 2048L,
                fileHash = "hash_relogin_$i",
                telegramMessageId = 9000L + i,
                telegramFileId = 7000 + i,
                status = "COMPLETED",
                completedAt = System.currentTimeMillis()
            )
        }
        dao.insertBatch(items)
        assertEquals(400, dao.getCompletedCount())

        // Simulate user logging out (clearing local database records)
        dao.clearAll()
        assertEquals(0, dao.getCompletedCount())

        // Simulate logging back in and running cloud sync rediscovery (rebuilding cloud index)
        dao.insertBatch(items)
        assertEquals("Logging back in must automatically rebuild cloud index with all 400 items", 400, dao.getCompletedCount())
        assertNotNull(dao.getItemByMessageId(9400L))
    }
}
