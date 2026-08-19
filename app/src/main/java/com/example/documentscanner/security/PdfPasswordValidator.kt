package com.example.documentscanner.security

object PdfPasswordValidator {
    fun validate(password: String): String? {
        return when {
            password.length < 4 -> "Password must contain at least 4 characters"
            password.length > 128 -> "Password is too long"
            password.any { it.isWhitespace() } -> "Password cannot contain spaces"
            else -> null
        }
    }

    fun passwordsMatch(password: String, confirm: String): Boolean =
        password == confirm
}
