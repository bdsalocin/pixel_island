package com.bdsalocin.pixelisland.data.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper

class FrontCameraMonitor(
    private val context: Context,
    private val onCameraUsageChanged: (cameraId: String, inUse: Boolean) -> Unit
) {
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isRegistered = false

    val frontCameraIds: Set<String> by lazy {
        val cm = cameraManager ?: return@lazy emptySet()
        try {
            cm.cameraIdList.filter { id ->
                val characteristics = cm.getCameraCharacteristics(id)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                facing == CameraCharacteristics.LENS_FACING_FRONT
            }.toSet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    private val availabilityCallback = object : CameraManager.AvailabilityCallback() {
        override fun onCameraAvailable(cameraId: String) {
            if (cameraId in frontCameraIds) {
                onCameraUsageChanged(cameraId, false)
            }
        }

        override fun onCameraUnavailable(cameraId: String) {
            if (cameraId in frontCameraIds) {
                onCameraUsageChanged(cameraId, true)
            }
        }
    }

    fun start() {
        if (!isRegistered) {
            try {
                cameraManager?.registerAvailabilityCallback(availabilityCallback, mainHandler)
                isRegistered = true
            } catch (_: Exception) {}
        }
    }

    fun stop() {
        if (isRegistered) {
            try {
                cameraManager?.unregisterAvailabilityCallback(availabilityCallback)
            } catch (_: Exception) {}
            isRegistered = false
        }
    }
}
