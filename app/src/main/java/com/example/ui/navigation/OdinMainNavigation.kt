package com.example.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ViewCarousel
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.model.AlbumItem
import com.example.ui.components.GlassSurface
import com.example.ui.screens.AlbumDetailScreen
import com.example.ui.screens.AlbumsScreen
import com.example.ui.screens.DonateScreen
import com.example.ui.screens.HiddenScreen
import com.example.ui.screens.MediaViewerScreen
import com.example.ui.screens.PhotosScreen
import com.example.ui.screens.RecentlyDeletedScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.OdinAnimations
import com.example.ui.theme.OdinColors
import com.example.ui.theme.OdinShapes
import com.example.ui.viewmodel.GalleryViewModel

enum class MainTab {
    PHOTOS,
    ALBUMS,
    SEARCH
}

sealed class ScreenDestination {
    data object Main : ScreenDestination()
    data class AlbumDetail(val album: AlbumItem) : ScreenDestination()
    data object RecentlyDeleted : ScreenDestination()
    data object Settings : ScreenDestination()
    data object Hidden : ScreenDestination()
    data object Donate : ScreenDestination()
}

@Composable
fun OdinMainNavigation(
    viewModel: GalleryViewModel
) {
    var currentTab by remember { mutableStateOf(MainTab.PHOTOS) }
    var currentDestination by remember { mutableStateOf<ScreenDestination>(ScreenDestination.Main) }

    val isViewerOpen by viewModel.isViewerOpen.collectAsStateWithLifecycle()
    val viewerMediaList by viewModel.viewerMediaList.collectAsStateWithLifecycle()
    val viewerIndex by viewModel.viewerCurrentIndex.collectAsStateWithLifecycle()
    val isSelectionMode by viewModel.isSelectionMode.collectAsStateWithLifecycle()

    BackHandler(enabled = currentDestination !is ScreenDestination.Main && !isViewerOpen) {
        currentDestination = when (currentDestination) {
            is ScreenDestination.Donate -> ScreenDestination.Settings
            is ScreenDestination.Hidden -> ScreenDestination.Settings
            else -> ScreenDestination.Main
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Main Screen Content
        when (val dest = currentDestination) {
            is ScreenDestination.Main -> {
                when (currentTab) {
                    MainTab.PHOTOS -> PhotosScreen(
                        viewModel = viewModel,
                        onOpenMedia = { item, list ->
                            viewModel.openViewer(item, list)
                        }
                    )
                    MainTab.ALBUMS -> AlbumsScreen(
                        viewModel = viewModel,
                        onOpenAlbum = { album ->
                            currentDestination = ScreenDestination.AlbumDetail(album)
                        },
                        onOpenRecentlyDeleted = {
                            currentDestination = ScreenDestination.RecentlyDeleted
                        }
                    )
                    MainTab.SEARCH -> SearchScreen(
                        viewModel = viewModel,
                        onOpenMedia = { item, list ->
                            viewModel.openViewer(item, list)
                        }
                    )
                }
            }
            is ScreenDestination.AlbumDetail -> {
                AlbumDetailScreen(
                    album = dest.album,
                    viewModel = viewModel,
                    onBack = { currentDestination = ScreenDestination.Main },
                    onOpenMedia = { item, list ->
                        viewModel.openViewer(item, list)
                    }
                )
            }
            is ScreenDestination.RecentlyDeleted -> {
                RecentlyDeletedScreen(
                    viewModel = viewModel,
                    onBack = { currentDestination = ScreenDestination.Main }
                )
            }
            is ScreenDestination.Settings -> {
                SettingsScreen(
                    viewModel = viewModel,
                    onBack = { currentDestination = ScreenDestination.Main },
                    onOpenHiddenVault = { currentDestination = ScreenDestination.Hidden },
                    onOpenDonate = { currentDestination = ScreenDestination.Donate }
                )
            }
            is ScreenDestination.Hidden -> {
                HiddenScreen(
                    viewModel = viewModel,
                    onBack = { currentDestination = ScreenDestination.Settings },
                    onOpenMedia = { item, list ->
                        viewModel.openViewer(item, list)
                    }
                )
            }
            is ScreenDestination.Donate -> {
                DonateScreen(
                    onBack = { currentDestination = ScreenDestination.Settings }
                )
            }
        }

        // Floating Liquid Glass Bottom Navigation Capsule
        val showBottomNav = currentDestination is ScreenDestination.Main && !isViewerOpen && !isSelectionMode
        AnimatedVisibility(
            visible = showBottomNav,
            enter = slideInVertically(initialOffsetY = { it }, animationSpec = OdinAnimations.springOffset) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }, animationSpec = OdinAnimations.springOffset) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 12.dp, start = 32.dp, end = 32.dp)
        ) {
            GlassSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = OdinShapes.navCapsule,
                tonalAlpha = 0.92f
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Photos Tab
                    NavItem(
                        title = stringResource(id = R.string.tab_photos),
                        selectedIcon = Icons.Filled.PhotoLibrary,
                        unselectedIcon = Icons.Outlined.PhotoLibrary,
                        isSelected = currentTab == MainTab.PHOTOS,
                        onClick = { currentTab = MainTab.PHOTOS }
                    )

                    // Albums Tab
                    NavItem(
                        title = stringResource(id = R.string.tab_albums),
                        selectedIcon = Icons.Filled.ViewCarousel,
                        unselectedIcon = Icons.Outlined.ViewCarousel,
                        isSelected = currentTab == MainTab.ALBUMS,
                        onClick = { currentTab = MainTab.ALBUMS }
                    )

                    // Search Tab
                    NavItem(
                        title = stringResource(id = R.string.tab_search),
                        selectedIcon = Icons.Filled.Search,
                        unselectedIcon = Icons.Outlined.Search,
                        isSelected = currentTab == MainTab.SEARCH,
                        onClick = { currentTab = MainTab.SEARCH }
                    )

                    // Settings Tab
                    NavItem(
                        title = stringResource(id = R.string.settings),
                        selectedIcon = Icons.Filled.Settings,
                        unselectedIcon = Icons.Outlined.Settings,
                        isSelected = false,
                        onClick = { currentDestination = ScreenDestination.Settings }
                    )
                }
            }
        }

        // Fullscreen Viewer with Smooth Expand Transition
        AnimatedVisibility(
            visible = isViewerOpen,
            enter = fadeIn(OdinAnimations.springNormal),
            exit = fadeOut(OdinAnimations.springNormal)
        ) {
            MediaViewerScreen(
                viewModel = viewModel,
                mediaList = viewerMediaList,
                initialIndex = viewerIndex,
                onClose = { viewModel.closeViewer() }
            )
        }
    }
}

@Composable
private fun NavItem(
    title: String,
    selectedIcon: ImageVector,
    unselectedIcon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.04f else 1f,
        animationSpec = OdinAnimations.springFast,
        label = "nav_item_scale"
    )

    val contentColor = if (isSelected) OdinColors.DarkAccentBlue else MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = Modifier
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if (isSelected) selectedIcon else unselectedIcon,
            contentDescription = title,
            tint = contentColor,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = title,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = contentColor,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}
