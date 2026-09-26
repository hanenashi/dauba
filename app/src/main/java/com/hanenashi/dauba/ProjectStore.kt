package com.hanenashi.dauba

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.OpenableColumns
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class Project(val id: String, val name: String, val bitmap: Bitmap, val drawing: Drawing)

class ProjectStore(private val context: Context) {
    private val root = File(context.filesDir, "project").apply { mkdirs() }
    private val state = AtomicFile(File(root, "current.json"))

    fun load(): Project? {
        if (!state.baseFile.exists()) return null
        val json = JSONObject(state.openRead().bufferedReader().use { it.readText() })
        val id = json.getString("id")
        require(id.matches(Regex("[a-f0-9-]{36}"))) { "Invalid saved project" }
        val bitmap = BitmapFactory.decodeFile(File(root, "$id.png").path)
            ?: error("The saved screenshot could not be opened.")
        return Project(id, json.getString("name"), bitmap, decodeDrawing(json))
    }

    fun import(uri: Uri): Project {
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: "Screenshot"
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            require(info.size.width.toLong() * info.size.height <= 20_000_000L) {
                "This image is too large for the test build (maximum 20 megapixels). Try a smaller screenshot."
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        val project = Project(UUID.randomUUID().toString(), name, bitmap, Drawing())
        val imageFile = File(root, "${project.id}.png")
        imageFile.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        save(project)
        // Only remove the previous base after the replacement and its state are safely stored.
        root.listFiles()?.filter { it.extension == "png" && it != imageFile }?.forEach { it.delete() }
        return project
    }

    fun save(project: Project) {
        val json = encodeDrawing(project.drawing, project.bitmap.width, project.bitmap.height)
            .put("id", project.id).put("name", project.name)
        val output = state.startWrite()
        try {
            output.write(json.toString().toByteArray(Charsets.UTF_8))
            state.finishWrite(output)
        } catch (e: Exception) {
            state.failWrite(output)
            throw e
        }
    }

    fun export(project: Project): File {
        val folder = File(context.cacheDir, "exports").apply { mkdirs() }
        // Keep recent shared files available to recipients, cap older cache growth after a day.
        folder.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 86_400_000 }?.forEach { it.delete() }
        val file = File(folder, "dauba-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(8)}.zip")
        try {
            ZipOutputStream(file.outputStream().buffered()).use { zip ->
                zip.putNextEntry(ZipEntry("screen.png"))
                File(root, "${project.id}.png").inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("screen-annotated.png"))
                val flat = project.bitmap.copy(Bitmap.Config.ARGB_8888, true)
                    ?: error("Not enough memory to export this screenshot.")
                try {
                    AnnotationRenderer.draw(Canvas(flat), project.drawing, project.bitmap.width)
                    check(flat.compress(Bitmap.CompressFormat.PNG, 100, zip))
                } finally { flat.recycle() }
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("screen.md"))
                zip.write(markdown(project.name, project.bitmap.width, project.bitmap.height, project.drawing).toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("screen.json"))
                zip.write(encodeDrawing(project.drawing, project.bitmap.width, project.bitmap.height).toString(2).toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        } catch (e: Exception) { file.delete(); throw e }
        return file
    }
}

fun encodeDrawing(drawing: Drawing, width: Int, height: Int): JSONObject = JSONObject()
    .put("version", 1).put("width", width).put("height", height)
    .put("coordinateSpace", "original-image-pixels").put("nextNoteId", drawing.nextNoteId)
    .put("strokes", JSONArray().apply {
        drawing.strokes.forEach { stroke -> put(JSONObject().put("color", stroke.color).put("width", stroke.width)
            .put("points", JSONArray().apply { stroke.points.forEach { put(JSONArray().put(it.x).put(it.y)) } })) }
    })
    .put("notes", JSONArray().apply {
        drawing.notes.forEach { note -> put(JSONObject().put("id", note.id).put("label", note.label)
            .put("x", note.point.x).put("y", note.point.y)
            .put("normalizedX", note.point.x / width).put("normalizedY", note.point.y / height).put("text", note.text)) }
    })

fun decodeDrawing(json: JSONObject): Drawing {
    require(json.getInt("version") == 1) { "Unsupported project version" }
    val strokes = json.getJSONArray("strokes")
    val notes = json.getJSONArray("notes")
    return Drawing(
        List(strokes.length()) { index ->
            val s = strokes.getJSONObject(index)
            val points = s.getJSONArray("points")
            Stroke(List(points.length()) { i -> points.getJSONArray(i).let { Point(it.getDouble(0).toFloat(), it.getDouble(1).toFloat()) } },
                s.getInt("color"), s.getDouble("width").toFloat())
        },
        List(notes.length()) { index -> notes.getJSONObject(index).let {
            Note(it.getInt("id"), Point(it.getDouble("x").toFloat(), it.getDouble("y").toFloat()), it.getString("text"))
        } },
        json.getInt("nextNoteId")
    )
}
