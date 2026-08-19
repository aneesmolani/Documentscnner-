package com.example.documentscanner

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.documentscanner.session.SavedSessionInfo
import com.example.documentscanner.session.SessionStore
import com.example.documentscanner.util.AppSettings
import com.example.documentscanner.util.ThemeMode
import java.text.SimpleDateFormat
import java.util.Locale

private enum class Screen { HOME, SCANNER, MY_SCANS, SETTINGS }

class MainActivity : ComponentActivity() {
    private var screen by mutableStateOf(Screen.HOME)
    private var openSessionId by mutableStateOf<String?>(null)
    private var themeVersion by mutableIntStateOf(0)

    private val cameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                openSessionId = null
                screen = Screen.SCANNER
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val mode = remember(themeVersion) { AppSettings.getThemeMode(this) }
            val darkTheme = when (mode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            val colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()

            MaterialTheme(colorScheme = colorScheme) {
                Surface(Modifier.fillMaxSize()) {
                    when (screen) {
                        Screen.SCANNER -> ScannerScreen(
                            onClose = { screen = Screen.HOME },
                            initialSessionId = openSessionId
                        )
                        Screen.MY_SCANS -> MyScansScreen(
                            onBack = { screen = Screen.HOME },
                            onOpen = { id ->
                                openSessionId = id
                                screen = Screen.SCANNER
                            }
                        )
                        Screen.SETTINGS -> SettingsScreen(
                            onBack = { screen = Screen.HOME },
                            onSettingsChanged = { themeVersion++ }
                        )
                        Screen.HOME -> HomeScreen(
                            onScan = ::openScanner,
                            onMyScans = { screen = Screen.MY_SCANS },
                            onSettings = { screen = Screen.SETTINGS }
                        )
                    }
                }
            }
        }
    }

    private fun openScanner() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            openSessionId = null
            screen = Screen.SCANNER
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }
}

@Composable
private fun HomeScreen(onScan: () -> Unit, onMyScans: () -> Unit, onSettings: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Document Scanner", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text("Round 8 • Library, folders, search, settings, print")
        Spacer(Modifier.height(28.dp))
        Button(onClick = onScan, Modifier.fillMaxWidth()) {
            Text("Scan Document")
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onMyScans, Modifier.fillMaxWidth()) {
            Text("My Scans")
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onSettings, Modifier.fillMaxWidth()) {
            Text("Settings")
        }
    }
}

private const val FILTER_ALL = "All"
private const val FILTER_FAVORITES = "Favorites"

@Composable
private fun MyScansScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var sessions by remember { mutableStateOf<List<SavedSessionInfo>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var activeFilter by remember { mutableStateOf(FILTER_ALL) }
    var renameTarget by remember { mutableStateOf<SavedSessionInfo?>(null) }
    var folderTarget by remember { mutableStateOf<SavedSessionInfo?>(null) }

    fun reload() { sessions = SessionStore.listSessions(context); loaded = true }
    LaunchedEffect(Unit) { reload() }

    val folders = remember(sessions) { sessions.mapNotNull { it.folder }.distinct().sorted() }
    val filtered = remember(sessions, query, activeFilter) {
        sessions.filter { info ->
            val matchesQuery = query.isBlank() ||
                info.name.contains(query, ignoreCase = true) ||
                (info.folder?.contains(query, ignoreCase = true) == true)
            val matchesFilter = when (activeFilter) {
                FILTER_ALL -> true
                FILTER_FAVORITES -> info.favorite
                else -> info.folder == activeFilter
            }
            matchesQuery && matchesFilter
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("My Scans", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search by name or folder") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(listOf(FILTER_ALL, FILTER_FAVORITES) + folders) { label ->
                FilterChip(
                    selected = activeFilter == label,
                    onClick = { activeFilter = label },
                    label = { Text(label) }
                )
            }
        }
        Spacer(Modifier.height(10.dp))

        when {
            !loaded -> Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                CircularProgressIndicator()
            }
            filtered.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                Text(if (sessions.isEmpty()) "No saved scans yet. Save a session from the Pages screen." else "No scans match")
            }
            else -> LazyColumn(Modifier.weight(1f)) {
                items(filtered, key = { it.id }) { info ->
                    SavedSessionRow(
                        info = info,
                        onOpen = { onOpen(info.id) },
                        onDelete = { SessionStore.deleteSession(context, info.id); reload() },
                        onToggleFavorite = { SessionStore.setFavorite(context, info.id, !info.favorite); reload() },
                        onRename = { renameTarget = info },
                        onFolder = { folderTarget = info }
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onBack, Modifier.fillMaxWidth()) { Text("Back") }
    }

    renameTarget?.let { info ->
        RenameDialog(
            initialName = info.name,
            onDismiss = { renameTarget = null },
            onConfirm = { newName ->
                SessionStore.rename(context, info.id, newName)
                renameTarget = null
                reload()
            }
        )
    }

    folderTarget?.let { info ->
        FolderDialog(
            existingFolders = folders,
            currentFolder = info.folder,
            onDismiss = { folderTarget = null },
            onConfirm = { folder ->
                SessionStore.setFolder(context, info.id, folder)
                folderTarget = null
                reload()
            }
        )
    }
}

@Composable
private fun SavedSessionRow(
    info: SavedSessionInfo,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRename: () -> Unit,
    onFolder: () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val thumb = remember(info.thumbnail) {
                info.thumbnail?.let { BitmapFactory.decodeFile(it.absolutePath) }
            }
            if (thumb != null) {
                Image(thumb.asImageBitmap(), info.name, Modifier.size(56.dp))
            } else {
                Box(Modifier.size(56.dp))
            }
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(info.name)
                val fmt = remember { SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()) }
                Text(
                    "${info.pageCount} page(s)${info.folder?.let { " • $it" } ?: ""} • ${fmt.format(java.util.Date(info.createdAt))}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            IconButton(onClick = onToggleFavorite) {
                Text(
                    text = if (info.favorite) "★" else "☆",
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onOpen) { Text("Open") }
            TextButton(onClick = onRename) { Text("Rename") }
            TextButton(onClick = onFolder) { Text("Folder") }
            TextButton(onClick = onDelete) { Text("Delete") }
        }
    }
}

@Composable
private fun RenameDialog(initialName: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename scan") },
        text = {
            OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true)
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun FolderDialog(
    existingFolders: List<String>,
    currentFolder: String?,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit
) {
    var newFolder by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move to folder") },
        text = {
            Column {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = currentFolder == null, onClick = { onConfirm(null) })
                    Text("None")
                }
                existingFolders.forEach { folder ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = currentFolder == folder, onClick = { onConfirm(folder) })
                        Text(folder)
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = newFolder,
                    onValueChange = { newFolder = it },
                    label = { Text("New folder name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(newFolder) },
                enabled = newFolder.isNotBlank()
            ) { Text("Create & Assign") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
