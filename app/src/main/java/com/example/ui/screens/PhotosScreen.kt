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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.model.MediaItem
import com.example.ui.components.GlassSurface
import com.example.ui.components.MediaThumbnailItem
import com.example.ui.components.MoveDestinationDialog
import com.example.ui.theme.OdinAnimations
import com.example.ui.theme.OdinColors
import com.example.ui.theme.OdinSpacing
import com.example.ui.theme.OdinTypography
import com.example.ui.viewmodel.GalleryViewModel

@Composable
fun PhotosScreen(
    viewModel: GalleryViewModel,
    onOpenMedia: (MediaItem, List<MediaItem>) -> Unit
) {
    val context = LocalContext.current
    val activeMedia by viewModel.activeMedia.collectAsStateWithLifecycle()
    val mediaGroups by viewModel.mediaGroups.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val isSelectionMode by viewModel.isSelectionMode.collectAsStateWithLifecycle()
    val selectedMediaIds by viewModel.selectedMediaIds.collectAsStateWithLifecycle()
    val showMoveDialog by viewModel.showMoveDialog.collectAsStateWithLifecycle()
    val albums by viewModel.albums.collectAsStateWithLifecycle()

    val gridState = rememberLazyGridState()

    BackHandler(enabled = isSelectionMode) {
        viewModel.clearSelection()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (activeMedia.isEmpty() && !isScanning) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(id = R.string.empty_gallery_title),
                    style = OdinTypography.title,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(id = R.string.empty_gallery_desc),
                    style = OdinTypography.subheadline,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            // 3-Column edge-to-edge photo grid with 2dp gap
            LazyVerticalGrid(
                state = gridState,
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
                // Header item: Title "Photos"
                item(
                    key = "header_photos_title",
                    span = { GridItemSpan(3) },
                    contentType = "title"
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Photo",
                            style = OdinTypography.largeTitle,
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        if (activeMedia.isNotEmpty() && !isSelectionMode) {
                            Text(
                                text = stringResource(id = R.string.select),
                                style = OdinTypography.headline,
                                color = OdinColors.DarkAccentBlue,
                                modifier = Modifier
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        viewModel.startSelectionWith(activeMedia.first().id)
                                    }
                                    .padding(vertical = 4.dp, horizontal = 8.dp)
                            )
                        }
                    }
                }

                // Natural Date Grouping
                mediaGroups.forEachIndexed { groupIndex, group ->
                    item(
                        key = "header_${group.title}_${group.dateSubtitle}_$groupIndex",
                        span = { GridItemSpan(3) },
                        contentType = "date_header"
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 14.dp, end = 14.dp, top = 26.dp, bottom = 8.dp)
                        ) {
                            Text(
                                text = group.title,
                                style = OdinTypography.sectionTitle,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            if (group.dateSubtitle != null && group.dateSubtitle != group.title) {
                                Text(
                                    text = group.dateSubtitle,
                                    style = OdinTypography.footnote,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    items(
                        items = group.items,
                        key = { "photo_${group.title}_${it.id}" },
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
                                    onOpenMedia(item, activeMedia)
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
                        text = if (selectedCount == activeMedia.size) stringResource(id = R.string.deselect_all) else stringResource(id = R.string.select_all),
                        style = OdinTypography.headline,
                        color = OdinColors.DarkAccentBlue,
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                if (selectedCount == activeMedia.size) {
                                    viewModel.clearSelection()
                                } else {
                                    viewModel.selectAll(activeMedia)
                                }
                            }
                            .padding(vertical = 4.dp)
                    )
                }
            }
        }

        // Selection Mode Bottom Bar
        AnimatedVisibility(
            visible = isSelectionMode,
            enter = slideInVertically(initialOffsetY = { it }, animationSpec = OdinAnimations.springOffset) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }, animationSpec = OdinAnimations.springOffset) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 16.dp, start = 20.dp, end = 20.dp)
        ) {
            val selectedCount = selectedMediaIds.size
            val selectedItems = remember(activeMedia, selectedMediaIds) {
                activeMedia.filter { it.id in selectedMediaIds }
            }

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
                    // Move
                    Icon(
                        imageVector = Icons.Outlined.Folder,
                        contentDescription = "Move",
                        tint = if (selectedCount > 0) OdinColors.DarkAccentBlue else OdinColors.DarkDisabled,
                        modifier = Modifier
                            .size(24.dp)
                            .clickable(enabled = selectedCount > 0) {
                                viewModel.openMoveDialog()
                            }
                    )

                    // Share
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

                    // Hide
                    Icon(
                        imageVector = Icons.Outlined.VisibilityOff,
                        contentDescription = "Hide",
                        tint = if (selectedCount > 0) OdinColors.DarkAccentBlue else OdinColors.DarkDisabled,
                        modifier = Modifier
                            .size(24.dp)
                            .clickable(enabled = selectedCount > 0) {
                                viewModel.hideSelectedMedia()
                            }
                    )

                    // Delete
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = stringResource(id = R.string.delete),
                        tint = if (selectedCount > 0) OdinColors.DarkDestructive else OdinColors.DarkDisabled,
                        modifier = Modifier
                            .size(24.dp)
                            .clickable(enabled = selectedCount > 0) {
                                viewModel.requestDeleteSelected(activeMedia)
                            }
                    )
                }
            }
        }

        // Move Destination Dialog
        if (showMoveDialog) {
            MoveDestinationDialog(
                albums = albums,
                onDismiss = { viewModel.closeMoveDialog() },
                onSelectAlbum = { albumId ->
                    viewModel.moveSelectedMediaToAlbum(albumId)
                }
            )
        }
    }
}
