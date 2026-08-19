package com.example.documentscanner.camera

sealed interface CameraError {
    data object PermissionDenied : CameraError
    data object NoCamera : CameraError
    data object CameraUnavailable : CameraError
    data object BindFailed : CameraError
    data class Unknown(val message: String) : CameraError
}
