package com.example.documentscanner.export

import android.content.ContentResolver
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlin.math.min

class PdfExportManager(private val resolver: ContentResolver) {
    fun export(
        pages: List<Bitmap>,
        displayName: String,
        options: PdfExportOptions = PdfExportOptions(),
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): Result<android.net.Uri> = runCatching {
        require(pages.isNotEmpty()) { "No pages to export" }
        require(options.marginPt >= 0) { "Margin cannot be negative" }

        val pdf = PdfDocument()
        try {
            pages.forEachIndexed { index, source ->
                onProgress(index, pages.size)
                val prepared = prepare(source, options)
                try {
                    val (pageW, pageH) = if (options.pageSize == PdfPageSize.ORIGINAL) {
                        val scale = min(1f, 1440f / maxOf(prepared.width, prepared.height))
                        Pair(
                            (prepared.width * scale).toInt().coerceAtLeast(1),
                            (prepared.height * scale).toInt().coerceAtLeast(1)
                        )
                    } else Pair(options.pageSize.widthPt, options.pageSize.heightPt)

                    val page = pdf.startPage(
                        PdfDocument.PageInfo.Builder(pageW, pageH, index + 1).create()
                    )
                    val margin = options.marginPt.coerceAtMost(min(pageW, pageH) / 2)
                    val aw = (pageW - margin * 2).coerceAtLeast(1)
                    val ah = (pageH - margin * 2).coerceAtLeast(1)
                    val scale = min(aw.toFloat()/prepared.width, ah.toFloat()/prepared.height)
                    val dw = prepared.width * scale
                    val dh = prepared.height * scale
                    val left = (pageW-dw)/2f
                    val top = (pageH-dh)/2f
                    page.canvas.drawBitmap(
                        prepared, null, RectF(left, top, left+dw, top+dh),
                        Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                    )
                    pdf.finishPage(page)
                } finally {
                    if (prepared !== source && !prepared.isRecycled) prepared.recycle()
                }
            }
            onProgress(pages.size, pages.size)

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, sanitizeFileName(displayName, ".pdf"))
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                if (Build.VERSION.SDK_INT >= 29) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH,
                        Environment.DIRECTORY_DOCUMENTS + "/DocumentScanner")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }
            val uri = resolver.insert(
                MediaStore.Files.getContentUri("external"), values
            ) ?: error("Could not create PDF destination")
            try {
                resolver.openOutputStream(uri)?.use { pdf.writeTo(it) }
                    ?: error("Could not open PDF output")
                if (Build.VERSION.SDK_INT >= 29) {
                    resolver.update(
                        uri, ContentValues().apply {
                            put(MediaStore.MediaColumns.IS_PENDING, 0)
                        }, null, null
                    )
                }
            } catch (t: Throwable) {
                resolver.delete(uri, null, null)
                throw t
            }
            uri
        } finally {
            pdf.close()
        }
    }

    private fun prepare(source: Bitmap, options: PdfExportOptions): Bitmap {
        if (!options.grayscale) return source
        val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val cm = android.graphics.ColorMatrix().apply { setSaturation(0f) }
        paint.colorFilter = android.graphics.ColorMatrixColorFilter(cm)
        canvas.drawBitmap(source, 0f, 0f, paint)
        return out
    }

    companion object {
        fun sanitizeFileName(input: String, extension: String): String {
            val base = input.substringBeforeLast('.', input)
                .replace(Regex("""[\\/:*?"<>|]"""), "_")
                .trim()
                .ifBlank { "scan" }
            return if (base.endsWith(extension, true)) base else base + extension
        }
    }
}
