package com.example

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.ui.components.DonateDialog
import com.example.ui.screens.DonateScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.OdinGalleryTheme
import com.example.ui.viewmodel.GalleryViewModel
import com.example.util.QrCodeGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class DonateTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testQrCodeGeneratorValidInputAndCaching() {
        val upiString = "upi://pay?pa=bholanitin308@okicici&pn=Nitin%20Kumar&cu=INR"
        val bitmap = QrCodeGenerator.generateQrBitmap(upiString, 256)
        assertNotNull("Bitmap must not be null for valid UPI URI", bitmap)
        assertEquals(256, bitmap!!.width)
        assertEquals(256, bitmap.height)

        // Caching test: next call returns cached instance immediately
        val cached = QrCodeGenerator.getCached(upiString)
        assertNotNull("Cached bitmap must be immediately available", cached)
        assertEquals(bitmap, cached)
    }

    @Test
    fun testQrCodeGeneratorInvalidInputDoesNotCrash() {
        assertNull(QrCodeGenerator.generateQrBitmap(null))
        assertNull(QrCodeGenerator.generateQrBitmap(""))
        assertNull(QrCodeGenerator.generateQrBitmap("   "))
        assertNull(QrCodeGenerator.generateQrBitmap("valid", -1))
        assertNull(QrCodeGenerator.generateQrBitmap("valid", 0))
    }

    @Test
    fun testDonateScreenRendersQrAndUpiId() {
        var backClicked = false
        composeTestRule.setContent {
            OdinGalleryTheme {
                DonateScreen(
                    onBack = { backClicked = true },
                    upiId = "bholanitin308@okicici",
                    payeeName = "Nitin Kumar"
                )
            }
        }
        composeTestRule.waitForIdle()

        // Verify Title and UPI ID are visible
        composeTestRule.onNodeWithText("Support Development").assertIsDisplayed()
        composeTestRule.onNodeWithTag("donate_screen_root").assertIsDisplayed()
        composeTestRule.onNodeWithTag("donate_upi_card").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("donate_upi_text", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("donate_copy_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("donate_pay_button").performScrollTo().assertIsDisplayed()

        // Verify Back button works
        composeTestRule.onNodeWithTag("donate_back_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertTrue("Back callback should have been invoked", backClicked)
    }

    @Test
    fun testDonateScreenFallbackOnMissingUpiId() {
        composeTestRule.setContent {
            OdinGalleryTheme {
                DonateScreen(
                    onBack = {},
                    upiId = "",
                    payeeName = ""
                )
            }
        }
        composeTestRule.waitForIdle()

        // Should gracefully display unavailable message instead of crashing
        composeTestRule.onNodeWithText("UPI ID is currently unavailable").assertIsDisplayed()
        composeTestRule.onNodeWithText("Support Development").assertIsDisplayed()
        composeTestRule.onNodeWithTag("donate_qr_fallback").assertIsDisplayed()
    }

    @Test
    fun testSettingsScreenTriggersOnOpenDonate() {
        val app = ApplicationProvider.getApplicationContext<OdinApplication>()
        val viewModel = GalleryViewModel(app)
        var donateOpened = false

        composeTestRule.setContent {
            OdinGalleryTheme {
                SettingsScreen(
                    viewModel = viewModel,
                    onBack = {},
                    onOpenHiddenVault = {},
                    onOpenDonate = { donateOpened = true }
                )
            }
        }
        composeTestRule.waitForIdle()

        // Scroll to and click Donate
        composeTestRule.onNodeWithTag("donate_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        assertTrue("onOpenDonate should be called when Donate is clicked", donateOpened)
    }

    @Test
    fun testRepeatedOpenAndBackDonateFlow() {
        var currentScreen by mutableStateOf("SETTINGS")
        val app = ApplicationProvider.getApplicationContext<OdinApplication>()
        val viewModel = GalleryViewModel(app)

        composeTestRule.setContent {
            OdinGalleryTheme {
                when (currentScreen) {
                    "SETTINGS" -> SettingsScreen(
                        viewModel = viewModel,
                        onBack = {},
                        onOpenHiddenVault = {},
                        onOpenDonate = { currentScreen = "DONATE" }
                    )
                    "DONATE" -> DonateScreen(
                        onBack = { currentScreen = "SETTINGS" }
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        // Repeat open and back 5 times to verify no memory leaks, state issues, or crashes
        for (i in 1..5) {
            composeTestRule.onNodeWithTag("donate_button").performScrollTo().performClick()
            composeTestRule.waitForIdle()
            assertEquals("DONATE", currentScreen)
            composeTestRule.onNodeWithTag("donate_back_button").performClick()
            composeTestRule.waitForIdle()
            assertEquals("SETTINGS", currentScreen)
        }
    }

    @Test
    fun testDonateDialogRendersWithoutCrash() {
        var dismissed = false
        composeTestRule.setContent {
            OdinGalleryTheme {
                DonateDialog(onDismiss = { dismissed = true })
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Support Development").assertIsDisplayed()
        composeTestRule.onNodeWithText("Close").performClick()
        assertTrue(dismissed)
    }
}
