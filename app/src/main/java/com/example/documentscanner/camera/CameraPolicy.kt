package com.example.documentscanner.camera

data class CameraPolicy(
    val preferredLensFacing: Int = 0, // CameraSelector.LENS_FACING_BACK
    val jpegQuality: Int = 95,
    val targetLongEdgePx: Int = 2400,
    val analyzerQueueDepth: Int = 1,
    val maxAnalyzerFps: Int = 12,
    val enableTorchForLowLight: Boolean = true
) {
    init {
        require(jpegQuality in 50..100)
        require(targetLongEdgePx in 720..5000)
        require(analyzerQueueDepth in 1..2)
        require(maxAnalyzerFps in 5..30)
    }
}
