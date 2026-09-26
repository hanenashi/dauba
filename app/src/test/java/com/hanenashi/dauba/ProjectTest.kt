package com.hanenashi.dauba

import org.junit.Assert.*
import org.junit.Test

class ProjectTest {
    @Test fun undoRedoRestoresNotesAndStrokesTogether() {
        val history = History()
        val marked = Drawing(strokes = listOf(Stroke(listOf(Point(10f, 20f)), -65536, 6f)))
        history.commit(marked)
        val noted = marked.copy(notes = listOf(Note(0, Point(50f, 60f), "Less padding")), nextNoteId = 1)
        history.commit(noted)
        history.undo()
        assertEquals(marked, history.current)
        history.undo()
        assertEquals(Drawing(), history.current)
        history.redo(); history.redo()
        assertEquals(noted, history.current)
    }

    @Test fun changingHistoryAfterUndoDiscardsRedo() {
        val history = History()
        history.commit(Drawing(nextNoteId = 1))
        history.undo()
        history.commit(Drawing(nextNoteId = 2))
        assertFalse(history.canRedo)
        history.redo()
        assertEquals(2, history.current.nextNoteId)
    }

    @Test fun lettersRemainReadableBeyondZ() {
        assertEquals("A", anchorLabel(0)); assertEquals("Z", anchorLabel(25))
        assertEquals("AA", anchorLabel(26)); assertEquals("AZ", anchorLabel(51))
        assertEquals("BA", anchorLabel(52))
    }

    @Test fun eraserHitsBetweenRecordedSamplesAndRespectsWidth() {
        val stroke = Stroke(listOf(Point(0f, 0f), Point(100f, 0f)), 0, 10f)
        assertTrue(stroke.hit(Point(50f, 7f), 3f))
        assertFalse(stroke.hit(Point(50f, 9f), 3f))
        assertTrue(Stroke(listOf(Point(2f, 2f)), 0, 10f).hit(Point(2f, 3f), 0f))
    }

    @Test fun markdownRetainsMultilineUnicodeAndStableLabels() {
        val text = markdown("screen.png", 1080, 2400, Drawing(notes = listOf(Note(2, Point(5f, 6f), "Reduce padding.\n日本語のコメント"))))
        assertTrue(text.contains("## C\n\nReduce padding.\n日本語のコメント"))
        assertTrue(text.contains("1080 × 2400"))
        assertTrue(text.contains("original orientation"))
    }
}
