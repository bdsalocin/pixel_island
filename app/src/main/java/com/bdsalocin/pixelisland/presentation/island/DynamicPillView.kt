package com.bdsalocin.pixelisland.presentation.island

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
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

enum class IslandStage {
    FACE_UNLOCK,
    EXPANDED_CARD,
    COMPACT_MUSIC,
    HIDDEN
}

@Composable
fun DynamicPillView(
    viewModel: IslandViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    // Vérifie si une action requiert l'affichage de l'îlot
    val isVisible = state.isMusicActive || state.isFaceUnlockActive || state.isExpanded

    // Bornes supérieure et inférieure de l'îlot.
    // En mode actif, le bord supérieur reste toujours ancré à 0dp (autour du poinçon) pour éliminer tout sursaut.
    val targetTop = when {
        state.isFaceUnlockActive -> 0.dp
        state.isExpanded -> 0.dp
        state.isMusicActive -> 0.dp
        else -> 12.dp // Repli vers le centre du poinçon (y=20dp)
    }

    val targetBottom = when {
        state.isFaceUnlockActive -> 76.dp // Élancé 76dp : cadenas et libellé bien visibles sous le poinçon
        state.isExpanded -> 180.dp       // Carte agrandie ergonomique et fluide
        state.isMusicActive -> 40.dp     // Hauteur compacte 40dp centrée sur le poinçon
        else -> 28.dp                    // Repli vers le centre du poinçon (y=20dp)
    }

    val targetWidth = when {
        state.isFaceUnlockActive -> 200.dp // 200dp pour le déverrouillage
        state.isExpanded -> 320.dp         // 320dp pour la carte multimédia
        state.isMusicActive -> 180.dp     // 180dp pour la pilule musique compacte
        else -> 0.dp                      // Rétraction à 0dp en largeur
    }

    // Détermination de l'état d'affichage pour la transition douce
    val currentStage = when {
        state.isFaceUnlockActive -> IslandStage.FACE_UNLOCK
        state.isExpanded -> IslandStage.EXPANDED_CARD
        state.isMusicActive && state.mediaTrack != null -> IslandStage.COMPACT_MUSIC
        else -> IslandStage.HIDDEN
    }

    // Courbe dynamique fluide de type Dynamic Island (ressort réactif avec amortissement doux)
    val pillSpringSpec = spring<Dp>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    val animatedTop by animateDpAsState(
        targetValue = targetTop,
        animationSpec = pillSpringSpec,
        label = "PillTopAnimation"
    )

    val animatedBottom by animateDpAsState(
        targetValue = targetBottom,
        animationSpec = pillSpringSpec,
        label = "PillBottomAnimation"
    )

    val animatedWidth by animateDpAsState(
        targetValue = targetWidth,
        animationSpec = pillSpringSpec,
        label = "PillWidthAnimation"
    )

    val currentHeight = (animatedBottom - animatedTop).coerceAtLeast(0.dp)

    // Rayon de courbure adapté : pilule parfaite en compact, rectangle arrondi doux en étendu et Face ID
    val cornerRadius by animateDpAsState(
        targetValue = when {
            state.isExpanded -> 36.dp
            state.isFaceUnlockActive -> 36.dp
            else -> 9999.dp
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "PillCornerAnimation"
    )

    // Fondu d'opacité global pour une fermeture et ouverture sans scintillement
    val contentAlpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(durationMillis = if (isVisible) 220 else 120),
        label = "ContentAlphaAnimation"
    )

    Box(
        modifier = Modifier.wrapContentSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        if (animatedWidth > 0.5.dp && currentHeight > 0.5.dp) {
            Box(
                modifier = modifier
                    .padding(top = animatedTop)
                    .width(animatedWidth)
                    .height(currentHeight)
                    .clip(RoundedCornerShape(cornerRadius))
                    .background(Color.Black),
                contentAlignment = Alignment.TopCenter
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = contentAlpha }
                ) {
                    AnimatedContent(
                        targetState = currentStage,
                        contentAlignment = Alignment.TopCenter,
                        transitionSpec = {
                            when {
                                initialState == IslandStage.COMPACT_MUSIC && targetState == IslandStage.EXPANDED_CARD -> {
                                    (fadeIn(animationSpec = tween(220, delayMillis = 80, easing = FastOutSlowInEasing)) +
                                            scaleIn(initialScale = 0.92f, animationSpec = tween(220, delayMillis = 80, easing = FastOutSlowInEasing))) togetherWith
                                            fadeOut(animationSpec = tween(100, easing = FastOutSlowInEasing))
                                }
                                initialState == IslandStage.EXPANDED_CARD && targetState == IslandStage.COMPACT_MUSIC -> {
                                    (fadeIn(animationSpec = tween(180, delayMillis = 110, easing = FastOutSlowInEasing)) +
                                            scaleIn(initialScale = 0.88f, animationSpec = tween(180, delayMillis = 110, easing = FastOutSlowInEasing))) togetherWith
                                            fadeOut(animationSpec = tween(100, easing = FastOutSlowInEasing))
                                }
                                initialState == IslandStage.FACE_UNLOCK && targetState == IslandStage.COMPACT_MUSIC -> {
                                    (fadeIn(animationSpec = tween(220, delayMillis = 80, easing = FastOutSlowInEasing)) +
                                            scaleIn(initialScale = 0.90f, animationSpec = tween(220, delayMillis = 80, easing = FastOutSlowInEasing))) togetherWith
                                            fadeOut(animationSpec = tween(120, easing = FastOutSlowInEasing))
                                }
                                else -> {
                                    fadeIn(animationSpec = tween(200, easing = FastOutSlowInEasing)) togetherWith
                                            fadeOut(animationSpec = tween(120, easing = FastOutSlowInEasing))
                                }
                            }
                        },
                        label = "IslandContentTransition"
                    ) { stage ->
                        when (stage) {
                            // Vue Déverrouillage Visage (Face Unlock 76dp avec cadenas et texte sous le poinçon)
                            IslandStage.FACE_UNLOCK -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(76.dp)
                                        .padding(top = 26.dp, bottom = 6.dp, start = 12.dp, end = 12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    FaceUnlockScanner(
                                        status = state.biometricStatus,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }

                            // Vue Musique Détaillée (Carte Agrandie au clic comme sur iPhone)
                            IslandStage.EXPANDED_CARD -> {
                                val track = state.mediaTrack
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                        .padding(top = 22.dp, bottom = 14.dp, start = 16.dp, end = 16.dp),
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
                                            modifier = Modifier.clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null
                                            ) {
                                                viewModel.onOpenMusicAppClicked()
                                            }
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

                                        Box(
                                            modifier = Modifier.clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null
                                            ) {
                                                viewModel.toggleExpand()
                                            }
                                        ) {
                                            EqualizerBars(isPlaying = track?.isPlaying == true)
                                        }
                                    }

                                    // Barre de progression musicale
                                    val durationMs = track?.durationMs ?: 0L
                                    val positionMs = track?.positionMs ?: 0L
                                    val progress = if (durationMs > 0L) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0.01f, 1f) else 0.35f
                                    val positionStr = formatMediaTime(positionMs)
                                    val durationStr = if (durationMs > 0L) formatMediaTime(durationMs) else "--:--"

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
                                                .clickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = null
                                                ) {
                                                    viewModel.onSkipPreviousClicked()
                                                },
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
                                                .clickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = null
                                                ) {
                                                    viewModel.onPlayPauseClicked()
                                                },
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
                                                .clickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = null
                                                ) {
                                                    viewModel.onSkipNextClicked()
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("⏭", color = Color.White, fontSize = 18.sp)
                                        }
                                    }
                                }
                            }

                            // Vue Musique Compacte (Hauteur fixe 40dp centrée sur le poinçon, ailes tactiles gauche et droite)
                            IslandStage.COMPACT_MUSIC -> {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(40.dp)
                                        .padding(horizontal = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Côté gauche : Aile de l'égaliseur (toucher direct pour ouvrir la carte)
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .background(Color.Transparent)
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null
                                            ) {
                                                viewModel.toggleExpand()
                                            },
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        EqualizerBars(isPlaying = state.mediaTrack?.isPlaying == true)
                                    }

                                    // Centre : Espace neutre réservé pour le poinçon de la caméra frontale
                                    Spacer(modifier = Modifier.width(36.dp))

                                    // Côté droit : Aile de la pochette d'album (toucher direct pour ouvrir la carte)
                                    val miniature = state.mediaTrack?.albumArt
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .background(Color.Transparent)
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null
                                            ) {
                                                viewModel.toggleExpand()
                                            },
                                        contentAlignment = Alignment.CenterEnd
                                    ) {
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

                            IslandStage.HIDDEN -> {
                                Spacer(modifier = Modifier.fillMaxSize())
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatMediaTime(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    val secStr = if (sec < 10) "0$sec" else "$sec"
    return "$min:$secStr"
}
