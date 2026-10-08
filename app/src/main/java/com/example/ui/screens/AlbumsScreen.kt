@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class
)

package com.example.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.model.AlbumItem
import com.example.model.SystemAlbumType
import com.example.ui.theme.OdinColors
import com.example.ui.theme.OdinShapes
import com.example.ui.theme.OdinTypography
import com.example.ui.viewmodel.GalleryViewModel

@Composable
fun AlbumsScreen(
    viewModel: GalleryViewModel,
    onOpenAlbum: (AlbumItem) -> Unit,
    onOpenRecentlyDeleted: () -> Unit
) {
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val showCreateDialog by viewModel.showCreateAlbumDialog.collectAsStateWithLifecycle()
    val showAddToAlbumSheet by viewModel.showAddToAlbumSheet.collectAsStateWithLifecycle()

    var newAlbumName by remember { mutableStateOf("") }
    var albumToDelete by remember { mutableStateOf<AlbumItem?>(null) }
    var showFirstDeleteConfirm by remember { mutableStateOf(false) }
    var showSecondDeleteConfirm by remember { mutableStateOf(false) }

    val customAlbums = remember(albums) { albums.filter { it.systemType == null } }
    val allPhotosAlbum = remember(albums) { albums.firstOrNull { it.systemType == SystemAlbumType.ALL_PHOTOS } }

    val mediaTypeAlbums = remember(albums) {
        albums.filter {
            it.systemType in listOf(
                SystemAlbumType.VIDEOS,
                SystemAlbumType.CAMERA,
                SystemAlbumType.SCREENSHOTS,
                SystemAlbumType.SCREEN_RECORDINGS,
                SystemAlbumType.DOWNLOADS,
                SystemAlbumType.FAVORITES
            )
        }
    }

    val utilityAlbums = remember(albums) {
        albums.filter {
            it.systemType in listOf(
                SystemAlbumType.RECENTLY_ADDED,
                SystemAlbumType.RECENTLY_DELETED
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            contentPadding = PaddingValues(top = 64.dp, bottom = 110.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Albums",
                        style = OdinTypography.largeTitle,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = stringResource(id = R.string.create_album),
                        tint = OdinColors.DarkAccentBlue,
                        modifier = Modifier
                            .size(26.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                viewModel.openCreateAlbumDialog()
                            }
                    )
                }
            }

            // Section 1: My Albums
            item {
                Text(
                    text = stringResource(id = R.string.album_my_albums),
                    style = OdinTypography.sectionTitle,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 12.dp)
                )

                val displayAlbums = if (allPhotosAlbum != null) {
                    listOf(allPhotosAlbum) + customAlbums
                } else customAlbums

                if (displayAlbums.isEmpty()) {
                    Text(
                        text = "No custom albums created yet.",
                        style = OdinTypography.subheadline,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                } else {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(displayAlbums, key = { it.id }) { album ->
                            AlbumCoverTile(
                                album = album,
                                onClick = { onOpenAlbum(album) },
                                onLongClick = {
                                    if (album.systemType == null) {
                                        albumToDelete = album
                                        showFirstDeleteConfirm = true
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // Section 2: Media Types
            item {
                Text(
                    text = stringResource(id = R.string.album_media_types),
                    style = OdinTypography.sectionTitle,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 32.dp, bottom = 8.dp)
                )
            }

            items(mediaTypeAlbums, key = { "media_type_${it.id}" }) { album ->
                val icon = when (album.systemType) {
                    SystemAlbumType.VIDEOS -> Icons.Outlined.Videocam
                    SystemAlbumType.CAMERA -> Icons.Outlined.CameraAlt
                    SystemAlbumType.SCREENSHOTS -> Icons.Outlined.PhoneAndroid
                    SystemAlbumType.SCREEN_RECORDINGS -> Icons.Outlined.VideoLibrary
                    SystemAlbumType.DOWNLOADS -> Icons.Outlined.Download
                    SystemAlbumType.FAVORITES -> Icons.Outlined.FavoriteBorder
                    else -> Icons.Outlined.Folder
                }

                AlbumListRow(
                    title = album.title,
                    count = album.count,
                    icon = icon,
                    onClick = { onOpenAlbum(album) }
                )
            }

            // Section 3: Utilities
            item {
                Text(
                    text = "Utilities",
                    style = OdinTypography.sectionTitle,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 32.dp, bottom = 8.dp)
                )
            }

            items(utilityAlbums, key = { "utility_${it.id}" }) { album ->
                val isRecentlyDeleted = album.systemType == SystemAlbumType.RECENTLY_DELETED
                val icon = if (isRecentlyDeleted) Icons.Outlined.Delete else Icons.Outlined.History

                AlbumListRow(
                    title = album.title,
                    count = album.count,
                    icon = icon,
                    isDestructive = isRecentlyDeleted && album.count > 0,
                    onClick = {
                        if (isRecentlyDeleted) {
                            onOpenRecentlyDeleted()
                        } else {
                            onOpenAlbum(album)
                        }
                    }
                )
            }
        }

        // First Double-Confirmation Dialog for Album Deletion
        if (showFirstDeleteConfirm && albumToDelete != null) {
            AlertDialog(
                onDismissRequest = {
                    showFirstDeleteConfirm = false
                    albumToDelete = null
                },
                title = {
                    Text(
                        text = "Delete Album",
                        style = OdinTypography.headline,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                text = {
                    Text(
                        text = "Are you sure you want to delete this album \"${albumToDelete?.title}\"?",
                        style = OdinTypography.subheadline,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showFirstDeleteConfirm = false
                            showSecondDeleteConfirm = true
                        }
                    ) {
                        Text(
                            text = "Continue",
                            style = OdinTypography.headline,
                            color = OdinColors.DarkAccentBlue
                        )
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showFirstDeleteConfirm = false
                            albumToDelete = null
                        }
                    ) {
                        Text(
                            text = stringResource(id = R.string.cancel),
                            style = OdinTypography.body,
                            color = OdinColors.DarkSecondaryText
                        )
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(20.dp)
            )
        }

        // Second Stronger Warning Dialog for Album Deletion
        if (showSecondDeleteConfirm && albumToDelete != null) {
            AlertDialog(
                onDismissRequest = {
                    showSecondDeleteConfirm = false
                    albumToDelete = null
                },
                title = {
                    Text(
                        text = "Delete Permanently",
                        style = OdinTypography.headline,
                        color = OdinColors.DarkDestructive
                    )
                },
                text = {
                    Text(
                        text = "This action will permanently delete this album. Are you sure?",
                        style = OdinTypography.subheadline,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.deleteAlbum(albumToDelete!!.id)
                            showSecondDeleteConfirm = false
                            albumToDelete = null
                        }
                    ) {
                        Text(
                            text = "Delete Permanently",
                            style = OdinTypography.headline,
                            color = OdinColors.DarkDestructive
                        )
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showSecondDeleteConfirm = false
                            albumToDelete = null
                        }
                    ) {
                        Text(
                            text = stringResource(id = R.string.cancel),
                            style = OdinTypography.body,
                            color = OdinColors.DarkSecondaryText
                        )
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(20.dp)
            )
        }

        // Create Album Dialog
        if (showCreateDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.closeCreateAlbumDialog() },
                title = {
                    Text(
                        text = stringResource(id = R.string.create_album),
                        style = OdinTypography.headline,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                text = {
                    OutlinedTextField(
                        value = newAlbumName,
                        onValueChange = { newAlbumName = it },
                        placeholder = { Text(stringResource(id = R.string.album_name_hint), style = OdinTypography.subheadline) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = OdinColors.DarkAccentBlue,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (newAlbumName.isNotBlank()) {
                                viewModel.createAlbum(newAlbumName)
                                newAlbumName = ""
                            }
                        }
                    ) {
                        Text(
                            text = stringResource(id = R.string.create),
                            style = OdinTypography.headline,
                            color = OdinColors.DarkAccentBlue
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.closeCreateAlbumDialog() }) {
                        Text(
                            text = stringResource(id = R.string.cancel),
                            style = OdinTypography.body,
                            color = OdinColors.DarkSecondaryText
                        )
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(20.dp)
            )
        }

        // Add to Album Bottom Sheet
        if (showAddToAlbumSheet) {
            ModalBottomSheet(
                onDismissRequest = { viewModel.closeAddToAlbumSheet() },
                sheetState = rememberModalBottomSheetState(),
                containerColor = MaterialTheme.colorScheme.surface,
                shape = OdinShapes.bottomSheet
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(id = R.string.add_to_album),
                            style = OdinTypography.headline,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "New Album",
                            style = OdinTypography.headline,
                            color = OdinColors.DarkAccentBlue,
                            modifier = Modifier.clickable {
                                viewModel.closeAddToAlbumSheet()
                                viewModel.openCreateAlbumDialog()
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (customAlbums.isEmpty()) {
                        Text(
                            text = "No custom albums available. Tap New Album to create one.",
                            style = OdinTypography.subheadline,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    } else {
                        customAlbums.forEach { album ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.addSelectedToAlbum(album.id) }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Folder,
                                    contentDescription = null,
                                    tint = OdinColors.DarkAccentBlue,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Column {
                                    Text(
                                        text = album.title,
                                        style = OdinTypography.body,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${album.count} items",
                                        style = OdinTypography.footnote,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun AlbumCoverTile(
    album: AlbumItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .width(150.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Box(
            modifier = Modifier
                .size(150.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1C1C1E))
        ) {
            if (album.coverUri != null) {
                val coverRequest = remember(album.coverUri) {
                    ImageRequest.Builder(context)
                        .data(album.coverUri)
                        .size(300)
                        .allowHardware(true)
                        .allowRgb565(true)
                        .crossfade(false)
                        .build()
                }
                AsyncImage(
                    model = coverRequest,
                    contentDescription = album.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.PhotoLibrary,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier
                        .size(36.dp)
                        .align(Alignment.Center)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = album.title,
            style = OdinTypography.subheadline,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1
        )
        Text(
            text = "${album.count}",
            style = OdinTypography.footnote,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AlbumListRow(
    title: String,
    count: Int,
    icon: ImageVector,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isDestructive) OdinColors.DarkDestructive else OdinColors.DarkAccentBlue,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = title,
                    style = OdinTypography.body,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$count",
                    style = OdinTypography.subheadline,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            thickness = 0.5.dp,
            modifier = Modifier.padding(start = 40.dp)
        )
    }
}
