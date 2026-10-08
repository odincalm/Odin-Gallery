package com.example

import android.Manifest
import android.app.Activity
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.navigation.OdinMainNavigation
import com.example.ui.screens.WelcomePermissionScreen
import com.example.ui.theme.OdinGalleryTheme
import com.example.ui.viewmodel.GalleryViewModel
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class MainActivity : ComponentActivity() {

    private val galleryViewModel: GalleryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Synchronize with device display refresh rate (60Hz, 90Hz, 120Hz, 144Hz)
        // Supports dynamic refresh-rate adaptation (VRR/LTPO) without forcing rigid display modes
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val modes = display?.supportedModes ?: emptyArray()
                val highestMode = modes.filter { it.refreshRate >= 90f }.maxByOrNull { it.refreshRate }
                if (highestMode != null) {
                    window.attributes = window.attributes.apply {
                        preferredDisplayModeId = highestMode.modeId
                    }
                }
            } catch (e: Exception) {
                // Graceful fallback to system display manager defaults
            }
        }

        setContent {
            val themeMode by galleryViewModel.themeMode.collectAsStateWithLifecycle()
            val isDarkTheme = when (themeMode) {
                "DARK" -> true
                "LIGHT" -> false
                else -> isSystemInDarkTheme()
            }

            var pendingIntentSenderCallback by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
            val intentSenderLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartIntentSenderForResult()
            ) { result ->
                pendingIntentSenderCallback?.invoke(result.resultCode == Activity.RESULT_OK)
                pendingIntentSenderCallback = null
            }

            LaunchedEffect(Unit) {
                galleryViewModel.intentSenderRequester = { intentSender ->
                    suspendCancellableCoroutine { continuation ->
                        pendingIntentSenderCallback = { success ->
                            if (continuation.isActive) continuation.resume(success)
                        }
                        try {
                            intentSenderLauncher.launch(
                                IntentSenderRequest.Builder(intentSender).build()
                            )
                        } catch (e: Exception) {
                            e.printStackTrace()
                            pendingIntentSenderCallback = null
                            if (continuation.isActive) continuation.resume(false)
                        }
                    }
                }
            }

            OdinGalleryTheme(darkTheme = isDarkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var hasPermission by remember { mutableStateOf(checkMediaPermissions()) }

                    // Re-check when app regains focus or state changes
                    LaunchedEffect(hasPermission) {
                        if (hasPermission) {
                            galleryViewModel.refresh()
                        }
                    }

                    if (hasPermission) {
                        OdinMainNavigation(viewModel = galleryViewModel)
                    } else {
                        WelcomePermissionScreen(
                            onPermissionGranted = {
                                hasPermission = true
                                galleryViewModel.refresh()
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (checkMediaPermissions()) {
            galleryViewModel.refresh()
        }
    }

    private fun checkMediaPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val images = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
            val video = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
            val visualUser = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED
            (images && video) || visualUser
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val images = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
            val video = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
            images && video
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }
}
