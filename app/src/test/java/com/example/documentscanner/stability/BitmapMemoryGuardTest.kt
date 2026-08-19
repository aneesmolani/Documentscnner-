package com.example.documentscanner.stability

import org.junit.Assert.assertTrue
import org.junit.Test

class BitmapMemoryGuardTest {
    @Test fun downsizesVeryLargeImages() {
        val sample = BitmapMemoryGuard.sampleSizeFor(8000, 6000)
        assertTrue(sample > 1)
    }

    @Test fun keepsReasonableImagesAtOne() {
        assertTrue(BitmapMemoryGuard.sampleSizeFor(1600, 1200) == 1)
    }
}
