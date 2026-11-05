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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
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

    // Dimensions cibles selon le mode
    val targetWidth = when {
        state.isFaceUnlockActive -> 190.dp
        state.isExpanded -> 310.dp
        state.isMusicActive -> 180.dp
        else -> 0.dp // Dissimulé derrière le poinçon au repos
    }

    // Hauteur verticale augmentée à 40dp pour un confort visuel optimal autour du poinçon
    val targetHeight = when {
        state.isFaceUnlockActive -> 74.dp
        state.isExpanded -> 156.dp
        state.isMusicActive -> 40.dp
        else -> 0.dp // Dissimulé derrière le poinçon au repos
    }

    // Animation ressort fluide et douce pour la fermeture et l'ouverture
    val animatedWidth by animateDpAsState(
        targetValue = targetWidth,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "PillWidthAnimation"
    )

    val animatedHeight by animateDpAsState(
        targetValue = targetHeight,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "PillHeightAnimation"
    )

    // Rayon de courbure adapté pour respecter l'arrondi d'écran et la forme de la pilule
    val cornerRadius by animateDpAsState(
        targetValue = when {
            state.isExpanded -> 38.dp
            state.isFaceUnlockActive -> 37.dp
            else -> 9999.dp
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "PillCornerAnimation"
    )

    // L'opacité du contenu s'estompe rapidement à la fermeture afin que la capsule noire
    // se referme visiblement et proprement sur elle-même dans le poinçon
    val contentAlpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 150),
        label = "ContentAlphaAnimation"
    )

    // La capsule reste affichée tant que sa dimension physique est supérieure à 4dp
    // pour garantir la visibilité complète de l'animation de fermeture
    if (animatedWidth > 4.dp && animatedHeight > 4.dp) {
        Box(
            modifier = modifier
                .width(animatedWidth)
                .height(animatedHeight)
                .clip(RoundedCornerShape(cornerRadius))
                .background(Color.Black)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    if (state.isMusicActive || state.isExpanded) {
                        viewModel.toggleExpand()
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = contentAlpha }
            ) {
                when {
                    // Vue Déverrouillage Visage (Face Unlock affiché vers le bas sous le poinçon)
                    state.isFaceUnlockActive -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
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

                    // Vue Musique Détaillée (Carte Agrandie au clic)
                    state.isExpanded && state.mediaTrack != null -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            // En-tête : Pochette, Titre, Artiste et Visualiseur sonore
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val albumArt = state.mediaTrack?.albumArt
                                    if (albumArt != null) {
                                        Image(
                                            bitmap = albumArt.asImageBitmap(),
                                            contentDescription = "Pochette d'album",
                                            modifier = Modifier
                                                .size(46.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(46.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(
                                                    Brush.linearGradient(
                                                        listOf(Color(0xFF6366F1), Color(0xFFEC4899))
                                                    )
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("♪", color = Color.White, fontSize = 22.sp)
                                        }
                                    }

                                    Column(
                                        modifier = Modifier.width(150.dp)
                                    ) {
                                        Text(
                                            text = state.mediaTrack?.title ?: "",
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = state.mediaTrack?.artist ?: "",
                                            color = Color.LightGray,
                                            fontSize = 12.sp,
                                            maxLines = 1
                                        )
                                    }
                                }

                                EqualizerBars(isPlaying = state.mediaTrack?.isPlaying == true)
                            }

                            // Barre de progression musicale
                            Column(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(Color.White.copy(alpha = 0.2f))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(0.45f)
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(Color.White)
                                    )
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("1:42", color = Color.Gray, fontSize = 10.sp)
                                    Text("3:50", color = Color.Gray, fontSize = 10.sp)
                                }
                            }

                            // Commandes multimédias : Précédent, Lecture / Pause, Suivant
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 2.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Piste précédente
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .clickable { viewModel.onSkipPreviousClicked() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("⏮", color = Color.White, fontSize = 16.sp)
                                }

                                Spacer(modifier = Modifier.width(28.dp))

                                // Lecture / Pause principal
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                        .clickable { viewModel.onPlayPauseClicked() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (state.mediaTrack?.isPlaying == true) "❚❚" else "▶",
                                        color = Color.Black,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Spacer(modifier = Modifier.width(28.dp))

                                // Piste suivante
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .clickable { viewModel.onSkipNextClicked() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("⏭", color = Color.White, fontSize = 16.sp)
                                }
                            }
                        }
                    }

                    // Vue Musique Compacte (Hauteur 40dp confortable, symétrie autour du capteur)
                    state.isMusicActive && state.mediaTrack != null -> {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp),
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
                            Spacer(modifier = Modifier.width(36.dp))

                            // Côté droit : Miniature de l'album ou icône musicale
                            val miniature = state.mediaTrack?.albumArt
                            if (miniature != null) {
                                Image(
                                    bitmap = miniature.asImageBitmap(),
                                    contentDescription = "Pochette miniature",
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.linearGradient(
                                                listOf(Color(0xFF6366F1), Color(0xFFEC4899))
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("♪", color = Color.White, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
