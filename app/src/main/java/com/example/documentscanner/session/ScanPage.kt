package com.example.documentscanner.session

import android.graphics.Bitmap

data class ScanPage(
    val id: Long,
    var bitmap: Bitmap,
    var rotation: Int = 0
)
