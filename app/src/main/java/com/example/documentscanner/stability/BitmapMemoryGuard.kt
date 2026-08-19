package com.example.documentscanner.stability

import android.graphics.BitmapFactory
import kotlin.math.max

object BitmapMemoryGuard {
    data class Bounds(val width: Int, val height: Int)

    fun sampleSizeFor(
        sourceWidth: Int,
        sourceHeight: Int,
        maxLongEdge: Int = 3200,
        maxPixels: Long = 12_000_000L
    ): Int {
        require(sourceWidth > 0 && sourceHeight > 0)
        require(maxLongEdge >= 512)
        require(maxPixels >= 1_000_000L)

        var sample = 1
        while (true) {
            val w = sourceWidth / sample
            val h = sourceHeight / sample
            if (max(w, h) <= maxLongEdge && w.toLong() * h.toLong() <= maxPixels) {
                return sample
            }
            sample *= 2
        }
    }

    fun bounds(path: String): Bounds? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, opts)
        return if (opts.outWidth > 0 && opts.outHeight > 0)
            Bounds(opts.outWidth, opts.outHeight)
        else null
    }
}
