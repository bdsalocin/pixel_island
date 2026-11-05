package com.bdsalocin.pixelisland.presentation.island

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bdsalocin.pixelisland.core.util.HapticFeedbackHelper
import kotlinx.coroutines.launch
import com.bdsalocin.pixelisland.presentation.island.components.ApplePlayPauseIcon
import com.bdsalocin.pixelisland.presentation.island.components.AppleSkipNextIcon
import com.bdsalocin.pixelisland.presentation.island.components.AppleSkipPreviousIcon
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
    // Le bord supérieur reste ancré à 0dp pour supprimer tout saut vertical.
    val targetTop = when {
        state.isFaceUnlockActive -> 0.dp
        state.isExpanded -> 0.dp
        state.isMusicActive -> 0.dp
        else -> 12.dp // Repli vers le centre du poinçon (y=20dp)
    }

    val targetBottom = when {
        state.isFaceUnlockActive -> 80.dp  // Format carré arrondi type iPhone (80dp)
        state.isExpanded -> 238.dp        // Carte agrandie généreuse pour loger la grande pochette et le scrubbing
        state.isMusicActive -> 40.dp      // Hauteur compacte 40dp centrée sur le poinçon
        else -> 28.dp                     // Repli vers le centre du poinçon (y=20dp)
    }

    val targetWidth = when {
        state.isFaceUnlockActive -> 80.dp  // Format carré 80dp sur 80dp
        state.isExpanded -> 384.dp         // Carte étendue allant presque sur les côtés de l'écran (~14dp de marge latérale)
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

    // Amortissement critique sans rebond (DampingRatioNoBouncy) pour une fluidité absolue à 120 FPS
    val pillSpringSpec = spring<Dp>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 450f
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

    val density = LocalDensity.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isDraggingCard by remember { mutableStateOf(false) }
    val dragOffsetY = remember { Animatable(0f) }
    val velocityTracker = remember { VelocityTracker() }

    LaunchedEffect(state.isExpanded) {
        if (!state.isExpanded) {
            isDraggingCard = false
            dragOffsetY.snapTo(0f)
        }
    }

    val maxDragDp = 198.dp
    val maxDragPx = with(density) { maxDragDp.toPx() }
    val upwardPx = (-dragOffsetY.value).coerceAtLeast(0f)
    val upwardDp = with(density) { upwardPx.toDp() }
    val dragFraction = if (maxDragPx > 0f) (upwardPx / maxDragPx).coerceIn(0f, 1f) else 0f

    val currentPillBottom = if (isDraggingCard && state.isExpanded) {
        (targetBottom - upwardDp).coerceIn(40.dp, 238.dp)
    } else {
        animatedBottom
    }

    val currentPillWidth = if (isDraggingCard && state.isExpanded) {
        (384.dp - ((384.dp - 180.dp) * dragFraction)).coerceIn(180.dp, 384.dp)
    } else {
        animatedWidth
    }

    // Rayon de courbure adapté : pilule parfaite en compact, squircle élégant pour Face ID et grande carte
    val cornerRadius by animateDpAsState(
        targetValue = when {
            state.isExpanded -> 40.dp
            state.isFaceUnlockActive -> 24.dp
            else -> 9999.dp
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = 450f
        ),
        label = "PillCornerAnimation"
    )

    val currentPillCornerRadius = if (isDraggingCard && state.isExpanded) {
        (40.dp + (50.dp * dragFraction)).coerceAtMost(9999.dp)
    } else {
        cornerRadius
    }

    val currentHeight = (currentPillBottom - animatedTop).coerceAtLeast(0.dp)

    val contentAlpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(durationMillis = if (isVisible) 180 else 100),
        label = "ContentAlphaAnimation"
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        if (currentPillWidth > 0.5.dp && currentHeight > 0.5.dp) {
            Box(
                modifier = modifier
                    .padding(top = animatedTop)
                    .width(currentPillWidth)
                    .height(currentHeight)
                    .clip(RoundedCornerShape(currentPillCornerRadius))
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
                                    (fadeIn(animationSpec = tween(180, delayMillis = 50, easing = FastOutSlowInEasing)) +
                                            scaleIn(initialScale = 0.94f, animationSpec = tween(180, delayMillis = 50, easing = FastOutSlowInEasing)))
                                        .togetherWith(fadeOut(animationSpec = tween(90, easing = FastOutSlowInEasing)))
                                        .using(SizeTransform(clip = false) { _, _ -> snap() })
                                }
                                initialState == IslandStage.EXPANDED_CARD && targetState == IslandStage.COMPACT_MUSIC -> {
                                    (fadeIn(animationSpec = tween(160, delayMillis = 50, easing = FastOutSlowInEasing)) +
                                            scaleIn(initialScale = 0.92f, animationSpec = tween(160, delayMillis = 50, easing = FastOutSlowInEasing)))
                                        .togetherWith(fadeOut(animationSpec = tween(90, easing = FastOutSlowInEasing)))
                                        .using(SizeTransform(clip = false) { _, _ -> snap() })
                                }
                                initialState == IslandStage.FACE_UNLOCK && targetState == IslandStage.COMPACT_MUSIC -> {
                                    (fadeIn(animationSpec = tween(180, delayMillis = 50, easing = FastOutSlowInEasing)) +
                                            scaleIn(initialScale = 0.92f, animationSpec = tween(180, delayMillis = 50, easing = FastOutSlowInEasing)))
                                        .togetherWith(fadeOut(animationSpec = tween(90, easing = FastOutSlowInEasing)))
                                        .using(SizeTransform(clip = false) { _, _ -> snap() })
                                }
                                else -> {
                                    fadeIn(animationSpec = tween(160, easing = FastOutSlowInEasing))
                                        .togetherWith(fadeOut(animationSpec = tween(100, easing = FastOutSlowInEasing)))
                                        .using(SizeTransform(clip = false) { _, _ -> snap() })
                                }
                            }
                        },
                        label = "IslandContentTransition"
                    ) { stage ->
                        when (stage) {
                            // Vue Déverrouillage Visage (Carré arrondi type iPhone avec cadenas vert abaissé sous le poinçon)
                            IslandStage.FACE_UNLOCK -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(80.dp)
                                        .padding(top = 40.dp, bottom = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    FaceUnlockScanner(
                                        status = state.biometricStatus
                                    )
                                }
                            }

                            // Vue Musique Détaillée (Grande carte étirée sur les côtés, pochette 64dp, titre 18sp, scrubbing et icônes Apple)
                            IslandStage.EXPANDED_CARD -> {
                                val track = state.mediaTrack
                                val dragContentScale = if (isDraggingCard) (1f - dragFraction * 0.12f).coerceIn(0.88f, 1f) else 1f
                                val dragContentAlpha = if (isDraggingCard) (1f - dragFraction * 0.45f).coerceIn(0f, 1f) else 1f

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(238.dp)
                                        .graphicsLayer {
                                            scaleX = dragContentScale
                                            scaleY = dragContentScale
                                            alpha = dragContentAlpha
                                            transformOrigin = TransformOrigin(0.5f, 0f)
                                        }
                                        .pointerInput(state.isExpanded) {
                                            if (!state.isExpanded) return@pointerInput
                                            detectVerticalDragGestures(
                                                onDragStart = {
                                                    velocityTracker.resetTracking()
                                                    isDraggingCard = true
                                                },
                                                onDragEnd = {
                                                    val velocityY = velocityTracker.calculateVelocity().y
                                                    val displacementDp = with(density) { (-dragOffsetY.value).toDp() }
                                                    val shouldDismiss = displacementDp >= 50.dp || velocityY <= -800f

                                                    coroutineScope.launch {
                                                        if (shouldDismiss) {
                                                            HapticFeedbackHelper.performConfirmationFeedback(context)
                                                            dragOffsetY.animateTo(
                                                                targetValue = -with(density) { maxDragDp.toPx() },
                                                                animationSpec = spring(
                                                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                                                    stiffness = 500f
                                                                )
                                                            )
                                                            viewModel.collapseIsland()
                                                            dragOffsetY.snapTo(0f)
                                                            isDraggingCard = false
                                                        } else {
                                                            dragOffsetY.animateTo(
                                                                targetValue = 0f,
                                                                animationSpec = spring(
                                                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                                                    stiffness = 450f
                                                                )
                                                            )
                                                            isDraggingCard = false
                                                        }
                                                    }
                                                },
                                                onDragCancel = {
                                                    coroutineScope.launch {
                                                        dragOffsetY.animateTo(
                                                            targetValue = 0f,
                                                            animationSpec = spring(
                                                                dampingRatio = Spring.DampingRatioNoBouncy,
                                                                stiffness = 450f
                                                            )
                                                        )
                                                        isDraggingCard = false
                                                    }
                                                },
                                                onVerticalDrag = { change, dragAmount ->
                                                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                                                    change.consume()
                                                    val maxPx = with(density) { maxDragDp.toPx() }
                                                    val newOffset = (dragOffsetY.value + dragAmount).coerceIn(-maxPx, 0f)
                                                    coroutineScope.launch {
                                                        dragOffsetY.snapTo(newOffset)
                                                    }
                                                }
                                            )
                                        }
                                        .padding(top = 42.dp, bottom = 16.dp, start = 20.dp, end = 20.dp),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    // En-tête : Grande pochette (64dp), Titre agrandi (18sp), Artiste (14sp) et Égaliseur à droite
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable(
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
                                                        .size(64.dp)
                                                        .clip(RoundedCornerShape(16.dp))
                                                )
                                            } else {
                                                Box(
                                                    modifier = Modifier
                                                        .size(64.dp)
                                                        .clip(RoundedCornerShape(16.dp))
                                                        .background(
                                                            Brush.linearGradient(
                                                                listOf(Color(0xFF6366F1), Color(0xFFEC4899))
                                                            )
                                                        ),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text("♪", color = Color.White, fontSize = 28.sp)
                                                }
                                            }

                                            Column(
                                                modifier = Modifier.weight(1f),
                                                verticalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                Text(
                                                    text = track?.title ?: "Média en cours",
                                                    color = Color.White,
                                                    fontSize = 18.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = if (!track?.artist.isNullOrBlank()) track?.artist ?: "" else track?.appName ?: "",
                                                    color = Color(0xFF9CA3AF),
                                                    fontSize = 14.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

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

                                    // Barre de progression tactile interactive avec scrubbing au doigt en temps réel
                                    val durationMs = track?.durationMs ?: 0L
                                    val positionMs = track?.positionMs ?: 0L

                                    MediaProgressBarScrubber(
                                        positionMs = positionMs,
                                        durationMs = durationMs,
                                        onSeek = { targetMs ->
                                            viewModel.onSeek(targetMs)
                                        }
                                    )

                                    // Commandes multimédias Apple : Précédent (48dp), Bouton central blanc Lecture / Pause (56dp), Suivant (48dp)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Piste précédente style Apple
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(CircleShape)
                                                .clickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = null
                                                ) {
                                                    viewModel.onSkipPreviousClicked()
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            AppleSkipPreviousIcon(tint = Color.White)
                                        }

                                        Spacer(modifier = Modifier.width(36.dp))

                                        // Bouton central blanc Apple pour Lecture / Pause
                                        Box(
                                            modifier = Modifier
                                                .size(56.dp)
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
                                            ApplePlayPauseIcon(
                                                isPlaying = track?.isPlaying == true,
                                                tint = Color.Black,
                                                sizeDp = 22.dp
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(36.dp))

                                        // Piste suivante style Apple
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(CircleShape)
                                                .clickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = null
                                                ) {
                                                    viewModel.onSkipNextClicked()
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            AppleSkipNextIcon(tint = Color.White)
                                        }
                                    }
                                }
                            }

                            // Vue Musique Compacte (Pochette d'album à GAUCHE, barres de son à DROITE)
                            IslandStage.COMPACT_MUSIC -> {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(40.dp)
                                        .pointerInput(Unit) {
                                            detectTapGestures(
                                                onTap = {
                                                    viewModel.onOpenMusicAppClicked()
                                                },
                                                onLongPress = {
                                                    HapticFeedbackHelper.performTapticFeedback(context)
                                                    viewModel.expandIsland()
                                                }
                                            )
                                        }
                                        .padding(horizontal = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Côté gauche : Aile de la pochette d'album
                                    val miniature = state.mediaTrack?.albumArt
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight(),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        if (miniature != null) {
                                            Image(
                                                bitmap = miniature.asImageBitmap(),
                                                contentDescription = "Pochette miniature",
                                                modifier = Modifier
                                                    .size(26.dp)
                                                    .clip(CircleShape)
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(26.dp)
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

                                    // Centre : Espace neutre réservé pour le poinçon de la caméra frontale
                                    Spacer(modifier = Modifier.width(36.dp))

                                    // Côté droit : Aile des barres de son (égaliseur)
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight(),
                                        contentAlignment = Alignment.CenterEnd
                                    ) {
                                        EqualizerBars(isPlaying = state.mediaTrack?.isPlaying == true)
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

/**
 * Barre de progression multimédia avec contrôle tactile et scrubbing direct au doigt en temps réel.
 */
@Composable
private fun MediaProgressBarScrubber(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }

    val currentProgress = if (durationMs > 0L) {
        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0.30f
    }

    val displayProgress = if (isScrubbing) scrubFraction else currentProgress
    val displayPositionMs = if (isScrubbing) {
        (scrubFraction * durationMs).toLong()
    } else {
        positionMs
    }

    val positionStr = formatMediaTime(displayPositionMs)
    val durationStr = if (durationMs > 0L) formatMediaTime(durationMs) else "--:--"

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
                .pointerInput(durationMs) {
                    if (durationMs <= 0L) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val barWidth = size.width.toFloat()
                        if (barWidth > 0f) {
                            isScrubbing = true
                            scrubFraction = (down.position.x / barWidth).coerceIn(0f, 1f)
                        }
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                change.consume()
                                break
                            }
                            change.consume()
                            val currentFrac = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                            scrubFraction = currentFrac
                        }
                        val finalTargetMs = (scrubFraction * durationMs).toLong()
                        isScrubbing = false
                        onSeek(finalTargetMs)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp)
            ) {
                val w = size.width
                val h = size.height
                val trackHeight = if (isScrubbing) 6.dp.toPx() else 4.5f.dp.toPx()
                val trackRadius = CornerRadius(trackHeight / 2f, trackHeight / 2f)
                val trackTop = (h - trackHeight) / 2f
                val activeWidth = (displayProgress * w).coerceIn(0f, w)

                // Piste de fond
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.22f),
                    topLeft = Offset(0f, trackTop),
                    size = Size(w, trackHeight),
                    cornerRadius = trackRadius
                )

                // Piste active blanche
                if (activeWidth > 0f) {
                    drawRoundRect(
                        color = Color.White,
                        topLeft = Offset(0f, trackTop),
                        size = Size(activeWidth, trackHeight),
                        cornerRadius = trackRadius
                    )
                }

                // Curseur Apple
                val thumbRadius = if (isScrubbing) 7.dp.toPx() else 4.5f.dp.toPx()
                val thumbCenterX = activeWidth.coerceIn(thumbRadius, w - thumbRadius)
                drawCircle(
                    color = Color.White,
                    radius = thumbRadius,
                    center = Offset(thumbCenterX, h / 2f)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 1.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = positionStr,
                color = if (isScrubbing) Color.White else Color(0xFF9CA3AF),
                fontSize = 12.sp,
                fontWeight = if (isScrubbing) FontWeight.SemiBold else FontWeight.Normal
            )
            Text(
                text = durationStr,
                color = Color(0xFF9CA3AF),
                fontSize = 12.sp
            )
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
