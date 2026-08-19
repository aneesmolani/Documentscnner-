package com.example.documentscanner.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserErrorMapperTest {
    @Test fun mapsOomToUsefulMessage() {
        assertTrue(UserErrorMapper.message(OutOfMemoryError("OutOfMemory")).contains("too large"))
    }

    @Test fun handlesBlankError() {
        assertEquals(
            "Something went wrong. Please try again.",
            UserErrorMapper.message(IllegalStateException())
        )
    }
}
