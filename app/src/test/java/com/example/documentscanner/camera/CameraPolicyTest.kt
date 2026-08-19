package com.example.documentscanner.camera

import org.junit.Assert.assertEquals
import org.junit.Test

class CameraPolicyTest {
    @Test fun defaultPolicyIsConservative() {
        val p = CameraPolicy()
        assertEquals(1, p.analyzerQueueDepth)
        assertEquals(12, p.maxAnalyzerFps)
    }
}
