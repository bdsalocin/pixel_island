package com.bdsalocin.pixelisland.presentation.island

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.ui.unit.Dp
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

    // Position verticale locale de l'axe du poinçon dans la fenêtre overlay (20dp sous le bord supérieur)
    val punchHoleCenterY = 20.dp

    // Calcul dynamique des bornes haut et bas pour garantir un centrage absolu sur le poinçon
    val targetTop = when {
        state.isExpanded -> 0.dp
        state.isFaceUnlockActive -> 0.dp
        state.isMusicActive -> 0.dp
        else -> punchHoleCenterY // Se rétracte exactement au centre du poinçon
    }

    val targetBottom = when {
        state.isExpanded -> 156.dp
        state.isFaceUnlockActive -> 74.dp
        state.isMusicActive -> 40.dp
        else -> punchHoleCenterY // Se rétracte exactement au centre du poinçon
    }

    val targetWidth = when {
        state.isExpanded -> 310.dp
        state.isFaceUnlockActive -> 190.dp
        state.isMusicActive -> 180.dp
        else -> 0.dp // Se rétracte à 0 en largeur au centre
    }

    // Animation d'absorption dynamique élastique pour la musique (effet de capture type iPhone)
    // et déploiement ralenti gracieux pour le déverrouillage
    val isOpeningMusic = state.isMusicActive && isVisible && !state.isExpanded
    val animSpec = when {
        state.isFaceUnlockActive && isVisible -> tween<Dp>(
            durationMillis = 650,
            easing = FastOutSlowInEasing
        )
        isOpeningMusic -> spring<Dp>(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
        isVisible -> tween<Dp>(
            durationMillis = 420,
            easing = FastOutSlowInEasing
        )
        else -> tween<Dp>(
            durationMillis = 380,
            easing = FastOutSlowInEasing
        )
    }

    val animatedTop by animateDpAsState(
        targetValue = targetTop,
        animationSpec = animSpec,
        label = "PillTopAnimation"
    )

    val animatedBottom by animateDpAsState(
        targetValue = targetBottom,
        animationSpec = animSpec,
        label = "PillBottomAnimation"
    )

    val animatedWidth by animateDpAsState(
        targetValue = targetWidth,
        animationSpec = animSpec,
        label = "PillWidthAnimation"
    )

    val currentHeight = (animatedBottom - animatedTop).coerceAtLeast(0.dp)

    // Rayon de courbure adapté pour respecter la silhouette de pilule ou rectangle arrondi
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

    // L'opacité du contenu s'estompe rapidement à la fermeture (140ms) pour que la capsule noire
    // se referme visiblement et sans bavure directement dans le poinçon
    val contentAlpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(durationMillis = if (isVisible) 320 else 140),
        label = "ContentAlphaAnimation"
    )

    // Affichage actif tant que l'îlot a une dimension physique visible
    if (animatedWidth > 0.5.dp && currentHeight > 0.5.dp) {
        Box(
            modifier = modifier
                .padding(top = animatedTop)
                .width(animatedWidth)
                .height(currentHeight)
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

                    // Vue Musique Détaillée (Carte Agrandie au clic comme sur iPhone)
                    state.isExpanded -> {
                        val track = state.mediaTrack
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
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable { viewModel.onOpenMusicAppClicked() }
                                ) {
                                    val albumArt = track?.albumArt
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
                                            text = track?.title ?: "Média en cours",
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = if (!track?.artist.isNullOrBlank()) track?.artist ?: "" else track?.appName ?: "",
                                            color = Color.LightGray,
                                            fontSize = 12.sp,
                                            maxLines = 1
                                        )
                                    }
                                }

                                EqualizerBars(isPlaying = track?.isPlaying == true)
                            }

                            // Barre de progression musicale
                            val durationMs = track?.durationMs ?: 0L
                            val positionMs = track?.positionMs ?: 0L
                            val progress = if (durationMs > 0L) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0.35f
                            val positionStr = if (positionMs > 0L) {
                                val s = positionMs / 1000
                                "%d:%02d".format(s / 60, s % 60)
                            } else "0:00"
                            val durationStr = if (durationMs > 0L) {
                                val s = durationMs / 1000
                                "%d:%02d".format(s / 60, s % 60)
                            } else "--:--"

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
                                            .fillMaxWidth(progress)
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
                                    Text(positionStr, color = Color.Gray, fontSize = 10.sp)
                                    Text(durationStr, color = Color.Gray, fontSize = 10.sp)
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
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .clickable { viewModel.onSkipPreviousClicked() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("⏮", color = Color.White, fontSize = 18.sp)
                                }

                                Spacer(modifier = Modifier.width(28.dp))

                                // Lecture / Pause principal
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                        .clickable { viewModel.onPlayPauseClicked() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (track?.isPlaying == true) "❚❚" else "▶",
                                        color = Color.Black,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Spacer(modifier = Modifier.width(28.dp))

                                // Piste suivante
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .clickable { viewModel.onSkipNextClicked() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("⏭", color = Color.White, fontSize = 18.sp)
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
                                        .clickable { viewModel.onOpenMusicAppClicked() }
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
                                        )
                                        .clickable { viewModel.onOpenMusicAppClicked() },
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
