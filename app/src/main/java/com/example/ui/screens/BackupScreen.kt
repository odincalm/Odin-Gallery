package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.MediaItem
import com.example.telegram.model.NetworkPreference
import com.example.telegram.model.TelegramAuthState
import com.example.ui.theme.OdinColors
import com.example.ui.theme.OdinTypography
import com.example.ui.viewmodel.GalleryViewModel
import com.example.ui.viewmodel.TelegramBackupViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BackupScreen(
    galleryViewModel: GalleryViewModel,
    onBack: () -> Unit,
    telegramViewModel: TelegramBackupViewModel = viewModel()
) {
    val activeMedia by galleryViewModel.activeMedia.collectAsStateWithLifecycle()
    val authState by telegramViewModel.authState.collectAsStateWithLifecycle()
    val backupStats by telegramViewModel.backupStats.collectAsStateWithLifecycle()
    val isBackupEnabled by telegramViewModel.isBackupEnabled.collectAsStateWithLifecycle()
    val networkPreference by telegramViewModel.networkPreference.collectAsStateWithLifecycle()
    val backupPhotos by telegramViewModel.backupPhotos.collectAsStateWithLifecycle()
    val backupVideos by telegramViewModel.backupVideos.collectAsStateWithLifecycle()
    val backupExistingMedia by telegramViewModel.backupExistingMedia.collectAsStateWithLifecycle()
    val backupNewMediaAutomatically by telegramViewModel.backupNewMediaAutomatically.collectAsStateWithLifecycle()
    val lastBackupTime by telegramViewModel.lastBackupTime.collectAsStateWithLifecycle()
    val isActionLoading by telegramViewModel.isActionLoading.collectAsStateWithLifecycle()
    val userFeedbackMessage by telegramViewModel.userFeedbackMessage.collectAsStateWithLifecycle()
    val testUploadStatus by telegramViewModel.testUploadStatus.collectAsStateWithLifecycle()
    val isTestingUpload by telegramViewModel.isTestingUpload.collectAsStateWithLifecycle()
    val isDiscovering by telegramViewModel.isDiscovering.collectAsStateWithLifecycle()
    val discoveredItems by telegramViewModel.discoveredItems.collectAsStateWithLifecycle()
    val isRestoring by telegramViewModel.isRestoring.collectAsStateWithLifecycle()
    val restoreProgress by telegramViewModel.restoreProgress.collectAsStateWithLifecycle()

    var showPhoneDialog by remember { mutableStateOf(false) }
    var showCodeDialog by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var showLogoutConfirmDialog by remember { mutableStateOf(false) }
    var showDeleteCloudConfirmDialog by remember { mutableStateOf(false) }
    var showRestoreSheetDialog by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    BackHandler {
        onBack()
    }

    // React to authState transitions to automatically pop dialogs when requested
    LaunchedEffect(authState) {
        when (authState) {
            is TelegramAuthState.WaitPhoneNumber -> {
                showPhoneDialog = true
                showCodeDialog = false
                showPasswordDialog = false
            }
            is TelegramAuthState.WaitCode -> {
                showPhoneDialog = false
                showCodeDialog = true
                showPasswordDialog = false
            }
            is TelegramAuthState.WaitPassword -> {
                showPhoneDialog = false
                showCodeDialog = false
                showPasswordDialog = true
            }
            is TelegramAuthState.Ready -> {
                showPhoneDialog = false
                showCodeDialog = false
                showPasswordDialog = false
            }
            else -> {}
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 24.dp)
        ) {
            // Header: Back button + Title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 20.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Telegram Backup",
                        style = OdinTypography.largeTitle,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Saved Messages Cloud Storage",
                        style = OdinTypography.footnote,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // User Feedback Banner
            userFeedbackMessage?.let { msg ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .clickable { telegramViewModel.clearFeedbackMessage() }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = msg,
                            style = OdinTypography.footnote,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // DISCONNECTED STATE: Clean intro + Connect Telegram only
            if (authState !is TelegramAuthState.Ready) {
                // Hero Banner
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(OdinColors.DarkAccentBlue.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CloudUpload,
                            contentDescription = "Cloud Backup",
                            tint = OdinColors.DarkAccentBlue,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Back Up to Saved Messages",
                        style = OdinTypography.title,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Store your personal photos and videos privately in your own Telegram account. No separate channels, no subscription fees, and accessible across all your devices.",
                        style = OdinTypography.body,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 22.sp
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Feature Highlights
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(16.dp)
                ) {
                    FeatureHighlightRow(
                        icon = Icons.Outlined.Lock,
                        title = "Private & Direct",
                        description = "Media is delivered directly into your personal Saved Messages chat."
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    FeatureHighlightRow(
                        icon = Icons.Default.CloudDone,
                        title = "Original Quality Preserved",
                        description = "Photos and videos maintain their full resolution, metadata, and timestamps."
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    FeatureHighlightRow(
                        icon = Icons.Outlined.Sync,
                        title = "Restore Anywhere",
                        description = "Easily discover and restore your media library onto new devices."
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Connection State / Action Button
                when (val state = authState) {
                    is TelegramAuthState.Connecting -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                strokeWidth = 2.5.dp,
                                color = OdinColors.DarkAccentBlue
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Connecting to Telegram...",
                                style = OdinTypography.body,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            TextButton(onClick = { telegramViewModel.cancelAuthentication() }) {
                                Text("Cancel", color = OdinColors.DarkDestructive)
                            }
                        }
                    }
                    is TelegramAuthState.Error -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(OdinColors.DarkDestructive.copy(alpha = 0.08f))
                                .border(1.dp, OdinColors.DarkDestructive.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                                .padding(18.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.WarningAmber,
                                    contentDescription = "Error",
                                    tint = OdinColors.DarkDestructive,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Connection Error",
                                    style = OdinTypography.headline,
                                    color = OdinColors.DarkDestructive
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = state.message,
                                style = OdinTypography.footnote,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = { telegramViewModel.resetError() }) {
                                    Text("Dismiss")
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        telegramViewModel.resetError()
                                        telegramViewModel.connectTelegram()
                                        showPhoneDialog = true
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = OdinColors.DarkAccentBlue)
                                ) {
                                    Text("Try Again")
                                }
                            }
                        }
                    }
                    is TelegramAuthState.WaitPhoneNumber,
                    is TelegramAuthState.WaitCode,
                    is TelegramAuthState.WaitPassword -> {
                        Button(
                            onClick = {
                                when (state) {
                                    is TelegramAuthState.WaitPhoneNumber -> showPhoneDialog = true
                                    is TelegramAuthState.WaitCode -> showCodeDialog = true
                                    is TelegramAuthState.WaitPassword -> showPasswordDialog = true
                                    else -> {}
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("telegram_continue_auth_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = OdinColors.DarkAccentBlue),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Enter Telegram Credentials", style = OdinTypography.headline)
                        }
                    }
                    else -> {
                        Button(
                            onClick = {
                                telegramViewModel.connectTelegram()
                                showPhoneDialog = true
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("telegram_connect_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = OdinColors.DarkAccentBlue),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudUpload,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Connect Telegram", style = OdinTypography.headline)
                        }
                    }
                }
            } else {
                // AUTHENTICATED STATE: Reveal complete backup settings & controls
                val readyState = authState as TelegramAuthState.Ready

                // Card 1: Connected Account Profile Card
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(OdinColors.DarkSuccess.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudDone,
                                contentDescription = "Connected",
                                tint = OdinColors.DarkSuccess,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = readyState.user.fullName,
                                style = OdinTypography.headline,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = readyState.user.displayHandle,
                                style = OdinTypography.footnote,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(OdinColors.DarkSuccess)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Connected to Saved Messages",
                                    style = OdinTypography.caption,
                                    color = OdinColors.DarkSuccess,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = { showLogoutConfirmDialog = true },
                            modifier = Modifier.testTag("telegram_disconnect_button"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Disconnect", color = OdinColors.DarkDestructive)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Card 2: Backup Preferences
                SectionHeader(title = "BACKUP CONFIGURATION")
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(16.dp)
                ) {
                    // Automatic Backup Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Automatic Backup",
                                style = OdinTypography.body,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Automatically back up media in background",
                                style = OdinTypography.footnote,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isBackupEnabled,
                            onCheckedChange = { telegramViewModel.setBackupEnabled(it) },
                            modifier = Modifier.testTag("auto_backup_toggle"),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = OdinColors.DarkAccentBlue
                            )
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    // Network Preference Chips
                    Text(
                        text = "Network Connection",
                        style = OdinTypography.subheadline,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        NetworkPreference.entries.forEach { pref ->
                            val isSelected = networkPreference == pref
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isSelected) OdinColors.DarkAccentBlue.copy(alpha = 0.2f)
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) OdinColors.DarkAccentBlue else Color.Transparent,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable { telegramViewModel.setNetworkPreference(pref) }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = pref.label,
                                    style = OdinTypography.caption,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) OdinColors.DarkAccentBlue else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    // Media Types Toggles
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Back up Photos", style = OdinTypography.body)
                        Switch(
                            checked = backupPhotos,
                            onCheckedChange = { telegramViewModel.setBackupPhotos(it) },
                            modifier = Modifier.testTag("backup_photos_toggle")
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Back up Videos", style = OdinTypography.body)
                        Switch(
                            checked = backupVideos,
                            onCheckedChange = { telegramViewModel.setBackupVideos(it) },
                            modifier = Modifier.testTag("backup_videos_toggle")
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    // Back up new media automatically toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Back up new media automatically", style = OdinTypography.body)
                            Text(
                                text = "Enqueue newly captured camera media",
                                style = OdinTypography.footnote,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = backupNewMediaAutomatically,
                            onCheckedChange = { telegramViewModel.setBackupNewMediaAutomatically(it) },
                            modifier = Modifier.testTag("backup_new_auto_toggle")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Card 3: Backup Status, Queue Counts & Stats
                SectionHeader(title = "STATUS & QUEUE")
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(16.dp)
                ) {
                    val lastBackupFormatted = remember(lastBackupTime) {
                        if (lastBackupTime > 0) {
                            val sdf = SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault())
                            sdf.format(Date(lastBackupTime))
                        } else {
                            "Never"
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Last successful backup",
                            style = OdinTypography.footnote,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = lastBackupFormatted,
                            style = OdinTypography.footnote,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Badges (Completed, Pending, Failed)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatBadge(
                            label = "Completed",
                            count = backupStats.completedCount,
                            color = OdinColors.DarkSuccess,
                            modifier = Modifier.weight(1f)
                        )
                        StatBadge(
                            label = "Pending",
                            count = backupStats.pendingCount,
                            color = OdinColors.DarkWarning,
                            modifier = Modifier.weight(1f)
                        )
                        StatBadge(
                            label = "Failed",
                            count = backupStats.failedCount,
                            color = OdinColors.DarkDestructive,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Retry failed uploads button if any failed
                    if (backupStats.failedCount > 0) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { telegramViewModel.retryFailedUploads() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("retry_failed_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = OdinColors.DarkDestructive),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Retry ${backupStats.failedCount} Failed Uploads")
                        }
                    }

                    // Back up existing media button
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedButton(
                        onClick = { telegramViewModel.backupExistingMediaNow(activeMedia) },
                        enabled = !isActionLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("backup_existing_media_button"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Back up existing media (${activeMedia.size} items)")
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Card 4: Cloud Actions & Restore
                SectionHeader(title = "CLOUD ACTIONS")
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(16.dp)
                ) {
                    // Test sample upload
                    Button(
                        onClick = {
                            val sample = activeMedia.firstOrNull()
                            if (sample != null) {
                                telegramViewModel.testUploadSampleMedia(sample)
                            }
                        },
                        enabled = activeMedia.isNotEmpty() && !isTestingUpload,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("test_upload_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (isTestingUpload) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Testing Upload...")
                        } else {
                            Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Test Backup to Saved Messages")
                        }
                    }

                    testUploadStatus?.let { status ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = status,
                            style = OdinTypography.caption,
                            color = if (status.contains("confirmed", ignoreCase = true)) OdinColors.DarkSuccess else OdinColors.DarkWarning,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    // Restore from Telegram
                    Button(
                        onClick = {
                            telegramViewModel.discoverCloudBackups()
                            showRestoreSheetDialog = true
                        },
                        enabled = !isDiscovering,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("restore_backup_button"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (isDiscovering) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Discovering Backups...")
                        } else {
                            Icon(imageVector = Icons.Default.SettingsBackupRestore, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Restore Backup from Telegram")
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    // Clear Cloud Backups (Destructive)
                    OutlinedButton(
                        onClick = { showDeleteCloudConfirmDialog = true },
                        enabled = !isActionLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("clear_cloud_backups_button"),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = OdinColors.DarkDestructive),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Clear Cloud Backups")
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Privacy Note
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .padding(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(18.dp)
                            .padding(top = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Telegram Saved Messages acts as your personal cloud backup storage. Deleting a local photo never deletes your cloud backup, and clearing cloud backups never touches your local device storage.",
                        style = OdinTypography.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 17.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // AUTH & ACTION DIALOGS

    // 1. Phone Dialog
    if (showPhoneDialog) {
        var phoneNumberInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = {
                showPhoneDialog = false
                telegramViewModel.cancelAuthentication()
            },
            title = { Text("Connect Telegram Account") },
            text = {
                Column {
                    Text(
                        "Enter your phone number in full international format (e.g. +1234567890):",
                        style = OdinTypography.footnote
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = phoneNumberInput,
                        onValueChange = { phoneNumberInput = it },
                        placeholder = { Text("+1234567890") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("phone_number_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (phoneNumberInput.isNotBlank()) {
                            showPhoneDialog = false
                            telegramViewModel.sendPhoneNumber(phoneNumberInput)
                        }
                    },
                    modifier = Modifier.testTag("submit_phone_button")
                ) {
                    Text("Send Code")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPhoneDialog = false
                    telegramViewModel.cancelAuthentication()
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    // 2. Verification Code Dialog
    if (showCodeDialog) {
        var codeInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = {
                showCodeDialog = false
                telegramViewModel.cancelAuthentication()
            },
            title = { Text("Enter Verification Code") },
            text = {
                Column {
                    Text(
                        "Please enter the login code sent to your Telegram app (or SMS):",
                        style = OdinTypography.footnote
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = codeInput,
                        onValueChange = { codeInput = it },
                        placeholder = { Text("12345") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("verification_code_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (codeInput.isNotBlank()) {
                            showCodeDialog = false
                            telegramViewModel.sendCode(codeInput)
                        }
                    },
                    modifier = Modifier.testTag("submit_code_button")
                ) {
                    Text("Verify")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCodeDialog = false
                    telegramViewModel.cancelAuthentication()
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    // 3. Two-Step Verification Password Dialog
    if (showPasswordDialog) {
        var passwordInput by remember { mutableStateOf("") }
        var isPasswordVisible by remember { mutableStateOf(false) }
        val hint = (authState as? TelegramAuthState.WaitPassword)?.hint

        AlertDialog(
            onDismissRequest = {
                showPasswordDialog = false
                telegramViewModel.cancelAuthentication()
            },
            title = { Text("Two-Step Verification") },
            text = {
                Column {
                    Text(
                        "Your Telegram account requires a Two-Step Verification password.",
                        style = OdinTypography.footnote
                    )
                    if (!hint.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Password hint: $hint",
                            style = OdinTypography.caption,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        placeholder = { Text("Enter 2FA password") },
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Icon(
                                    imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle password visibility"
                                )
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("password_2fa_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (passwordInput.isNotBlank()) {
                            showPasswordDialog = false
                            telegramViewModel.sendPassword(passwordInput)
                        }
                    },
                    modifier = Modifier.testTag("submit_password_button")
                ) {
                    Text("Submit")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPasswordDialog = false
                    telegramViewModel.cancelAuthentication()
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    // 4. Logout Confirmation Dialog
    if (showLogoutConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirmDialog = false },
            title = { Text("Disconnect Telegram") },
            text = {
                Text(
                    "Are you sure you want to disconnect your Telegram account? Cloud backups will stop, but your media in Saved Messages will remain intact."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutConfirmDialog = false
                        telegramViewModel.logOut()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OdinColors.DarkDestructive)
                ) {
                    Text("Disconnect")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // 5. Clear Cloud Backups Confirmation Dialog
    if (showDeleteCloudConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteCloudConfirmDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.WarningAmber,
                    contentDescription = null,
                    tint = OdinColors.DarkDestructive
                )
            },
            title = { Text("Delete All Cloud Backups?") },
            text = {
                Text(
                    "This action will permanently delete all Odin Gallery backup messages from your Telegram Saved Messages.\n\n" +
                    "Your local photos and videos on this device will NOT be deleted."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteCloudConfirmDialog = false
                        telegramViewModel.deleteCloudBackups {}
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OdinColors.DarkDestructive),
                    modifier = Modifier.testTag("confirm_delete_cloud_button")
                ) {
                    Text("Delete Cloud Backups")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteCloudConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // 6. Restore Backups Dialog
    if (showRestoreSheetDialog) {
        AlertDialog(
            onDismissRequest = { if (!isRestoring) showRestoreSheetDialog = false },
            title = { Text("Restore from Telegram") },
            text = {
                Column {
                    if (isRestoring) {
                        Text(
                            text = "Restoring item ${restoreProgress.first} of ${restoreProgress.second}...",
                            style = OdinTypography.body
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = {
                                if (restoreProgress.second > 0) {
                                    restoreProgress.first.toFloat() / restoreProgress.second.toFloat()
                                } else 0f
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        val newCount = discoveredItems.count { !it.isAlreadyLocal }
                        Text(
                            text = "Found ${discoveredItems.size} backups in Saved Messages.\n" +
                                   "$newCount items are new and ready to be restored into your gallery.",
                            style = OdinTypography.body
                        )
                        if (discoveredItems.isEmpty() && !isDiscovering) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No #ODIN_BACKUP messages were found in Saved Messages.",
                                style = OdinTypography.footnote,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = {
                if (!isRestoring) {
                    val newCount = discoveredItems.count { !it.isAlreadyLocal }
                    Button(
                        onClick = { telegramViewModel.restoreDiscoveredItems() },
                        enabled = newCount > 0,
                        modifier = Modifier.testTag("start_restore_button")
                    ) {
                        Text("Restore ($newCount new)")
                    }
                }
            },
            dismissButton = {
                if (!isRestoring) {
                    TextButton(onClick = { showRestoreSheetDialog = false }) {
                        Text("Close")
                    }
                }
            }
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = OdinTypography.caption,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 14.dp, bottom = 6.dp)
    )
}

@Composable
private fun FeatureHighlightRow(
    icon: ImageVector,
    title: String,
    description: String
) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = OdinColors.DarkAccentBlue,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                style = OdinTypography.headline,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = OdinTypography.footnote,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun StatBadge(
    label: String,
    count: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
            .padding(vertical = 10.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "$count",
            style = OdinTypography.headline,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = OdinTypography.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
