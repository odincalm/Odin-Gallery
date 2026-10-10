@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    UnstableApi::class
)

package com.example.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.model.MediaItem
import com.example.telegram.restore.TelegramRestoreManager
import com.example.ui.components.GlassIconButton
import com.example.ui.components.GlassSurface
import com.example.ui.components.ZoomableBox
import com.example.ui.theme.OdinAnimations
import com.example.ui.theme.OdinColors
import com.example.ui.theme.OdinShapes
import com.example.ui.theme.OdinTypography
import com.example.ui.viewmodel.GalleryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MediaViewerScreen(
    viewModel: GalleryViewModel,
    mediaList: List<MediaItem>,
    initialIndex: Int,
    onClose: () -> Unit
) {
    if (mediaList.isEmpty()) {
        onClose()
        return
    }

    val context = LocalContext.current
    val activity = context as? Activity
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val coroutineScope = rememberCoroutineScope()
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, mediaList.size - 1),
        pageCount = { mediaList.size }
    )

    var showControls by remember { mutableStateOf(true) }
    var showInfoSheet by remember { mutableStateOf(false) }
    var userInteractionTrigger by remember { mutableLongStateOf(0L) }
    var isVideoCurrentlyPlaying by remember { mutableStateOf(false) }
    var is16_9Mode by remember { mutableStateOf(false) }

    val currentItem = mediaList.getOrNull(pagerState.currentPage) ?: mediaList.first()

    // Automatically trigger full media download when viewing a cloud-only item
    LaunchedEffect(currentItem.id, currentItem.isCloudOnly) {
        if (currentItem.isCloudOnly && currentItem.cloudFileId != null) {
            withContext(Dispatchers.IO) {
                try {
                    TelegramRestoreManager.restoreMediaItem(context, currentItem)
                } catch (ignored: Exception) {}
            }
        }
    }

    // Restore orientation when leaving viewer
    DisposableEffect(Unit) {
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    val toggleOrientation = {
        userInteractionTrigger++
        activity?.let { act ->
            act.requestedOrientation = if (isLandscape) {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            } else {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
        }
    }

    // Auto-hide controls after 3.5s of inactivity while playing video
    LaunchedEffect(showControls, userInteractionTrigger, isVideoCurrentlyPlaying) {
        if (showControls && isVideoCurrentlyPlaying) {
            delay(3500)
            showControls = false
        }
    }

    val hasPrevMedia = pagerState.currentPage > 0
    val hasNextMedia = pagerState.currentPage < mediaList.size - 1

    fun goToPrevVideo() {
        userInteractionTrigger++
        val cur = pagerState.currentPage
        var target = cur - 1
        while (target >= 0 && !mediaList[target].isVideo) {
            target--
        }
        val finalTarget = if (target >= 0) target else (cur - 1).coerceAtLeast(0)
        if (finalTarget != cur) {
            coroutineScope.launch {
                pagerState.animateScrollToPage(finalTarget)
            }
        }
    }

    fun goToNextVideo() {
        userInteractionTrigger++
        val cur = pagerState.currentPage
        var target = cur + 1
        while (target < mediaList.size && !mediaList[target].isVideo) {
            target++
        }
        val finalTarget = if (target < mediaList.size) target else (cur + 1).coerceAtMost(mediaList.size - 1)
        if (finalTarget != cur) {
            coroutineScope.launch {
                pagerState.animateScrollToPage(finalTarget)
            }
        }
    }

    BackHandler {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        onClose()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Fullscreen Horizontal Pager with edge-to-edge media
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            key = { index -> mediaList[index].id }
        ) { page ->
            val item = mediaList[page]
            val isActive = pagerState.currentPage == page
            if (item.isVideo) {
                VideoPlayerPage(
                    item = item,
                    isActive = isActive,
                    is16_9Mode = is16_9Mode,
                    showControls = showControls,
                    hasPrev = hasPrevMedia,
                    hasNext = hasNextMedia,
                    onToggleControls = {
                        showControls = !showControls
                        userInteractionTrigger++
                    },
                    onUserInteraction = { userInteractionTrigger++ },
                    onPlayingStateChanged = { playing ->
                        if (isActive) {
                            isVideoCurrentlyPlaying = playing
                        }
                    },
                    onPrevVideo = { goToPrevVideo() },
                    onNextVideo = { goToNextVideo() },
                    onSavePosition = { uri, pos, dur ->
                        coroutineScope.launch {
                            viewModel.repository.savePlaybackPosition(uri, pos, dur)
                        }
                    },
                    getInitialPosition = { uri ->
                        viewModel.repository.getPlaybackPosition(uri)
                    }
                )
            } else {
                ImageViewerPage(
                    item = item,
                    is16_9Mode = is16_9Mode,
                    onToggleControls = {
                        showControls = !showControls
                        userInteractionTrigger++
                    }
                )
            }
        }

        // Top Floating Liquid Glass Toolbar
        AnimatedVisibility(
            visible = showControls,
            enter = slideInVertically(initialOffsetY = { -it }, animationSpec = OdinAnimations.springOffset) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }, animationSpec = OdinAnimations.springOffset) + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            GlassSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = OdinShapes.pill,
                tonalAlpha = 0.90f
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Back Chevron
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable {
                                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                onClose()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Title Date & Index
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val dateFormatted = remember(currentItem.dateAdded) {
                            val time = if (currentItem.dateAdded > 10_000_000_000L) currentItem.dateAdded else currentItem.dateAdded * 1000L
                            SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(time))
                        }
                        Text(
                            text = dateFormatted,
                            style = OdinTypography.subheadline,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Text(
                            text = "${pagerState.currentPage + 1} of ${mediaList.size}",
                            style = OdinTypography.caption,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }

                    // Top Right Action Buttons
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .clickable {
                                    userInteractionTrigger++
                                    is16_9Mode = !is16_9Mode
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (is16_9Mode) "16:9" else "9:16",
                                style = OdinTypography.caption,
                                fontWeight = FontWeight.Bold,
                                color = if (is16_9Mode) OdinColors.DarkAccentBlue else Color.White
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .clickable { toggleOrientation() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isLandscape) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                contentDescription = "Toggle Fullscreen Orientation",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .clickable {
                                    userInteractionTrigger++
                                    showInfoSheet = true
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = stringResource(id = R.string.details),
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        // Bottom Floating Liquid Glass Toolbar
        AnimatedVisibility(
            visible = showControls,
            enter = slideInVertically(initialOffsetY = { it }, animationSpec = OdinAnimations.springOffset) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }, animationSpec = OdinAnimations.springOffset) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            GlassSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = OdinShapes.pill,
                tonalAlpha = 0.90f
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Share
                    Icon(
                        imageVector = Icons.Outlined.IosShare,
                        contentDescription = stringResource(id = R.string.share),
                        tint = Color.White,
                        modifier = Modifier
                            .size(24.dp)
                            .clickable { viewModel.shareMedia(context, listOf(currentItem)) }
                    )

                    // Favorite
                    Icon(
                        imageVector = if (currentItem.isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (currentItem.isFavorite) OdinColors.DarkDestructive else Color.White,
                        modifier = Modifier
                            .size(24.dp)
                            .clickable { viewModel.toggleFavorite(currentItem) }
                    )

                    // Delete
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = stringResource(id = R.string.delete),
                        tint = OdinColors.DarkDestructive,
                        modifier = Modifier
                            .size(24.dp)
                            .clickable { viewModel.requestDeleteSingle(currentItem) }
                    )
                }
            }
        }

        // Metadata Modal Bottom Sheet with AI Analysis
        if (showInfoSheet) {
            ModalBottomSheet(
                onDismissRequest = {
                    viewModel.clearAiDescription()
                    showInfoSheet = false
                },
                sheetState = rememberModalBottomSheetState(),
                containerColor = OdinColors.DarkSecondaryBackground,
                shape = OdinShapes.bottomSheet
            ) {
                MediaMetadataContent(item = currentItem, viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun ImageViewerPage(
    item: MediaItem,
    is16_9Mode: Boolean,
    onToggleControls: () -> Unit
) {
    val context = LocalContext.current

    ZoomableBox(
        modifier = Modifier.fillMaxSize(),
        onTap = onToggleControls
    ) {
        Box(
            modifier = if (is16_9Mode) {
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .align(Alignment.Center)
            } else {
                Modifier.fillMaxSize()
            },
            contentAlignment = Alignment.Center
        ) {
            // Materialize cloud-only originals on demand so the viewer never loads an empty/invalid URI
            var resolvedUri by remember(item.id) { mutableStateOf(item.uri) }
            var isLoadingCloud by remember(item.id) { mutableStateOf(false) }
            var loadError by remember(item.id) { mutableStateOf<String?>(null) }

            LaunchedEffect(item.id) {
                val needsDownload = item.isCloudOnly &&
                    (item.uri == Uri.EMPTY || item.uri.scheme == null ||
                        (item.uri.scheme == "file" && (item.uri.path == null || !java.io.File(item.uri.path!!).exists())))
                if (needsDownload && item.cloudFileId != null && item.cloudFileId != 0) {
                    isLoadingCloud = true
                    loadError = null
                    val result = withContext(Dispatchers.IO) {
                        TelegramRestoreManager.restoreMediaItem(context, item)
                    }
                    result.fold(
                        onSuccess = { file ->
                            if (file.exists() && file.length() > 0) {
                                resolvedUri = Uri.fromFile(file)
                            } else {
                                loadError = "Downloaded file is empty"
                            }
                        },
                        onFailure = { e ->
                            loadError = e.message ?: "Failed to download from Telegram"
                        }
                    )
                    isLoadingCloud = false
                }
            }

            when {
                isLoadingCloud -> {
                    CircularProgressIndicator(color = Color.White)
                }
                loadError != null -> {
                    Text(text = loadError ?: "Error", color = Color.White)
                }
                resolvedUri != Uri.EMPTY -> {
                    val fullImageRequest = remember(resolvedUri) {
                        ImageRequest.Builder(context)
                            .data(resolvedUri)
                            .allowHardware(true)
                            .crossfade(true)
                            .build()
                    }
                    AsyncImage(
                        model = fullImageRequest,
                        contentDescription = item.displayName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoPlayerPage(
    item: MediaItem,
    isActive: Boolean,
    is16_9Mode: Boolean,
    showControls: Boolean,
    hasPrev: Boolean,
    hasNext: Boolean,
    onToggleControls: () -> Unit,
    onUserInteraction: () -> Unit,
    onPlayingStateChanged: (Boolean) -> Unit,
    onPrevVideo: () -> Unit,
    onNextVideo: () -> Unit,
    onSavePosition: (Uri, Long, Long) -> Unit,
    getInitialPosition: suspend (Uri) -> Long
) {
    val context = LocalContext.current

    if (!isActive) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onToggleControls
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = if (is16_9Mode) {
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                } else {
                    Modifier.fillMaxSize()
                },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(item.uri)
                        .crossfade(true)
                        .build(),
                    contentDescription = item.displayName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }

            GlassSurface(
                modifier = Modifier.size(56.dp),
                shape = CircleShape,
                tonalAlpha = 0.85f
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.White,
                    modifier = Modifier
                        .size(28.dp)
                        .align(Alignment.Center)
                )
            }
        }
        return
    }

    var isPlaying by remember { mutableStateOf(true) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var totalDurationMs by remember { mutableLongStateOf(item.duration) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var isMuted by remember { mutableStateOf(false) }

    val exoPlayer = remember(item.uri) {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }

    LaunchedEffect(isPlaying) {
        onPlayingStateChanged(isPlaying)
    }

    LaunchedEffect(item.uri) {
        val savedPos = getInitialPosition(item.uri)
        val mediaItem = ExoMediaItem.fromUri(item.uri)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        if (savedPos > 0) {
            exoPlayer.seekTo(savedPos)
        }
        exoPlayer.play()
        isPlaying = true
        onPlayingStateChanged(true)
    }

    LaunchedEffect(exoPlayer, isPlaying) {
        while (isPlaying) {
            currentPositionMs = exoPlayer.currentPosition
            val dur = exoPlayer.duration
            if (dur > 0) totalDurationMs = dur
            delay(250)
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            onPlayingStateChanged(false)
            val curPos = exoPlayer.currentPosition
            val dur = exoPlayer.duration
            if (curPos > 1000) {
                onSavePosition(item.uri, curPos, dur)
            }
            exoPlayer.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggleControls
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = if (is16_9Mode) {
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
            } else {
                Modifier.fillMaxSize()
            },
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(OdinAnimations.springFast),
            exit = fadeOut(OdinAnimations.springFast),
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 80.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    GlassIconButton(
                        icon = Icons.Default.SkipPrevious,
                        contentDescription = "Previous Video",
                        size = 40.dp,
                        iconSize = 20.dp,
                        enabled = hasPrev,
                        tint = if (hasPrev) Color.White else Color.White.copy(alpha = 0.35f),
                        onClick = {
                            onUserInteraction()
                            onPrevVideo()
                        }
                    )

                    GlassIconButton(
                        icon = Icons.Default.Replay10,
                        contentDescription = "Rewind 10s",
                        size = 44.dp,
                        iconSize = 22.dp,
                        onClick = {
                            onUserInteraction()
                            val newPos = (exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L)
                            exoPlayer.seekTo(newPos)
                            currentPositionMs = newPos
                        }
                    )

                    GlassIconButton(
                        icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        size = 56.dp,
                        iconSize = 28.dp,
                        onClick = {
                            onUserInteraction()
                            if (exoPlayer.isPlaying) {
                                exoPlayer.pause()
                                isPlaying = false
                            } else {
                                exoPlayer.play()
                                isPlaying = true
                            }
                        }
                    )

                    GlassIconButton(
                        icon = Icons.Default.Forward10,
                        contentDescription = "Forward 10s",
                        size = 44.dp,
                        iconSize = 22.dp,
                        onClick = {
                            onUserInteraction()
                            val newPos = (exoPlayer.currentPosition + 10_000L).coerceAtMost(exoPlayer.duration)
                            exoPlayer.seekTo(newPos)
                            currentPositionMs = newPos
                        }
                    )

                    GlassIconButton(
                        icon = Icons.Default.SkipNext,
                        contentDescription = "Next Video",
                        size = 40.dp,
                        iconSize = 20.dp,
                        enabled = hasNext,
                        tint = if (hasNext) Color.White else Color.White.copy(alpha = 0.35f),
                        onClick = {
                            onUserInteraction()
                            onNextVideo()
                        }
                    )
                }

                GlassSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    shape = OdinShapes.pill,
                    tonalAlpha = 0.90f
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        val dur = if (totalDurationMs > 0) totalDurationMs.toFloat() else 1f
                        val progress = (currentPositionMs.toFloat() / dur).coerceIn(0f, 1f)

                        Slider(
                            value = progress,
                            onValueChange = { frac ->
                                onUserInteraction()
                                val target = (frac * dur).toLong()
                                exoPlayer.seekTo(target)
                                currentPositionMs = target
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = OdinColors.DarkAccentBlue,
                                inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(22.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${formatMillis(currentPositionMs)} / ${formatMillis(totalDurationMs)}",
                                style = OdinTypography.caption,
                                color = Color.White
                            )

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${playbackSpeed}x",
                                    style = OdinTypography.caption,
                                    fontWeight = FontWeight.SemiBold,
                                    color = OdinColors.DarkAccentBlue,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            onUserInteraction()
                                            playbackSpeed = when (playbackSpeed) {
                                                0.5f -> 0.75f
                                                0.75f -> 1.0f
                                                1.0f -> 1.25f
                                                1.25f -> 1.5f
                                                1.5f -> 2.0f
                                                else -> 0.5f
                                            }
                                            exoPlayer.playbackParameters = PlaybackParameters(playbackSpeed)
                                        }
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )

                                Spacer(modifier = Modifier.width(12.dp))

                                Icon(
                                    imageVector = if (isMuted) Icons.Default.VolumeMute else Icons.Default.VolumeUp,
                                    contentDescription = if (isMuted) "Unmute" else "Mute",
                                    tint = Color.White,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clickable {
                                            onUserInteraction()
                                            isMuted = !isMuted
                                            exoPlayer.volume = if (isMuted) 0f else 1f
                                        }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaMetadataContent(item: MediaItem, viewModel: GalleryViewModel) {
    val context = LocalContext.current
    val aiDescription by viewModel.aiDescriptionText.collectAsStateWithLifecycle()
    val isAiLoading by viewModel.isAiLoading.collectAsStateWithLifecycle()
    val aiError by viewModel.aiError.collectAsStateWithLifecycle()

    val dateTaken = remember(item.dateAdded) {
        val time = if (item.dateAdded > 10_000_000_000L) item.dateAdded else item.dateAdded * 1000L
        SimpleDateFormat("EEEE, MMMM d, yyyy • h:mm a", Locale.getDefault()).format(Date(time))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Text(
            text = stringResource(id = R.string.details),
            style = OdinTypography.sectionTitle,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(16.dp))

        MetadataRow(label = "File Name", value = item.displayName)
        MetadataRow(label = "Type", value = item.mimeType)
        MetadataRow(label = "Resolution", value = item.resolutionFormatted)
        MetadataRow(label = "File Size", value = item.sizeFormatted)
        MetadataRow(label = "Date Added", value = dateTaken)

        if (item.isVideo) {
            MetadataRow(label = "Duration", value = item.durationFormatted)
        }

        if (!item.bucketDisplayName.isNullOrEmpty()) {
            MetadataRow(label = "Album", value = item.bucketDisplayName)
        }

        if (!item.relativePath.isNullOrEmpty()) {
            MetadataRow(label = "Path", value = item.relativePath)
        }

        Spacer(modifier = Modifier.height(20.dp))

        // AI Analysis Container
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF2C2C2E))
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = OdinColors.DarkAccentBlue,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Gemini AI Analysis",
                        style = OdinTypography.headline,
                        color = Color.White
                    )
                }

                if (!isAiLoading && aiDescription == null) {
                    TextButton(
                        onClick = { viewModel.describeCurrentMediaItem(context, item) }
                    ) {
                        Text("Analyze", color = OdinColors.DarkAccentBlue)
                    }
                }
            }

            if (isAiLoading) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = OdinColors.DarkAccentBlue
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Analyzing media with Gemini AI...",
                        style = OdinTypography.footnote,
                        color = OdinColors.DarkSecondaryText
                    )
                }
            } else if (aiDescription != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = aiDescription!!,
                    style = OdinTypography.body,
                    color = Color.White,
                    lineHeight = 22.sp
                )
            } else if (aiError != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = aiError!!,
                    style = OdinTypography.footnote,
                    color = OdinColors.DarkDestructive
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = { viewModel.describeCurrentMediaItem(context, item) }
                ) {
                    Text("Try Again", color = OdinColors.DarkAccentBlue)
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))
    }
}

@Composable
private fun MetadataRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = OdinTypography.subheadline,
            color = OdinColors.DarkSecondaryText
        )
        Text(
            text = value,
            style = OdinTypography.subheadline,
            fontWeight = FontWeight.Medium,
            color = Color.White
        )
    }
}

private fun formatMillis(millis: Long): String {
    if (millis <= 0) return "0:00"
    val totalSec = millis / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return String.format("%d:%02d", min, sec)
}
