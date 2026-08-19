package com.example.documentscanner.vision

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

object PerspectiveProcessor {
    fun correct(bitmap: Bitmap, quad: Quad): Bitmap? {
        val src = Mat()
        val warped = Mat()
        return try {
            Utils.bitmapToMat(bitmap, src)
            if (src.empty()) return null

            val p = arrayOf(quad.topLeft, quad.topRight, quad.bottomRight, quad.bottomLeft)
            val widthTop = hypot(p[1].x - p[0].x, p[1].y - p[0].y)
            val widthBottom = hypot(p[2].x - p[3].x, p[2].y - p[3].y)
            val heightLeft = hypot(p[3].x - p[0].x, p[3].y - p[0].y)
            val heightRight = hypot(p[2].x - p[1].x, p[2].y - p[1].y)

            val outWidth = max(64, max(widthTop, widthBottom).roundToInt())
            val outHeight = max(64, max(heightLeft, heightRight).roundToInt())

            // Limit output dimensions to protect memory.
            val maxDimension = 4096
            val factor = minOf(
                1.0,
                maxDimension.toDouble() / max(outWidth, outHeight).toDouble()
            )
            val finalWidth = max(64, (outWidth * factor).roundToInt())
            val finalHeight = max(64, (outHeight * factor).roundToInt())

            val source = MatOfPoint2f(*p)
            val destination = MatOfPoint2f(
                Point(0.0, 0.0),
                Point((finalWidth - 1).toDouble(), 0.0),
                Point((finalWidth - 1).toDouble(), (finalHeight - 1).toDouble()),
                Point(0.0, (finalHeight - 1).toDouble())
            )

            try {
                val matrix = Imgproc.getPerspectiveTransform(source, destination)
                try {
                    Imgproc.warpPerspective(
                        src, warped, matrix,
                        Size(finalWidth.toDouble(), finalHeight.toDouble()),
                        Imgproc.INTER_CUBIC,
                        Core.BORDER_REPLICATE
                    )
                    val result = Bitmap.createBitmap(
                        warped.cols(), warped.rows(), Bitmap.Config.ARGB_8888
                    )
                    Utils.matToBitmap(warped, result)
                    return result
                } finally {
                    matrix.release()
                }
            } finally {
                source.release()
                destination.release()
            }
        } finally {
            src.release()
            warped.release()
        }
    }
}
