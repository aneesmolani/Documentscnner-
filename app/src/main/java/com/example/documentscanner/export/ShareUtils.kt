package com.example.documentscanner.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Turns an exported document into a system share sheet.
 *
 * Exports on Android 10+ already come back as MediaStore content:// Uris,
 * which are shareable as-is. On older devices (or for files written to
 * app-private storage, e.g. before a MediaStore insert succeeds) we hand
 * out a FileProvider Uri instead of a raw file:// Uri, since Android 7+
 * blocks file:// Uri exposure to other apps (FileUriExposedException).
 */
object ShareUtils {

    private const val AUTHORITY_SUFFIX = ".fileprovider"

    fun shareUri(context: Context, uri: Uri, mimeType: String, chooserTitle: String = "Share") {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    }

    fun shareFile(context: Context, file: File, mimeType: String, chooserTitle: String = "Share") {
        val authority = context.packageName + AUTHORITY_SUFFIX
        val uri = FileProvider.getUriForFile(context, authority, file)
        shareUri(context, uri, mimeType, chooserTitle)
    }
}
