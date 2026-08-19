package com.example.documentscanner.stability

import com.example.documentscanner.diagnostics.UserErrorMapper

sealed interface SafeResult<out T> {
    data class Success<T>(val value: T) : SafeResult<T>
    data class Failure(val message: String) : SafeResult<Nothing>
}

inline fun <T> safeOperation(block: () -> T): SafeResult<T> =
    try {
        SafeResult.Success(block())
    } catch (t: Throwable) {
        SafeResult.Failure(UserErrorMapper.message(t))
    }
