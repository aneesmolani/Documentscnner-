package com.example.documentscanner.session

import android.graphics.Bitmap

class ScanSession {
    private val _pages = mutableListOf<ScanPage>()
    val pages: List<ScanPage> get() = _pages.toList()

    fun add(bitmap: Bitmap): ScanPage {
        val page = ScanPage(System.nanoTime(), bitmap)
        _pages.add(page)
        return page
    }

    fun remove(id: Long): Boolean {
        val index = _pages.indexOfFirst { it.id == id }
        if (index < 0) return false
        _pages.removeAt(index).bitmap.recycle()
        return true
    }

    fun move(from: Int, to: Int) {
        if (from !in _pages.indices || to !in _pages.indices || from == to) return
        val page = _pages.removeAt(from)
        _pages.add(to, page)
    }

    fun clear() {
        _pages.forEach { if (!it.bitmap.isRecycled) it.bitmap.recycle() }
        _pages.clear()
    }
}
