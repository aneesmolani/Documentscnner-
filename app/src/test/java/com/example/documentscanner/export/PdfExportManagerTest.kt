package com.example.documentscanner.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfExportManagerTest {
    @Test fun sanitizesUnsafeCharacters() {
        assertEquals("my_scan_test_name.pdf",
            PdfExportManager.sanitizeFileName("my:scan/test?name", ".pdf"))
    }
    @Test fun addsExtension() {
        assertEquals("document.pdf",
            PdfExportManager.sanitizeFileName("document", ".pdf"))
    }
    @Test fun qualityRange() {
        PdfQuality.entries.forEach { assertTrue(it.jpegQuality in 1..100) }
    }
}
