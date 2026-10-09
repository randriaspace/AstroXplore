package com.example.astroxplore.core.ui.components

import androidx.annotation.RawRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.astroxplore.R

/**
 * Backward-compatible bridge to modern Material 3 Loading Indicators.
 * Replaces legacy Lottie JSON animations with native hardware-accelerated M3 components.
 */
@Composable
fun LottieLoadingView(
    modifier: Modifier = Modifier,
    size: Int = 200,
    @RawRes resId: Int = R.raw.book_loader
) {
    val loadingSize = when {
        size <= 40 -> AstroLoadingSize.SMALL
        size <= 100 -> AstroLoadingSize.MEDIUM
        else -> AstroLoadingSize.LARGE
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        AstroM3LoadingIndicator(
            size = loadingSize
        )
    }
}
