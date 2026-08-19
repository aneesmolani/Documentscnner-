package com.example.documentscanner.release

object ReleaseInfo {
    const val VERSION_NAME = "1.5.0"
    const val VERSION_CODE = 15
    const val PRIVACY_FRIENDLY = true
    const val AI_ENABLED = false
    const val CLOUD_SYNC_ENABLED = false

    fun summary(): String =
        "DocumentScanner $VERSION_NAME ($VERSION_CODE) • Offline • No AI • No cloud sync"
}
