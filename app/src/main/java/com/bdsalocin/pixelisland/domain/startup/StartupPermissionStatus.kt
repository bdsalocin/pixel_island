package com.bdsalocin.pixelisland.domain.startup

/**
 * Enumeration of distinct permissions and services required for Pixel Island setup.
 */
enum class SetupPermissionType {
    /**
     * Overlay permission (SYSTEM_ALERT_WINDOW).
     * Required for displaying the dynamic pill via IslandOverlayService.
     */
    OVERLAY,

    /**
     * Notification Listener access (BIND_NOTIFICATION_LISTENER_SERVICE).
     * Required by MusicNotificationListenerService to observe active media playback.
     */
    NOTIFICATION,

    /**
     * Accessibility Service (BIND_ACCESSIBILITY_SERVICE).
     * Required by PixelIslandAccessibilityService for punch hole touch interception
     * and high z order TYPE_ACCESSIBILITY_OVERLAY.
     */
    ACCESSIBILITY
}

/**
 * Snapshot of setup permission grant states for Pixel Island.
 *
 * @property isOverlayGranted True if SYSTEM_ALERT_WINDOW is granted.
 * @property isNotificationGranted True if notification listener access is enabled.
 * @property isAccessibilityGranted True if PixelIslandAccessibilityService is enabled.
 */
data class StartupPermissionStatus(
    val isOverlayGranted: Boolean = false,
    val isNotificationGranted: Boolean = false,
    val isAccessibilityGranted: Boolean = false
) {
    /**
     * True when all three permissions and services are active.
     * When true, all dashboard checkmarks display as confirmed.
     */
    val allGranted: Boolean
        get() = isOverlayGranted && isNotificationGranted && isAccessibilityGranted

    /**
     * True if the device has sufficient permission to display the island capsule.
     * Either overlay permission (TYPE_APPLICATION_OVERLAY) or accessibility service
     * (TYPE_ACCESSIBILITY_OVERLAY) allows displaying the dynamic pill.
     */
    val canLaunchIsland: Boolean
        get() = isOverlayGranted || isAccessibilityGranted

    /**
     * Checks grant status for a specific SetupPermissionType.
     * Essential for AutoReturnWatcher state transition checks.
     */
    fun isGranted(type: SetupPermissionType): Boolean = when (type) {
        SetupPermissionType.OVERLAY -> isOverlayGranted
        SetupPermissionType.NOTIFICATION -> isNotificationGranted
        SetupPermissionType.ACCESSIBILITY -> isAccessibilityGranted
    }
}
