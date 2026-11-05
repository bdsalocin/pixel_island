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
import androidx.compose.ui.unit.dp

@Composable
fun EqualizerBars(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    barColor: Color = Color(0xFF34D399) // Vert émeraude vibrant
) {
    val transition = rememberInfiniteTransition(label = "EqualizerAnimation")

    val h1 by transition.animateFloat(
        initialValue = 4f,
        targetValue = if (isPlaying) 16f else 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Bar1"
    )

    val h2 by transition.animateFloat(
        initialValue = 6f,
        targetValue = if (isPlaying) 20f else 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 500, delayMillis = 100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Bar2"
    )

    val h3 by transition.animateFloat(
        initialValue = 4f,
        targetValue = if (isPlaying) 14f else 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, delayMillis = 200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Bar3"
    )

    val h4 by transition.animateFloat(
        initialValue = 5f,
        targetValue = if (isPlaying) 18f else 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 550, delayMillis = 150, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Bar4"
    )

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        EqualizerSingleBar(heightDp = h1, color = barColor)
        EqualizerSingleBar(heightDp = h2, color = barColor)
        EqualizerSingleBar(heightDp = h3, color = barColor)
        EqualizerSingleBar(heightDp = h4, color = barColor)
    }
}

@Composable
private fun EqualizerSingleBar(
    heightDp: Float,
    color: Color
) {
    Box(
        modifier = Modifier
            .width(3.dp)
            .height(heightDp.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(color)
    )
}
