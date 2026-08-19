package com.example.documentscanner.quality

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import kotlin.math.max

/**
 * Deterministic, on-device quality heuristics.
 *
 * This is deliberately NOT AI/ML. It only measures blur, brightness and
 * clipping from the pixels already in memory. It is used as a warning system,
 * never as an automatic rejection mechanism.
 */
data class ScanQualityReport(
    val score: Int,
    val blurScore: Int,
    val brightnessScore: Int,
    val clippingScore: Int,
    val warnings: List<String>
) {
    val good: Boolean get() = score >= 70
}

object ScanQualityAnalyzer {
    fun analyze(bitmap: Bitmap): ScanQualityReport {
        if (bitmap.width < 100 || bitmap.height < 100) {
            return ScanQualityReport(0, 0, 0, 0, listOf("Image is too small"))
        }

        val src = Mat()
        val gray = Mat()
        return try {
            Utils.bitmapToMat(bitmap, src)
            Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY)

            val mean = Core.mean(gray).`val`[0]
            val meanScalar = Mat()
            val lap = Mat()
            try {
                Imgproc.Laplacian(gray, lap, CvType.CV_64F)
                val std = Core.meanStdDev(lap, meanScalar, Mat()).`val`[0]
                val variance = std * std

                val blur = ((variance / 900.0) * 100.0).toInt().coerceIn(0, 100)
                val brightness = when {
                    mean < 45 -> (mean / 45.0 * 55.0).toInt()
                    mean > 225 -> ((255.0 - mean) / 30.0 * 55.0).toInt().coerceIn(0, 55)
                    else -> 100
                }

                val hist = Mat()
                try {
                    val channels = listOf(0)
                    val mask = Mat()
                    val histSize = MatOfInt(256)
                    val ranges = MatOfFloat(0f, 256f)
                    val mats = listOf(gray)
                    Imgproc.calcHist(mats, channels, mask, hist, histSize, ranges)
                    val total = gray.total().coerceAtLeast(1L).toDouble()
                    val low = Core.sumElems(hist.rowRange(0, 8)).`val`[0] / total
                    val high = Core.sumElems(hist.rowRange(248, 256)).`val`[0] / total
                    val clipping = ((1.0 - (low + high).coerceIn(0.0, 1.0)) * 100.0)
                        .toInt().coerceIn(0, 100)

                    val warnings = buildList {
                        if (blur < 45) add("Image may be blurry — hold the phone steadier")
                        if (brightness < 55) add("Image may be too dark")
                        if (brightness > 95) add("Image may be overexposed")
                        if (clipping < 65) add("Some detail may be clipped")
                    }

                    val score = (
                        blur * 0.45 +
                        brightness * 0.30 +
                        clipping * 0.25
                    ).toInt().coerceIn(0, 100)

                    ScanQualityReport(score, blur, brightness, clipping, warnings)
                } finally {
                    hist.release()
                    mask.release()
                    histSize.release()
                    ranges.release()
                }
            } finally {
                meanScalar.release()
                lap.release()
            }
        } catch (_: Throwable) {
            ScanQualityReport(50, 50, 50, 50, listOf("Quality check could not be completed"))
        } finally {
            src.release()
            gray.release()
        }
    }
}
