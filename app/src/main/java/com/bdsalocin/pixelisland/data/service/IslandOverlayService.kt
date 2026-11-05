package com.bdsalocin.pixelisland.data.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.bdsalocin.pixelisland.PixelIslandApplication
import com.bdsalocin.pixelisland.presentation.island.DynamicPillView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class IslandOverlayService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val serviceScope = CoroutineScope(Dispatchers.Main.immediate + Job())

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private lateinit var windowManager: WindowManager
    private var rootLayout: FrameLayout? = null
    private var composeView: ComposeView? = null
    private lateinit var layoutParams: WindowManager.LayoutParams

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        startForegroundServiceNotification()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        setupOverlayWindow()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    private fun startForegroundServiceNotification() {
        val channelId = "pixel_island_overlay"
        val channel = NotificationChannel(
            channelId,
            "Pixel Island Service",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)

        val notification = Notification.Builder(this, channelId)
            .setContentTitle("Pixel Island actif")
            .setContentText("Superposition ancrée sur le poinçon Pixel 8 Pro")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()

        startForeground(101, notification)
    }

    private fun setupOverlayWindow() {
        // Centrage physique absolu sur le poinçon du Google Pixel 8 Pro
        // Écran 1344 x 2992 px, caméra frontale centrée horizontalement à exactement 28dp du haut (98px).
        val density = resources.displayMetrics.density
        val pillHeightPx = (40 * density).toInt()
        var cutoutCenterY = (28 * density).toInt()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val cutout = windowManager.maximumWindowMetrics.windowInsets.displayCutout
                val topRect = cutout?.boundingRectTop
                if (topRect != null && !topRect.isEmpty) {
                    cutoutCenterY = topRect.centerY()
                }
            } catch (_: Exception) {}
        }

        val initialY = (cutoutCenterY - (pillHeightPx / 2)).coerceAtLeast(0)

        val baseFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED

        // Au repos l'îlot est inactif, le drapeau FLAG_NOT_TOUCHABLE et des dimensions 1x1 garantissent
        // que pas un seul pixel de l'écran n'est bloqué aux clics
        val initialFlags = baseFlags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE

        layoutParams = WindowManager.LayoutParams(
            1,
            1,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            initialFlags,
            PixelFormat.TRANSLUCENT
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            y = initialY
        }

        val container = (application as PixelIslandApplication).appContainer
        val frameContainer = object : FrameLayout(this) {
            private var startX = 0f
            private var startY = 0f
            private var startTime = 0L

            override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                if (event.action == MotionEvent.ACTION_OUTSIDE) {
                    val state = container.islandViewModel.uiState.value
                    if (state.isExpanded) {
                        container.islandViewModel.toggleExpand()
                    }
                    return false
                }

                val state = container.islandViewModel.uiState.value
                if (!state.isExpanded && state.isMusicActive) {
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            startX = event.rawX
                            startY = event.rawY
                            startTime = System.currentTimeMillis()
                        }
                        MotionEvent.ACTION_UP -> {
                            val duration = System.currentTimeMillis() - startTime
                            val dx = kotlin.math.abs(event.rawX - startX)
                            val dy = kotlin.math.abs(event.rawY - startY)
                            if (duration < 500 && dx < 40 * density && dy < 40 * density) {
                                container.islandViewModel.toggleExpand()
                                return true
                            }
                        }
                        MotionEvent.ACTION_CANCEL -> {
                            val duration = System.currentTimeMillis() - startTime
                            val dx = kotlin.math.abs(event.rawX - startX)
                            val dy = kotlin.math.abs(event.rawY - startY)
                            if (duration in 30..400 && dx < 25 * density && dy < 25 * density) {
                                container.islandViewModel.toggleExpand()
                                return true
                            }
                        }
                    }
                }

                return super.dispatchTouchEvent(event)
            }
        }.apply {
            setViewTreeLifecycleOwner(this@IslandOverlayService)
            setViewTreeSavedStateRegistryOwner(this@IslandOverlayService)
        }

        composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@IslandOverlayService)
            setViewTreeSavedStateRegistryOwner(this@IslandOverlayService)
            setContent {
                DynamicPillView(
                    viewModel = container.islandViewModel
                )
            }
        }

        frameContainer.addView(composeView)
        rootLayout = frameContainer
        windowManager.addView(rootLayout, layoutParams)

        // Adapter dynamiquement la surface WindowManager pour qu'elle corresponde exactement
        // à la taille physique de l'îlot sans jamais bloquer les zones environnantes de l'écran
        var resizeJob: Job? = null
        serviceScope.launch {
            container.islandViewModel.uiState.collect { state ->
                val density = resources.displayMetrics.density
                val shouldBeExpanded = state.isExpanded

                val targetWindowWidth: Int
                val targetWindowHeight: Int
                val targetFlags: Int

                when {
                    state.isFaceUnlockActive -> {
                        // Déverrouillage visage : priorité absolue, dimensions complètes même si musique en fond
                        targetWindowWidth = (190 * density).toInt()
                        targetWindowHeight = (74 * density).toInt()
                        targetFlags = baseFlags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    }
                    shouldBeExpanded -> {
                        // Carte de musique détaillée : dimensionnée au plus juste
                        targetWindowWidth = (310 * density).toInt()
                        targetWindowHeight = (156 * density).toInt()
                        targetFlags = baseFlags or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                    }
                    state.isMusicActive -> {
                        // Pilule compacte : zone réceptive confortable couvrant la pilule
                        targetWindowWidth = (190 * density).toInt()
                        targetWindowHeight = (48 * density).toInt()
                        targetFlags = baseFlags
                    }
                    else -> {
                        // Inactif : fenêtre 1x1 complètement non tactile
                        targetWindowWidth = 1
                        targetWindowHeight = 1
                        targetFlags = baseFlags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    }
                }

                val sizeChanged = layoutParams.width != targetWindowWidth || layoutParams.height != targetWindowHeight
                val flagsChanged = layoutParams.flags != targetFlags

                if (sizeChanged || flagsChanged) {
                    resizeJob?.cancel()
                    val isGrowing = targetWindowWidth >= layoutParams.width && targetWindowHeight >= layoutParams.height

                    if (isGrowing) {
                        // Agrandissement immédiat pour ne pas rogner l'animation
                        layoutParams.width = targetWindowWidth
                        layoutParams.height = targetWindowHeight
                        layoutParams.flags = targetFlags
                        rootLayout?.let { view ->
                            try {
                                windowManager.updateViewLayout(view, layoutParams)
                            } catch (_: Exception) {}
                        }
                    } else {
                        // Si l'îlot passe en mode non tactile (ex. fermeture), libérer immédiatement l'écran
                        if ((targetFlags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) != 0) {
                            layoutParams.flags = targetFlags
                            rootLayout?.let { view ->
                                try {
                                    windowManager.updateViewLayout(view, layoutParams)
                                } catch (_: Exception) {}
                            }
                        }
                        // Réduction différée des dimensions pour laisser l'animation Compose se refermer
                        resizeJob = serviceScope.launch {
                            kotlinx.coroutines.delay(420)
                            layoutParams.width = targetWindowWidth
                            layoutParams.height = targetWindowHeight
                            layoutParams.flags = targetFlags
                            rootLayout?.let { view ->
                                try {
                                    windowManager.updateViewLayout(view, layoutParams)
                                } catch (_: Exception) {}
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val rotation = windowManager.defaultDisplay.rotation
        val container = (application as PixelIslandApplication).appContainer
        container.calculateCutoutAnchorUseCase.onConfigurationChanged(rotation)

        layoutParams.gravity = when (rotation) {
            1 -> Gravity.START or Gravity.CENTER_VERTICAL
            3 -> Gravity.END or Gravity.CENTER_VERTICAL
            else -> Gravity.TOP or Gravity.CENTER_HORIZONTAL
        }
        try {
            rootLayout?.let { windowManager.updateViewLayout(it, layoutParams) }
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        rootLayout?.let { windowManager.removeView(it) }
    }
}
