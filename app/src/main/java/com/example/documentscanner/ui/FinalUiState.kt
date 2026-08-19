package com.example.documentscanner.ui

sealed interface FinalUiState {
    data object Ready : FinalUiState
    data object CameraPermissionRequired : FinalUiState
    data object Capturing : FinalUiState
    data object Processing : FinalUiState
    data object Editing : FinalUiState
    data object Exporting : FinalUiState
    data class Completed(val message: String) : FinalUiState
    data class Error(val message: String) : FinalUiState
}
