package com.bdsalocin.pixelisland.presentation.settings

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import com.bdsalocin.pixelisland.data.service.MusicNotificationListenerService
import com.bdsalocin.pixelisland.data.service.PixelIslandAccessibilityService
import com.bdsalocin.pixelisland.domain.startup.SetupPermissionType

/**
 * Resolves and dispatches direct deep links to system settings sub pages
 * for Pixel Island on Android 11 to Android 14+ (Google Pixel 8 Pro).
 *
 * Provides package specific overlay, notification listener detail,
 * targeted accessibility fragment arguments, and app details for restricted settings,
 * with safe fallbacks in case of ActivityNotFoundException or OEM restrictions.
 */
object SettingsIntentResolver {

    const val EXTRA_FRAGMENT_ARG_KEY: String = ":settings:fragment_args_key"
    const val EXTRA_SHOW_FRAGMENT_ARGUMENTS: String = ":settings:show_fragment_args"

    /**
     * Creates a direct deep link intent to Pixel Island's overlay switch
     * via Settings.ACTION_MANAGE_OVERLAY_PERMISSION with package URI.
     */
    fun createOverlayIntent(context: Context): Intent {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        return intent.applyNewTaskIfNeeded(context)
    }

    /**
     * Creates a fallback intent to the general overlay permission screen.
     */
    fun createFallbackOverlayIntent(context: Context): Intent {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
        return intent.applyNewTaskIfNeeded(context)
    }

    /**
     * Safely launches the overlay settings screen, attempting the direct package
     * sub page first and falling back to the general overlay screen upon ActivityNotFoundException.
     *
     * @return true if an activity was started successfully, false otherwise.
     */
    fun launchOverlaySettings(context: Context): Boolean {
        return try {
            context.startActivity(createOverlayIntent(context))
            true
        } catch (_: ActivityNotFoundException) {
            try {
                context.startActivity(createFallbackOverlayIntent(context))
                true
            } catch (_: Exception) {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Creates a direct deep link intent to Pixel Island's notification listener
     * permission sub page on API 30+ (Android 11+) via ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS,
     * targeting MusicNotificationListenerService.
     *
     * On pre API 30 devices, defaults to ACTION_NOTIFICATION_LISTENER_SETTINGS.
     */
    fun createNotificationListenerIntent(context: Context): Intent {
        val component = ComponentName(context, MusicNotificationListenerService::class.java)
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).apply {
                putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component.flattenToString())
            }
        } else {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        }
        return intent.applyNewTaskIfNeeded(context)
    }

    /**
     * Creates a fallback intent to the general notification listener list.
     */
    fun createFallbackNotificationListenerIntent(context: Context): Intent {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        return intent.applyNewTaskIfNeeded(context)
    }

    /**
     * Safely launches the notification listener settings screen, attempting the
     * direct detail sub page first and falling back to the general list upon ActivityNotFoundException.
     *
     * @return true if an activity was started successfully, false otherwise.
     */
    fun launchNotificationListenerSettings(context: Context): Boolean {
        return try {
            context.startActivity(createNotificationListenerIntent(context))
            true
        } catch (_: ActivityNotFoundException) {
            try {
                context.startActivity(createFallbackNotificationListenerIntent(context))
                true
            } catch (_: Exception) {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Creates an intent navigating to Accessibility Settings with fragment arguments
     * targeting PixelIslandAccessibilityService for automatic highlight and focus.
     */
    fun createAccessibilityIntent(context: Context): Intent {
        val componentString = ComponentName(context, PixelIslandAccessibilityService::class.java).flattenToString()
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            putExtra(EXTRA_FRAGMENT_ARG_KEY, componentString)
            val bundle = Bundle().apply {
                putString(EXTRA_FRAGMENT_ARG_KEY, componentString)
            }
            putExtra(EXTRA_SHOW_FRAGMENT_ARGUMENTS, bundle)
        }
        return intent.applyNewTaskIfNeeded(context)
    }

    /**
     * Creates a fallback intent to general accessibility settings without fragment args.
     */
    fun createFallbackAccessibilityIntent(context: Context): Intent {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        return intent.applyNewTaskIfNeeded(context)
    }

    /**
     * Safely launches accessibility settings, falling back to general accessibility
     * or root settings if needed.
     *
     * @return true if an activity was started successfully, false otherwise.
     */
    fun launchAccessibilitySettings(context: Context): Boolean {
        return try {
            context.startActivity(createAccessibilityIntent(context))
            true
        } catch (_: ActivityNotFoundException) {
            try {
                context.startActivity(createFallbackAccessibilityIntent(context))
                true
            } catch (_: Exception) {
                try {
                    context.startActivity(Intent(Settings.ACTION_SETTINGS).applyNewTaskIfNeeded(context))
                    true
                } catch (_: Exception) {
                    false
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Creates an intent navigating directly to Pixel Island's App Info page
     * via ACTION_APPLICATION_DETAILS_SETTINGS with package URI.
     *
     * This is required on Android 13+ (API 33+) when Pixel Island is sideloaded,
     * allowing the user to access the 3 dot overflow menu and select
     * "Allow restricted settings" ("Autoriser les paramètres restreints").
     */
    fun createAppDetailsIntent(context: Context): Intent {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        return intent.applyNewTaskIfNeeded(context)
    }

    /**
     * Creates a fallback intent to general application settings.
     */
    fun createFallbackAppDetailsIntent(context: Context): Intent {
        val intent = Intent(Settings.ACTION_APPLICATION_SETTINGS)
        return intent.applyNewTaskIfNeeded(context)
    }

    /**
     * Safely launches the app details screen, falling back to general app settings upon failure.
     *
     * @return true if an activity was started successfully, false otherwise.
     */
    fun launchAppDetailsSettings(context: Context): Boolean {
        return try {
            context.startActivity(createAppDetailsIntent(context))
            true
        } catch (_: ActivityNotFoundException) {
            try {
                context.startActivity(createFallbackAppDetailsIntent(context))
                true
            } catch (_: Exception) {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Resolves the appropriate intent for a given SetupPermissionType.
     */
    fun createIntentForPermission(context: Context, type: SetupPermissionType): Intent =
        when (type) {
            SetupPermissionType.OVERLAY -> createOverlayIntent(context)
            SetupPermissionType.NOTIFICATION -> createNotificationListenerIntent(context)
            SetupPermissionType.ACCESSIBILITY -> createAccessibilityIntent(context)
        }

    /**
     * Safely launches settings for a given SetupPermissionType with automatic fallbacks.
     */
    fun launchSettingsForPermission(context: Context, type: SetupPermissionType): Boolean =
        when (type) {
            SetupPermissionType.OVERLAY -> launchOverlaySettings(context)
            SetupPermissionType.NOTIFICATION -> launchNotificationListenerSettings(context)
            SetupPermissionType.ACCESSIBILITY -> launchAccessibilitySettings(context)
        }

    private fun Intent.applyNewTaskIfNeeded(context: Context): Intent {
        if (context !is Activity) {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return this
    }
}
