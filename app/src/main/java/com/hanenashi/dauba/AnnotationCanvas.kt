package com.hanenashi.dauba

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot
import kotlin.math.min

enum class Tool { Brush, Eraser, Note, Hand }

object AnnotationRenderer {
    fun anchorRadius(width: Int): Float = (width * 0.022f).coerceAtLeast(12f)

    fun draw(canvas: Canvas, drawing: Drawing, imageWidth: Int, draft: Stroke? = null) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        (drawing.strokes + listOfNotNull(draft)).forEach { stroke ->
            if (stroke.points.isEmpty()) return@forEach
            paint.color = stroke.color
            paint.strokeWidth = stroke.width
            if (stroke.points.size == 1) {
                paint.style = Paint.Style.FILL
                canvas.drawCircle(stroke.points[0].x, stroke.points[0].y, stroke.width / 2, paint)
                paint.style = Paint.Style.STROKE
            } else {
                val path = Path().apply {
                    moveTo(stroke.points.first().x, stroke.points.first().y)
                    for (i in 1 until stroke.points.size) lineTo(stroke.points[i].x, stroke.points[i].y)
                }
                canvas.drawPath(path, paint)
            }
        }
        val radius = anchorRadius(imageWidth)
        drawing.notes.forEach { note ->
            paint.style = Paint.Style.FILL
            paint.color = Color.WHITE
            canvas.drawCircle(note.point.x, note.point.y, radius + radius * 0.13f, paint)
            paint.color = Color.rgb(27, 31, 32)
            canvas.drawCircle(note.point.x, note.point.y, radius, paint)
            paint.color = Color.rgb(255, 146, 123)
            paint.textSize = radius * (if (note.label.length > 1) 0.95f else 1.25f)
            paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
            paint.textAlign = Paint.Align.CENTER
            val baseline = note.point.y - (paint.ascent() + paint.descent()) / 2
            canvas.drawText(note.label, note.point.x, baseline, paint)
        }
    }
}

/** The bitmap, strokes and anchors all share one image-to-view matrix. */
class AnnotationCanvas(context: Context) : View(context) {
    var onCommit: (Drawing) -> Unit = {}
    var onNote: (Point, Note?) -> Unit = { _, _ -> }
    var tool = Tool.Brush
    var brushColor = Color.rgb(255, 92, 91)
    var brushWidth = 7f
    private var bitmap: Bitmap? = null
    private var drawing = Drawing()
    private var projectId: String? = null
    private val transform = Matrix()
    private val inverse = Matrix()
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var zoom = 1f
    private var panX = 0f
    private var panY = 0f
    private var rotation = 0
    private var resetToken = 0
    private var draft = mutableListOf<Point>()
    private var erased = mutableSetOf<Int>()
    private var transforming = false
    private var active = false
    private var lastX = 0f
    private var lastY = 0f
    private var span = 0f
    private var downX = 0f
    private var downY = 0f
    private var moved = false

    init {
        contentDescription = "Screenshot annotation canvas. One finger uses the selected tool. Two fingers pan and zoom."
        isFocusable = true
    }

    fun update(project: Project, quarterTurns: Int, fitToken: Int) {
        val changed = projectId != project.id
        bitmap = project.bitmap
        drawing = project.drawing
        projectId = project.id
        if (changed || rotation != quarterTurns || resetToken != fitToken) {
            rotation = quarterTurns
            resetToken = fitToken
            resetView()
        }
        invalidate()
    }

