package com.bdsalocin.pixelisland.domain.startup

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.app.NotificationManagerCompat
import com.bdsalocin.pixelisland.data.service.MusicNotificationListenerService
import com.bdsalocin.pixelisland.data.service.PixelIslandAccessibilityService

/**
 * Domain interface for querying the current startup permission status.
 */
interface StartupPermissionChecker {
    /**
     * Evaluates and returns the instantaneous permission status snapshot.
     */
    fun checkStatus(): StartupPermissionStatus
}

/**
 * Production Android implementation of StartupPermissionChecker.
 *
 * Employs multi layer verification for each permission to ensure immediate,
 * synchronous detection without delay when user toggles settings in system sub pages.
 */
class AndroidStartupPermissionChecker(
    context: Context
) : StartupPermissionChecker {

    private val appContext = context.applicationContext ?: context

    override fun checkStatus(): StartupPermissionStatus {
        return StartupPermissionStatus(
            isOverlayGranted = checkOverlayPermission(),
            isNotificationGranted = checkNotificationListenerPermission(),
            isAccessibilityGranted = checkAccessibilityPermission()
        )
    }

    /**
     * Verifies system overlay permission (Settings.canDrawOverlays).
     */
    private fun checkOverlayPermission(): Boolean {
        return try {
            Settings.canDrawOverlays(appContext)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Verifies notification listener permission using two complementary mechanisms:
     * 1. NotificationManagerCompat.getEnabledListenerPackages(context)
     * 2. Direct parsing of Settings.Secure "enabled_notification_listeners"
     */
    private fun checkNotificationListenerPermission(): Boolean {
        return try {
            val enabledPackages = NotificationManagerCompat.getEnabledListenerPackages(appContext)
            if (enabledPackages.contains(appContext.packageName)) {
                return true
            }

            val flat = Settings.Secure.getString(
                appContext.contentResolver,
                "enabled_notification_listeners"
            )
            if (!flat.isNullOrEmpty()) {
                val fullComponent = ComponentName(appContext, MusicNotificationListenerService::class.java).flattenToString()
                val shortComponent = ComponentName(appContext, MusicNotificationListenerService::class.java).flattenToShortString()
                flat.split(':').any { entry ->
                    entry.equals(fullComponent, ignoreCase = true) ||
                        entry.equals(shortComponent, ignoreCase = true) ||
                        (entry.contains(appContext.packageName) && entry.contains("MusicNotificationListenerService"))
                }
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Verifies accessibility service permission using three complementary tiers:
     * 1. PixelIslandAccessibilityService.isAccessibilityServiceActive (live in memory flag)
     * 2. AccessibilityManager.getEnabledAccessibilityServiceList
     * 3. Direct parsing of Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES with ACCESSIBILITY_ENABLED check
     */
    private fun checkAccessibilityPermission(): Boolean {
        // Tier 1: In memory live active flag
        if (PixelIslandAccessibilityService.isAccessibilityServiceActive) {
            return true
        }

        return try {
            // Tier 2: Query system AccessibilityManager
            val am = appContext.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            val enabledList = am?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            val isEnabledInManager = enabledList?.any { info ->
                val serviceInfo = info.resolveInfo?.serviceInfo
                serviceInfo != null && serviceInfo.packageName == appContext.packageName &&
                    (serviceInfo.name == PixelIslandAccessibilityService::class.java.name ||
                        serviceInfo.name.endsWith("PixelIslandAccessibilityService"))
            } ?: false

            if (isEnabledInManager) {
                return true
            }

            // Tier 3: Query Settings.Secure settings directly
            val accessibilityEnabled = Settings.Secure.getInt(
                appContext.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED,
                0
            )
            if (accessibilityEnabled == 1) {
                val flat = Settings.Secure.getString(
                    appContext.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                )
                if (!flat.isNullOrEmpty()) {
                    val fullComponent = ComponentName(appContext, PixelIslandAccessibilityService::class.java).flattenToString()
                    val shortComponent = ComponentName(appContext, PixelIslandAccessibilityService::class.java).flattenToShortString()
                    flat.split(':').any { entry ->
                        entry.equals(fullComponent, ignoreCase = true) ||
                            entry.equals(shortComponent, ignoreCase = true) ||
                            (entry.contains(appContext.packageName) && entry.contains("PixelIslandAccessibilityService"))
                    }
                } else {
                    false
                }
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }
}

/**
 * Pure JVM test double for StartupPermissionChecker.
 *
 * Allows unit tests to synchronously configure permission states, simulate user grants
 * in background or settings sub pages, and verify watcher or UI state reactions.
 */
class FakeStartupPermissionChecker(
    var isOverlayGranted: Boolean = false,
    var isNotificationGranted: Boolean = false,
    var isAccessibilityGranted: Boolean = false
) : StartupPermissionChecker {

    override fun checkStatus(): StartupPermissionStatus {
        return StartupPermissionStatus(
            isOverlayGranted = isOverlayGranted,
            isNotificationGranted = isNotificationGranted,
            isAccessibilityGranted = isAccessibilityGranted
        )
    }

    /**
     * Convenience method to toggle all permissions simultaneously.
     */
    fun setAll(granted: Boolean) {
        isOverlayGranted = granted
        isNotificationGranted = granted
        isAccessibilityGranted = granted
    }

    /**
     * Updates grant state for a specific SetupPermissionType.
     */
    fun setPermission(type: SetupPermissionType, granted: Boolean) {
        when (type) {
            SetupPermissionType.OVERLAY -> isOverlayGranted = granted
            SetupPermissionType.NOTIFICATION -> isNotificationGranted = granted
            SetupPermissionType.ACCESSIBILITY -> isAccessibilityGranted = granted
        }
    }
}
