package com.bdsalocin.pixelisland.presentation.island

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.graphics.asImageBitmap
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

    // Dimensions et rayons de courbure spécifiquement calqués sur le Pixel 8 Pro
    // Largeur compacte limitée à 165dp pour préserver l'espace de l'heure et des icônes 5G et batterie
    val targetWidth = when {
        state.isFaceUnlockActive -> 56.dp
        state.isExpanded -> 300.dp
        state.isMusicActive -> 165.dp
        else -> 30.dp // Diamètre repos entourant le capteur photo
    }

    val targetHeight = when {
        state.isFaceUnlockActive -> 56.dp
        state.isExpanded -> 146.dp
        state.isMusicActive -> 34.dp
        else -> 30.dp
    }

    val animatedWidth by animateDpAsState(
        targetValue = targetWidth,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "PillWidthAnimation"
    )

    val animatedHeight by animateDpAsState(
        targetValue = targetHeight,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "PillHeightAnimation"
    )

    // Rayon de courbure de 38dp en mode étendu pour respecter l'arrondi d'écran du Pixel 8 Pro
    val cornerRadius by animateDpAsState(
        targetValue = if (state.isExpanded) 38.dp else 9999.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "PillCornerAnimation"
    )

    // Conteneur noir OLED absolu (#000000)
    Box(
        modifier = modifier
            .width(animatedWidth)
            .height(animatedHeight)
            .clip(RoundedCornerShape(cornerRadius))
            .background(androidx.compose.ui.graphics.Color.Black)
            .clickable { viewModel.toggleExpand() },
        contentAlignment = Alignment.Center
    ) {
        when {
            // Vue Déverrouillage Visage (Face Unlock)
            state.isFaceUnlockActive -> {
                FaceUnlockScanner(status = state.biometricStatus)
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
                            state.mediaTrack?.albumArt?.let { bitmap ->
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Pochette d'album",
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                )
                            }
                            Column {
                                Text(
                                    text = state.mediaTrack?.title ?: "",
                                    color = androidx.compose.ui.graphics.Color.White,
                                    fontSize = 13.sp,
                                    maxLines = 1
                                )
                                Text(
                                    text = state.mediaTrack?.artist ?: "",
                                    color = androidx.compose.ui.graphics.Color.Gray,
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
                            color = androidx.compose.ui.graphics.Color.White,
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
                        .padding(horizontal = 8.dp),
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
                    Spacer(modifier = Modifier.width(28.dp))

                    // Côté droit : Miniature de l'album
                    state.mediaTrack?.albumArt?.let { bitmap ->
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Pochette miniature",
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                        )
                    }
                }
            }
        }
    }
}
