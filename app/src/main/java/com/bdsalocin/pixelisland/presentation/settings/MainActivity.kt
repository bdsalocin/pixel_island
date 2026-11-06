package com.bdsalocin.pixelisland.presentation.settings

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.bdsalocin.pixelisland.data.service.IslandOverlayService
import com.bdsalocin.pixelisland.domain.startup.AndroidStartupPermissionChecker
import com.bdsalocin.pixelisland.domain.startup.AutoReturnWatcher
import com.bdsalocin.pixelisland.domain.startup.SetupPermissionType
import com.bdsalocin.pixelisland.domain.startup.StartupPermissionChecker
import com.bdsalocin.pixelisland.domain.startup.StartupPermissionStatus
import com.bdsalocin.pixelisland.presentation.theme.PixelIslandButtonShape
import com.bdsalocin.pixelisland.presentation.theme.PixelIslandCardShape
import com.bdsalocin.pixelisland.presentation.theme.PixelIslandTheme
import com.bdsalocin.pixelisland.presentation.theme.StateConfirmedGreen
import com.bdsalocin.pixelisland.presentation.theme.StatePendingNeutral

/**
 * Activite de demarrage Material 3 Expressive pour Pixel Island sur Google Pixel 8 Pro.
 * Reflet d etat en direct, coches de confirmation animees, routage bidirectionnel,
 * assistance pour reglages restreints et flux de sortie furtive.
 */
class MainActivity : ComponentActivity() {

    private var _permissionChecker: StartupPermissionChecker? = null

    internal var permissionChecker: StartupPermissionChecker
        get() = _permissionChecker ?: AndroidStartupPermissionChecker(this).also { _permissionChecker = it }
        set(value) {
            _permissionChecker = value
        }

    internal val autoReturnWatcher: AutoReturnWatcher by lazy {
        AutoReturnWatcher(
            permissionChecker = permissionChecker,
            onAutoReturnTriggered = { _ ->
                AutoReturnWatcher.bringToForeground(this@MainActivity, MainActivity::class.java)
            },
            scope = lifecycleScope
        )
    }

    private val permissionStatusState = mutableStateOf(StartupPermissionStatus())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissionStatusState.value = permissionChecker.checkStatus()

