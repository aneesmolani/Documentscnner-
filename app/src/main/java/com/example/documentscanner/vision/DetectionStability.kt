package com.example.documentscanner.vision

class DetectionStability(
    private val requiredFrames: Int = 8,
    private val maxNormalizedMovement: Double = 0.018,
    private val minConfidence: Float = 0.55f
) {
    private var last: Quad? = null
    private var stableFrames = 0

    fun update(current: DetectedDocument): Boolean {
        if (current.confidence < minConfidence) {
            reset()
            return false
        }

        val previous = last
        if (previous == null) {
            last = current.quad
            stableFrames = 1
            return false
        }

        val movement = quadDistance(
            previous,
            current.quad,
            current.imageWidth.toDouble(),
            current.imageHeight.toDouble()
        )

        if (movement <= maxNormalizedMovement) {
            stableFrames++
        } else {
            stableFrames = 1
        }

        last = current.quad
        return stableFrames >= requiredFrames
    }

    fun reset() {
        last = null
        stableFrames = 0
    }

    fun progress(): Float = (stableFrames.toFloat() / requiredFrames).coerceIn(0f, 1f)
}
