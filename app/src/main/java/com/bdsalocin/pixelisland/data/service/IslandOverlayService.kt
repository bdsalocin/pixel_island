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
import android.view.WindowManager
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

        val windowFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            windowFlags,
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
        composeView = ComposeView(this).apply {
            isClickable = true
            setViewTreeLifecycleOwner(this@IslandOverlayService)
            setViewTreeSavedStateRegistryOwner(this@IslandOverlayService)
            setContent {
                DynamicPillView(
                    viewModel = container.islandViewModel
                )
            }
        }

        windowManager.addView(composeView, layoutParams)

        // Adapter dynamiquement la surface WindowManager lors de l'expansion pour afficher
        // l'intégralité de la carte détaillée comme sur iPhone sans aucun rognage
        serviceScope.launch {
            container.islandViewModel.uiState.collect { state ->
                val density = resources.displayMetrics.density
                val shouldBeExpanded = state.isExpanded
                val targetWindowWidth = if (shouldBeExpanded) {
                    (330 * density).toInt()
                } else if (state.isFaceUnlockActive) {
                    (210 * density).toInt()
                } else if (state.isMusicActive) {
                    (200 * density).toInt()
                } else {
                    WindowManager.LayoutParams.WRAP_CONTENT
                }

                val targetWindowHeight = if (shouldBeExpanded) {
                    (180 * density).toInt()
                } else if (state.isFaceUnlockActive) {
                    (90 * density).toInt()
                } else if (state.isMusicActive) {
                    (50 * density).toInt()
                } else {
                    WindowManager.LayoutParams.WRAP_CONTENT
                }

                if (layoutParams.width != targetWindowWidth || layoutParams.height != targetWindowHeight) {
                    layoutParams.width = targetWindowWidth
                    layoutParams.height = targetWindowHeight
                    val view = composeView
                    if (view != null) {
                        try {
                            windowManager.updateViewLayout(view, layoutParams)
                        } catch (_: Exception) {}
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
            windowManager.updateViewLayout(composeView, layoutParams)
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        composeView?.let { windowManager.removeView(it) }
    }
}
