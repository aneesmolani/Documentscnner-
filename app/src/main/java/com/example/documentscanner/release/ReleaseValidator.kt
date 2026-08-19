package com.example.documentscanner.release

data class ReleaseCheck(val name: String, val passed: Boolean, val detail: String)

object ReleaseValidator {
    fun validate(
        applicationId: String,
        versionCode: Int,
        versionName: String,
        hasCameraPermission: Boolean
    ): List<ReleaseCheck> = listOf(
        ReleaseCheck(
            "Application ID",
            applicationId.isNotBlank(),
            if (applicationId.isBlank()) "Missing applicationId" else applicationId
        ),
        ReleaseCheck(
            "Version code",
            versionCode > 0,
            "versionCode=$versionCode"
        ),
        ReleaseCheck(
            "Version name",
            versionName.isNotBlank(),
            "versionName=$versionName"
        ),
        ReleaseCheck(
            "Camera permission declaration",
            hasCameraPermission,
            if (hasCameraPermission) "Declared" else "Missing"
        ),
        ReleaseCheck(
            "AI disabled",
            !ReleaseInfo.AI_ENABLED,
            "AI/ML is disabled"
        ),
        ReleaseCheck(
            "Cloud sync disabled",
            !ReleaseInfo.CLOUD_SYNC_ENABLED,
            "Cloud sync is disabled"
        )
    )
}
