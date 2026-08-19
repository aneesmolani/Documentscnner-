package com.example.documentscanner.capture

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import kotlin.math.max

/**
 * Stacks a front and back ID capture into a single image (front on top,
 * back below, separated by a small white gap), matching the common
 * "one combined image per card" output most ID-scan flows produce.
 * Traditional bitmap compositing only — no AI involved.
 */
object IdCardComposer {
    fun compose(front: Bitmap, back: Bitmap): Bitmap {
        val width = max(front.width, back.width)
        val gapPx = max(12, (width * 0.04f).toInt())

        val frontScaled = scaleToWidth(front, width)
        val backScaled = scaleToWidth(back, width)
        try {
            val height = frontScaled.height + gapPx + backScaled.height
            val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            canvas.drawColor(Color.WHITE)
            canvas.drawBitmap(frontScaled, 0f, 0f, null)
            canvas.drawBitmap(backScaled, 0f, (frontScaled.height + gapPx).toFloat(), null)
            return out
        } finally {
            if (frontScaled !== front) frontScaled.recycle()
            if (backScaled !== back) backScaled.recycle()
        }
    }

    private fun scaleToWidth(bitmap: Bitmap, targetWidth: Int): Bitmap {
        if (bitmap.width == targetWidth) return bitmap
        val scale = targetWidth.toFloat() / bitmap.width.toFloat()
        val h = max(1, (bitmap.height * scale).toInt())
        return Bitmap.createScaledBitmap(bitmap, targetWidth, h, true)
    }
}
