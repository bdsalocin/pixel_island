package com.bdsalocin.pixelisland

import com.bdsalocin.pixelisland.domain.biometric.InAppBiometricFilter
import com.bdsalocin.pixelisland.domain.model.BiometricStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InAppBiometricFilterTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var filter: InAppBiometricFilter

    @Before
    fun setUp() {
        filter = InAppBiometricFilter(
            frontCameraIds = setOf("0"),
            coroutineScope = testScope,
            successDwellMs = 500L
        )
    }

    @Test
    fun testInitialStateIsIdle() {
        assertEquals(BiometricStatus.IDLE, filter.status.value)
        assertFalse(filter.isKeyguardLocked)
        assertTrue(filter.isScreenOn)
        assertFalse(filter.isSystemPromptShowing)
        assertFalse(filter.isFrontCameraInUse)
    }

    @Test
    fun testTriggerOnlyWhenBiometricPromptAndFrontCameraActive() {
        // Condition 1: Déverrouillé sur le bureau ou dans l'app
        filter.onScreenStateChanged(isKeyguardLocked = false, isScreenOn = true)
        assertEquals(BiometricStatus.IDLE, filter.status.value)

        // Boîte biométrique système ouverte (ex: AuthContainerView)
        filter.onSystemBiometricPromptChanged(true)
        assertEquals(BiometricStatus.IDLE, filter.status.value)

        // Caméra frontale sollicitée par Google Face Unlock
        filter.onFrontCameraAvailabilityChanged("0", inUse = true)

        // Doit déclencher SCANNING
        assertEquals(BiometricStatus.SCANNING, filter.status.value)
    }

    @Test
    fun testNoTriggerOnFingerprintOnlyPrompt() {
        // Boîte biométrique ouverte pour empreinte seule (caméra frontale inactive)
        filter.onScreenStateChanged(isKeyguardLocked = false, isScreenOn = true)
        filter.onSystemBiometricPromptChanged(true)
        filter.onFrontCameraAvailabilityChanged("0", inUse = false)

        assertEquals(BiometricStatus.IDLE, filter.status.value)
    }

    @Test
    fun testNoTriggerOnCameraAppWithoutBiometricPrompt() {
        // Appareil photo ou réseau social utilisant la caméra frontale hors boîte biométrique
        filter.onScreenStateChanged(isKeyguardLocked = false, isScreenOn = true)
        filter.onSystemBiometricPromptChanged(false)
        filter.onFrontCameraAvailabilityChanged("0", inUse = true)

        assertEquals(BiometricStatus.IDLE, filter.status.value)
    }

    @Test
    fun testNoTriggerWhenKeyguardLocked() {
        // Si le téléphone est verrouillé (Keyguard), le filtre applicatif reste inactif
        filter.onScreenStateChanged(isKeyguardLocked = true, isScreenOn = true)
        filter.onSystemBiometricPromptChanged(true)
        filter.onFrontCameraAvailabilityChanged("0", inUse = true)

        assertEquals(BiometricStatus.IDLE, filter.status.value)
    }

    @Test
    fun testSuccessfulAuthenticationTransitionsToSuccessThenIdle() = testScope.runTest {
        filter.onScreenStateChanged(isKeyguardLocked = false, isScreenOn = true)
        filter.onSystemBiometricPromptChanged(true)
        filter.onFrontCameraAvailabilityChanged("0", inUse = true)
        assertEquals(BiometricStatus.SCANNING, filter.status.value)

        // La caméra frontale se coupe dès que le visage est reconnu avec succès
        filter.onFrontCameraAvailabilityChanged("0", inUse = false)
        assertEquals(BiometricStatus.SUCCESS, filter.status.value)

        // Après temporisation d'affichage (500 ms), bascule à IDLE
        advanceTimeBy(501L)
        assertEquals(BiometricStatus.IDLE, filter.status.value)
    }

    @Test
    fun testExplicitBiometricSuccessTriggersAnimationAndDismissal() = testScope.runTest {
        filter.onScreenStateChanged(isKeyguardLocked = false, isScreenOn = true)
        filter.onSystemBiometricPromptChanged(true)
        filter.onFrontCameraAvailabilityChanged("0", inUse = true)
        assertEquals(BiometricStatus.SCANNING, filter.status.value)

        filter.onBiometricSuccess()
        assertEquals(BiometricStatus.SUCCESS, filter.status.value)

        advanceTimeBy(501L)
        assertEquals(BiometricStatus.IDLE, filter.status.value)
    }

    @Test
    fun testCancelledAuthenticationDismissesDirectlyToIdleWithoutSuccess() {
        filter.onScreenStateChanged(isKeyguardLocked = false, isScreenOn = true)
        filter.onSystemBiometricPromptChanged(true)
        filter.onFrontCameraAvailabilityChanged("0", inUse = true)
        assertEquals(BiometricStatus.SCANNING, filter.status.value)

        // Utilisateur clique sur Annuler
        filter.onBiometricCancelled()
        assertEquals(BiometricStatus.IDLE, filter.status.value)
        assertTrue(filter.isCancelled)

        // Même si la caméra frontale se coupe ensuite, elle ne doit pas passer par SUCCESS
        filter.onFrontCameraAvailabilityChanged("0", inUse = false)
        assertEquals(BiometricStatus.IDLE, filter.status.value)
    }

    @Test
    fun testFailedAuthenticationDismissesDirectlyToIdleWithoutSuccess() {
        filter.onScreenStateChanged(isKeyguardLocked = false, isScreenOn = true)
        filter.onSystemBiometricPromptChanged(true)
        filter.onFrontCameraAvailabilityChanged("0", inUse = true)
        assertEquals(BiometricStatus.SCANNING, filter.status.value)

        // Échec ou rejet
        filter.onBiometricFailed()
        assertEquals(BiometricStatus.IDLE, filter.status.value)
        assertTrue(filter.isFailed)

        filter.onFrontCameraAvailabilityChanged("0", inUse = false)
        assertEquals(BiometricStatus.IDLE, filter.status.value)
    }

    @Test
    fun testPromptDismissalWhileScanningRevertsToIdle() {
        filter.onScreenStateChanged(isKeyguardLocked = false, isScreenOn = true)
        filter.onSystemBiometricPromptChanged(true)
        filter.onFrontCameraAvailabilityChanged("0", inUse = true)
        assertEquals(BiometricStatus.SCANNING, filter.status.value)

        // Fermeture inopinée de la boîte sans validation
        filter.onSystemBiometricPromptChanged(false)
        assertEquals(BiometricStatus.IDLE, filter.status.value)
    }

    @Test
    fun testScreenOffDismissesScanningImmediately() {
        filter.onScreenStateChanged(isKeyguardLocked = false, isScreenOn = true)
        filter.onSystemBiometricPromptChanged(true)
        filter.onFrontCameraAvailabilityChanged("0", inUse = true)
        assertEquals(BiometricStatus.SCANNING, filter.status.value)

        // Extinction de l'écran
        filter.onScreenOff()
        assertEquals(BiometricStatus.IDLE, filter.status.value)
        assertFalse(filter.isScreenOn)
    }
}
