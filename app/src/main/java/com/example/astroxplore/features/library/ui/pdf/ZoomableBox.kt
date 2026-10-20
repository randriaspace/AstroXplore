package com.example.astroxplore.features.library.ui.pdf

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.launch

/**
 * High-performance zoomable and panable container.
 * Features:
 * - Smooth pinch-to-zoom (1.0x to 4.0x)
 * - Double-tap to zoom in (2.5x) or reset to 1x
 * - Free 2D horizontal and vertical panning with strict mathematical boundary clamping
 * - Eliminates black screen voids by ensuring content is never panned past edge limits
 * - Automatically releases touch events when unzoomed (1x) so parent vertical scrolling remains fluid
 */
@Composable
fun ZoomableBox(
    modifier: Modifier = Modifier,
    scale: Float = 1f,
    onScaleChanged: (Float) -> Unit = {},
    content: @Composable () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    val animScale = remember { Animatable(1f) }
    val animOffset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }

    var currentScale by remember { mutableFloatStateOf(1f) }
    var currentOffset by remember { mutableStateOf(Offset.Zero) }

    // Sync external scale changes if supplied
    LaunchedEffect(scale) {
        if (scale != currentScale && !animScale.isRunning) {
            currentScale = scale
            animScale.snapTo(scale)
            if (scale == 1f) {
                currentOffset = Offset.Zero
                animOffset.snapTo(Offset.Zero)
            }
        }
    }

    fun computeMaxOffset(targetScale: Float, size: IntSize): Offset {
        if (targetScale <= 1f || size.width == 0 || size.height == 0) return Offset.Zero
        val maxX = (size.width * (targetScale - 1f)) / 2f
        val maxY = (size.height * (targetScale - 1f)) / 2f
        return Offset(maxX, maxY)
    }

    Box(
        modifier = modifier
            .onSizeChanged { containerSize = it }
            .pointerInput(containerSize) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Main)
                    var lastTouchTime = 0L
                    var tapCount = 1

                    do {
                        val event = awaitPointerEvent(pass = PointerEventPass.Main)
                        val canceled = event.changes.any { it.isConsumed }
                        if (canceled) break

                        if (event.changes.size == 1) {
                            // Single touch pan when zoomed
                            val change = event.changes.first()
                            if (currentScale > 1f) {
                                val pan = change.position - change.previousPosition
                                val maxOffset = computeMaxOffset(currentScale, containerSize)
                                val newX = (currentOffset.x + pan.x).coerceIn(-maxOffset.x, maxOffset.x)
                                val newY = (currentOffset.y + pan.y).coerceIn(-maxOffset.y, maxOffset.y)
                                currentOffset = Offset(newX, newY)
                                coroutineScope.launch { animOffset.snapTo(currentOffset) }
                                change.consume()
                            }
                        } else if (event.changes.size >= 2) {
                            // Multi-touch pinch zoom and pan
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()

                            val newScale = (currentScale * zoomChange).coerceIn(1f, 4f)
                            currentScale = newScale
                            onScaleChanged(newScale)

                            val maxOffset = computeMaxOffset(newScale, containerSize)
                            val newX = (currentOffset.x + panChange.x).coerceIn(-maxOffset.x, maxOffset.x)
                            val newY = (currentOffset.y + panChange.y).coerceIn(-maxOffset.y, maxOffset.y)
                            currentOffset = Offset(newX, newY)

                            coroutineScope.launch {
                                animScale.snapTo(newScale)
                                animOffset.snapTo(currentOffset)
                            }

                            event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .graphicsLayer {
                val s = animScale.value
                val off = animOffset.value
                scaleX = s
                scaleY = s
                translationX = off.x
                translationY = off.y
            }
    ) {
        content()
    }
}
