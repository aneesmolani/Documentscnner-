package com.example.documentscanner.util

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface

/**
 * Gallery-imported JPEGs frequently carry an EXIF orientation tag instead of
 * being physically rotated. Ignoring it means portrait photos come in
 * sideways or upside down. This normalizes any imported bitmap to "upright"
 * before it enters the scan pipeline, the same way the camera capture path
 * already handles rotation via ImageInfo.rotationDegrees.
 */
object ExifUtils {

    fun correctOrientation(resolver: ContentResolver, uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = readOrientation(resolver, uri)
        if (orientation == ExifInterface.ORIENTATION_NORMAL ||
            orientation == ExifInterface.ORIENTATION_UNDEFINED
        ) {
            return bitmap
        }
        return applyOrientation(bitmap, orientation)
    }

    private fun readOrientation(resolver: ContentResolver, uri: Uri): Int {
        return try {
            resolver.openInputStream(uri)?.use { input ->
                ExifInterface(input).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (_: Exception) {
            // Some content providers/formats (PNG, some cloud URIs) don't expose EXIF.
            // Treat as "no correction needed" rather than failing the import.
            ExifInterface.ORIENTATION_NORMAL
        }
    }

    private fun applyOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f); matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f); matrix.postScale(-1f, 1f)
            }
            else -> return bitmap
        }
        return try {
            val rotated = Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
            )
            if (rotated !== bitmap) bitmap.recycle()
            rotated
        } catch (_: OutOfMemoryError) {
            bitmap
        }
    }
}
