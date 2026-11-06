package com.bdsalocin.pixelisland.presentation.island.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.bdsalocin.pixelisland.presentation.theme.DefaultIslandAccentColor

@Composable
fun EqualizerBars(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    barColor: Color = DefaultIslandAccentColor
) {
    val transition = rememberInfiniteTransition(label = "EqualizerAnimation")

    val h1 by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = if (isPlaying) 0.85f else 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 550, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Bar1"
    )

    val h2 by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = if (isPlaying) 1.0f else 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 450, delayMillis = 80, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Bar2"
    )

    val h3 by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = if (isPlaying) 0.75f else 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650, delayMillis = 150, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Bar3"
    )

    val h4 by transition.animateFloat(
        initialValue = 0.30f,
        targetValue = if (isPlaying) 0.90f else 0.30f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 500, delayMillis = 120, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Bar4"
    )

    Row(
        modifier = modifier.height(20.dp),
        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        EqualizerSingleBar(fraction = h1, color = barColor)
        EqualizerSingleBar(fraction = h2, color = barColor)
        EqualizerSingleBar(fraction = h3, color = barColor)
        EqualizerSingleBar(fraction = h4, color = barColor)
    }
}

@Composable
private fun EqualizerSingleBar(
    fraction: Float,
    color: Color
) {
    // Animation 100% matérielle via scaleY sans provoquer de re-mesure du layout (60/120 FPS constant)
    Box(
        modifier = Modifier
            .width(3.dp)
            .height(20.dp)
            .graphicsLayer {
                scaleY = fraction
                transformOrigin = TransformOrigin(0.5f, 0.5f)
            }
            .clip(RoundedCornerShape(2.dp))
            .background(color)
    )
}
