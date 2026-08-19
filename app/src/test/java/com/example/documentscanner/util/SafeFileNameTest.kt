package com.example.documentscanner.util

import org.junit.Assert.assertEquals
import org.junit.Test

class SafeFileNameTest {
    @Test fun removesIllegalCharacters() {
        assertEquals("abc_def_.pdf", SafeFileName.clean("""abc:/def?.pdf"""))
    }

    @Test fun blankGetsFallback() {
        assertEquals("fixed", SafeFileName.clean("fixed"))
    }
}
