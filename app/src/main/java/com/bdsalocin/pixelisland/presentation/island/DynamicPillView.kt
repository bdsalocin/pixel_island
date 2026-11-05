package com.bdsalocin.pixelisland.presentation.island

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bdsalocin.pixelisland.presentation.island.components.EqualizerBars
import com.bdsalocin.pixelisland.presentation.island.components.FaceUnlockScanner

@Composable
fun DynamicPillView(
    viewModel: IslandViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    // Vérifie si une action requiert l'affichage de l'îlot
    val isVisible = state.isMusicActive || state.isFaceUnlockActive || state.isExpanded

    // Dimensions dynamiques : au repos, la bulle est à 0dp (masquée derrière le poinçon)
    val targetWidth = when {
        state.isFaceUnlockActive -> 190.dp
        state.isExpanded -> 300.dp
        state.isMusicActive -> 170.dp
        else -> 0.dp // Complètement invisible et caché au repos
    }

    val targetHeight = when {
        state.isFaceUnlockActive -> 72.dp
        state.isExpanded -> 146.dp
        state.isMusicActive -> 34.dp
        else -> 0.dp // Complètement invisible et caché au repos
    }

    val animatedWidth by animateDpAsState(
        targetValue = targetWidth,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "PillWidthAnimation"
    )

    val animatedHeight by animateDpAsState(
        targetValue = targetHeight,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "PillHeightAnimation"
    )

    val animatedAlpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "PillAlphaAnimation"
    )

    // Rayon de courbure adapté pour respecter l'arrondi d'écran et la forme de la pilule
    val cornerRadius by animateDpAsState(
        targetValue = when {
            state.isExpanded -> 38.dp
            state.isFaceUnlockActive -> 36.dp
            else -> 9999.dp
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "PillCornerAnimation"
    )

    // Ne rien afficher si inactif
    if (animatedWidth > 2.dp && animatedHeight > 2.dp) {
        Box(
            modifier = modifier
                .width(animatedWidth)
                .height(animatedHeight)
                .graphicsLayer { alpha = animatedAlpha }
                .clip(RoundedCornerShape(cornerRadius))
                .background(Color.Black)
                .clickable { viewModel.toggleExpand() },
            contentAlignment = Alignment.Center
        ) {
            when {
                // Vue Déverrouillage Visage (Face Unlock affiché vers le bas sous le poinçon)
                state.isFaceUnlockActive -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(animatedHeight)
                            .padding(horizontal = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        FaceUnlockScanner(
                            status = state.biometricStatus,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp)
                        )
                    }
                }

                // Vue Musique Étendue
                state.isExpanded && state.mediaTrack != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val albumArt = state.mediaTrack?.albumArt
                                if (albumArt != null) {
                                    Image(
                                        bitmap = albumArt.asImageBitmap(),
                                        contentDescription = "Pochette d'album",
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                Brush.linearGradient(
                                                    listOf(Color(0xFF6366F1), Color(0xFFEC4899))
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("♪", color = Color.White, fontSize = 20.sp)
                                    }
                                }

                                Column {
                                    Text(
                                        text = state.mediaTrack?.title ?: "",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = state.mediaTrack?.artist ?: "",
                                        color = Color.Gray,
                                        fontSize = 11.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                            EqualizerBars(isPlaying = state.mediaTrack?.isPlaying == true)
                        }

                        // Bouton Lecture / Pause
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .clickable { viewModel.onPlayPauseClicked() }
                                .padding(top = 8.dp)
                        ) {
                            Text(
                                text = if (state.mediaTrack?.isPlaying == true) "PAUSE" else "LECTURE",
                                color = Color.White,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                // Vue Musique Compacte (Extension symétrique de part et d'autre du capteur)
                state.isMusicActive && state.mediaTrack != null -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Côté gauche : Onde sonore vibrante
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            EqualizerBars(isPlaying = state.mediaTrack?.isPlaying == true)
                        }

                        // Centre : Espace réservé pour le poinçon de la caméra frontale
                        Spacer(modifier = Modifier.width(34.dp))

                        // Côté droit : Miniature de l'album ou icône de secours
                        val miniature = state.mediaTrack?.albumArt
                        if (miniature != null) {
                            Image(
                                bitmap = miniature.asImageBitmap(),
                                contentDescription = "Pochette miniature",
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(Color(0xFF6366F1), Color(0xFFEC4899))
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("♪", color = Color.White, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
