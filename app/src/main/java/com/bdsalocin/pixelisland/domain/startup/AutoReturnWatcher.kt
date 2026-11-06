package com.bdsalocin.pixelisland.domain.startup

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Coroutine driven state watcher that monitors permission status transitions in the background.
 *
 * Designed for the Pixel Island startup flow on Google Pixel 8 Pro (Android 14+).
 * When a user is guided to a system settings sub page (Overlay, Notification Listener, Accessibility),
 * AutoReturnWatcher polls the target permission at a configured interval (default 250ms).
 *
 * Transition Contract:
 * * Triggers [onAutoReturnTriggered] ONLY when the target permission undergoes a strict
 *   transition from false (unauthorized) to true (authorized).
 * * If the target permission was already true when [startWatching] was invoked, it will NEVER
 *   fire an auto return trigger.
 * * Automatically stops watching and cleans up the active polling job upon firing or cancellation.
 */
class AutoReturnWatcher(
    private val permissionChecker: StartupPermissionChecker,
    private val onAutoReturnTriggered: (SetupPermissionType) -> Unit,
    private val scope: CoroutineScope,
    private val pollIntervalMs: Long = DEFAULT_POLL_INTERVAL_MS
) {
    @Volatile
    private var pollingJob: Job? = null

    @Volatile
    private var _currentTarget: SetupPermissionType? = null

    /**
     * Whether a polling coroutine is currently active.
     */
    val isWatching: Boolean
        get() = pollingJob?.isActive == true

    /**
     * The target permission currently being monitored, or null if idle.
     */
    val currentTarget: SetupPermissionType?
        get() = _currentTarget

    /**
     * Starts watching for the grant of [target].
     *
     * Any previous watching job is cancelled. The initial status of [target] is sampled:
     * * If [target] was already true when watching began, [lastStatus] begins as true,
     *   preventing any auto return trigger.
     * * A coroutine is launched in [scope] to poll at [pollIntervalMs] intervals.
     */
    fun startWatching(target: SetupPermissionType) {
        stopWatching()

        val initialStatus = try {
            permissionChecker.checkStatus().isGranted(target)
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            false
        }

        _currentTarget = target
        pollingJob = scope.launch {
            var lastStatus = initialStatus

            while (isActive) {
                delay(pollIntervalMs)

                val currentStatus = try {
                    permissionChecker.checkStatus().isGranted(target)
                } catch (e: Throwable) {
                    if (e is CancellationException) throw e
                    false
                }

                if (!lastStatus && currentStatus) {
                    // Strict false to true flip detected
                    stopWatching()
                    onAutoReturnTriggered(target)
                    break
                }

                lastStatus = currentStatus
            }
        }
    }

    /**
     * Stops the active polling job and resets the target permission.
     */
    fun stopWatching() {
        pollingJob?.cancel()
        pollingJob = null
        _currentTarget = null
    }

    /**
     * Directly signals that [target] has been granted (e.g. from an onResume check, activity result,
     * or accessibility broadcast), bypassing the remaining poll delay.
     *
     * If the watcher is currently monitoring [target] (or was idle), it cleans up
     * and fires [onAutoReturnTriggered].
     */
    fun onPermissionActivatedDirectly(target: SetupPermissionType) {
        val isTargetMatch = _currentTarget == null || _currentTarget == target
        if (isTargetMatch) {
            stopWatching()
            onAutoReturnTriggered(target)
        }
    }

    companion object {
        const val DEFAULT_POLL_INTERVAL_MS: Long = 250L

        /**
         * Creates an intent configured to bring MainActivity back to the foreground.
         *
         * Uses the Android 14 recommended flag triad:
         * * FLAG_ACTIVITY_NEW_TASK: Required for starting activities from non activity or background contexts.
         * * FLAG_ACTIVITY_REORDER_TO_FRONT: Brings the existing task to the front of the screen.
         * * FLAG_ACTIVITY_SINGLE_TOP: Prevents creating duplicate instances if already at the top.
         */
        fun createForegroundIntent(
            context: Context,
            activityClass: Class<*>? = null
        ): Intent {
            return if (activityClass != null) {
                Intent(context, activityClass)
            } else {
                Intent().apply {
                    setClassName(
                        context.packageName,
                        "com.bdsalocin.pixelisland.presentation.settings.MainActivity"
                    )
                }
            }.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        }

        /**
         * Brings MainActivity back to the foreground safely within a try catch block
         * to handle Android 14 Background Activity Launch (BAL) restrictions without crashing.
         */
        fun bringToForeground(context: Context, activityClass: Class<*>? = null) {
            try {
                val intent = createForegroundIntent(context, activityClass)
                context.startActivity(intent)
            } catch (_: Exception) {
                // Safeguard: prevents crash if platform restricts background activity launch
            }
        }
    }
}
