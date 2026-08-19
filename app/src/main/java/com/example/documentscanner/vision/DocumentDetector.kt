package com.example.documentscanner.vision

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class DetectedDocument(
    val quad: Quad,
    val confidence: Float,
    val imageWidth: Int,
    val imageHeight: Int
)

class DocumentDetector(
    private val minAreaRatio: Double = 0.18,
    private val maxAreaRatio: Double = 0.98
) {
    fun detect(bitmap: Bitmap): DetectedDocument? {
        val src = Mat()
        return try {
            Utils.bitmapToMat(bitmap, src)
            detect(src)
        } finally {
            src.release()
        }
    }

    fun detect(src: Mat): DetectedDocument? {
        if (src.empty() || src.cols() < 200 || src.rows() < 200) return null

        val scale = min(1.0, 1000.0 / max(src.cols(), src.rows()).toDouble())
        val work = Mat()
        try {
            Imgproc.resize(src, work, Size(), scale, scale, Imgproc.INTER_AREA)

            val gray = Mat()
            val blur = Mat()
            val edges = Mat()
            try {
                Imgproc.cvtColor(work, gray, Imgproc.COLOR_RGBA2GRAY)
                Imgproc.GaussianBlur(gray, blur, Size(5.0, 5.0), 0.0)
                Imgproc.Canny(blur, edges, 60.0, 180.0)

                val contours = ArrayList<MatOfPoint>()
                val hierarchy = Mat()
                try {
                    Imgproc.findContours(
                        edges, contours, hierarchy,
                        Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE
                    )

                    val imageArea = work.cols().toDouble() * work.rows()
                    var best: DetectedDocument? = null
                    var bestScore = 0.0

                    for (contour in contours) {
                        val contourArea = abs(Imgproc.contourArea(contour))
                        val ratio = contourArea / imageArea
                        if (ratio !in minAreaRatio..maxAreaRatio) continue

                        val curve = MatOfPoint2f(*contour.toArray())
                        val perimeter = Imgproc.arcLength(curve, true)
                        val approx = MatOfPoint2f()
                        try {
                            Imgproc.approxPolyDP(curve, approx, 0.02 * perimeter, true)
                            if (approx.total() != 4L) continue

                            val ordered = orderCorners(approx.toArray())
                            val convex = Imgproc.isContourConvex(MatOfPoint(*ordered))
                            if (!convex) continue

                            val area = abs(Imgproc.contourArea(MatOfPoint2f(*ordered)))
                            val rectangularity = area / imageArea
                            if (rectangularity < minAreaRatio) continue

                            val edgeScore = edgeSupport(edges, ordered)
                            val score = rectangularity * 0.75 + edgeScore * 0.25

                            if (score > bestScore) {
                                val factor = 1.0 / scale
                                best = DetectedDocument(
                                    quad = Quad(
                                        Point(ordered[0].x * factor, ordered[0].y * factor),
                                        Point(ordered[1].x * factor, ordered[1].y * factor),
                                        Point(ordered[2].x * factor, ordered[2].y * factor),
                                        Point(ordered[3].x * factor, ordered[3].y * factor)
                                    ),
                                    confidence = score.coerceIn(0.0, 1.0).toFloat(),
                                    imageWidth = src.cols(),
                                    imageHeight = src.rows()
                                )
                                bestScore = score
                            }
                        } finally {
                            approx.release()
                            curve.release()
                        }
                    }
                    return best
                } finally {
                    contours.forEach { it.release() }
                    hierarchy.release()
                }
            } finally {
                gray.release()
                blur.release()
                edges.release()
            }
        } finally {
            work.release()
        }
    }

    private fun orderCorners(points: Array<Point>): Array<Point> {
        val sums = points.sortedBy { it.x + it.y }
        val diffs = points.sortedBy { it.y - it.x }
        return arrayOf(sums.first(), diffs.first(), sums.last(), diffs.last())
    }

    private fun edgeSupport(edges: Mat, corners: Array<Point>): Double {
        if (edges.cols() < 5 || edges.rows() < 5) return 0.0
        var supported = 0
        for (p in corners) {
            val x = p.x.toInt().coerceIn(2, edges.cols() - 3)
            val y = p.y.toInt().coerceIn(2, edges.rows() - 3)
            val roi = edges.submat(y - 2, y + 3, x - 2, x + 3)
            try {
                if (Core.countNonZero(roi) > 0) supported++
            } finally {
                roi.release()
            }
        }
        return supported / 4.0
    }
}
