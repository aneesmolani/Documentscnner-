package com.example.documentscanner.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import kotlin.math.max
import kotlin.math.min

object DocumentExporter {

    /**
     * Downscales a page bitmap to the quality preset's max dimension. This is
     * the practical lever for PDF/JPEG file size, since Android's PdfDocument
     * embeds the raw bitmap rather than re-encoding it as a compressed JPEG
     * stream internally.
     */
    private fun constrained(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val largest = max(bitmap.width, bitmap.height)
        if (largest <= maxDimension) return bitmap
        val scale = maxDimension.toFloat() / largest.toFloat()
        val w = max(1, (bitmap.width * scale).toInt())
        val h = max(1, (bitmap.height * scale).toInt())
        return Bitmap.createScaledBitmap(bitmap, w, h, true)
    }

    fun exportPdf(
        context: Context,
        pages: List<Bitmap>,
        displayName: String,
        options: PdfExportOptions = PdfExportOptions()
    ): Result<Uri> = runCatching {
        require(pages.isNotEmpty()) { "No pages to export" }

        val pdf = PdfDocument()
        val scaledPages = mutableListOf<Bitmap>()
        try {
            pages.forEach { original ->
                val scaled = constrained(original, options.quality.maxDimension)
                scaledPages.add(scaled)

                val fixed = options.pageSize.fixedPointSize()
                val pageWidthPt: Float
                val pageHeightPt: Float
                if (fixed != null) {
                    pageWidthPt = fixed.first
                    pageHeightPt = fixed.second
                } else if (options.pageSize == PdfPageSize.ORIGINAL) {
                    pageWidthPt = scaled.width.toFloat()
                    pageHeightPt = scaled.height.toFloat()
                } else {
                    // AUTO: page matches the scanned page's own aspect ratio at A4-like width.
                    val targetWidth = 595f
                    val aspect = scaled.height.toFloat() / scaled.width.toFloat()
                    pageWidthPt = targetWidth
                    pageHeightPt = targetWidth * aspect
                }

                val pageInfo = PdfDocument.PageInfo.Builder(
                    max(1, pageWidthPt.toInt()),
                    max(1, pageHeightPt.toInt()),
                    scaledPages.size
                ).create()

                val page = pdf.startPage(pageInfo)
                val canvas = page.canvas

                val margin = options.marginPt.coerceIn(0f, min(pageWidthPt, pageHeightPt) / 2f - 1f)
                val contentRect = RectF(
                    margin, margin,
                    pageWidthPt - margin, pageHeightPt - margin
                )

                val bitmapAspect = scaled.width.toFloat() / scaled.height.toFloat()
                val boxAspect = contentRect.width() / contentRect.height()
                val drawRect = if (bitmapAspect > boxAspect) {
                    val h = contentRect.width() / bitmapAspect
                    val top = contentRect.top + (contentRect.height() - h) / 2f
                    RectF(contentRect.left, top, contentRect.right, top + h)
                } else {
                    val w = contentRect.height() * bitmapAspect
                    val left = contentRect.left + (contentRect.width() - w) / 2f
                    RectF(left, contentRect.top, left + w, contentRect.bottom)
                }

                val matrix = Matrix().apply {
                    setRectToRect(
                        RectF(0f, 0f, scaled.width.toFloat(), scaled.height.toFloat()),
                        drawRect,
                        Matrix.ScaleToFit.FILL
                    )
                }
                canvas.drawBitmap(scaled, matrix, null)
                pdf.finishPage(page)
            }

            val safeName = if (displayName.endsWith(".pdf", true)) displayName else "$displayName.pdf"
            writeOutput(
                context = context,
                displayName = safeName,
                mimeType = "application/pdf",
                relativeDir = Environment.DIRECTORY_DOCUMENTS,
                collection = MediaStore.Files.getContentUri("external"),
                write = { out -> pdf.writeTo(out) }
            )
        } finally {
            pdf.close()
            scaledPages.forEach { if (it !in pages && !it.isRecycled) it.recycle() }
        }
    }

    fun exportJpeg(
        context: Context,
        bitmap: Bitmap,
        displayName: String,
        quality: ExportQuality = ExportQuality.HIGH
    ): Result<Uri> = runCatching {
        val scaled = constrained(bitmap, quality.maxDimension)
        try {
            val safeName = if (displayName.endsWith(".jpg", true)) displayName else "$displayName.jpg"
            writeOutput(
                context = context,
                displayName = safeName,
                mimeType = "image/jpeg",
                relativeDir = Environment.DIRECTORY_PICTURES,
                collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                write = { out ->
                    if (!scaled.compress(Bitmap.CompressFormat.JPEG, quality.jpegQuality, out)) {
                        error("JPEG compression failed")
                    }
                }
            )
        } finally {
            if (scaled !== bitmap && !scaled.isRecycled) scaled.recycle()
        }
    }

    /**
     * Android 10+: goes through MediaStore so the file lands in the public
     * Documents/Pictures collection and is immediately shareable as a
     * content:// Uri.
     *
     * Below Android 10 (no scoped-storage MediaStore.Files insert path for
     * arbitrary app-generated documents): write into app-private storage and
     * hand back a FileProvider content:// Uri instead, which is shareable
     * and avoids FileUriExposedException without requesting legacy external
     * storage permissions.
     */
    private fun writeOutput(
        context: Context,
        displayName: String,
        mimeType: String,
        relativeDir: String,
        collection: Uri,
        write: (java.io.OutputStream) -> Unit
    ): Uri {
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "$relativeDir/DocumentScanner")
            }
            val uri = resolver.insert(collection, values) ?: error("Could not create output file")
            try {
                resolver.openOutputStream(uri)?.use { write(it) } ?: error("Could not open output stream")
                return uri
            } catch (t: Throwable) {
                resolver.delete(uri, null, null)
                throw t
            }
        }

        val exportsDir = File(context.filesDir, "exports").apply { mkdirs() }
        val file = File(exportsDir, displayName)
        file.outputStream().use { write(it) }
        val authority = context.packageName + ".fileprovider"
        return FileProvider.getUriForFile(context, authority, file)
    }
}
