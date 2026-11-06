package com.bdsalocin.pixelisland.presentation.island.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.bdsalocin.pixelisland.domain.model.BiometricStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos

@Composable
fun FaceUnlockScanner(
    status: BiometricStatus,
    isUnlocked: Boolean = (status == BiometricStatus.SUCCESS),
    modifier: Modifier = Modifier
) {
    // Couleur verte émeraude style iOS Face ID
    val greenColor = Color(0xFF34D399)

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Cadenas vectoriel stable (aucun saut d'échelle) avec ouverture mécanique en direct
        AnimatedPadlock(
            isUnlocked = isUnlocked,
            color = greenColor
        )
    }
}

@Composable
private fun AnimatedPadlock(
    isUnlocked: Boolean,
    color: Color,
    modifier: Modifier = Modifier
) {
    val shackleLift = remember { Animatable(if (isUnlocked) 1f else 0f) }
    val shackleSwivel = remember { Animatable(if (isUnlocked) 1f else 0f) }
    val bodyRecoil = remember { Animatable(0f) }
    val shacklePath = remember { Path() }

    LaunchedEffect(isUnlocked) {
        if (isUnlocked) {
            // 1. Montée fluide et progressive de l'anse hors du corps du cadenas (~350ms visibles à l'œil)
            launch {
                shackleLift.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = 0.68f,
                        stiffness = 250f
                    )
                )
            }
            // 2. Pivot 3D autour de la tige droite dès que la pointe gauche franchit le bord supérieur
            launch {
                delay(75)
                shackleSwivel.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = 0.72f,
                        stiffness = 220f
                    )
                )
            }
            // 3. Micro-impulsion mécanique subtile du corps lors du déclic d'ouverture
            launch {
                bodyRecoil.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 90, easing = FastOutSlowInEasing)
                )
                bodyRecoil.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = 0.75f,
                        stiffness = 320f
                    )
                )
            }
        } else {
            launch { shackleLift.snapTo(0f) }
            launch { shackleSwivel.snapTo(0f) }
            launch { bodyRecoil.snapTo(0f) }
        }
    }

    Canvas(
        modifier = modifier.size(width = 38.dp, height = 42.dp)
    ) {
        val cx = size.width / 2f
        val strokePx = 2.8.dp.toPx()

        val liftPx = shackleLift.value * 6.2.dp.toPx()
        val recoilPx = bodyRecoil.value * 1.1.dp.toPx()

        // Géométrie du corps du cadenas
        val bodyW = 22.dp.toPx()
        val bodyH = 16.5.dp.toPx()
        val bodyLeft = cx - (bodyW / 2f)
        val bodyTop = 22.0.dp.toPx() + recoilPx
        val bodyCorner = 4.6.dp.toPx()

        // Géométrie 3D de l'anse (ouverte en bas, ancrée à droite dans le corps, pointe gauche libre)
        val archRadiusY = 6.2.dp.toPx()
        val fullSpanX = 12.4.dp.toPx()
        val rightX = cx + archRadiusY
        val swivelRad = (shackleSwivel.value * 26f) * (PI.toFloat() / 180f)
        val currentSpanX = fullSpanX * cos(swivelRad)
        val leftX = rightX - currentSpanX

        val archCenterY = 15.0.dp.toPx() - liftPx
        val leftTipY = 23.6.dp.toPx() - liftPx
        val rightBottomY = 30.5.dp.toPx() - liftPx

        shacklePath.reset()
        shacklePath.moveTo(leftX, leftTipY)
        shacklePath.lineTo(leftX, archCenterY)
        shacklePath.arcTo(
            rect = Rect(
                left = leftX,
                top = archCenterY - archRadiusY,
                right = rightX,
                bottom = archCenterY + archRadiusY
            ),
            startAngleDegrees = 180f,
            sweepAngleDegrees = 180f,
            forceMoveTo = false
        )
        shacklePath.lineTo(rightX, rightBottomY)

        drawPath(
            path = shacklePath,
            color = color,
            style = Stroke(
                width = strokePx,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )

        // Corps plein du cadenas dessiné par-dessus la tige droite ancrée
        drawRoundRect(
            color = color,
            topLeft = Offset(bodyLeft, bodyTop),
            size = Size(bodyW, bodyH),
            cornerRadius = CornerRadius(bodyCorner, bodyCorner)
        )

        // Trou de serrure noir style Apple SF Symbols
        val keyholeCircleY = bodyTop + 6.8.dp.toPx()
        drawCircle(
            color = Color.Black,
            radius = 1.85.dp.toPx(),
            center = Offset(cx, keyholeCircleY)
        )
        val slotW = 1.9.dp.toPx()
        val slotH = 4.6.dp.toPx()
        drawRoundRect(
            color = Color.Black,
            topLeft = Offset(cx - (slotW / 2f), keyholeCircleY),
            size = Size(slotW, slotH),
            cornerRadius = CornerRadius(slotW / 2f, slotW / 2f)
        )
    }
}
