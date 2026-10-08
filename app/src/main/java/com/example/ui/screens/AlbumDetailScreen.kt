package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.model.AlbumItem
import com.example.model.MediaItem
import com.example.ui.components.GlassSurface
import com.example.ui.components.MediaThumbnailItem
import com.example.ui.theme.OdinAnimations
import com.example.ui.theme.OdinColors
import com.example.ui.theme.OdinSpacing
import com.example.ui.theme.OdinTypography
import com.example.ui.viewmodel.GalleryViewModel

@Composable
fun AlbumDetailScreen(
    album: AlbumItem,
    viewModel: GalleryViewModel,
    onBack: () -> Unit,
    onOpenMedia: (MediaItem, List<MediaItem>) -> Unit
) {
    val context = LocalContext.current
    val albumMediaFlow = remember(album) { viewModel.repository.getMediaForAlbum(album) }
    val mediaItems by albumMediaFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    val isSelectionMode by viewModel.isSelectionMode.collectAsStateWithLifecycle()
    val selectedMediaIds by viewModel.selectedMediaIds.collectAsStateWithLifecycle()
    var showDeleteAlbumDialog by remember { mutableStateOf(false) }

    BackHandler {
        if (isSelectionMode) {
            viewModel.clearSelection()
        } else {
            onBack()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (mediaItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No Photos or Videos",
                    style = OdinTypography.headline,
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
                // Header with Album Title & Count
                item(
                    key = "album_header",
                    span = { GridItemSpan(3) },
                    contentType = "header"
                ) {
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
                            Column {
                                Text(
                                    text = album.title,
                                    style = OdinTypography.title,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = "${mediaItems.size} items",
                                    style = OdinTypography.footnote,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (album.systemType == null) {
                                Text(
                                    text = "Delete",
                                    style = OdinTypography.headline,
                                    color = OdinColors.DarkDestructive,
                                    modifier = Modifier
                                        .clickable { showDeleteAlbumDialog = true }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }

                            if (!isSelectionMode && mediaItems.isNotEmpty()) {
                                Text(
                                    text = stringResource(id = R.string.select),
                                    style = OdinTypography.headline,
                                    color = OdinColors.DarkAccentBlue,
                                    modifier = Modifier
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) {
                                            viewModel.startSelectionWith(mediaItems.first().id)
                                        }
                                        .padding(vertical = 4.dp, horizontal = 4.dp)
                                )
                            }
                        }
                    }
                }

                items(
                    items = mediaItems,
                    key = { it.id },
                    contentType = { if (it.isVideo) "video" else "photo" }
                ) { item ->
                    val isSelected = selectedMediaIds.contains(item.id)
                    MediaThumbnailItem(
                        item = item,
                        isSelected = isSelected,
                        isSelectionMode = isSelectionMode,
                        onClick = {
                            if (isSelectionMode) {
                                viewModel.toggleSelection(item.id)
                            } else {
                                onOpenMedia(item, mediaItems)
                            }
                        },
                        onLongClick = {
                            if (!isSelectionMode) {
                                viewModel.startSelectionWith(item.id)
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
            val selectedCount = selectedMediaIds.size
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
                                viewModel.clearSelection()
                            }
                            .padding(vertical = 4.dp)
                    )

                    Text(
                        text = if (selectedCount == 0) "Select Items" else "$selectedCount Selected",
                        style = OdinTypography.headline,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = if (selectedCount == mediaItems.size) stringResource(id = R.string.deselect_all) else stringResource(id = R.string.select_all),
                        style = OdinTypography.headline,
                        color = OdinColors.DarkAccentBlue,
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                if (selectedCount == mediaItems.size) {
                                    viewModel.clearSelection()
                                } else {
                                    viewModel.selectAll(mediaItems)
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
            val selectedCount = selectedMediaIds.size
            val selectedItems = mediaItems.filter { it.id in selectedMediaIds }

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
                    Icon(
                        imageVector = Icons.Outlined.IosShare,
                        contentDescription = stringResource(id = R.string.share),
                        tint = if (selectedCount > 0) OdinColors.DarkAccentBlue else OdinColors.DarkDisabled,
                        modifier = Modifier
                            .size(24.dp)
                            .clickable(enabled = selectedCount > 0) {
                                viewModel.shareMedia(context, selectedItems)
                            }
                    )

                    Text(
                        text = "$selectedCount Selected",
                        style = OdinTypography.subheadline,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = stringResource(id = R.string.delete),
                        tint = if (selectedCount > 0) OdinColors.DarkDestructive else OdinColors.DarkDisabled,
                        modifier = Modifier
                            .size(24.dp)
                            .clickable(enabled = selectedCount > 0) {
                                viewModel.requestDeleteSelected(mediaItems)
                            }
                    )
                }
            }
        }

        // Delete Custom Album confirmation dialog
        if (showDeleteAlbumDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteAlbumDialog = false },
                title = { Text(text = "Delete Album", style = OdinTypography.headline) },
                text = { Text("Are you sure you want to delete the album \"${album.title}\"? The photos and videos inside will remain in your library.", style = OdinTypography.subheadline) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.deleteAlbum(album.id)
                            showDeleteAlbumDialog = false
                            onBack()
                        }
                    ) {
                        Text(text = "Delete", style = OdinTypography.headline, color = OdinColors.DarkDestructive)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteAlbumDialog = false }) {
                        Text(stringResource(id = R.string.cancel), style = OdinTypography.body, color = OdinColors.DarkAccentBlue)
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(20.dp)
            )
        }
    }
}