    fun resetView() {
        zoom = 1f; panX = 0f; panY = 0f
        draft.clear(); erased.clear(); active = false
        updateMatrix(); invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { resetView() }

    private fun updateMatrix() {
        val b = bitmap ?: return
        val odd = rotation % 2 != 0
        val fit = min((width - 32f).coerceAtLeast(1f) / (if (odd) b.height else b.width),
            (height - 32f).coerceAtLeast(1f) / (if (odd) b.width else b.height))
        val scale = fit * zoom
        val cos = floatArrayOf(1f, 0f, -1f, 0f)[rotation]
        val sin = floatArrayOf(0f, 1f, 0f, -1f)[rotation]
        val a = cos * scale
        val c = -sin * scale
        val d = sin * scale
        val e = cos * scale
        transform.setValues(floatArrayOf(a, c, width / 2f + panX - a * b.width / 2f - c * b.height / 2f,
            d, e, height / 2f + panY - d * b.width / 2f - e * b.height / 2f, 0f, 0f, 1f))
        transform.invert(inverse)
    }

    private fun imagePoint(x: Float, y: Float): Point {
        val p = floatArrayOf(x, y)
        inverse.mapPoints(p)
        return Point(p[0], p[1])
    }

    @SuppressLint("DrawAllocation") // The tentative preview is small; committed strokes share immutable lists.
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.rgb(34, 38, 39))
        val b = bitmap ?: return
        updateMatrix()
        canvas.save()
        canvas.concat(transform)
        canvas.clipRect(0f, 0f, b.width.toFloat(), b.height.toFloat())
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(b, 0f, 0f, bitmapPaint)
        val visible = if (erased.isEmpty()) drawing else drawing.copy(strokes = drawing.strokes.filterIndexed { i, _ -> i !in erased })
        AnnotationRenderer.draw(canvas, visible, b.width, draft.takeIf { it.isNotEmpty() }?.let { Stroke(it, brushColor, brushWidth) })
        canvas.restore()
    }

    private fun mark(x: Float, y: Float) {
        val p = imagePoint(x, y)
        when (tool) {
            Tool.Brush -> if (draft.isEmpty() || hypot(p.x - draft.last().x, p.y - draft.last().y) > brushWidth / 5) draft.add(p)
            Tool.Eraser -> {
                val values = FloatArray(9)
                transform.getValues(values)
                val scale = hypot(values[0], values[3]).coerceAtLeast(0.001f)
                drawing.strokes.forEachIndexed { index, stroke ->
                    if (stroke.hit(p, 16 * resources.displayMetrics.density / scale)) erased.add(index)
                }
            }
            else -> Unit
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val b = bitmap ?: return false
        updateMatrix()
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                draft.clear(); erased.clear(); transforming = false; moved = false
                downX = event.x; downY = event.y; lastX = event.x; lastY = event.y
                val p = imagePoint(event.x, event.y)
                active = tool == Tool.Hand || (p.x in 0f..b.width.toFloat() && p.y in 0f..b.height.toFloat())
                if (active) mark(event.x, event.y)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                // Roll back the entire tentative draw/erase when a second finger joins.
                draft.clear(); erased.clear(); transforming = true; active = false
                lastX = (event.getX(0) + event.getX(1)) / 2
                lastY = (event.getY(0) + event.getY(1)) / 2
                span = hypot(event.getX(0) - event.getX(1), event.getY(0) - event.getY(1))
            }
            MotionEvent.ACTION_MOVE -> {
                if (hypot(event.x - downX, event.y - downY) > 12 * resources.displayMetrics.density) moved = true
                if (transforming && event.pointerCount >= 2) {
                    val x = (event.getX(0) + event.getX(1)) / 2
                    val y = (event.getY(0) + event.getY(1)) / 2
                    val nextSpan = hypot(event.getX(0) - event.getX(1), event.getY(0) - event.getY(1))
                    val focal = imagePoint(lastX, lastY)
                    if (span > 10) zoom = (zoom * nextSpan / span).coerceIn(0.5f, 12f)
                    updateMatrix()
                    val mapped = floatArrayOf(focal.x, focal.y)
                    transform.mapPoints(mapped)
                    panX += x - mapped[0]; panY += y - mapped[1]
                    lastX = x; lastY = y; span = nextSpan
                    updateMatrix()
                } else if (!transforming && active) {
                    if (tool == Tool.Hand) {
                        panX += event.x - lastX; panY += event.y - lastY
                        lastX = event.x; lastY = event.y
                    } else {
                        for (i in 0 until event.historySize) mark(event.getHistoricalX(i), event.getHistoricalY(i))
                        mark(event.x, event.y)
                    }
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                // Swallow all remaining fingers until a fresh gesture begins.
                active = false
                span = 0f
            }
            MotionEvent.ACTION_UP -> {
                if (!transforming && active) {
                    when (tool) {
                        Tool.Brush -> if (draft.isNotEmpty()) onCommit(drawing.copy(strokes = drawing.strokes + Stroke(draft.toList(), brushColor, brushWidth)))
                        Tool.Eraser -> if (erased.isNotEmpty()) onCommit(drawing.copy(strokes = drawing.strokes.filterIndexed { i, _ -> i !in erased }))
                        Tool.Note -> if (!moved) {
                            val p = imagePoint(event.x, event.y)
                            val radius = AnnotationRenderer.anchorRadius(b.width)
                            val existing = drawing.notes.lastOrNull { hypot(p.x - it.point.x, p.y - it.point.y) <= radius * 1.5f }
                            val marginX = (radius * 1.2f).coerceAtMost(b.width / 2f)
                            val marginY = (radius * 1.2f).coerceAtMost(b.height / 2f)
                            onNote(Point(p.x.coerceIn(marginX, b.width - marginX), p.y.coerceIn(marginY, b.height - marginY)), existing)
                        }
                        Tool.Hand -> Unit
                    }
                    performClick()
                }
                draft.clear(); erased.clear(); active = false
                parent?.requestDisallowInterceptTouchEvent(false)
            }
            MotionEvent.ACTION_CANCEL -> {
                draft.clear(); erased.clear(); active = false; transforming = false
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        invalidate()
        return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }
}
