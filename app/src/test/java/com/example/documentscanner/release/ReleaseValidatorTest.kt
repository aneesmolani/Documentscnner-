package com.example.documentscanner.release

import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseValidatorTest {
    @Test fun releaseChecksPassForValidConfiguration() {
        val checks = ReleaseValidator.validate(
            applicationId = "com.example.documentscanner",
            versionCode = 15,
            versionName = "1.5.0",
            hasCameraPermission = true
        )
        assertTrue(checks.all { it.passed })
    }
}
