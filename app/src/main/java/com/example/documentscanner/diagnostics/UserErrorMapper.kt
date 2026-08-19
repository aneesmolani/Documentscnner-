package com.example.documentscanner.diagnostics

object UserErrorMapper {
    fun message(t: Throwable): String {
        val raw = t.message?.trim().orEmpty()
        if (raw.isBlank()) return "Something went wrong. Please try again."
        val safe = raw.replace(Regex("""[\r\n\t]+"""), " ")
        return when {
            safe.contains("OutOfMemory", ignoreCase = true) ->
                "The image is too large. Try scanning at a lower resolution."
            safe.contains("permission", ignoreCase = true) ->
                "Camera permission is required to scan."
            safe.length > 160 ->
                "Something went wrong. Please try again."
            else -> safe
        }
    }
}
