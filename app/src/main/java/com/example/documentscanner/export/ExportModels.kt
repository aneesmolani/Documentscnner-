package com.example.documentscanner.export

enum class PdfPageSize(val widthPt: Int, val heightPt: Int) {
    A4(595, 842), LETTER(612, 792), LEGAL(612, 1008), ORIGINAL(0, 0)
}

enum class PdfQuality(val jpegQuality: Int) {
    STANDARD(82), HIGH(92), MAXIMUM(97)
}

data class PdfExportOptions(
    val pageSize: PdfPageSize = PdfPageSize.A4,
    val marginPt: Int = 18,
    val quality: PdfQuality = PdfQuality.HIGH,
    val grayscale: Boolean = false
)

sealed interface ExportState {
    data object Idle : ExportState
    data class Running(val current: Int, val total: Int) : ExportState
    data class Success(val uri: android.net.Uri) : ExportState
    data class Error(val message: String) : ExportState
}
