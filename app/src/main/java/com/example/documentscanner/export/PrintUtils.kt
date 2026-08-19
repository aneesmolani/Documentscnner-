package com.example.documentscanner.export

import android.content.Context
import android.net.Uri
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import java.io.FileOutputStream
import java.io.IOException

/**
 * Sends an already-exported PDF straight to the Android print dialog
 * (physical printer, Save-as-PDF target, etc). Uses the system Print
 * framework directly rather than a third-party/cloud printing service.
 */
object PrintUtils {
    fun printPdf(context: Context, pdfUri: Uri, jobName: String) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
            ?: return

        val adapter = object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback,
                extras: android.os.Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback.onLayoutCancelled()
                    return
                }
                val info = PrintDocumentInfo.Builder("$jobName.pdf")
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .build()
                callback.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>,
                destination: ParcelFileDescriptor,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback
            ) {
                try {
                    context.contentResolver.openInputStream(pdfUri)?.use { input ->
                        FileOutputStream(destination.fileDescriptor).use { output ->
                            input.copyTo(output)
                        }
                    } ?: error("Could not reopen exported PDF")
                    callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: IOException) {
                    callback.onWriteFailed(e.message)
                } catch (e: Exception) {
                    callback.onWriteFailed(e.message)
                }
            }
        }

        printManager.print(jobName, adapter, PrintAttributes.Builder().build())
    }
}
