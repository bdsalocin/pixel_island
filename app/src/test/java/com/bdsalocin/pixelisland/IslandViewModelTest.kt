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
class IslandViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val mediaFlow = MutableSharedFlow<MediaTrack?>(replay = 1)
    private val biometricFlow = MutableStateFlow(BiometricStatus.IDLE)
    private val cutoutFlow = MutableStateFlow(CutoutGeometry(0, 0, 0, 0))

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
    fun testInitialUiState() {
        val state = viewModel.uiState.value
        assertEquals(null, state.mediaTrack)
        assertEquals(BiometricStatus.IDLE, state.biometricStatus)
        assertFalse(state.isExpanded)
        assertFalse(state.isMusicActive)
        assertFalse(state.isFaceUnlockActive)
    }

    @Test
    fun testExpandAndCollapseIsland() = runTest {
        val track = MediaTrack(title = "Track", artist = "Artist", isPlaying = true)
        mediaFlow.emit(track)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.expandIsland()
        assertTrue(viewModel.uiState.value.isExpanded)

        viewModel.collapseIsland()
        assertFalse(viewModel.uiState.value.isExpanded)
        assertTrue(viewModel.uiState.value.isMusicActive)
    }

    @Test
    fun testOnScreenOffCollapsesExpandedCard() = runTest {
        val track = MediaTrack(title = "Track", artist = "Artist", isPlaying = true)
        mediaFlow.emit(track)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.expandIsland()
        assertTrue(viewModel.uiState.value.isExpanded)

        viewModel.onScreenOff()
        assertFalse(viewModel.uiState.value.isExpanded)
    }
}
