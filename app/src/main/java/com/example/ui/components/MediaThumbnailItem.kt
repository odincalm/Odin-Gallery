package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import coil.size.Precision
import coil.size.Scale
import com.example.model.MediaItem
import com.example.ui.theme.OdinAnimations
import com.example.ui.theme.OdinColors
import com.example.ui.theme.OdinShapes

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaThumbnailItem(
    modifier: Modifier = Modifier,
    item: MediaItem,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val context = LocalContext.current

    val scaleState = if (isSelectionMode) {
        animateFloatAsState(
            targetValue = if (isSelected) 0.93f else 1f,
            animationSpec = OdinAnimations.springFast,
            label = "thumbnail_scale"
        )
    } else null

    // In normal mode: sharp edge-to-edge square.
    // In selection mode: slight rounding for selected items.
    val tileShape = remember(isSelected) {
        if (isSelected) OdinShapes.selectionThumbnail else OdinShapes.thumbnail
    }

    // High performance thumbnail request:
    // - Specific memory & disk cache keys per item ID + dateModified
    // - Inexact thumbnail dimensions matching the display cell
    // - GPU hardware bitmaps (allowHardware)
    // - Fast video keyframe extraction for video items
    // - No crossfade animation during scroll for max FPS
    val imageRequest = remember(item.id, item.dateModified) {
        ImageRequest.Builder(context)
            .data(item.uri)
            .memoryCacheKey("thumb_${item.id}_${item.dateModified}")
            .diskCacheKey("thumb_${item.id}_${item.dateModified}")
            .size(280)
            .scale(Scale.FILL)
            .precision(Precision.INEXACT)
            .allowHardware(true)
            .allowRgb565(true)
            .crossfade(false)
            .apply {
                if (item.isVideo) {
                    videoFrameMillis(500)
                }
            }
            .build()
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .graphicsLayer {
                val s = scaleState?.value ?: 1f
                scaleX = s
                scaleY = s
            }
            .clip(tileShape)
            .background(Color(0xFF1C1C1E))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .semantics {
                contentDescription = "${if (item.isVideo) "Video" else "Photo"} ${item.displayName}"
            }
    ) {
        AsyncImage(
            model = imageRequest,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Subtle unobtrusive video duration badge (e.g. "01:24")
        if (item.isVideo && item.durationFormatted.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0x99000000))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text = item.durationFormatted,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    letterSpacing = 0.sp
                )
            }
        }

        // Quiet favorite heart indicator in bottom-left
        if (item.isFavorite) {
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = "Favorite",
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(5.dp)
                    .size(13.dp)
            )
        }

        // Selection mode visual feedback
        if (isSelectionMode) {
            // Subtle blue edge highlight on selected item
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(2.dp, OdinColors.DarkAccentBlue, tileShape)
                )
            }

            // Selection circle indicator in top-right
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
