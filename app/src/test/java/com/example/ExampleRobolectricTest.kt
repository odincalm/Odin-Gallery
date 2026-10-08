package com.example

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.data.repository.MediaStoreScanner
import com.example.model.MediaItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("ODIN Gallery", appName)
  }

  @Test
  fun `verify app tagline`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val tagline = context.getString(R.string.app_tagline)
    assertEquals("Your photos. Your device. Your privacy.", tagline)
  }

  @Test
  fun `test media item duration and size formatting`() {
    val dummyUri = Uri.parse("content://media/external/video/media/1")
    val item = MediaItem(
      id = 1L,
      uri = dummyUri,
      displayName = "sample.mp4",
      mimeType = "video/mp4",
      dateAdded = 1700000000L,
      dateModified = 1700000000L,
      size = 10_485_760L,
      width = 1920,
      height = 1080,
      duration = 125_000L,
      isVideo = true
    )

    assertEquals("2:05", item.durationFormatted)
    assertTrue(item.sizeFormatted.contains("10.0 MB"))
    assertEquals("1920 × 1080", item.resolutionFormatted)
  }

  @Test
  fun `test media store automatic categorization`() {
    val dummyUri = Uri.parse("content://media/external/images/media/10")

    val cameraPhoto = MediaItem(
      id = 10L,
      uri = dummyUri,
      displayName = "IMG_20261004.jpg",
      mimeType = "image/jpeg",
      dateAdded = 1700000000L,
      dateModified = 1700000000L,
      size = 2_000_000L,
      width = 4000,
      height = 3000,
      bucketDisplayName = "Camera",
      relativePath = "DCIM/Camera/"
    )
    assertTrue(MediaStoreScanner.isCameraMedia(cameraPhoto))
    assertFalse(MediaStoreScanner.isScreenshotMedia(cameraPhoto))
    assertFalse(MediaStoreScanner.isDownloadMedia(cameraPhoto))

    val screenshot = MediaItem(
      id = 11L,
      uri = dummyUri,
      displayName = "Screenshot_20261004-123456.png",
      mimeType = "image/png",
      dateAdded = 1700000000L,
      dateModified = 1700000000L,
      size = 500_000L,
      width = 1080,
      height = 2400,
      bucketDisplayName = "Screenshots",
      relativePath = "Pictures/Screenshots/"
    )
    assertTrue(MediaStoreScanner.isScreenshotMedia(screenshot))
    assertFalse(MediaStoreScanner.isCameraMedia(screenshot))

    val screenRecording = MediaItem(
      id = 12L,
      uri = dummyUri,
      displayName = "Screen_Recording_20261004.mp4",
      mimeType = "video/mp4",
      dateAdded = 1700000000L,
      dateModified = 1700000000L,
      size = 15_000_000L,
      width = 1080,
      height = 2400,
      duration = 45_000L,
      isVideo = true,
      bucketDisplayName = "ScreenRecordings",
      relativePath = "Movies/Screen records/"
    )
    assertTrue(MediaStoreScanner.isScreenRecordingMedia(screenRecording))
    assertFalse(MediaStoreScanner.isScreenshotMedia(screenRecording))
    assertFalse(MediaStoreScanner.isCameraMedia(screenRecording))

    val download = MediaItem(
      id = 13L,
      uri = dummyUri,
      displayName = "wallpaper_download.jpg",
      mimeType = "image/jpeg",
      dateAdded = 1700000000L,
      dateModified = 1700000000L,
      size = 1_000_000L,
      width = 1920,
      height = 1080,
      bucketDisplayName = "Download",
      relativePath = "Download/"
    )
    assertTrue(MediaStoreScanner.isDownloadMedia(download))
    assertFalse(MediaStoreScanner.isCameraMedia(download))
  }
}
