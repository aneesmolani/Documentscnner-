package com.example.documentscanner.session

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Local-only session persistence: each saved scan session is a folder under
 * filesDir/sessions/<id>/ containing page_0.jpg, page_1.jpg, ... and a
 * manifest.json with display name, timestamps, favorite flag, an optional
 * folder label, and page count.
 *
 * Nothing here ever leaves the device — no network calls, no cloud sync.
 */
data class SavedSessionInfo(
    val id: String,
    val name: String,
    val createdAt: Long,
    val pageCount: Int,
    val favorite: Boolean,
    val folder: String?,
    val thumbnail: File?
)

object SessionStore {
    private const val ROOT_DIR = "sessions"
    private const val MANIFEST = "manifest.json"

    private fun rootDir(context: Context): File =
        File(context.filesDir, ROOT_DIR).apply { mkdirs() }

    fun saveSession(context: Context, session: ScanSession, name: String?): Result<String> =
        runCatching {
            val pages = session.pages
            require(pages.isNotEmpty()) { "Nothing to save — add at least one page" }

            val id = "session_${System.currentTimeMillis()}"
            val dir = File(rootDir(context), id).apply { mkdirs() }

            try {
                pages.forEachIndexed { index, page ->
                    val file = File(dir, "page_$index.jpg")
                    file.outputStream().use { out ->
                        if (!page.bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)) {
                            error("Failed to encode page ${index + 1}")
                        }
                    }
                }

                val manifest = JSONObject().apply {
                    put("id", id)
                    put("name", name?.takeIf { it.isNotBlank() } ?: defaultName())
                    put("createdAt", System.currentTimeMillis())
                    put("pageCount", pages.size)
                    put("favorite", false)
                    put("folder", JSONObject.NULL)
                }
                File(dir, MANIFEST).writeText(manifest.toString())
                id
            } catch (t: Throwable) {
                dir.deleteRecursively()
                throw t
            }
        }

    fun listSessions(context: Context): List<SavedSessionInfo> {
        val dirs = rootDir(context).listFiles { f -> f.isDirectory } ?: emptyArray()
        return dirs.mapNotNull { dir -> readManifest(dir) }
            .sortedByDescending { it.createdAt }
    }

    fun listFolders(context: Context): List<String> =
        listSessions(context).mapNotNull { it.folder }.distinct().sorted()

    fun loadSession(context: Context, id: String): Result<List<Bitmap>> = runCatching {
        val dir = File(rootDir(context), id)
        val manifest = readManifest(dir) ?: error("Session not found")
        (0 until manifest.pageCount).map { index ->
            val file = File(dir, "page_$index.jpg")
            BitmapFactory.decodeFile(file.absolutePath)
                ?: error("Page ${index + 1} could not be read")
        }
    }

    fun deleteSession(context: Context, id: String): Boolean {
        val dir = File(rootDir(context), id)
        return !dir.exists() || dir.deleteRecursively()
    }

    fun rename(context: Context, id: String, newName: String): Result<Unit> =
        updateManifest(context, id) { json ->
            json.put("name", newName.takeIf { it.isNotBlank() } ?: json.optString("name"))
        }

    fun setFavorite(context: Context, id: String, favorite: Boolean): Result<Unit> =
        updateManifest(context, id) { json -> json.put("favorite", favorite) }

    fun setFolder(context: Context, id: String, folder: String?): Result<Unit> =
        updateManifest(context, id) { json ->
            json.put("folder", folder?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
        }

    private fun updateManifest(context: Context, id: String, mutate: (JSONObject) -> Unit): Result<Unit> =
        runCatching {
            val dir = File(rootDir(context), id)
            val manifestFile = File(dir, MANIFEST)
            require(manifestFile.isFile) { "Session not found" }
            val json = JSONObject(manifestFile.readText())
            mutate(json)
            manifestFile.writeText(json.toString())
        }

    private fun readManifest(dir: File): SavedSessionInfo? {
        val manifestFile = File(dir, MANIFEST)
        if (!manifestFile.isFile) return null
        return try {
            val json = JSONObject(manifestFile.readText())
            val pageCount = json.optInt("pageCount", 0)
            val thumb = File(dir, "page_0.jpg").takeIf { it.isFile }
            SavedSessionInfo(
                id = json.optString("id", dir.name),
                name = json.optString("name", dir.name),
                createdAt = json.optLong("createdAt", dir.lastModified()),
                pageCount = pageCount,
                favorite = json.optBoolean("favorite", false),
                folder = if (json.isNull("folder")) null else json.optString("folder").takeIf { it.isNotBlank() },
                thumbnail = thumb
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun defaultName(): String {
        val fmt = SimpleDateFormat("d MMM, HH:mm", Locale.getDefault())
        return "Scan ${fmt.format(Date())}"
    }
}
