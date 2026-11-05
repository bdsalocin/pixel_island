package com.bdsalocin.pixelisland.presentation.island.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.bdsalocin.pixelisland.domain.model.BiometricStatus

@Composable
fun FaceUnlockScanner(
    status: BiometricStatus,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "FaceScanPulse")
    val pulseScale by transition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Pulse"
    )

    val ringColor = when (status) {
        BiometricStatus.SUCCESS -> Color(0xFF34D399) // Vert succès
        BiometricStatus.SCANNING -> Color(0xFF818CF8) // Indigo scan
        BiometricStatus.FAILED -> Color(0xFFF87171)   // Rouge échec
        BiometricStatus.IDLE -> Color.Transparent
    }

    Box(
        modifier = modifier
            .size(56.dp)
            .scale(if (status == BiometricStatus.SCANNING) pulseScale else 1f)
            .border(2.dp, ringColor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        // En temps réel, l'objectif physique du Pixel 8 Pro se trouve au centre exact de ce cercle
        Box(
            modifier = Modifier
                .size(16.dp)
                .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
        )
    }
}
