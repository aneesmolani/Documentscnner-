package com.example.documentscanner.camera

class AnalyzerThrottle(private val maxFps: Int = 12) {
    private val minIntervalNs = 1_000_000_000L / maxFps.coerceIn(1, 60)
    private var lastAcceptedNs = Long.MIN_VALUE

    @Synchronized
    fun shouldAnalyze(nowNs: Long = System.nanoTime()): Boolean {
        if (nowNs - lastAcceptedNs < minIntervalNs) return false
        lastAcceptedNs = nowNs
        return true
    }
}
