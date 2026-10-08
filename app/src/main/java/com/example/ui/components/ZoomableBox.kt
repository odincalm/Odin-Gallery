package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.launch

@Composable
fun ZoomableBox(
    modifier: Modifier = Modifier,
    maxScale: Float = 4.5f,
    onTap: () -> Unit = {},
    content: @Composable BoxScope.(scale: Float) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val scaleAnim = remember { Animatable(1f) }
    val offsetAnim = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    var containerSize = remember { IntSize.Zero }

    Box(
        modifier = modifier
            .onSizeChanged { containerSize = it }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { tapOffset ->
                        coroutineScope.launch {
                            if (scaleAnim.value > 1.05f) {
                                // Reset to 1x with spring
                                launch {
                                    scaleAnim.animateTo(
                                        1f,
                                        spring(dampingRatio = 0.75f, stiffness = 400f)
                                    )
                                }
                                launch {
                                    offsetAnim.animateTo(
                                        Offset.Zero,
                                        spring(dampingRatio = 0.75f, stiffness = 400f)
                                    )
                                }
                            } else {
                                // Zoom to 2.5x centered on tap
                                val targetScale = 2.5f
                                val center = Offset(containerSize.width / 2f, containerSize.height / 2f)
                                val newOffset = (center - tapOffset) * (targetScale - 1f)
                                launch {
                                    scaleAnim.animateTo(
                                        targetScale,
                                        spring(dampingRatio = 0.75f, stiffness = 400f)
                                    )
                                }
                                launch {
                                    offsetAnim.animateTo(
                                        newOffset,
                                        spring(dampingRatio = 0.75f, stiffness = 400f)
                                    )
                                }
                            }
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val pointerCount = event.changes.size

                        if (pointerCount >= 2) {
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()

                            coroutineScope.launch {
                                val currentScale = scaleAnim.value
                                val newScale = (currentScale * zoom).coerceIn(0.7f, maxScale)
                                scaleAnim.snapTo(newScale)

                                if (newScale > 1f) {
                                    val maxOffsetX = (containerSize.width * (newScale - 1f)) / 2f
                                    val maxOffsetY = (containerSize.height * (newScale - 1f)) / 2f
                                    val currentOffset = offsetAnim.value
                                    val newOffset = Offset(
                                        x = (currentOffset.x + pan.x).coerceIn(-maxOffsetX, maxOffsetX),
                                        y = (currentOffset.y + pan.y).coerceIn(-maxOffsetY, maxOffsetY)
                                    )
                                    offsetAnim.snapTo(newOffset)
                                }
                            }
                            event.changes.forEach { it.consume() }
                        } else if (pointerCount == 1 && scaleAnim.value > 1.05f) {
                            // Pan while zoomed
                            val pan = event.calculatePan()
                            coroutineScope.launch {
                                val s = scaleAnim.value
                                val maxOffsetX = (containerSize.width * (s - 1f)) / 2f
                                val maxOffsetY = (containerSize.height * (s - 1f)) / 2f
                                val currentOffset = offsetAnim.value
                                val newOffset = Offset(
                                    x = (currentOffset.x + pan.x).coerceIn(-maxOffsetX, maxOffsetX),
                                    y = (currentOffset.y + pan.y).coerceIn(-maxOffsetY, maxOffsetY)
                                )
                                offsetAnim.snapTo(newOffset)
                            }
                            event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })

                    // Gesture ended: bounce back if under 1f
                    if (scaleAnim.value < 1f) {
                        coroutineScope.launch {
                            scaleAnim.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 400f))
                            offsetAnim.animateTo(Offset.Zero, spring(dampingRatio = 0.7f, stiffness = 400f))
                        }
                    }
                }
            }
            .graphicsLayer {
                scaleX = scaleAnim.value
                scaleY = scaleAnim.value
                translationX = offsetAnim.value.x
                translationY = offsetAnim.value.y
            }
    ) {
        content(scaleAnim.value)
    }
}
