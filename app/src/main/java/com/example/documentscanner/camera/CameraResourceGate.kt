package com.example.documentscanner.camera

import java.util.concurrent.atomic.AtomicBoolean

class CameraResourceGate {
    private val active = AtomicBoolean(false)

    fun acquire(): Boolean = active.compareAndSet(false, true)

    fun release() {
        active.set(false)
    }

    fun isActive(): Boolean = active.get()
}
