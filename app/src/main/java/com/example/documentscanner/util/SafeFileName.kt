package com.example.documentscanner.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SafeFileName {
    fun clean(input: String): String {
        val cleaned = input.trim()
            .replace(Regex("""[\\/:*?"<>|]"""), "_")
            .replace(Regex("""\s+"""), " ")
        return cleaned.take(80).ifBlank { defaultName() }
    }

    fun defaultName(now: Long = System.currentTimeMillis()): String =
        SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date(now))
}
