package com.bdsalocin.pixelisland.data.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.Surface
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
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

class PixelIslandAccessibilityService : AccessibilityService(), LifecycleOwner, SavedStateRegistryOwner {

    companion object {
        @Volatile
        var isAccessibilityServiceActive: Boolean = false
            private set
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

    private val baseFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Pixel Island utilise l'accessibilité pour l'élévation tactile de la Dynamic Island
    }

    override fun onInterrupt() {
        // Aucun traitement bloquant sur interruption
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isAccessibilityServiceActive = true
        IslandOverlayService.removeOverlayIfPresent()

        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        setupOverlayWindow()

        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
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

        val initialFlags = baseFlags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE

        // TYPE_ACCESSIBILITY_OVERLAY : Z-order supérieur à la barre d'état système, autorisant les clics sur le poinçon
        overlayLayoutParams = WindowManager.LayoutParams(
            1,
            1,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
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
            private val longPressHandler = Handler(Looper.getMainLooper())
            private val longPressTimeout = 380L
            private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()

            private val longPressRunnable = Runnable {
                val state = container.islandViewModel.uiState.value
                if (state.isMusicActive && !state.isExpanded) {
                    isLongPressTriggered = true
                    HapticFeedbackHelper.performTapticFeedback(this@PixelIslandAccessibilityService, this)
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
                    } else if (event.actionMasked == MotionEvent.ACTION_UP) {
                        if (isLongPressTriggered) {
                            isLongPressTriggered = false
                            isExpandedTouchDown = false
                            return true
                        }
                        if (isExpandedTouchDown && event.y < 40 * d) {
                            val duration = System.currentTimeMillis() - startTime
                            val dy = kotlin.math.abs(event.rawY - startRawY)
                            if (duration < 600 && dy < 30 * d) {
                                isExpandedTouchDown = false
                                container.islandViewModel.collapseIsland()
                                return true
                            }
                        }
                        isExpandedTouchDown = false
                    } else if (event.actionMasked == MotionEvent.ACTION_CANCEL) {
                        isLongPressTriggered = false
                        isExpandedTouchDown = false
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
            setViewTreeLifecycleOwner(this@PixelIslandAccessibilityService)
            setViewTreeSavedStateRegistryOwner(this@PixelIslandAccessibilityService)
        }

        composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@PixelIslandAccessibilityService)
            setViewTreeSavedStateRegistryOwner(this@PixelIslandAccessibilityService)
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
        overlayLayoutParams.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        overlayLayoutParams.x = 0
        overlayLayoutParams.y = (getCutoutCenterY() - (pillHeightPx / 2)).coerceAtLeast(0)

        val shouldBeExpanded = state.isExpanded
        val targetWindowWidth: Int
        val targetWindowHeight: Int
        val targetFlags: Int

        when {
            state.isFaceUnlockActive -> {
                targetWindowWidth = (84 * density).toInt()
                targetWindowHeight = (84 * density).toInt()
                targetFlags = baseFlags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            }
            shouldBeExpanded -> {
                targetWindowWidth = (390 * density).toInt()
                targetWindowHeight = (244 * density).toInt()
                targetFlags = baseFlags or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
            }
            state.isMusicActive -> {
                targetWindowWidth = (180 * density).toInt()
                targetWindowHeight = (44 * density).toInt()
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
            val needsBiggerWidth = targetWindowWidth > overlayLayoutParams.width
            val needsBiggerHeight = targetWindowHeight > overlayLayoutParams.height

            if (needsBiggerWidth || needsBiggerHeight) {
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
                resizeJob?.cancel()
                resizeJob = serviceScope.launch {
                    kotlinx.coroutines.delay(280)
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

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        isAccessibilityServiceActive = false
        cleanUp()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        isAccessibilityServiceActive = false
        cleanUp()
    }

    private fun cleanUp() {
        resizeJob?.cancel()
        serviceScope.cancel()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        rootLayout?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {}
            rootLayout = null
        }
    }
}
