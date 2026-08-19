package com.example.documentscanner.performance

data class PerformanceSnapshot(
    val analyzerMs: Long,
    val captureMs: Long,
    val memoryUsedMb: Long
)

class PerformanceSampler {
    private var analyzerStart = 0L
    private var captureStart = 0L

    fun startAnalyzer(nowNs: Long = System.nanoTime()) {
        analyzerStart = nowNs
    }

    fun endAnalyzer(nowNs: Long = System.nanoTime()): Long =
        if (analyzerStart == 0L) 0L else (nowNs - analyzerStart) / 1_000_000L

    fun startCapture(nowNs: Long = System.nanoTime()) {
        captureStart = nowNs
    }

    fun endCapture(nowNs: Long = System.nanoTime()): Long =
        if (captureStart == 0L) 0L else (nowNs - captureStart) / 1_000_000L

    fun memoryUsedMb(): Long {
        val rt = Runtime.getRuntime()
        return (rt.totalMemory() - rt.freeMemory()) / (1024L * 1024L)
    }
}
