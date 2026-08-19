package com.example.documentscanner

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.documentscanner.export.ExportQuality
import com.example.documentscanner.util.AppSettings
import com.example.documentscanner.util.ThemeMode

@Composable
fun SettingsScreen(onBack: () -> Unit, onSettingsChanged: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var theme by remember { mutableStateOf(AppSettings.getThemeMode(context)) }
    var autoCapture by remember { mutableStateOf(AppSettings.getAutoCaptureDefault(context)) }
    var quality by remember { mutableStateOf(AppSettings.getDefaultQuality(context)) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        Text("Appearance", style = MaterialTheme.typography.labelLarge)
        ThemeMode.entries.forEach { mode ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = theme == mode,
                    onClick = {
                        theme = mode
                        AppSettings.setThemeMode(context, mode)
                        onSettingsChanged()
                    }
                )
                Text(mode.label)
            }
        }

        Spacer(Modifier.height(20.dp))
        Text("Scanner", style = MaterialTheme.typography.labelLarge)
        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Auto capture by default")
            Switch(
                checked = autoCapture,
                onCheckedChange = {
                    autoCapture = it
                    AppSettings.setAutoCaptureDefault(context, it)
                }
            )
        }

        Spacer(Modifier.height(20.dp))
        Text("Default export quality", style = MaterialTheme.typography.labelLarge)
        ExportQuality.entries.forEach { q ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = quality == q,
                    onClick = {
                        quality = q
                        AppSettings.setDefaultQuality(context, q)
                    }
                )
                Text(q.label)
            }
        }

        Spacer(Modifier.height(20.dp))
        Text("About", style = MaterialTheme.typography.labelLarge)
        Text("Document Scanner — Round 8 · 0.8.0")
        Text(
            "Offline-first, traditional computer vision (OpenCV). No AI, no ML Kit, " +
                "no cloud API, no internet required for scanning.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(Modifier.weight(1f))
        OutlinedButton(onClick = onBack, Modifier.fillMaxWidth()) { Text("Back") }
    }
}
