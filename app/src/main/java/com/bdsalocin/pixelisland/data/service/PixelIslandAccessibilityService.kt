package com.bdsalocin.pixelisland.data.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.Surface
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
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
            private var startLocalX = 0f
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
                val d = resources.displayMetrics.density

                // Si la carte est ouverte et qu'on tape dans la marge au-dessus (zone barre d'état)
                if (state.isExpanded) {
                    if (event.actionMasked == MotionEvent.ACTION_UP && event.y < 40 * d) {
                        container.islandViewModel.toggleExpand()
                        return true
                    }
                    return super.dispatchTouchEvent(event)
                }

                // Si l'îlot est en mode compact musique :
                if (state.isMusicActive) {
                    val w = if (width > 0) width.toFloat() else (180f * d)
                    val centerX = w / 2f
                    val deadZone = 16f * d // 32dp au centre réservé pour le capteur photo

                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            startRawX = event.rawX
                            startRawY = event.rawY
                            startLocalX = event.x
                            startTime = System.currentTimeMillis()
                            // Consommer ACTION_DOWN pour garantir la réception de ACTION_UP par le système
                            return true
                        }
                        MotionEvent.ACTION_UP -> {
                            val duration = System.currentTimeMillis() - startTime
                            val dx = kotlin.math.abs(event.rawX - startRawX)
                            val dy = kotlin.math.abs(event.rawY - startRawY)
                            val isTap = duration < 800 && dx < 40 * d && dy < 40 * d

                            if (isTap) {
                                val isLeftWing = (startLocalX < centerX - deadZone) || (event.x < centerX - deadZone)
                                val isRightWing = (startLocalX > centerX + deadZone) || (event.x > centerX + deadZone)

                                if (isLeftWing || isRightWing) {
                                    container.islandViewModel.toggleExpand()
                                    return true
                                }
                            }
                            return true
                        }
                        MotionEvent.ACTION_CANCEL -> {
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
