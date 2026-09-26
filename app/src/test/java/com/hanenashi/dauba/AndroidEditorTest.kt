package com.hanenashi.dauba

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.view.MotionEvent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.zip.ZipFile

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AndroidEditorTest {
    private val context: Application get() = RuntimeEnvironment.getApplication()

    private fun fixture(): Project {
        val bitmap = Bitmap.createBitmap(200, 400, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        // ImageDecoder's file-descriptor JNI is device-only in Robolectric.
        // Seed storage here; actual picker/share import is covered on the emulator.
        val project = Project(java.util.UUID.randomUUID().toString(), "fixture.png", bitmap, Drawing())
        val folder = File(context.filesDir, "project").apply { mkdirs() }
        val file = File(folder, "${project.id}.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        ProjectStore(context).save(project)
        return project
    }

    @Test fun savedProjectRoundTripsAndExportsOriginalAndMarkupSeparately() {
        val store = ProjectStore(context)
        val base = fixture()
        val drawing = Drawing(listOf(Stroke(listOf(Point(20f, 30f), Point(180f, 30f)), Color.RED, 10f)),
            listOf(Note(3, Point(70f, 200f), "Move this.\n日本語")), 4)
        val project = base.copy(drawing = drawing)
        store.save(project)
        assertEquals(drawing, store.load()!!.drawing)
        assertEquals(base.id, store.load()!!.id)
        val packet = store.export(project)
        ZipFile(packet).use { zip ->
            assertEquals(setOf("screen.png", "screen-annotated.png", "screen.md", "screen.json"), zip.entries().asSequence().map { it.name }.toSet())
            val original = zip.getInputStream(zip.getEntry("screen.png")).use { BitmapFactory.decodeStream(it) }
            val marked = zip.getInputStream(zip.getEntry("screen-annotated.png")).use { BitmapFactory.decodeStream(it) }
            assertEquals(200, marked.width); assertEquals(400, marked.height)
            assertEquals(Color.WHITE, original.getPixel(100, 30))
            assertEquals(Color.RED, marked.getPixel(100, 30))
            val notes = zip.getInputStream(zip.getEntry("screen.md")).bufferedReader().use { it.readText() }
            assertTrue(notes.contains("## D")); assertTrue(notes.contains("日本語"))
            val json = org.json.JSONObject(zip.getInputStream(zip.getEntry("screen.json")).bufferedReader().use { it.readText() })
            assertEquals(drawing, decodeDrawing(json))
            assertEquals(.35, json.getJSONArray("notes").getJSONObject(0).getDouble("normalizedX"), .0001)
        }
    }

    @Test fun interruptedImportPreservesPriorProject() {
        val store = ProjectStore(context)
        val project = fixture()
        try { store.import(Uri.fromFile(File(context.cacheDir, "missing.png"))); fail("Must fail") } catch (_: Exception) { }
        assertEquals(project.id, store.load()!!.id)
    }

    private fun canvas(project: Project): AnnotationCanvas = AnnotationCanvas(context).apply {
        layout(0, 0, 400, 800)
        update(project, 0, 0)
    }

    private fun touch(view: AnnotationCanvas, action: Int, vararg points: Point) {
        val properties = Array(points.size) { index -> MotionEvent.PointerProperties().apply { id = index; toolType = MotionEvent.TOOL_TYPE_FINGER } }
        val coords = Array(points.size) { index -> MotionEvent.PointerCoords().apply { x = points[index].x; y = points[index].y; pressure = 1f; size = 1f } }
        val event = MotionEvent.obtain(0, 10, action, points.size, properties, coords, 0, 0, 1f, 1f, 0, 0, android.view.InputDevice.SOURCE_TOUCHSCREEN, 0)
        view.onTouchEvent(event); event.recycle()
    }

    @Test fun secondFingerCancelsTentativeBrushAndRemainingFingerCannotDraw() {
        val view = canvas(fixture())
        var commits = 0
        view.onCommit = { commits++ }
        touch(view, MotionEvent.ACTION_DOWN, Point(100f, 200f))
        touch(view, MotionEvent.ACTION_MOVE, Point(130f, 230f))
        touch(view, MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), Point(130f, 230f), Point(230f, 330f))
        touch(view, MotionEvent.ACTION_MOVE, Point(100f, 200f), Point(260f, 360f))
        touch(view, MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), Point(100f, 200f), Point(260f, 360f))
        touch(view, MotionEvent.ACTION_MOVE, Point(140f, 240f))
        touch(view, MotionEvent.ACTION_UP, Point(140f, 240f))
        assertEquals(0, commits)
        touch(view, MotionEvent.ACTION_DOWN, Point(200f, 400f))
        touch(view, MotionEvent.ACTION_UP, Point(200f, 400f))
        assertEquals(1, commits)
    }

    @Test fun cancelledEraserDoesNotDestroyStroke() {
        val project = fixture().copy(drawing = Drawing(strokes = listOf(Stroke(listOf(Point(100f, 200f)), Color.RED, 10f))))
        val view = canvas(project).apply { tool = Tool.Eraser }
        var result = project.drawing
        view.onCommit = { result = it }
        touch(view, MotionEvent.ACTION_DOWN, Point(200f, 400f))
        touch(view, MotionEvent.ACTION_CANCEL, Point(200f, 400f))
        assertEquals(1, result.strokes.size)
        touch(view, MotionEvent.ACTION_DOWN, Point(200f, 400f))
        touch(view, MotionEvent.ACTION_UP, Point(200f, 400f))
        assertTrue(result.strokes.isEmpty())
    }

    @Test fun quarterTurnsKeepTouchCoordinatesAttachedToImage() {
        val project = fixture()
        val view = canvas(project).apply { tool = Tool.Note }
        var point: Point? = null
        view.onNote = { p, _ -> point = p }
        // Original top-left quadrant (50,100) maps differently at each rotation.
        val screenPoints = listOf(Point(108f, 216f), Point(292f, 354f), Point(292f, 584f), Point(108f, 446f))
        for (rotation in 0..3) {
            view.update(project, rotation, 0)
            // Exercise the same renderer path used by AndroidView.
            view.draw(Canvas(Bitmap.createBitmap(400, 800, Bitmap.Config.ARGB_8888)))
            touch(view, MotionEvent.ACTION_DOWN, screenPoints[rotation])
            touch(view, MotionEvent.ACTION_UP, screenPoints[rotation])
            assertEquals("rotation $rotation x", 50f, point!!.x, .01f)
            assertEquals("rotation $rotation y", 100f, point!!.y, .01f)
        }
    }
}
