package com.bdsalocin.pixelisland.data.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.Surface
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import com.bdsalocin.pixelisland.core.util.HapticFeedbackHelper
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

    companion object {
        private var instance: IslandOverlayService? = null

        fun removeOverlayIfPresent() {
            instance?.removeOverlayView()
        }
    }

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val serviceScope = CoroutineScope(Dispatchers.Main.immediate + Job())

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private lateinit var windowManager: WindowManager
    private var rootLayout: FrameLayout? = null
    private var composeView: ComposeView? = null
    private lateinit var overlayLayoutParams: WindowManager.LayoutParams
    private var resizeJob: Job? = null
    private var pendingTargetWidth: Int? = null
    private var pendingTargetHeight: Int? = null
    private var cachedPortraitCutoutCenterY: Int? = null
    private var screenReceiver: BroadcastReceiver? = null

    private val baseFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
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
        val isPortrait = resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
        if (isPortrait) {
            cachedPortraitCutoutCenterY?.let { return it }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val cutout = windowManager.maximumWindowMetrics.windowInsets.displayCutout
                val topRect = cutout?.boundingRectTop
                if (topRect != null && !topRect.isEmpty) {
                    val cy = topRect.centerY()
                    if (isPortrait) {
                        cachedPortraitCutoutCenterY = cy
                    }
                    return cy
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
        if (PixelIslandAccessibilityService.isAccessibilityServiceActive) return
        val density = resources.displayMetrics.density
        val pillHeightPx = (40 * density).toInt()
        val cutoutCenterY = getCutoutCenterY()
        val initialY = (cutoutCenterY - (pillHeightPx / 2)).coerceAtLeast(0)

        val initialFlags = baseFlags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        val stableWidthPx = (390 * density).toInt()
        val idleHeightPx = (104 * density).toInt()

        overlayLayoutParams = WindowManager.LayoutParams(
            stableWidthPx,
            idleHeightPx,
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
            private var startRawX = 0f
            private var startRawY = 0f
            private var startTime = 0L
            private var isLongPressTriggered = false
            private var isExpandedTouchDown = false
            private var isCardMarginTouchDown = false
            private val longPressHandler = Handler(Looper.getMainLooper())
            private val longPressTimeout = 380L
            private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()

            private val longPressRunnable = Runnable {
                val state = container.islandViewModel.uiState.value
                if (state.isMusicActive && !state.isExpanded) {
                    isLongPressTriggered = true
                    HapticFeedbackHelper.performTapticFeedback(this@IslandOverlayService, this)
                    container.islandViewModel.expandIsland()
                }
            }

            override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                if (event.action == MotionEvent.ACTION_OUTSIDE) {
                    val state = container.islandViewModel.uiState.value
                    if (state.isExpanded) {
                        container.islandViewModel.collapseIsland()
                    }
                    return false
                }

                val state = container.islandViewModel.uiState.value
                val d = resources.displayMetrics.density

                if (state.isExpanded) {
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                        startRawX = event.rawX
                        startRawY = event.rawY
                        startTime = System.currentTimeMillis()
                        isExpandedTouchDown = true
                        val cardWidthPx = 384f * d
                        val cardHeightPx = 238f * d
                        val currentW = if (width > 0) width.toFloat() else cardWidthPx
                        val cardLeft = (currentW - cardWidthPx) / 2f
                        val cardRight = (currentW + cardWidthPx) / 2f
                        isCardMarginTouchDown = !(event.x in cardLeft..cardRight && event.y in 0f..cardHeightPx)
                    } else if (event.actionMasked == MotionEvent.ACTION_UP) {
                        if (isLongPressTriggered) {
                            isLongPressTriggered = false
                            isExpandedTouchDown = false
                            isCardMarginTouchDown = false
                            return true
                        }
                        val duration = System.currentTimeMillis() - startTime
                        val dx = kotlin.math.abs(event.rawX - startRawX)
                        val dy = kotlin.math.abs(event.rawY - startRawY)
                        val isTap = duration < 600 && dx < 30 * d && dy < 30 * d
                        if (isTap && (isCardMarginTouchDown || (isExpandedTouchDown && event.y < 40 * d))) {
                            isExpandedTouchDown = false
                            isCardMarginTouchDown = false
                            container.islandViewModel.collapseIsland()
                            return true
                        }
                        isExpandedTouchDown = false
                        isCardMarginTouchDown = false
                    } else if (event.actionMasked == MotionEvent.ACTION_CANCEL) {
                        isLongPressTriggered = false
                        isExpandedTouchDown = false
                        isCardMarginTouchDown = false
                    }
                    return super.dispatchTouchEvent(event)
                }

                if (state.isMusicActive) {
                    val d = resources.displayMetrics.density
                    val pillWidthPx = 180f * d
                    val pillHeightPx = 44f * d
                    val currentW = if (width > 0) width.toFloat() else pillWidthPx
                    val pillLeft = (currentW - pillWidthPx) / 2f
                    val pillRight = (currentW + pillWidthPx) / 2f
                    val isTouchOnPill = event.x in pillLeft..pillRight && event.y in 0f..pillHeightPx

                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            if (!isTouchOnPill) {
                                return false
                            }
                            startRawX = event.rawX
                            startRawY = event.rawY
                            startTime = System.currentTimeMillis()
                            isLongPressTriggered = false
                            isExpandedTouchDown = false
                            longPressHandler.removeCallbacks(longPressRunnable)
                            longPressHandler.postDelayed(longPressRunnable, longPressTimeout)
                            return true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val dx = kotlin.math.abs(event.rawX - startRawX)
                            val dy = kotlin.math.abs(event.rawY - startRawY)
                            if (dx > touchSlop || dy > touchSlop) {
                                longPressHandler.removeCallbacks(longPressRunnable)
                            }
                            return true
                        }
                        MotionEvent.ACTION_UP -> {
                            longPressHandler.removeCallbacks(longPressRunnable)
                            if (!isLongPressTriggered) {
                                val duration = System.currentTimeMillis() - startTime
                                val dx = kotlin.math.abs(event.rawX - startRawX)
                                val dy = kotlin.math.abs(event.rawY - startRawY)
                                val isTap = duration < longPressTimeout && dx <= touchSlop && dy <= touchSlop
                                if (isTap && isTouchOnPill) {
                                    container.islandViewModel.onOpenMusicAppClicked()
                                }
                            }
                            isLongPressTriggered = false
                            return true
                        }
                        MotionEvent.ACTION_CANCEL -> {
                            longPressHandler.removeCallbacks(longPressRunnable)
                            isLongPressTriggered = false
                            return true
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

        val composeLayoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
            Gravity.TOP or Gravity.CENTER_HORIZONTAL
        )
        frameContainer.addView(composeView, composeLayoutParams)
        rootLayout = frameContainer
        windowManager.addView(rootLayout, overlayLayoutParams)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        container.islandViewModel.onScreenOff()
                        val s = container.islandViewModel.uiState.value
                        if (!s.isMusicActive) {
                            composeView?.alpha = 0f
                        }
                        resizeJob?.cancel()
                        pendingTargetWidth = null
                        pendingTargetHeight = null
                        val density = resources.displayMetrics.density
                        val targetH = if (s.isMusicActive) (44 * density).toInt() else (104 * density).toInt()
                        overlayLayoutParams.height = targetH
                        overlayLayoutParams.flags = if (s.isMusicActive) baseFlags else (baseFlags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
                        rootLayout?.let { view ->
                            try {
                                windowManager.updateViewLayout(view, overlayLayoutParams)
                            } catch (_: Exception) {}
                        }
                    }
                    Intent.ACTION_USER_PRESENT -> {
                        composeView?.alpha = 1f
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }
        screenReceiver = receiver

        serviceScope.launch {
            container.islandViewModel.uiState.collect {
                updateWindowLayoutForState()
            }
        }
    }

    private fun updateWindowLayoutForState() {
        val container = (application as? PixelIslandApplication)?.appContainer ?: return
        val state = container.islandViewModel.uiState.value
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val density = resources.displayMetrics.density
        val pillHeightPx = (40 * density).toInt()

        if (isLandscape) {
            // Option 1 : Masquage total de la Dynamic Island en mode paysage pour libérer l'écran
            resizeJob?.cancel()
            pendingTargetWidth = null
            pendingTargetHeight = null
            rootLayout?.visibility = android.view.View.GONE
            overlayLayoutParams.width = 1
            overlayLayoutParams.height = 1
            overlayLayoutParams.flags = baseFlags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            rootLayout?.let { view ->
                try {
                    windowManager.updateViewLayout(view, overlayLayoutParams)
                } catch (_: Exception) {}
            }
            return
        }

        // Mode vertical (portrait) : actif et centré sur le capteur photo physique
        rootLayout?.visibility = android.view.View.VISIBLE
        if (state.isFaceUnlockActive || state.isMusicActive || state.isExpanded) {
            composeView?.alpha = 1f
        }
        overlayLayoutParams.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        overlayLayoutParams.x = 0
        overlayLayoutParams.y = (getCutoutCenterY() - (pillHeightPx / 2)).coerceAtLeast(0)

        val shouldBeExpanded = state.isExpanded
        val targetWindowWidth: Int
        val targetWindowHeight: Int
        val targetFlags: Int

        when {
            state.isFaceUnlockActive -> {
                targetWindowWidth = (390 * density).toInt()
                targetWindowHeight = (104 * density).toInt()
                targetFlags = baseFlags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            }
            shouldBeExpanded -> {
                targetWindowWidth = (390 * density).toInt()
                targetWindowHeight = (244 * density).toInt()
                targetFlags = baseFlags or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
            }
            state.isMusicActive -> {
                targetWindowWidth = (390 * density).toInt()
                targetWindowHeight = (44 * density).toInt()
                targetFlags = baseFlags
            }
            else -> {
                targetWindowWidth = (390 * density).toInt()
                targetWindowHeight = (104 * density).toInt()
                targetFlags = baseFlags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            }
        }

        val sizeChanged = overlayLayoutParams.width != targetWindowWidth || overlayLayoutParams.height != targetWindowHeight
        val flagsChanged = overlayLayoutParams.flags != targetFlags

        if (!sizeChanged) {
            resizeJob?.cancel()
            pendingTargetWidth = null
            pendingTargetHeight = null
        }

        if (sizeChanged || flagsChanged) {
            val needsBiggerWidth = targetWindowWidth > overlayLayoutParams.width
            val needsBiggerHeight = targetWindowHeight > overlayLayoutParams.height

            if (needsBiggerWidth || needsBiggerHeight) {
                resizeJob?.cancel()
                pendingTargetWidth = null
                pendingTargetHeight = null
                // Agrandissement immédiat de la fenêtre overlay pour ne jamais rogner l'animation Compose
                overlayLayoutParams.width = maxOf(overlayLayoutParams.width, targetWindowWidth)
                overlayLayoutParams.height = maxOf(overlayLayoutParams.height, targetWindowHeight)
                overlayLayoutParams.flags = targetFlags
                rootLayout?.let { view ->
                    try {
                        windowManager.updateViewLayout(view, overlayLayoutParams)
                    } catch (_: Exception) {}
                }
            }

            val isShrinking = targetWindowWidth < overlayLayoutParams.width || targetWindowHeight < overlayLayoutParams.height
            if (isShrinking) {
                if ((targetFlags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) != 0) {
                    overlayLayoutParams.flags = targetFlags
                    rootLayout?.let { view ->
                        try {
                            windowManager.updateViewLayout(view, overlayLayoutParams)
                        } catch (_: Exception) {}
                    }
                }
                if (resizeJob?.isActive == true && pendingTargetWidth == targetWindowWidth && pendingTargetHeight == targetWindowHeight) {
                    return
                }
                resizeJob?.cancel()
                pendingTargetWidth = targetWindowWidth
                pendingTargetHeight = targetWindowHeight
                resizeJob = serviceScope.launch {
                    kotlinx.coroutines.delay(320)
                    pendingTargetWidth = null
                    pendingTargetHeight = null
                    overlayLayoutParams.width = targetWindowWidth
                    overlayLayoutParams.height = targetWindowHeight
                    overlayLayoutParams.flags = targetFlags
                    rootLayout?.let { view ->
                        try {
                            windowManager.updateViewLayout(view, overlayLayoutParams)
                        } catch (_: Exception) {}
                    }
                }
            } else if (!needsBiggerWidth && !needsBiggerHeight) {
                if (flagsChanged) {
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

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val container = (application as? PixelIslandApplication)?.appContainer
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            container?.islandViewModel?.collapseIsland()
        }
        updateWindowLayoutForState()
    }

    private fun removeOverlayView() {
        resizeJob?.cancel()
        pendingTargetWidth = null
        pendingTargetHeight = null
        screenReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: Exception) {}
            screenReceiver = null
        }
        rootLayout?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {}
            rootLayout = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) instance = null
        serviceScope.cancel()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        removeOverlayView()
    }
}
