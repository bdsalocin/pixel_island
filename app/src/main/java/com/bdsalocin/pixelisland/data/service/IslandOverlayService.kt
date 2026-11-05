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
import android.view.Surface
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
    private lateinit var overlayLayoutParams: WindowManager.LayoutParams

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

    private fun getCutoutCenterY(): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val cutout = windowManager.maximumWindowMetrics.windowInsets.displayCutout
                val topRect = cutout?.boundingRectTop
                if (topRect != null && !topRect.isEmpty) {
                    return topRect.centerY()
                }
            } catch (_: Exception) {}
        }
        val resId = resources.getIdentifier("status_bar_height", "dimen", "android")
        val statusBarHeight = if (resId > 0) {
            resources.getDimensionPixelSize(resId)
        } else {
            (44 * resources.displayMetrics.density).toInt()
        }
        return statusBarHeight / 2
    }

    private fun setupOverlayWindow() {
        val density = resources.displayMetrics.density
        val pillHeightPx = (40 * density).toInt()
        val cutoutCenterY = getCutoutCenterY()
        val initialY = (cutoutCenterY - (pillHeightPx / 2)).coerceAtLeast(0)

        val baseFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED

        val initialFlags = baseFlags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE

        overlayLayoutParams = WindowManager.LayoutParams(
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
            override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                if (event.action == MotionEvent.ACTION_OUTSIDE) {
                    val state = container.islandViewModel.uiState.value
                    if (state.isExpanded) {
                        container.islandViewModel.toggleExpand()
                    }
                    return false
                }

                val state = container.islandViewModel.uiState.value
                val d = resources.displayMetrics.density

                // Si la carte est ouverte et qu'on tape dans la marge au-dessus (zone barre d'état)
                if (state.isExpanded && event.actionMasked == MotionEvent.ACTION_UP && event.y < 40 * d) {
                    container.islandViewModel.toggleExpand()
                    return true
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
        windowManager.addView(rootLayout, overlayLayoutParams)

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
                        targetWindowWidth = (200 * density).toInt()
                        targetWindowHeight = (80 * density).toInt()
                        targetFlags = baseFlags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    }
                    shouldBeExpanded -> {
                        targetWindowWidth = (320 * density).toInt()
                        targetWindowHeight = (216 * density).toInt()
                        targetFlags = baseFlags or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                    }
                    state.isMusicActive -> {
                        targetWindowWidth = (180 * density).toInt()
                        targetWindowHeight = (40 * density).toInt()
                        targetFlags = baseFlags
                    }
                    else -> {
                        targetWindowWidth = 1
                        targetWindowHeight = 1
                        targetFlags = baseFlags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    }
                }

                val sizeChanged = overlayLayoutParams.width != targetWindowWidth || overlayLayoutParams.height != targetWindowHeight
                val flagsChanged = overlayLayoutParams.flags != targetFlags

                if (sizeChanged || flagsChanged) {
                    resizeJob?.cancel()
                    val isGrowing = targetWindowWidth >= overlayLayoutParams.width && targetWindowHeight >= overlayLayoutParams.height

                    if (isGrowing) {
                        overlayLayoutParams.width = targetWindowWidth
                        overlayLayoutParams.height = targetWindowHeight
                        overlayLayoutParams.flags = targetFlags
                        rootLayout?.let { view ->
                            try {
                                windowManager.updateViewLayout(view, overlayLayoutParams)
                            } catch (_: Exception) {}
                        }
                    } else {
                        if ((targetFlags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) != 0) {
                            overlayLayoutParams.flags = targetFlags
                            rootLayout?.let { view ->
                                try {
                                    windowManager.updateViewLayout(view, overlayLayoutParams)
                                } catch (_: Exception) {}
                            }
                        }
                        resizeJob = serviceScope.launch {
                            kotlinx.coroutines.delay(420)
                            overlayLayoutParams.width = targetWindowWidth
                            overlayLayoutParams.height = targetWindowHeight
                            overlayLayoutParams.flags = targetFlags
                            rootLayout?.let { view ->
                                try {
                                    windowManager.updateViewLayout(view, overlayLayoutParams)
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
        val rotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display?.rotation ?: Surface.ROTATION_0
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.rotation
        }
        val container = (application as PixelIslandApplication).appContainer
        container.calculateCutoutAnchorUseCase.onConfigurationChanged(rotation)

        overlayLayoutParams.gravity = when (rotation) {
            1 -> Gravity.START or Gravity.CENTER_VERTICAL
            3 -> Gravity.END or Gravity.CENTER_VERTICAL
            else -> Gravity.TOP or Gravity.CENTER_HORIZONTAL
        }
        overlayLayoutParams.y = if (rotation == 0) getCutoutCenterY() - (40 * resources.displayMetrics.density / 2).toInt() else 0
        try {
            rootLayout?.let { windowManager.updateViewLayout(it, overlayLayoutParams) }
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