        setContent {
            PixelIslandTheme {
                StartupDashboardScreen(
                    status = permissionStatusState.value,
                    onPermissionCardClick = { type, isGranted ->
                        handlePermissionCardClick(type, isGranted)
                    },
                    onOpenAppDetailsClick = {
                        SettingsIntentResolver.launchAppDetailsSettings(this@MainActivity)
                    },
                    onLaunchClick = {
                        launchPixelIslandAndExit()
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        autoReturnWatcher.stopWatching()
        permissionStatusState.value = permissionChecker.checkStatus()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        autoReturnWatcher.stopWatching()
        permissionStatusState.value = permissionChecker.checkStatus()
    }

    override fun onDestroy() {
        super.onDestroy()
        autoReturnWatcher.stopWatching()
    }

    private fun handlePermissionCardClick(type: SetupPermissionType, isGranted: Boolean) {
        if (!isGranted) {
            autoReturnWatcher.startWatching(type)
        }
        SettingsIntentResolver.launchSettingsForPermission(this, type)
    }

    private fun launchPixelIslandAndExit() {
        val currentStatus = permissionChecker.checkStatus()
        if (currentStatus.canLaunchIsland) {
            if (currentStatus.isOverlayGranted) {
                try {
                    val serviceIntent = Intent(this, IslandOverlayService::class.java)
                    startForegroundService(serviceIntent)
                } catch (_: Exception) {
                    // Protection contre les exceptions sur variantes specifiques
                }
            }
            finishAndRemoveTask()
        }
    }
}

/**
 * Ecran racine du tableau de bord de configuration Material 3 Expressive.
 */
@Composable
fun StartupDashboardScreen(
    status: StartupPermissionStatus,
    onPermissionCardClick: (SetupPermissionType, Boolean) -> Unit,
    onOpenAppDetailsClick: () -> Unit,
    onLaunchClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            StartupHeader()

            Spacer(modifier = Modifier.height(20.dp))

            ReadinessSummaryBanner(allGranted = status.allGranted)

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Autorisations et services",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Carte 1 : Superposition
            SetupPermissionCard(
                title = "Affichage au dessus des autres applications",
                description = "Superposition permanente de la pilule autour du poinçon",
                isGranted = status.isOverlayGranted,
                onClick = { onPermissionCardClick(SetupPermissionType.OVERLAY, status.isOverlayGranted) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Carte 2 : Ecoute audio et notifications
            SetupPermissionCard(
                title = "Écoute audio et notifications",
                description = "Détection du lecteur musical et de la pochette",
                isGranted = status.isNotificationGranted,
                onClick = { onPermissionCardClick(SetupPermissionType.NOTIFICATION, status.isNotificationGranted) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Carte 3 : Toucher du poincon et accessibilite
            SetupPermissionCard(
                title = "Toucher du poinçon et accessibilité",
                description = "Interactions tactiles directes sur la caméra",
                isGranted = status.isAccessibilityGranted,
                onClick = { onPermissionCardClick(SetupPermissionType.ACCESSIBILITY, status.isAccessibilityGranted) }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Carte d assistance reglages restreints Android 13+
            RestrictedSettingsAssistanceCard(
                onOpenAppDetails = onOpenAppDetailsClick
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Bouton principal de lancement et fermeture
            Button(
                onClick = onLaunchClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = PixelIslandButtonShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                ),
                enabled = status.canLaunchIsland
            ) {
                Text(
                    text = "Lancer Pixel Island",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "L'application continue de fonctionner en arrière plan",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

/**
 * En tete de demarrage avec silhouette de la pilule et du capteur photo.
 */
@Composable
fun StartupHeader(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .width(56.dp)
                .height(18.dp)
                .background(Color.Black, shape = RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(Color(0xFF1E293B), shape = CircleShape)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "Pixel Island",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Configuration pour Google Pixel 8 Pro",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Banniere recapitulative de statut global.
 */
@Composable
fun ReadinessSummaryBanner(
    allGranted: Boolean,
    modifier: Modifier = Modifier
) {
    val containerColor = if (allGranted) {
        StateConfirmedGreen.copy(alpha = 0.14f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }

    val contentColor = if (allGranted) {
        StateConfirmedGreen
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    val bannerText = if (allGranted) {
        "Toutes les autorisations sont accordées"
    } else {
        "Autorisations requises pour configuration complète"
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = BorderStroke(
            1.dp,
            if (allGranted) StateConfirmedGreen.copy(alpha = 0.35f) else Color.Transparent
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = bannerText,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                color = contentColor,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Carte interactive d autorisation avec pastille indicatrice en direct.
 */
@Composable
fun SetupPermissionCard(
    title: String,
    description: String,
    isGranted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardBackground = if (isGranted) {
        MaterialTheme.colorScheme.surfaceContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }

    val borderColor = if (isGranted) {
        StateConfirmedGreen.copy(alpha = 0.35f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(PixelIslandCardShape)
            .clickable(onClick = onClick),
        shape = PixelIslandCardShape,
        color = cardBackground,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 14.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (isGranted) "Accordé" else "À configurer",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    color = if (isGranted) StateConfirmedGreen else StatePendingNeutral
                )
            }

            PermissionStatusIndicator(isGranted = isGranted)
        }
    }
}

/**
 * Indicateur visuel d etat avec transition douce (Coche verte vs Fleche neutre).
 */
@Composable
fun PermissionStatusIndicator(
    isGranted: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedContent(
        targetState = isGranted,
        transitionSpec = {
            (fadeIn(animationSpec = tween(220, easing = FastOutSlowInEasing)) +
             scaleIn(initialScale = 0.85f, animationSpec = tween(220, easing = FastOutSlowInEasing)))
                .togetherWith(
                    fadeOut(animationSpec = tween(180, easing = FastOutSlowInEasing)) +
                    scaleOut(targetScale = 0.85f, animationSpec = tween(180, easing = FastOutSlowInEasing))
                )
        },
        label = "PermissionStatusTransition",
        modifier = modifier
    ) { granted ->
        if (granted) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(color = StateConfirmedGreen, shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                StatusCheckmarkIcon(
                    modifier = Modifier.size(20.dp),
                    tint = Color(0xFF022C22)
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(color = Color(0xFF334155), shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                StatusArrowForwardIcon(
                    modifier = Modifier.size(18.dp),
                    tint = Color(0xFF94A3B8)
                )
            }
        }
    }
}

/**
 * Carte d assistance pour resoudre les parametres restreints sous Android 13 et versions superieures.
 */
@Composable
fun RestrictedSettingsAssistanceCard(
    onOpenAppDetails: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            Text(
                text = "Paramètres restreints (Android 13 et supérieur)",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Si le commutateur d'accessibilité est grisé dans les réglages système, ouvrez les détails de l'application, touchez les trois points verticaux en haut à droite puis choisissez Autoriser les paramètres restreints.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedButton(
                onClick = onOpenAppDetails,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Text(
                    text = "Ouvrir les détails de l'application",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

/**
 * Icone vectorielle nette de coche de validation dessinee sur Canvas.
 */
@Composable
fun StatusCheckmarkIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val strokeWidth = 2.6.dp.toPx()

        val checkPath = Path().apply {
            moveTo(w * 0.22f, h * 0.52f)
            lineTo(w * 0.42f, h * 0.72f)
            lineTo(w * 0.78f, h * 0.32f)
        }

        drawPath(
            path = checkPath,
            color = tint,
            style = Stroke(
                width = strokeWidth,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}

/**
 * Icone vectorielle nette de fleche vers l avant dessinee sur Canvas.
 */
@Composable
fun StatusArrowForwardIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val strokeWidth = 2.4.dp.toPx()

        val arrowPath = Path().apply {
            moveTo(w * 0.20f, h * 0.50f)
            lineTo(w * 0.76f, h * 0.50f)
            moveTo(w * 0.52f, h * 0.26f)
            lineTo(w * 0.78f, h * 0.50f)
            lineTo(w * 0.52f, h * 0.74f)
        }

        drawPath(
            path = arrowPath,
            color = tint,
            style = Stroke(
                width = strokeWidth,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}
