package com.example.documentscanner.permissions

sealed interface PermissionState {
    data object Granted : PermissionState
    data object Denied : PermissionState
    data object PermanentlyDenied : PermissionState
}

object PermissionPolicy {
    fun cameraRequiredForScan(state: PermissionState): Boolean =
        state != PermissionState.Granted
}
