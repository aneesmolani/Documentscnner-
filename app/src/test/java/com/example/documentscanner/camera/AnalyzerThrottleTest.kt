package com.example.documentscanner.camera

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalyzerThrottleTest {
    @Test fun throttlesFrames() {
        val t = AnalyzerThrottle(10)
        assertTrue(t.shouldAnalyze(1_000_000_000L))
        assertFalse(t.shouldAnalyze(1_050_000_000L))
        assertTrue(t.shouldAnalyze(1_101_000_000L))
    }
}
