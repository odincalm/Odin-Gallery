package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.model.MediaItem
import com.example.ui.components.GlassSurface
import com.example.ui.components.MediaThumbnailItem
import com.example.ui.theme.OdinColors
import com.example.ui.theme.OdinSpacing
import com.example.ui.theme.OdinTypography
import com.example.ui.viewmodel.GalleryViewModel

@Composable
fun HiddenScreen(
    viewModel: GalleryViewModel,
    onBack: () -> Unit,
    onOpenMedia: (MediaItem, List<MediaItem>) -> Unit
) {
    val hiddenMedia by viewModel.hiddenMedia.collectAsStateWithLifecycle()
    val isUnlocked by viewModel.isHiddenVaultUnlocked.collectAsStateWithLifecycle()
    val hasPassword by viewModel.hasHidePhotosPassword.collectAsStateWithLifecycle()

    var passwordInput by remember { mutableStateOf("") }
    var confirmPasswordInput by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf(false) }

    val isSelectionMode by viewModel.isHiddenSelectionMode.collectAsStateWithLifecycle()
    val selectedIds by viewModel.hiddenSelectedIds.collectAsStateWithLifecycle()

    val hidePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.hideMediaFromUris(uris)
        }
    }

    BackHandler {
        if (isSelectionMode) {
            viewModel.clearHiddenSelection()
        } else {
            viewModel.lockHiddenVault()
            onBack()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (!isUnlocked) {
            // Authentication / Setup Dialog
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.LockOpen,
                    contentDescription = null,
                    tint = OdinColors.DarkAccentBlue,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = if (hasPassword) "Enter Hidden Vault Password" else "Create Hidden Vault Password",
                    style = OdinTypography.title,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (hasPassword) "Please enter your password to view hidden photos and videos." else "Set a secure password to protect your hidden vault.",
                    style = OdinTypography.subheadline,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = passwordInput,
                    onValueChange = {
                        passwordInput = it
                        passwordError = false
                    },
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = OdinColors.DarkAccentBlue,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                if (!hasPassword) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = confirmPasswordInput,
                        onValueChange = {
                            confirmPasswordInput = it
                            passwordError = false
                        },
                        label = { Text("Confirm Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = OdinColors.DarkAccentBlue,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (passwordError) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (hasPassword) "Incorrect password" else "Passwords do not match",
                        style = OdinTypography.footnote,
                        color = OdinColors.DarkDestructive
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    TextButton(
                        onClick = onBack,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(text = "Cancel", color = OdinColors.DarkSecondaryText)
                    }

                    TextButton(
                        onClick = {
                            if (hasPassword) {
                                val success = viewModel.unlockHiddenVault(passwordInput)
                                if (!success) passwordError = true
                            } else {
                                if (passwordInput.isNotBlank() && passwordInput == confirmPasswordInput) {
                                    viewModel.setHidePhotosPassword(passwordInput)
                                    viewModel.unlockHiddenVault(passwordInput)
                                } else {
                                    passwordError = true
                                }
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .background(OdinColors.DarkAccentBlue, RoundedCornerShape(12.dp))
                    ) {
                        Text(text = if (hasPassword) "Unlock" else "Set Password", color = Color.White)
                    }
                }
            }
        } else {
            // Unlocked Hidden Vault Grid
            if (hiddenMedia.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "No Hidden Photos or Videos",
                        style = OdinTypography.title,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Items you hide will appear securely in this vault.",
                        style = OdinTypography.subheadline,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(
                        start = 0.dp,
                        end = 0.dp,
                        top = 64.dp,
                        bottom = if (isSelectionMode) 130.dp else 100.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(OdinSpacing.gridGap),
                    verticalArrangement = Arrangement.spacedBy(OdinSpacing.gridGap),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item(span = { GridItemSpan(3) }) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clickable {
                                            viewModel.lockHiddenVault()
                                            onBack()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        tint = MaterialTheme.colorScheme.onBackground
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Hidden Vault",
                                    style = OdinTypography.largeTitle,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (!isSelectionMode) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clickable { hidePickerLauncher.launch("*/*") },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = "Add Media to Hide",
                                            tint = OdinColors.DarkAccentBlue,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                }

                                if (hiddenMedia.isNotEmpty() && !isSelectionMode) {
                                    Text(
                                        text = stringResource(id = R.string.select),
                                        style = OdinTypography.headline,
                                        color = OdinColors.DarkAccentBlue,
                                        modifier = Modifier
                                            .clickable { viewModel.startHiddenSelection(hiddenMedia.first().id) }
                                            .padding(4.dp)
                                    )
                                }
                            }
                        }
                    }

                    items(hiddenMedia, key = { it.id }) { item ->
                        val isSelected = selectedIds.contains(item.id)
                        MediaThumbnailItem(
                            item = item,
                            isSelected = isSelected,
                            isSelectionMode = isSelectionMode,
                            onClick = {
                                if (isSelectionMode) {
                                    viewModel.toggleHiddenSelection(item.id)
                                } else {
                                    onOpenMedia(item, hiddenMedia)
                                }
                            },
                            onLongClick = {
                                if (!isSelectionMode) {
                                    viewModel.startHiddenSelection(item.id)
                                }
                            }
                        )
                    }
                }
            }

            // Selection Mode Bottom Bar
            AnimatedVisibility(
                visible = isSelectionMode,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp, start = 20.dp, end = 20.dp)
            ) {
                GlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(26.dp),
                    tonalAlpha = 0.94f
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Unhide
                        Text(
                            text = "Unhide",
                            style = OdinTypography.headline,
                            color = OdinColors.DarkAccentBlue,
                            modifier = Modifier.clickable {
                                viewModel.unhideSelectedHidden()
                            }
                        )

                        // Cancel Selection
                        Text(
                            text = "Cancel",
                            style = OdinTypography.headline,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.clickable {
                                viewModel.clearHiddenSelection()
                            }
                        )

                        // Delete
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = "Delete",
                            tint = OdinColors.DarkDestructive,
                            modifier = Modifier
                                .size(24.dp)
                                .clickable {
                                    viewModel.deleteSelectedHidden(hiddenMedia)
                                }
                        )
                    }
                }
            }
        }
    }
}
