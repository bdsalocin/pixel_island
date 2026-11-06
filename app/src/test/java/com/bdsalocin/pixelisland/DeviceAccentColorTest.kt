package com.bdsalocin.pixelisland

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
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
import com.bdsalocin.pixelisland.presentation.theme.DefaultIslandAccentColor
import com.bdsalocin.pixelisland.presentation.theme.ensureContrastOnBlack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceAccentColorTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun ensureContrastOnBlackPreservesVibrantPixelTone80Accent() {
        val pixelSkyBlue = Color(0xFFA8C7FA)
        val result = ensureContrastOnBlack(pixelSkyBlue)
        assertEquals(pixelSkyBlue, result)
    }

    @Test
    fun ensureContrastOnBlackBoostsOverlyDarkAccentForOledLegibility() {
        val veryDarkNavy = Color(0xFF0A1020)
        val result = ensureContrastOnBlack(veryDarkNavy)
        assertTrue(result.luminance() > 0.14f)
    }

    @Test
    fun ensureContrastOnBlackFallsBackWhenUnspecified() {
        val result = ensureContrastOnBlack(Color.Unspecified)
        assertEquals(DefaultIslandAccentColor, result)
    }

    @Test
    fun onThemeChangedIncrementsThemeVersionInViewModel() {
        val mediaRepo = object : MediaRepository {
            override fun observeCurrentTrack(): Flow<MediaTrack?> = MutableSharedFlow()
            override suspend fun playPause() {}
            override suspend fun skipToNext() {}
            override suspend fun skipToPrevious() {}
            override suspend fun seekTo(positionMs: Long) {}
            override suspend fun openMusicApp() {}
        }
        val bioRepo = object : BiometricRepository {
            override fun observeUnlockEvents(): Flow<BiometricStatus> = MutableStateFlow(BiometricStatus.IDLE)
        }
        val cutoutRepo = object : DisplayCutoutRepository {
            override fun observeCutoutGeometry(): Flow<CutoutGeometry> = MutableStateFlow(CutoutGeometry(0, 0, 0, 0))
            override fun refreshCutoutGeometry(rotation: Int) {}
        }

        val vm = IslandViewModel(
            observeActiveMediaUseCase = ObserveActiveMediaUseCase(mediaRepo),
            observeUnlockStateUseCase = ObserveUnlockStateUseCase(bioRepo),
            calculateCutoutAnchorUseCase = CalculateCutoutAnchorUseCase(cutoutRepo),
            togglePlaybackUseCase = TogglePlaybackUseCase(mediaRepo),
            skipNextUseCase = SkipNextUseCase(mediaRepo),
            skipPreviousUseCase = SkipPreviousUseCase(mediaRepo),
            openMusicAppUseCase = OpenMusicAppUseCase(mediaRepo),
            seekToUseCase = SeekToUseCase(mediaRepo),
            keyguardManager = null
        )

        assertEquals(0, vm.uiState.value.themeVersion)
        vm.onThemeChanged()
        assertEquals(1, vm.uiState.value.themeVersion)
        vm.onThemeChanged()
        assertEquals(2, vm.uiState.value.themeVersion)
    }
}
