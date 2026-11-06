package com.bdsalocin.pixelisland

import com.bdsalocin.pixelisland.domain.model.BiometricStatus
import com.bdsalocin.pixelisland.domain.model.CutoutGeometry
import com.bdsalocin.pixelisland.domain.model.MediaTrack
import com.bdsalocin.pixelisland.domain.repository.BiometricRepository
import com.bdsalocin.pixelisland.domain.repository.DisplayCutoutRepository
import com.bdsalocin.pixelisland.domain.repository.MediaRepository
import com.bdsalocin.pixelisland.domain.usecase.CalculateCutoutAnchorUseCase
import com.bdsalocin.pixelisland.domain.usecase.ObserveActiveMediaUseCase
import com.bdsalocin.pixelisland.domain.usecase.ObserveUnlockStateUseCase
import com.bdsalocin.pixelisland.domain.usecase.OpenMusicAppUseCase
import com.bdsalocin.pixelisland.domain.usecase.SeekToUseCase
import com.bdsalocin.pixelisland.domain.usecase.SkipNextUseCase
import com.bdsalocin.pixelisland.domain.usecase.SkipPreviousUseCase
import com.bdsalocin.pixelisland.domain.usecase.TogglePlaybackUseCase
import com.bdsalocin.pixelisland.presentation.island.IslandViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
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
class BiometricStatusLifecycleTest {

    private val testDispatcher = StandardTestDispatcher()

    private val mediaFlow = MutableSharedFlow<MediaTrack?>(replay = 1)
    private val biometricFlow = MutableStateFlow(BiometricStatus.IDLE)
    private val cutoutFlow = MutableStateFlow(CutoutGeometry(0f, 0f, 0f, 0f))

    private val fakeMediaRepo = object : MediaRepository {
        override fun observeCurrentTrack(): Flow<MediaTrack?> = mediaFlow
        override suspend fun playPause() {}
        override suspend fun skipToNext() {}
        override suspend fun skipToPrevious() {}
        override suspend fun seekTo(positionMs: Long) {}
        override suspend fun openMusicApp() {}
    }

    private val fakeBiometricRepo = object : BiometricRepository {
        override fun observeUnlockEvents(): Flow<BiometricStatus> = biometricFlow
    }

    private val fakeCutoutRepo = object : DisplayCutoutRepository {
        override fun observeCutoutGeometry(): Flow<CutoutGeometry> = cutoutFlow
        override fun refreshCutoutGeometry(rotation: Int) {}
    }

    private lateinit var viewModel: IslandViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = IslandViewModel(
            observeActiveMediaUseCase = ObserveActiveMediaUseCase(fakeMediaRepo),
            observeUnlockStateUseCase = ObserveUnlockStateUseCase(fakeBiometricRepo),
            calculateCutoutAnchorUseCase = CalculateCutoutAnchorUseCase(fakeCutoutRepo),
            togglePlaybackUseCase = TogglePlaybackUseCase(fakeMediaRepo),
            skipNextUseCase = SkipNextUseCase(fakeMediaRepo),
            skipPreviousUseCase = SkipPreviousUseCase(fakeMediaRepo),
            openMusicAppUseCase = OpenMusicAppUseCase(fakeMediaRepo),
            seekToUseCase = SeekToUseCase(fakeMediaRepo),
            keyguardManager = null
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testScanningStateSetsDeviceUnlockedFalse() = runTest {
        biometricFlow.value = BiometricStatus.SCANNING
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(BiometricStatus.SCANNING, viewModel.uiState.value.biometricStatus)
        assertTrue(viewModel.uiState.value.isFaceUnlockActive)
        // Pendant le scan, le cadenas doit impérativement être fermé
        assertFalse(viewModel.uiState.value.isDeviceUnlocked)
    }

    @Test
    fun testSuccessStateSetsDeviceUnlockedTrue() = runTest {
        biometricFlow.value = BiometricStatus.SCANNING
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isDeviceUnlocked)

        biometricFlow.value = BiometricStatus.SUCCESS
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(BiometricStatus.SUCCESS, viewModel.uiState.value.biometricStatus)
        assertTrue(viewModel.uiState.value.isFaceUnlockActive)
        // Dès que l'authentification réussit, le cadenas bascule en position ouverte
        assertTrue(viewModel.uiState.value.isDeviceUnlocked)
    }

    @Test
    fun testCancellationRevertsToIdleWithoutSuccess() = runTest {
        biometricFlow.value = BiometricStatus.SCANNING
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isDeviceUnlocked)

        // Annulation directe vers IDLE
        biometricFlow.value = BiometricStatus.IDLE
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(BiometricStatus.IDLE, viewModel.uiState.value.biometricStatus)
        assertFalse(viewModel.uiState.value.isFaceUnlockActive)
    }

    @Test
    fun testFaceUnlockPreemptsMusicInUiState() = runTest {
        val track = MediaTrack(title = "Song", artist = "Artist", isPlaying = true)
        mediaFlow.emit(track)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isMusicActive)
        assertFalse(viewModel.uiState.value.isFaceUnlockActive)

        // Début du Face ID applicatif
        biometricFlow.value = BiometricStatus.SCANNING
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isFaceUnlockActive)
        assertTrue(viewModel.uiState.value.isMusicActive)

        // Fin du Face ID : retour à IDLE
        biometricFlow.value = BiometricStatus.IDLE
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isFaceUnlockActive)
        assertTrue(viewModel.uiState.value.isMusicActive)
        assertFalse(viewModel.uiState.value.isExpanded)
    }
}
