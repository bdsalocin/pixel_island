package com.bdsalocin.pixelisland.presentation.island.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Icône vectorielle Apple style SF Symbols pour la piste précédente.
 * Deux triangles pleins orientés vers la gauche.
 */
@Composable
fun AppleSkipPreviousIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    width: Dp = 22.dp,
    height: Dp = 18.dp
) {
    Canvas(modifier = modifier.size(width, height)) {
        val w = size.width
        val h = size.height
        val overlap = 0.5f.dp.toPx()
        val midX = (w / 2f) + (overlap / 2f)

        // Triangle de gauche
        val leftPath = Path().apply {
            moveTo(midX, 0f)
            lineTo(midX, h)
            lineTo(0f, h / 2f)
            close()
        }
        // Triangle de droite
        val rightPath = Path().apply {
            moveTo(w, 0f)
            lineTo(w, h)
            lineTo((w / 2f) - (overlap / 2f), h / 2f)
            close()
        }
        drawPath(leftPath, tint)
        drawPath(rightPath, tint)
    }
}

/**
 * Icône vectorielle Apple style SF Symbols pour la piste suivante.
 * Deux triangles pleins orientés vers la droite.
 */
@Composable
fun AppleSkipNextIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    width: Dp = 22.dp,
    height: Dp = 18.dp
) {
    Canvas(modifier = modifier.size(width, height)) {
        val w = size.width
        val h = size.height
        val overlap = 0.5f.dp.toPx()
        val midX = (w / 2f) - (overlap / 2f)

        // Triangle de gauche
        val leftPath = Path().apply {
            moveTo(0f, 0f)
            lineTo(midX, h / 2f)
            lineTo(0f, h)
            close()
        }
        // Triangle de droite
        val rightPath = Path().apply {
            moveTo((w / 2f) + (overlap / 2f), 0f)
            lineTo(w, h / 2f)
            lineTo((w / 2f) + (overlap / 2f), h)
            close()
        }
        drawPath(leftPath, tint)
        drawPath(rightPath, tint)
    }
}

/**
 * Icône vectorielle Apple style SF Symbols pour Lecture / Pause.
 * Triangle plein pour Lecture, deux barres verticales aux extrémités arrondies pour Pause.
 */
@Composable
fun ApplePlayPauseIcon(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = Color.Black,
    sizeDp: Dp = 22.dp
) {
    Canvas(modifier = modifier.size(sizeDp)) {
        val w = size.width
        val h = size.height

        if (isPlaying) {
            // Pause : Deux barres verticales arrondies
            val barWidth = w * 0.28f
            val gap = w * 0.22f
            val barHeight = h * 0.90f
            val topOffset = (h - barHeight) / 2f
            val leftBarX = (w - (2 * barWidth + gap)) / 2f
            val rightBarX = leftBarX + barWidth + gap
            val radius = CornerRadius(barWidth / 2f, barWidth / 2f)

            drawRoundRect(
                color = tint,
                topLeft = Offset(leftBarX, topOffset),
                size = Size(barWidth, barHeight),
                cornerRadius = radius
            )
            drawRoundRect(
                color = tint,
                topLeft = Offset(rightBarX, topOffset),
                size = Size(barWidth, barHeight),
                cornerRadius = radius
            )
        } else {
            // Lecture : Triangle équilibré orienté à droite
            val startX = w * 0.18f
            val endX = w * 0.92f
            val topY = h * 0.08f
            val bottomY = h * 0.92f
            val midY = h / 2f

            val playPath = Path().apply {
                moveTo(startX, topY)
                lineTo(endX, midY)
                lineTo(startX, bottomY)
                close()
            }
            drawPath(playPath, tint)
        }
    }
}
