package com.example.documentscanner.camera

import android.content.Context
import android.net.Uri
import androidx.camera.core.ImageCapture
import java.io.File

object CaptureUtils {
    fun newCaptureFile(context: Context): File =
        File(context.cacheDir, "capture_${System.currentTimeMillis()}.jpg")

    fun outputOptions(file: File): ImageCapture.OutputFileOptions =
        ImageCapture.OutputFileOptions.Builder(file).build()

    fun uri(file: File): Uri = Uri.fromFile(file)
}
