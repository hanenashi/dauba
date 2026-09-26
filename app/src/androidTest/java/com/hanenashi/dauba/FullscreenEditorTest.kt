package com.hanenashi.dauba

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assume.assumeTrue
import java.io.File
import java.util.UUID

/** Run on an emulator only: these tests seed their own project in private app storage. */
@RunWith(AndroidJUnit4::class)
class FullscreenEditorTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var store: ProjectStore

    @Before fun seedScreenshot() {
        assumeTrue("Never replace a user's project on a physical device", Build.FINGERPRINT.startsWith("generic") || Build.MODEL.contains("sdk_gphone"))
        store = ProjectStore(context)
        val bitmap = Bitmap.createBitmap(400, 800, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        Canvas(bitmap).drawRect(40f, 160f, 180f, 240f, android.graphics.Paint().apply { color = Color.BLUE })
        val project = Project(UUID.randomUUID().toString(), "fullscreen-fixture.png", bitmap, Drawing())
        File(context.filesDir, "project/${project.id}.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        store.save(project)
    }

    private fun View.findCanvas(): AnnotationCanvas? {
        if (this is AnnotationCanvas) return this
        if (this is ViewGroup) for (i in 0 until childCount) getChildAt(i).findCanvas()?.let { return it }
        return null
    }

    private fun waitForEditor() {
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Open tools").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
    }

    private fun waitForSavedDrawing(scenario: ActivityScenario<MainActivity>, predicate: (Drawing) -> Boolean) {
        // AtomicFile.openRead is a recovery operation, not a safe polling API during a write.
        // Wait on the editor, then read the completed disk snapshot once.
        compose.waitUntil(5_000) {
            var saved = false
            scenario.onActivity { activity ->
                val model = ViewModelProvider(activity)[EditorModel::class.java]
                saved = model.saveStatus == "Saved on this device" && model.project?.drawing?.let(predicate) == true
            }
            saved
        }
        assertTrue(predicate(store.load()!!.drawing))
    }

    @Test fun fullscreenTrayDoesNotReframeCanvasAndFirstStrokeSurvivesDismissal() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForEditor()
            var before: Bitmap? = null
            var bounds: List<Int>? = null
            val events = mutableListOf<String>()
            scenario.onActivity { activity ->
                val view = activity.window.decorView.findCanvas()!!
                view.setOnTouchListener { _, event ->
                    events.add("${event.actionMasked}: ${view.width}x${view.height}, tool=${view.tool}")
                    false
                }
                assertEquals(activity.window.decorView.width, view.width)
                assertEquals(activity.window.decorView.height, view.height)
                val location = IntArray(2); view.getLocationOnScreen(location)
                bounds = listOf(location[0], location[1], view.width, view.height)
                before = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
            }
            compose.onNodeWithContentDescription("Open tools").performClick()
            compose.onNodeWithContentDescription("Hide tools").assertExists()
            scenario.onActivity { activity ->
                val view = activity.window.decorView.findCanvas()!!
                val location = IntArray(2); view.getLocationOnScreen(location)
                assertEquals(bounds, listOf(location[0], location[1], view.width, view.height))
                val after = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
                assertTrue("Tool tray must not move or rescale the image", before!!.sameAs(after))
            }
            // Keep a finger down across recomposition; all controls disappear without losing the stroke.
            compose.onNodeWithTag("annotation-canvas").performTouchInput { down(Offset(width * .35f, height * .3f)) }
            compose.onNodeWithContentDescription("Hide tools").assertDoesNotExist()
            compose.onNodeWithContentDescription("Open tools").assertDoesNotExist()
            compose.onNodeWithTag("annotation-canvas").performTouchInput {
                moveTo(Offset(width * .6f, height * .4f)); up()
            }
            try {
                waitForSavedDrawing(scenario) { it.strokes.size == 1 }
            } catch (failure: Throwable) { throw AssertionError("Stroke was lost; events=$events", failure) }
            compose.onNodeWithContentDescription("Open tools").assertExists().performClick()
            compose.onNodeWithContentDescription("Undo").performClick()
            waitForSavedDrawing(scenario) { it.strokes.isEmpty() }
            scenario.onActivity { activity ->
                assertFalse(ViewCompat.getRootWindowInsets(activity.window.decorView)!!.isVisible(WindowInsetsCompat.Type.statusBars()))
                assertFalse(ViewCompat.getRootWindowInsets(activity.window.decorView)!!.isVisible(WindowInsetsCompat.Type.navigationBars()))
            }
        }
    }

    @Test fun noteKeyboardMenuRotationAndExportRemainReachable() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForEditor()
            compose.onNodeWithContentDescription("Open tools").performClick()
            compose.onNodeWithText("Note").performClick()
            compose.onNodeWithTag("annotation-canvas").performTouchInput { click(Offset(width * .5f, height * .35f)) }
            compose.onNodeWithText("Note A").assertExists()
            compose.onNode(hasSetTextAction()).performTextInput("Less padding here")
            compose.onNodeWithText("Save note").performClick()
            waitForSavedDrawing(scenario) { it.notes.size == 1 }
            compose.onNodeWithContentDescription("Open tools").performClick()
            compose.onNodeWithContentDescription("More actions").performClick()
            compose.onNodeWithText("Rotate 90° clockwise").performScrollTo().performClick()
            compose.onNodeWithContentDescription("Open tools").performClick()
            compose.onNodeWithContentDescription("More actions").performClick()
            compose.onNodeWithText("Notes (1)").performClick()
            compose.onNodeWithText("Less padding here").performClick()
            compose.onNodeWithText("Note A").assertExists()
            compose.onNodeWithText("Cancel").performClick()
            compose.onNodeWithContentDescription("Open tools").performClick()
            compose.onNodeWithContentDescription("More actions").performClick()
            compose.onNodeWithText("Export packet").performClick()
            compose.onNodeWithText("Save ZIP").assertExists()
            compose.onNodeWithText("Share ZIP").assertExists()
        }
    }
}
