package com.hanenashi.dauba

import kotlin.math.hypot

data class Point(val x: Float, val y: Float)
data class Stroke(val points: List<Point>, val color: Int, val width: Float)
data class Note(val id: Int, val point: Point, val text: String) {
    val label: String get() = anchorLabel(id)
}
data class Drawing(val strokes: List<Stroke> = emptyList(), val notes: List<Note> = emptyList(), val nextNoteId: Int = 0)

/** Coordinates and widths are in original image pixels, independent of the viewport. */
class History(initial: Drawing = Drawing()) {
    var current: Drawing = initial
        private set
    private val past = ArrayDeque<Drawing>()
    private val future = ArrayDeque<Drawing>()
    val canUndo get() = past.isNotEmpty()
    val canRedo get() = future.isNotEmpty()
    fun commit(next: Drawing) {
        if (next == current) return
        past.addLast(current)
        if (past.size > 100) past.removeFirst()
        current = next
        future.clear()
    }
    fun undo() {
        if (past.isEmpty()) return
        future.addLast(current)
        current = past.removeLast()
    }
    fun redo() {
        if (future.isEmpty()) return
        past.addLast(current)
        current = future.removeLast()
    }
}

fun anchorLabel(id: Int): String {
    var n = id + 1
    var result = ""
    while (n > 0) {
        n--
        result = ('A' + n % 26) + result
        n /= 26
    }
    return result
}

fun distanceToSegment(p: Point, a: Point, b: Point): Float {
    val dx = b.x - a.x
    val dy = b.y - a.y
    val length = dx * dx + dy * dy
    if (length == 0f) return hypot(p.x - a.x, p.y - a.y)
    val t = (((p.x - a.x) * dx + (p.y - a.y) * dy) / length).coerceIn(0f, 1f)
    return hypot(p.x - (a.x + t * dx), p.y - (a.y + t * dy))
}

fun Stroke.hit(point: Point, radius: Float): Boolean = when (points.size) {
    0 -> false
    1 -> distanceToSegment(point, points[0], points[0]) <= radius + width / 2
    else -> points.zipWithNext().any { (a, b) -> distanceToSegment(point, a, b) <= radius + width / 2 }
}

fun markdown(name: String, width: Int, height: Int, drawing: Drawing): String = buildString {
    appendLine("# $name")
    appendLine()
    appendLine("Original: screen.png ($width × $height pixels).")
    appendLine("Markup: screen-annotated.png. Match lettered anchors to the notes below.")
    appendLine("Both images use the original orientation; canvas rotation is for viewing only.")
    appendLine()
    if (drawing.notes.isEmpty()) appendLine("No text notes. See the drawn markup for requested changes.")
    drawing.notes.forEach { note ->
        appendLine("## ${note.label}")
        appendLine()
        appendLine(note.text.trim())
        appendLine()
    }
}
