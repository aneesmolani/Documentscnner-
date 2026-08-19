package com.example.documentscanner.stability

import java.io.Closeable
import java.util.concurrent.CopyOnWriteArrayList

class CloseRegistry : Closeable {
    private val resources = CopyOnWriteArrayList<Closeable>()
    @Volatile private var closed = false

    fun <T : Closeable> register(resource: T): T {
        if (closed) {
            resource.close()
            throw IllegalStateException("Registry already closed")
        }
        resources.add(resource)
        return resource
    }

    override fun close() {
        if (closed) return
        closed = true
        resources.asReversed().forEach { runCatching { it.close() } }
        resources.clear()
    }
}
