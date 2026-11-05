package com.bdsalocin.pixelisland.presentation.island.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.bdsalocin.pixelisland.domain.model.BiometricStatus

@Composable
fun FaceUnlockScanner(
    status: BiometricStatus,
    modifier: Modifier = Modifier
) {
    val isUnlocked = status == BiometricStatus.SUCCESS || status == BiometricStatus.IDLE

    val accentColor = when (status) {
        BiometricStatus.SUCCESS -> Color(0xFF34D399) // Vert émeraude
        BiometricStatus.SCANNING -> Color(0xFFA5B4FC) // Indigo doux
        BiometricStatus.FAILED -> Color(0xFFF87171)   // Rouge corail
        BiometricStatus.IDLE -> Color(0xFF34D399)     // Maintien du vert émeraude pour un fondu de sortie continu
    }

    val transition = rememberInfiniteTransition(label = "FaceScanPulse")
    val pulseScale by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Pulse"
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Cadenas animé seul (sans texte)
        AnimatedPadlock(
            isUnlocked = isUnlocked,
            color = accentColor,
            modifier = Modifier.scale(if (status == BiometricStatus.SCANNING) pulseScale else 1f)
        )
    }
}

@Composable
private fun AnimatedPadlock(
    isUnlocked: Boolean,
    color: Color,
    modifier: Modifier = Modifier
) {
    val shackleOffset by animateDpAsState(
        targetValue = if (isUnlocked) (-4).dp else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "ShackleOffset"
    )

    val shackleRotation by animateFloatAsState(
        targetValue = if (isUnlocked) -24f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "ShackleRotation"
    )

    Box(
        modifier = modifier.size(26.dp),
        contentAlignment = Alignment.Center
    ) {
        // Anse du cadenas
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 2.dp)
                .offset(y = shackleOffset)
                .graphicsLayer {
                    rotationZ = shackleRotation
                    transformOrigin = TransformOrigin(0.2f, 1f)
                }
                .width(13.dp)
                .height(11.dp)
                .border(
                    width = 2.dp,
                    color = color,
                    shape = RoundedCornerShape(
                        topStart = 7.dp,
                        topEnd = 7.dp,
                        bottomStart = 0.dp,
                        bottomEnd = 0.dp
                    )
                )
        )

        // Corps du cadenas
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 2.dp)
                .width(17.dp)
                .height(13.dp)
                .clip(RoundedCornerShape(3.5.dp))
                .background(color),
            contentAlignment = Alignment.Center
        ) {
            // Trou de serrure
            Box(
                modifier = Modifier
                    .size(3.dp)
                    .clip(CircleShape)
                    .background(Color.Black)
            )
        }
    }
}
