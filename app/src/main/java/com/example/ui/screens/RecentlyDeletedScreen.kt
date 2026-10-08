@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.data.local.DeletedMediaEntity
import com.example.ui.components.GlassSurface
import com.example.ui.theme.OdinAnimations
import com.example.ui.theme.OdinColors
import com.example.ui.theme.OdinShapes
import com.example.ui.theme.OdinSpacing
import com.example.ui.theme.OdinTypography
import com.example.ui.viewmodel.GalleryViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RecentlyDeletedScreen(
    viewModel: GalleryViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val deletedItems by viewModel.recentlyDeleted.collectAsStateWithLifecycle()
    val selectedDeletedIds by viewModel.selectedDeletedIds.collectAsStateWithLifecycle()
    val showEmptyConfirm by viewModel.showEmptyTrashConfirm.collectAsStateWithLifecycle()

    var isSelectionMode by remember { mutableStateOf(false) }
    var itemToManage by remember { mutableStateOf<DeletedMediaEntity?>(null) }
    var showPermanentDeleteConfirm by remember { mutableStateOf(false) }

    BackHandler {
        if (isSelectionMode) {
            isSelectionMode = false
            viewModel.clearDeletedSelection()
        } else {
            onBack()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (deletedItems.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "No Recently Deleted Items",
                    style = OdinTypography.title,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Items show the days remaining before deletion.",
                    style = OdinTypography.subheadline,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            // Photo Grid matching main library (3-column, 2dp gap)
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
                // Header item: Title and actions
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
                                    .clip(CircleShape)
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
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Recently Deleted",
                                style = OdinTypography.title,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }

                        if (!isSelectionMode && deletedItems.isNotEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Empty",
                                    style = OdinTypography.headline,
                                    color = OdinColors.DarkDestructive,
                                    modifier = Modifier
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) {
                                            viewModel.requestEmptyTrash()
                                        }
                                        .padding(vertical = 4.dp, horizontal = 8.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(id = R.string.select),
                                    style = OdinTypography.headline,
                                    color = OdinColors.DarkAccentBlue,
                                    modifier = Modifier
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) {
                                            isSelectionMode = true
                                            viewModel.toggleDeletedSelection(deletedItems.first().id)
                                        }
                                        .padding(vertical = 4.dp, horizontal = 4.dp)
                                )
                            }
                        }
                    }
                }

                items(deletedItems, key = { it.id }) { item ->
                    val isSelected = selectedDeletedIds.contains(item.id)
                    DeletedThumbnailItem(
                        item = item,
                        isSelected = isSelected,
                        isSelectionMode = isSelectionMode,
                        onClick = {
                            if (isSelectionMode) {
                                viewModel.toggleDeletedSelection(item.id)
                            } else {
                                itemToManage = item
                            }
                        },
                        onLongClick = {
                            if (!isSelectionMode) {
                                isSelectionMode = true
                                viewModel.toggleDeletedSelection(item.id)
                            }
                        }
                    )
                }
            }
        }

        // Selection Mode Top Header
        AnimatedVisibility(
            visible = isSelectionMode,
            enter = fadeIn(OdinAnimations.springFast) + slideInVertically(initialOffsetY = { -it / 2 }),
            exit = fadeOut(OdinAnimations.springFast) + slideOutVertically(targetOffsetY = { -it / 2 }),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
        ) {
            val selectedCount = selectedDeletedIds.size
            GlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(20.dp),
                tonalAlpha = 0.92f
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(id = R.string.cancel),
                        style = OdinTypography.headline,
                        color = OdinColors.DarkAccentBlue,
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                isSelectionMode = false
                                viewModel.clearDeletedSelection()
                            }
                            .padding(vertical = 4.dp)
                    )

                    Text(
                        text = if (selectedCount == 0) "Select Items" else "$selectedCount Selected",
                        style = OdinTypography.headline,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = if (selectedCount == deletedItems.size) stringResource(id = R.string.deselect_all) else stringResource(id = R.string.select_all),
                        style = OdinTypography.headline,
                        color = OdinColors.DarkAccentBlue,
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                if (selectedCount == deletedItems.size) {
                                    viewModel.clearDeletedSelection()
                                } else {
                                    viewModel.selectAllDeleted(deletedItems)
                                }
                            }
                            .padding(vertical = 4.dp)
                    )
                }
            }
        }

        // Selection Mode Bottom Liquid Glass Bar
        AnimatedVisibility(
            visible = isSelectionMode,
            enter = slideInVertically(initialOffsetY = { it }, animationSpec = OdinAnimations.springOffset) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }, animationSpec = OdinAnimations.springOffset) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 16.dp, start = 24.dp, end = 24.dp)
        ) {
            val selectedCount = selectedDeletedIds.size

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
                    // Restore Selected
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable(enabled = selectedCount > 0) {
                                viewModel.restoreSelectedDeleted(deletedItems)
                                isSelectionMode = false
                            }
                            .padding(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Restore,
                            contentDescription = null,
                            tint = if (selectedCount > 0) OdinColors.DarkAccentBlue else OdinColors.DarkDisabled,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(id = R.string.restore),
                            style = OdinTypography.headline,
                            color = if (selectedCount > 0) OdinColors.DarkAccentBlue else OdinColors.DarkDisabled
                        )
                    }

                    // Delete Selected Permanently
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable(enabled = selectedCount > 0) {
                                showPermanentDeleteConfirm = true
                            }
                            .padding(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = null,
                            tint = if (selectedCount > 0) OdinColors.DarkDestructive else OdinColors.DarkDisabled,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Delete",
                            style = OdinTypography.headline,
                            color = if (selectedCount > 0) OdinColors.DarkDestructive else OdinColors.DarkDisabled
                        )
                    }
                }
            }
        }

        // Single Item Action Sheet
        itemToManage?.let { entity ->
            ModalBottomSheet(
                onDismissRequest = { itemToManage = null },
                sheetState = rememberModalBottomSheetState(),
                containerColor = MaterialTheme.colorScheme.surface,
                shape = OdinShapes.bottomSheet
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF1C1C1E))
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(File(entity.trashFilePath))
                                .crossfade(true)
                                .size(240)
                                .build(),
                            contentDescription = entity.displayName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = entity.displayName,
                        style = OdinTypography.headline,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    val deletedDate = remember(entity.deletedTimestamp) {
                        SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(entity.deletedTimestamp))
                    }
                    Text(
                        text = "Deleted on $deletedDate • ${entity.daysRemaining} days remaining",
                        style = OdinTypography.footnote,
                        color = OdinColors.DarkDestructive,
                        modifier = Modifier.padding(top = 2.dp)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Restore action
                    Button(
                        onClick = {
                            viewModel.restoreSingleDeleted(entity)
                            itemToManage = null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = OdinShapes.card,
                        colors = ButtonDefaults.buttonColors(containerColor = OdinColors.DarkAccentBlue)
                    ) {
                        Text(
                            text = stringResource(id = R.string.restore),
                            style = OdinTypography.headline,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Delete Permanently action
                    Button(
                        onClick = {
                            viewModel.permanentlyDeleteSingle(entity)
                            itemToManage = null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = OdinShapes.card,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0x26FF453A))
                    ) {
                        Text(
                            text = stringResource(id = R.string.delete_permanently),
                            style = OdinTypography.headline,
                            color = OdinColors.DarkDestructive
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }

        // Destructive Confirmation Dialog
        if (showPermanentDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showPermanentDeleteConfirm = false },
                title = {
                    Text(
                        text = stringResource(id = R.string.delete_permanently),
                        style = OdinTypography.headline,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                text = {
                    Text(
                        text = "This will permanently delete ${selectedDeletedIds.size} photo(s) from your device. This action cannot be undone.",
                        style = OdinTypography.subheadline,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.permanentlyDeleteSelected(deletedItems)
                            showPermanentDeleteConfirm = false
                            isSelectionMode = false
                        }
                    ) {
                        Text(
                            text = stringResource(id = R.string.delete_permanently),
                            style = OdinTypography.headline,
                            color = OdinColors.DarkDestructive
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPermanentDeleteConfirm = false }) {
                        Text(
                            text = stringResource(id = R.string.cancel),
                            style = OdinTypography.body,
                            color = OdinColors.DarkAccentBlue
                        )
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(20.dp)
            )
        }

        // Strong Empty Confirmation
        if (showEmptyConfirm) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissEmptyTrashDialog() },
                title = {
                    Text(
                        text = stringResource(id = R.string.empty_recently_deleted),
                        style = OdinTypography.headline,
                        color = OdinColors.DarkDestructive
                    )
                },
                text = {
                    Text(
                        text = stringResource(id = R.string.empty_recently_deleted_confirm),
                        style = OdinTypography.subheadline,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = { viewModel.confirmEmptyTrash() }
                    ) {
                        Text(
                            text = stringResource(id = R.string.delete_permanently),
                            style = OdinTypography.headline,
                            color = OdinColors.DarkDestructive
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissEmptyTrashDialog() }) {
                        Text(
                            text = stringResource(id = R.string.cancel),
                            style = OdinTypography.body,
                            color = OdinColors.DarkAccentBlue
                        )
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(20.dp)
            )
        }
    }
}

@Composable
private fun DeletedThumbnailItem(
    item: DeletedMediaEntity,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val context = LocalContext.current
    val tileShape = if (isSelected) OdinShapes.selectionThumbnail else OdinShapes.thumbnail

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(tileShape)
            .background(Color(0xFF1C1C1E))
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(File(item.trashFilePath))
                .crossfade(true)
                .size(300)
                .build(),
            contentDescription = item.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Subtle remaining days pill at bottom
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(4.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0x99000000))
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Text(
                text = "${item.daysRemaining}d",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal
            )
        }

        if (isSelectionMode) {
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(2.dp, OdinColors.DarkAccentBlue, tileShape)
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(5.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) OdinColors.DarkAccentBlue
                        else Color.Black.copy(alpha = 0.35f)
                    )
                    .border(
                        1.dp,
                        if (isSelected) Color.Transparent else Color.White.copy(alpha = 0.7f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                AnimatedVisibility(
                    visible = isSelected,
                    enter = scaleIn(OdinAnimations.springFast) + fadeIn(),
                    exit = scaleOut(OdinAnimations.springFast) + fadeOut()
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
    }
}
