package com.example.documentscanner.share

import android.content.Context
import android.content.Intent
import android.net.Uri

object ShareIntents {
    fun share(context: Context, uri: Uri, mimeType: String, title: String = "Share document") {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, title))
    }

    fun shareMultiple(context: Context, uris: List<Uri>, mimeType: String, title: String = "Share documents") {
        require(uris.isNotEmpty())
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = mimeType
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, title))
    }
}
