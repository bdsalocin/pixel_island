package com.bdsalocin.pixelisland

import com.bdsalocin.pixelisland.domain.biometric.InAppBiometricFilter
import com.bdsalocin.pixelisland.domain.model.BiometricStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
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
        Dispatchers.setMain(testDispatcher)
        filter = InAppBiometricFilter(
            frontCameraIds = setOf("0"),
            coroutineScope = testScope,
            successDwellMs = 500L
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialStateIsIdle() {
        assertEquals(BiometricStatus.IDLE, filter.status.value)
        assertFalse(filter.isKeyguardLocked)
        assertTrue(filter.isScreenOn)
        assertFalse(filter.isSystemPromptShowing)
        assertFalse(filter.isFrontCameraInUse)
        assertFalse(filter.isCancelled)
        assertFalse(filter.isFailed)
        assertFalse(filter.isFaceTimeout)
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

        // Authentification faciale validée : fermeture de la boîte système pendant le scan actif
        filter.onSystemBiometricPromptChanged(false)
        assertEquals(BiometricStatus.SUCCESS, filter.status.value)

        // La coupure de caméra postérieure au succès n'interrompt pas l'animation de dwell
        filter.onFrontCameraAvailabilityChanged("0", inUse = false)
        assertEquals(BiometricStatus.SUCCESS, filter.status.value)

        // Après temporisation d'affichage (500 ms), bascule à IDLE
        advanceTimeBy(501L)
        assertEquals(BiometricStatus.IDLE, filter.status.value)
    }

    @Test
    fun testFaceTimeoutWhilePromptShowingTransitionsDirectlyToIdleWithoutSuccess() = testScope.runTest {
        filter.onScreenStateChanged(isKeyguardLocked = false, isScreenOn = true)
        filter.onSystemBiometricPromptChanged(true)
        filter.onFrontCameraAvailabilityChanged("0", inUse = true)
        assertEquals(BiometricStatus.SCANNING, filter.status.value)

        // Coupure caméra alors que la boîte reste ouverte (timeout ~5s ou repli empreinte)
        filter.onFrontCameraAvailabilityChanged("0", inUse = false)
        assertEquals(BiometricStatus.IDLE, filter.status.value)
        assertTrue(filter.isFaceTimeout)

        // Fermeture ultérieure de la boîte : ne doit JAMAIS déclencher SUCCESS
        filter.onSystemBiometricPromptChanged(false)
        assertEquals(BiometricStatus.IDLE, filter.status.value)
        advanceTimeBy(501L)
        assertEquals(BiometricStatus.IDLE, filter.status.value)
    }

    @Test
    fun testGestureCancellationViaCameraCutoffThenDismissal() = testScope.runTest {
        filter.onScreenStateChanged(isKeyguardLocked = false, isScreenOn = true)
        filter.onSystemBiometricPromptChanged(true)
        filter.onFrontCameraAvailabilityChanged("0", inUse = true)
        assertEquals(BiometricStatus.SCANNING, filter.status.value)

        // Balayage retour ou tap extérieur coupant le capteur avant la fenêtre
        filter.onFrontCameraAvailabilityChanged("0", inUse = false)
        assertEquals(BiometricStatus.IDLE, filter.status.value)

        filter.onSystemBiometricPromptChanged(false)
        assertEquals(BiometricStatus.IDLE, filter.status.value)
        advanceTimeBy(501L)
        assertEquals(BiometricStatus.IDLE, filter.status.value)
    }

    @Test
    fun testConsecutiveRequestsResetTransientFlags() = testScope.runTest {
        filter.onScreenStateChanged(isKeyguardLocked = false, isScreenOn = true)

        // Première requête avec scan actif
        filter.onSystemBiometricPromptChanged(true)
        filter.onFrontCameraAvailabilityChanged("0", inUse = true)
        assertEquals(BiometricStatus.SCANNING, filter.status.value)

        // Annulation ou fermeture de la première boîte
        filter.onBiometricCancelled()
        filter.onSystemBiometricPromptChanged(false)
        assertEquals(BiometricStatus.IDLE, filter.status.value)
        assertFalse(filter.isFrontCameraInUse)
        assertFalse(filter.isCancelled)
        assertFalse(filter.isFailed)
        assertFalse(filter.isFaceTimeout)

        // Deuxième requête consécutive
        filter.onSystemBiometricPromptChanged(true)
        assertEquals(BiometricStatus.IDLE, filter.status.value)

        // Notification retardée de la session 1
        filter.onFrontCameraAvailabilityChanged("0", inUse = false)
        assertEquals(BiometricStatus.IDLE, filter.status.value)

        // Caméra active pour la session 2
        filter.onFrontCameraAvailabilityChanged("0", inUse = true)
        assertEquals(BiometricStatus.SCANNING, filter.status.value)

        // Validation réussie session 2
        filter.onSystemBiometricPromptChanged(false)
        assertEquals(BiometricStatus.SUCCESS, filter.status.value)
        advanceTimeBy(501L)
        assertEquals(BiometricStatus.IDLE, filter.status.value)
    }

    @Test
    fun testExplicitBiometricFailureTransitionsToIdleWithoutSuccess() = testScope.runTest {
        filter.onScreenStateChanged(isKeyguardLocked = false, isScreenOn = true)
        filter.onSystemBiometricPromptChanged(true)
        filter.onFrontCameraAvailabilityChanged("0", inUse = true)
        assertEquals(BiometricStatus.SCANNING, filter.status.value)

        // Échec explicite (visage non reconnu, message d'accessibilité)
        filter.onBiometricFailed()
        assertEquals(BiometricStatus.IDLE, filter.status.value)
        assertTrue(filter.isFailed)

        // Coupure de caméra consécutive
        filter.onFrontCameraAvailabilityChanged("0", inUse = false)
        assertEquals(BiometricStatus.IDLE, filter.status.value)

        // Fermeture de la boîte
        filter.onSystemBiometricPromptChanged(false)
        assertEquals(BiometricStatus.IDLE, filter.status.value)

        advanceTimeBy(1000L)
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

        // Coupure caméra
        filter.onFrontCameraAvailabilityChanged("0", inUse = false)
        assertEquals(BiometricStatus.IDLE, filter.status.value)

        // Fermeture invite
        filter.onSystemBiometricPromptChanged(false)
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
