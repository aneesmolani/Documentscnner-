package com.example.documentscanner.export

enum class PdfPageSize(val label: String) {
    AUTO("Auto (match page)"),
    A4("A4"),
    LETTER("Letter"),
    ORIGINAL("Original size");

    /** Page size in PDF points (1/72 inch), or null when the size should follow the bitmap. */
    fun fixedPointSize(): Pair<Float, Float>? = when (this) {
        A4 -> 595f to 842f
        LETTER -> 612f to 792f
        AUTO, ORIGINAL -> null
    }
}

enum class ExportQuality(val label: String, val jpegQuality: Int, val maxDimension: Int) {
    SMALL_SIZE("Small size", 55, 1400),
    MEDIUM("Medium", 75, 2200),
    HIGH("High quality", 90, 3200),
    ORIGINAL("Original", 95, Int.MAX_VALUE)
}

data class PdfExportOptions(
    val pageSize: PdfPageSize = PdfPageSize.AUTO,
    val marginPt: Float = 0f,
    val quality: ExportQuality = ExportQuality.HIGH
)
