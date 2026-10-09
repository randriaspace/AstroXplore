package com.example.astroxplore.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Material 3 Expressive Shapes System.
 * Emphasizes sculpted, organic geometry with 24dp-32dp large containers and rounded pill elements.
 * Reference: https://m3.material.io/blog/building-with-m3-expressive
 */
val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

val ExpressivePillShape = CircleShape
val ExpressiveCardShape = RoundedCornerShape(26.dp)
val ExpressiveDockShape = RoundedCornerShape(32.dp)
val ExpressiveButtonShape = RoundedCornerShape(16.dp)
val ExpressiveChipShape = RoundedCornerShape(14.dp)
