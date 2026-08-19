package com.example.documentscanner.diagnostics

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build

data class DeviceDiagnostics(
    val manufacturer: String,
    val model: String,
    val sdk: Int,
    val cameraCount: Int
)

object DeviceDiagnosticsReader {
    fun read(context: Context): DeviceDiagnostics {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val count = runCatching { manager.cameraIdList.size }.getOrDefault(0)
        return DeviceDiagnostics(
            Build.MANUFACTURER,
            Build.MODEL,
            Build.VERSION.SDK_INT,
            count
        )
    }
}
