package com.example.documentscanner.capture

import android.graphics.Bitmap

/**
 * Splits a corrected book-spread capture into two single pages at a
 * user-adjustable vertical line. This is plain pixel cropping — there is
 * no curvature/dewarp correction for the page bend near the spine, which
 * the original no-AI blueprint calls out as a known gap versus Adobe Scan.
 */
object BookSplitter {
    fun split(bitmap: Bitmap, splitFraction: Float): Pair<Bitmap, Bitmap> {
        val fraction = splitFraction.coerceIn(0.1f, 0.9f)
        val x = (bitmap.width * fraction).toInt().coerceIn(1, bitmap.width - 1)
        val left = Bitmap.createBitmap(bitmap, 0, 0, x, bitmap.height)
        val right = Bitmap.createBitmap(bitmap, x, 0, bitmap.width - x, bitmap.height)
        return left to right
    }
}
