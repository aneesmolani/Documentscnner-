package com.example.documentscanner.editor

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import kotlin.math.max
import kotlin.math.roundToInt

data class CropCorners(
    val topLeft: Point,
    val topRight: Point,
    val bottomRight: Point,
    val bottomLeft: Point
)

object InteractiveCrop {
    fun apply(bitmap: Bitmap, corners: CropCorners): Bitmap? {
        val src = Mat()
        val dst = Mat()
        return try {
            Utils.bitmapToMat(bitmap, src)
            if (src.empty()) return null

            val widthTop = distance(corners.topLeft, corners.topRight)
            val widthBottom = distance(corners.bottomLeft, corners.bottomRight)
            val heightLeft = distance(corners.topLeft, corners.bottomLeft)
            val heightRight = distance(corners.topRight, corners.bottomRight)

            val outW = max(64, max(widthTop, widthBottom).roundToInt())
            val outH = max(64, max(heightLeft, heightRight).roundToInt())
            val scale = minOf(1.0, 4096.0 / max(outW, outH).toDouble())
            val w = max(64, (outW * scale).roundToInt())
            val h = max(64, (outH * scale).roundToInt())

            val source = MatOfPoint2f(
                corners.topLeft, corners.topRight,
                corners.bottomRight, corners.bottomLeft
            )
            val target = MatOfPoint2f(
                Point(0.0, 0.0),
                Point((w - 1).toDouble(), 0.0),
                Point((w - 1).toDouble(), (h - 1).toDouble()),
                Point(0.0, (h - 1).toDouble())
            )
            try {
                val matrix = Imgproc.getPerspectiveTransform(source, target)
                try {
                    Imgproc.warpPerspective(
                        src, dst, matrix, Size(w.toDouble(), h.toDouble()),
                        Imgproc.INTER_CUBIC, Core.BORDER_REPLICATE
                    )
                    val out = Bitmap.createBitmap(dst.cols(), dst.rows(), Bitmap.Config.ARGB_8888)
                    Utils.matToBitmap(dst, out)
                    out
                } finally { matrix.release() }
            } finally {
                source.release()
                target.release()
            }
        } finally {
            src.release()
            dst.release()
        }
    }

    private fun distance(a: Point, b: Point): Double =
        kotlin.math.hypot(a.x - b.x, a.y - b.y)
}
