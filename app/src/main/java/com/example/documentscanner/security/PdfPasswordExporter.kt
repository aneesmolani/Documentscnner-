package com.example.documentscanner.security

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import java.io.ByteArrayOutputStream
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import kotlin.math.min

/**
 * Creates a genuinely encrypted PDF using PDFBox.
 * This class does not pretend that a password UI is encryption.
 */
class PdfPasswordExporter(
    private val resolver: ContentResolver
) {
    fun export(
        pages: List<Bitmap>,
        displayName: String,
        options: PdfPasswordOptions,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): Result<Uri> = runCatching {
        require(pages.isNotEmpty()) { "No pages to export" }

        val document = PDDocument()
        try {
            pages.forEachIndexed { index, bitmap ->
                onProgress(index, pages.size)
                val width = bitmap.width.coerceAtLeast(1)
                val height = bitmap.height.coerceAtLeast(1)

                val page = PDPage(PDRectangle(width.toFloat(), height.toFloat()))
                document.addPage(page)

                val imageBytes = ByteArrayOutputStream().use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 94, out)
                    out.toByteArray()
                }
                val image = JPEGFactory.createFromStream(document, imageBytes.inputStream())

                PDPageContentStream(document, page).use { content ->
                    content.drawImage(image, 0f, 0f, width.toFloat(), height.toFloat())
                }
            }

            onProgress(pages.size, pages.size)

            val access = AccessPermission().apply {
                isCanPrint = options.allowPrinting
                isCanExtractContent = options.allowCopying
                isCanModify = options.allowModifying
            }

            val policy = StandardProtectionPolicy(
                options.ownerPassword,
                options.userPassword,
                access
            ).apply {
                encryptionKeyLength = 256
            }
            document.protect(policy)

            val pdfBytes = ByteArrayOutputStream().also {
                document.save(it)
            }.toByteArray()

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, safePdfName(displayName))
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                if (Build.VERSION.SDK_INT >= 29) {
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        Environment.DIRECTORY_DOCUMENTS + "/DocumentScanner"
                    )
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val uri = resolver.insert(
                MediaStore.Files.getContentUri("external"), values
            ) ?: error("Could not create PDF destination")

            try {
                resolver.openOutputStream(uri)?.use { it.write(pdfBytes) }
                    ?: error("Could not open PDF output")

                if (Build.VERSION.SDK_INT >= 29) {
                    resolver.update(
                        uri,
                        ContentValues().apply {
                            put(MediaStore.MediaColumns.IS_PENDING, 0)
                        },
                        null,
                        null
                    )
                }
            } catch (t: Throwable) {
                resolver.delete(uri, null, null)
                throw t
            }

            uri
        } finally {
            document.close()
        }
    }

    private fun safePdfName(name: String): String {
        val base = name.substringBeforeLast('.', name)
            .replace(Regex("""[\\/:*?"<>|]"""), "_")
            .trim()
            .ifBlank { "scan" }
        return if (base.endsWith(".pdf", true)) base else "$base.pdf"
    }
}
