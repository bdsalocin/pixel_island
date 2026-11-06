package com.bdsalocin.pixelisland.domain.biometric

import com.bdsalocin.pixelisland.domain.model.BiometricStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class InAppBiometricFilter(
    private val frontCameraIds: Set<String> = emptySet(),
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
    private val successDwellMs: Long = 500L
) {
    var isKeyguardLocked: Boolean = false
        private set
    var isScreenOn: Boolean = true
        private set
    var isSystemPromptShowing: Boolean = false
        private set
    var isFrontCameraInUse: Boolean = false
        private set
    var isCancelled: Boolean = false
        private set
    var isFailed: Boolean = false
        private set

    private val _status = MutableStateFlow(BiometricStatus.IDLE)
    val status: StateFlow<BiometricStatus> = _status.asStateFlow()

    private var dwellJob: Job? = null

    private fun evaluateTrigger() {
        if (!isKeyguardLocked && isScreenOn && isSystemPromptShowing && isFrontCameraInUse && !isCancelled && !isFailed) {
            if (_status.value != BiometricStatus.SCANNING && _status.value != BiometricStatus.SUCCESS) {
                dwellJob?.cancel()
                _status.value = BiometricStatus.SCANNING
            }
        }
    }

    fun onSystemBiometricPromptChanged(isShowing: Boolean) {
        isSystemPromptShowing = isShowing
        if (isShowing) {
            isCancelled = false
            isFailed = false
            evaluateTrigger()
        } else {
            if (_status.value == BiometricStatus.SCANNING) {
                _status.value = BiometricStatus.IDLE
            }
        }
    }

    fun onFrontCameraStateChanged(inUse: Boolean) {
        val wasInUse = isFrontCameraInUse
        isFrontCameraInUse = inUse
        if (inUse) {
            evaluateTrigger()
        } else {
            if (wasInUse && _status.value == BiometricStatus.SCANNING) {
                if (isSystemPromptShowing && !isCancelled && !isFailed) {
                    onBiometricSuccess()
                } else {
                    _status.value = BiometricStatus.IDLE
                }
            }
        }
    }

    fun onFrontCameraAvailabilityChanged(cameraId: String, inUse: Boolean) {
        if (frontCameraIds.isEmpty() || cameraId in frontCameraIds) {
            onFrontCameraStateChanged(inUse)
        }
    }

    fun onScreenStateChanged(isKeyguardLocked: Boolean, isScreenOn: Boolean) {
        this.isKeyguardLocked = isKeyguardLocked
        this.isScreenOn = isScreenOn
        if (isKeyguardLocked || !isScreenOn) {
            dwellJob?.cancel()
            _status.value = BiometricStatus.IDLE
        } else {
            evaluateTrigger()
        }
    }

    fun onScreenOff() {
        onScreenStateChanged(isKeyguardLocked = true, isScreenOn = false)
    }

    fun onBiometricSuccess() {
        if (_status.value == BiometricStatus.SCANNING) {
            dwellJob?.cancel()
            _status.value = BiometricStatus.SUCCESS
            dwellJob = coroutineScope.launch {
                delay(successDwellMs)
                _status.value = BiometricStatus.IDLE
            }
        }
    }

    fun onBiometricCancelled() {
        isCancelled = true
        dwellJob?.cancel()
        _status.value = BiometricStatus.IDLE
    }

    fun onBiometricFailed() {
        isFailed = true
        dwellJob?.cancel()
        _status.value = BiometricStatus.IDLE
    }

    fun reset() {
        dwellJob?.cancel()
        isCancelled = false
        isFailed = false
        isSystemPromptShowing = false
        isFrontCameraInUse = false
        _status.value = BiometricStatus.IDLE
    }
}
