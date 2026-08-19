package com.example.documentscanner.vision

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc

data class FilterSettings(
    val brightness: Int = 0,
    val contrast: Float = 1f,
    val grayscale: Boolean = false,
    val sharpen: Boolean = false
)

object ImageFilters {
    fun apply(input: Bitmap, settings: FilterSettings): Bitmap {
        if (settings == FilterSettings()) return input.copy(Bitmap.Config.ARGB_8888, false)

        val src = Mat()
        val dst = Mat()
        return try {
            Utils.bitmapToMat(input, src)

            if (settings.grayscale) {
                val gray = Mat()
                try {
                    Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY)
                    Imgproc.cvtColor(gray, dst, Imgproc.COLOR_GRAY2RGBA)
                } finally {
                    gray.release()
                }
            } else {
                src.copyTo(dst)
            }

            dst.convertTo(
                dst,
                -1,
                settings.contrast.toDouble(),
                settings.brightness.toDouble()
            )

            if (settings.sharpen) {
                val sharpened = Mat()
                try {
                    val kernel = Mat(3, 3, CvType.CV_32F)
                    kernel.put(0, 0,
                        0.0, -1.0, 0.0,
                        -1.0, 5.0, -1.0,
                        0.0, -1.0, 0.0
                    )
                    Imgproc.filter2D(dst, sharpened, -1, kernel)
                    sharpened.copyTo(dst)
                    kernel.release()
                } finally {
                    sharpened.release()
                }
            }

            val out = Bitmap.createBitmap(dst.cols(), dst.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(dst, out)
            out
        } finally {
            src.release()
            dst.release()
        }
    }
}
