package com.example.documentscanner.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

@Composable
fun ExportProgress(current: Int, total: Int) {
    val t = max(1, total)
    val p = min(1f, max(0f, current.toFloat()/t))
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text("Creating PDF… $current / $total")
        LinearProgressIndicator(progress={p}, Modifier.fillMaxWidth().padding(top=8.dp))
    }
}
