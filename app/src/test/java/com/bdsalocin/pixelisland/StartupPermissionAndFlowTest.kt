package com.bdsalocin.pixelisland

import com.bdsalocin.pixelisland.domain.startup.AutoReturnWatcher
import com.bdsalocin.pixelisland.domain.startup.FakeStartupPermissionChecker
import com.bdsalocin.pixelisland.domain.startup.SetupPermissionType
import com.bdsalocin.pixelisland.domain.startup.StartupPermissionChecker
import com.bdsalocin.pixelisland.domain.startup.StartupPermissionStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StartupPermissionAndFlowTest {

    // =========================================================================
    // 1. StartupPermissionStatus & Readiness Computation
    // =========================================================================

    @Test
    fun testStartupPermissionStatus_defaultValuesAreFalse() {
        val status = StartupPermissionStatus()
        assertFalse(status.isOverlayGranted)
        assertFalse(status.isNotificationGranted)
        assertFalse(status.isAccessibilityGranted)
        assertFalse(status.allGranted)
        assertFalse(status.canLaunchIsland)
    }

    @Test
    fun testStartupPermissionStatus_allGrantedEvaluatesCorrectly() {
        // Only true when all three are granted
        val allOff = StartupPermissionStatus(isOverlayGranted = false, isNotificationGranted = false, isAccessibilityGranted = false)
        val overlayOnly = StartupPermissionStatus(isOverlayGranted = true, isNotificationGranted = false, isAccessibilityGranted = false)
        val notifOnly = StartupPermissionStatus(isOverlayGranted = false, isNotificationGranted = true, isAccessibilityGranted = false)
        val accessOnly = StartupPermissionStatus(isOverlayGranted = false, isNotificationGranted = false, isAccessibilityGranted = true)
        val overlayAndNotif = StartupPermissionStatus(isOverlayGranted = true, isNotificationGranted = true, isAccessibilityGranted = false)
        val overlayAndAccess = StartupPermissionStatus(isOverlayGranted = true, isNotificationGranted = false, isAccessibilityGranted = true)
        val notifAndAccess = StartupPermissionStatus(isOverlayGranted = false, isNotificationGranted = true, isAccessibilityGranted = true)
        val allThree = StartupPermissionStatus(isOverlayGranted = true, isNotificationGranted = true, isAccessibilityGranted = true)

        assertFalse(allOff.allGranted)
        assertFalse(overlayOnly.allGranted)
        assertFalse(notifOnly.allGranted)
        assertFalse(accessOnly.allGranted)
        assertFalse(overlayAndNotif.allGranted)
        assertFalse(overlayAndAccess.allGranted)
        assertFalse(notifAndAccess.allGranted)
        assertTrue(allThree.allGranted)
    }

    @Test
    fun testStartupPermissionStatus_canLaunchIslandRequiresOverlayOrAccessibility() {
        // Can launch if either overlay or accessibility is granted
        val none = StartupPermissionStatus(isOverlayGranted = false, isNotificationGranted = false, isAccessibilityGranted = false)
        val notifOnly = StartupPermissionStatus(isOverlayGranted = false, isNotificationGranted = true, isAccessibilityGranted = false)
        val overlayOnly = StartupPermissionStatus(isOverlayGranted = true, isNotificationGranted = false, isAccessibilityGranted = false)
        val accessOnly = StartupPermissionStatus(isOverlayGranted = false, isNotificationGranted = false, isAccessibilityGranted = true)
        val both = StartupPermissionStatus(isOverlayGranted = true, isNotificationGranted = false, isAccessibilityGranted = true)

        assertFalse(none.canLaunchIsland)
        assertFalse(notifOnly.canLaunchIsland)
        assertTrue(overlayOnly.canLaunchIsland)
        assertTrue(accessOnly.canLaunchIsland)
        assertTrue(both.canLaunchIsland)
    }

    @Test
    fun testStartupPermissionStatus_isGrantedByType() {
        val status = StartupPermissionStatus(
            isOverlayGranted = true,
            isNotificationGranted = false,
            isAccessibilityGranted = true
        )

        assertTrue(status.isGranted(SetupPermissionType.OVERLAY))
        assertFalse(status.isGranted(SetupPermissionType.NOTIFICATION))
        assertTrue(status.isGranted(SetupPermissionType.ACCESSIBILITY))
    }

    // =========================================================================
    // 2. FakeStartupPermissionChecker Operations
    // =========================================================================

    @Test
    fun testFakeStartupPermissionChecker_manipulation() {
        val checker = FakeStartupPermissionChecker()
        val initialStatus = checker.checkStatus()
        assertFalse(initialStatus.allGranted)

        checker.setPermission(SetupPermissionType.OVERLAY, true)
        assertTrue(checker.checkStatus().isOverlayGranted)
        assertFalse(checker.checkStatus().isNotificationGranted)

        checker.setPermission(SetupPermissionType.NOTIFICATION, true)
        assertTrue(checker.checkStatus().isNotificationGranted)

        checker.setPermission(SetupPermissionType.ACCESSIBILITY, true)
        assertTrue(checker.checkStatus().isAccessibilityGranted)
        assertTrue(checker.checkStatus().allGranted)

        checker.setAll(false)
        assertFalse(checker.checkStatus().allGranted)
        assertFalse(checker.checkStatus().isOverlayGranted)
        assertFalse(checker.checkStatus().isNotificationGranted)
        assertFalse(checker.checkStatus().isAccessibilityGranted)

        checker.setAll(true)
        assertTrue(checker.checkStatus().allGranted)
    }

    // =========================================================================
    // 3. AutoReturnWatcher State Transitions & Polling
    // =========================================================================

    @Test
    fun testAutoReturnWatcher_initialStateIsNotWatching() {
        val fakeChecker = FakeStartupPermissionChecker()
        val watcher = AutoReturnWatcher(
            permissionChecker = fakeChecker,
            onAutoReturnTriggered = {},
            scope = TestScope(StandardTestDispatcher()),
            pollIntervalMs = 250L
        )

        assertFalse(watcher.isWatching)
        assertNull(watcher.currentTarget)
    }

    @Test
    fun testAutoReturnWatcher_falseToTrueTransitionTriggersCallbackAndStopsWatching() = runTest {
        val fakeChecker = FakeStartupPermissionChecker(isOverlayGranted = false)
        var triggeredTarget: SetupPermissionType? = null
        var triggerCount = 0

        val watcher = AutoReturnWatcher(
            permissionChecker = fakeChecker,
            onAutoReturnTriggered = { target ->
                triggeredTarget = target
                triggerCount++
            },
            scope = backgroundScope,
            pollIntervalMs = 250L
        )

        watcher.startWatching(SetupPermissionType.OVERLAY)
        assertTrue(watcher.isWatching)
        assertEquals(SetupPermissionType.OVERLAY, watcher.currentTarget)
        assertNull(triggeredTarget)

        // Advance 500ms while still false (nothing triggered)
        advanceTimeBy(500L)
        runCurrent()
        assertNull(triggeredTarget)
        assertEquals(0, triggerCount)
        assertTrue(watcher.isWatching)

        // User grants permission in system settings (false to true transition)
        fakeChecker.setPermission(SetupPermissionType.OVERLAY, true)

        // Advance by pollIntervalMs
        advanceTimeBy(250L)
        runCurrent()

        assertEquals(SetupPermissionType.OVERLAY, triggeredTarget)
        assertEquals(1, triggerCount)
        assertFalse(watcher.isWatching)
        assertNull(watcher.currentTarget)
    }

    @Test
    fun testAutoReturnWatcher_alreadyGrantedDoesNotTriggerAutoReturn() = runTest {
        val fakeChecker = FakeStartupPermissionChecker(isNotificationGranted = true)
        var triggerCount = 0

        val watcher = AutoReturnWatcher(
            permissionChecker = fakeChecker,
            onAutoReturnTriggered = { triggerCount++ },
            scope = backgroundScope,
            pollIntervalMs = 250L
        )

        watcher.startWatching(SetupPermissionType.NOTIFICATION)
        assertTrue(watcher.isWatching)

        // Advance multiple polling cycles (1000ms = 4 cycles)
        advanceTimeBy(1000L)
        runCurrent()

        // Must not fire auto return because permission was already granted at start
        assertEquals(0, triggerCount)

        watcher.stopWatching()
        assertFalse(watcher.isWatching)
    }

    @Test
    fun testAutoReturnWatcher_stopWatchingCancelsActivePolling() = runTest {
        val fakeChecker = FakeStartupPermissionChecker(isAccessibilityGranted = false)
        var triggerCount = 0

        val watcher = AutoReturnWatcher(
            permissionChecker = fakeChecker,
            onAutoReturnTriggered = { triggerCount++ },
            scope = backgroundScope,
            pollIntervalMs = 250L
        )

        watcher.startWatching(SetupPermissionType.ACCESSIBILITY)
        assertTrue(watcher.isWatching)

        // Explicitly stop watching before grant
        watcher.stopWatching()
        assertFalse(watcher.isWatching)
        assertNull(watcher.currentTarget)

        // Grant permission afterwards
        fakeChecker.setPermission(SetupPermissionType.ACCESSIBILITY, true)
        advanceTimeBy(500L)
        runCurrent()

        assertEquals(0, triggerCount)
    }

    @Test
    fun testAutoReturnWatcher_onPermissionActivatedDirectly_triggersImmediatelyAndStopsWatching() = runTest {
        val fakeChecker = FakeStartupPermissionChecker(isOverlayGranted = false)
        var triggeredTarget: SetupPermissionType? = null

        val watcher = AutoReturnWatcher(
            permissionChecker = fakeChecker,
            onAutoReturnTriggered = { target -> triggeredTarget = target },
            scope = backgroundScope,
            pollIntervalMs = 250L
        )

        watcher.startWatching(SetupPermissionType.OVERLAY)
        assertTrue(watcher.isWatching)

        // Direct signal arrives before next poll tick
        watcher.onPermissionActivatedDirectly(SetupPermissionType.OVERLAY)

        assertEquals(SetupPermissionType.OVERLAY, triggeredTarget)
        assertFalse(watcher.isWatching)
        assertNull(watcher.currentTarget)
    }

    @Test
    fun testAutoReturnWatcher_onPermissionActivatedDirectly_ignoresMismatchedTarget() = runTest {
        val fakeChecker = FakeStartupPermissionChecker(isOverlayGranted = false)
        var triggeredTarget: SetupPermissionType? = null

        val watcher = AutoReturnWatcher(
            permissionChecker = fakeChecker,
            onAutoReturnTriggered = { target -> triggeredTarget = target },
            scope = backgroundScope,
            pollIntervalMs = 250L
        )

        watcher.startWatching(SetupPermissionType.OVERLAY)

        // Direct signal for a different permission
        watcher.onPermissionActivatedDirectly(SetupPermissionType.NOTIFICATION)

        assertNull(triggeredTarget)
        assertTrue(watcher.isWatching)
        assertEquals(SetupPermissionType.OVERLAY, watcher.currentTarget)

        watcher.stopWatching()
    }

    @Test
    fun testAutoReturnWatcher_onPermissionActivatedDirectly_whenIdle_firesDirectly() = runTest {
        val fakeChecker = FakeStartupPermissionChecker()
        var triggeredTarget: SetupPermissionType? = null

        val watcher = AutoReturnWatcher(
            permissionChecker = fakeChecker,
            onAutoReturnTriggered = { target -> triggeredTarget = target },
            scope = backgroundScope,
            pollIntervalMs = 250L
        )

        // Direct activation when idle
        watcher.onPermissionActivatedDirectly(SetupPermissionType.NOTIFICATION)

        assertEquals(SetupPermissionType.NOTIFICATION, triggeredTarget)
        assertFalse(watcher.isWatching)
        assertNull(watcher.currentTarget)
    }

    @Test
    fun testAutoReturnWatcher_switchingTargetCancelsPreviousAndWatchesNewTarget() = runTest {
        val fakeChecker = FakeStartupPermissionChecker(
            isOverlayGranted = false,
            isNotificationGranted = false
        )
        val triggeredList = mutableListOf<SetupPermissionType>()

        val watcher = AutoReturnWatcher(
            permissionChecker = fakeChecker,
            onAutoReturnTriggered = { triggeredList.add(it) },
            scope = backgroundScope,
            pollIntervalMs = 250L
        )

        watcher.startWatching(SetupPermissionType.OVERLAY)
        assertEquals(SetupPermissionType.OVERLAY, watcher.currentTarget)

        // User switches to NOTIFICATION
        watcher.startWatching(SetupPermissionType.NOTIFICATION)
        assertEquals(SetupPermissionType.NOTIFICATION, watcher.currentTarget)

        // Old target (OVERLAY) is granted (should not trigger)
        fakeChecker.setPermission(SetupPermissionType.OVERLAY, true)
        advanceTimeBy(300L)
        runCurrent()
        assertTrue(triggeredList.isEmpty())

        // New target (NOTIFICATION) is granted (should trigger)
        fakeChecker.setPermission(SetupPermissionType.NOTIFICATION, true)
        advanceTimeBy(250L)
        runCurrent()

        assertEquals(listOf(SetupPermissionType.NOTIFICATION), triggeredList)
        assertFalse(watcher.isWatching)
    }

    @Test
    fun testAutoReturnWatcher_handlesCheckerExceptionGracefully() = runTest {
        var shouldThrow = true
        var triggerCount = 0

        val faultTolerantChecker = object : StartupPermissionChecker {
            override fun checkStatus(): StartupPermissionStatus {
                if (shouldThrow) {
                    throw IllegalStateException("Simulated system service error")
                }
                return StartupPermissionStatus(isOverlayGranted = true)
            }
        }

        val watcher = AutoReturnWatcher(
            permissionChecker = faultTolerantChecker,
            onAutoReturnTriggered = { triggerCount++ },
            scope = backgroundScope,
            pollIntervalMs = 250L
        )

        // Start watching (initial check encounters exception, handled as false)
        watcher.startWatching(SetupPermissionType.OVERLAY)
        assertTrue(watcher.isWatching)

        advanceTimeBy(250L)
        runCurrent()
        assertEquals(0, triggerCount)

        // Stop throwing exception and return true
        shouldThrow = false
        advanceTimeBy(250L)
        runCurrent()

        assertEquals(1, triggerCount)
        assertFalse(watcher.isWatching)
    }

    @Test
    fun testAutoReturnWatcher_sequentialWatchCycles() = runTest {
        val fakeChecker = FakeStartupPermissionChecker()
        val triggeredList = mutableListOf<SetupPermissionType>()

        val watcher = AutoReturnWatcher(
            permissionChecker = fakeChecker,
            onAutoReturnTriggered = { triggeredList.add(it) },
            scope = backgroundScope,
            pollIntervalMs = 250L
        )

        // Cycle 1: Watch OVERLAY and grant
        watcher.startWatching(SetupPermissionType.OVERLAY)
        advanceTimeBy(100L)
        fakeChecker.setPermission(SetupPermissionType.OVERLAY, true)
        advanceTimeBy(250L)
        runCurrent()
        assertEquals(listOf(SetupPermissionType.OVERLAY), triggeredList)
        assertFalse(watcher.isWatching)

        // Cycle 2: Watch ACCESSIBILITY and grant
        watcher.startWatching(SetupPermissionType.ACCESSIBILITY)
        assertTrue(watcher.isWatching)
        advanceTimeBy(100L)
        fakeChecker.setPermission(SetupPermissionType.ACCESSIBILITY, true)
        advanceTimeBy(250L)
        runCurrent()
        assertEquals(listOf(SetupPermissionType.OVERLAY, SetupPermissionType.ACCESSIBILITY), triggeredList)
        assertFalse(watcher.isWatching)
    }
}
