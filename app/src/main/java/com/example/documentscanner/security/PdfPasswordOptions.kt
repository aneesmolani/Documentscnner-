package com.example.documentscanner.security

data class PdfPasswordOptions(
    val userPassword: String,
    val ownerPassword: String = userPassword,
    val allowPrinting: Boolean = true,
    val allowCopying: Boolean = false,
    val allowModifying: Boolean = false
) {
    init {
        require(userPassword.isNotEmpty()) { "Password cannot be empty" }
        require(userPassword.length >= 4) { "Password must contain at least 4 characters" }
        require(ownerPassword.isNotEmpty()) { "Owner password cannot be empty" }
    }
}
