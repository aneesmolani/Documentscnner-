package com.example.documentscanner.export

import org.junit.Assert.assertEquals
import org.junit.Test

class PdfExportOptionsTest {
    @Test fun a4HasExpectedPoints() {
        assertEquals(595f to 842f, PdfPageSize.A4.fixedPointSize())
    }

    @Test fun qualityPresetsAreOrdered() {
        assert(PdfExportOptions().quality.maxDimension > 0)
        assert(ExportQuality.ORIGINAL.maxDimension > ExportQuality.HIGH.maxDimension)
    }
}
