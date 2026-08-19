package com.example.documentscanner.security

import org.junit.Assert.*
import org.junit.Test

class PdfPasswordValidatorTest {
    @Test fun rejectsShortPassword() {
        assertNotNull(PdfPasswordValidator.validate("123"))
    }

    @Test fun acceptsValidPassword() {
        assertNull(PdfPasswordValidator.validate("abcd1234"))
    }

    @Test fun rejectsSpaces() {
        assertNotNull(PdfPasswordValidator.validate("abc def"))
    }

    @Test fun confirmsMatch() {
        assertTrue(PdfPasswordValidator.passwordsMatch("abcd1234", "abcd1234"))
        assertFalse(PdfPasswordValidator.passwordsMatch("abcd1234", "abcd1235"))
    }
}
