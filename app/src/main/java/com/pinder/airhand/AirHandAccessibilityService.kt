package com.pinder.airhand

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import kotlin.math.abs

class AirHandAccessibilityService : AccessibilityService() {
    private lateinit var wm: WindowManager
    private lateinit var cursor: View
    private lateinit var params: WindowManager.LayoutParams
    private var cursorX = 0f
    private var cursorY = 0f

    // Continuous-drag stroke tracking (see dragMove/dragEnd).
    private var dragStroke: GestureDescription.StrokeDescription? = null
    private var dragLastX = 0f
    private var dragLastY = 0f

    override fun onServiceConnected() {
        super.onServiceConnected()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        cursor = View(this).apply {
            background = normalCursorDrawable()
        }
        val size = dp(28)
        params = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }
        cursor.visibility = View.INVISIBLE
        wm.addView(cursor, params)

        GestureBus.listener = { event -> cursor.post { handle(event) } }
    }

    private fun normalCursorDrawable() = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(Color.argb(210, 255, 255, 255))
        setStroke(4, Color.argb(230, 0, 0, 0))
    }

    private fun dragCursorDrawable() = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(Color.argb(230, 255, 196, 0))
        setStroke(5, Color.argb(230, 0, 0, 0))
    }

    private fun handle(event: GestureEvent) {
        val metrics = resources.displayMetrics
        val w = metrics.widthPixels.toFloat()
        val h = metrics.heightPixels.toFloat()
        when (event) {
            is GestureEvent.Cursor -> {
                moveCursorTo(event.x, event.y, w, h)
                cursor.visibility = View.VISIBLE
            }
            is GestureEvent.Tap -> tap(event.x * w, event.y * h)
            is GestureEvent.DragStart -> {
                moveCursorTo(event.x, event.y, w, h)
                cursor.background = dragCursorDrawable()
                dragStart(cursorX, cursorY)
            }
            is GestureEvent.DragMove -> {
                moveCursorTo(event.x, event.y, w, h)
                dragMove(cursorX, cursorY)
            }
            is GestureEvent.DragEnd -> {
                moveCursorTo(event.x, event.y, w, h)
                dragEnd(cursorX, cursorY)
                cursor.background = normalCursorDrawable()
            }
            is GestureEvent.Scroll -> scroll(event.deltaY, w, h)
            is GestureEvent.Back -> runCatching { performGlobalAction(GLOBAL_ACTION_BACK) }
            is GestureEvent.Home -> runCatching { performGlobalAction(GLOBAL_ACTION_HOME) }
            is GestureEvent.Pause -> {
                cursor.visibility = if (event.paused) View.INVISIBLE else View.VISIBLE
                if (event.paused && dragStroke != null) dragEnd(cursorX, cursorY)
            }
        }
    }

    private fun moveCursorTo(nx: Float, ny: Float, w: Float, h: Float) {
        cursorX = nx.coerceIn(0f, 1f) * w
        cursorY = ny.coerceIn(0f, 1f) * h
        params.x = (cursorX - cursor.width / 2f).toInt()
        params.y = (cursorY - cursor.height / 2f).toInt()
        runCatching { wm.updateViewLayout(cursor, params) }
    }

    private fun tap(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 60)
        dispatchGestureSafely(GestureDescription.Builder().addStroke(stroke).build())
    }

    /** Starts a draggable stroke that later [dragMove]/[dragEnd] calls extend. */
    private fun dragStart(x: Float, y: Float) {
        dragLastX = x
        dragLastY = y
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, STROKE_SEGMENT_MS, true)
        dragStroke = stroke
        dispatchGestureSafely(GestureDescription.Builder().addStroke(stroke).build())
    }

    private fun dragMove(x: Float, y: Float) {
        val prev = dragStroke ?: return dragStart(x, y)
        val path = Path().apply { moveTo(dragLastX, dragLastY); lineTo(x, y) }
        val next = prev.continueStroke(path, 0L, STROKE_SEGMENT_MS, true)
        dragStroke = next
        dragLastX = x
        dragLastY = y
        dispatchGestureSafely(GestureDescription.Builder().addStroke(next).build())
    }

    private fun dragEnd(x: Float, y: Float) {
        val prev = dragStroke ?: return
        val path = Path().apply { moveTo(dragLastX, dragLastY); lineTo(x, y) }
        val next = prev.continueStroke(path, 0L, STROKE_SEGMENT_MS, false)
        dragStroke = null
        dispatchGestureSafely(GestureDescription.Builder().addStroke(next).build())
    }

    private fun scroll(deltaY: Float, w: Float, h: Float) {
        if (abs(deltaY) < 0.01f) return
        val x = cursorX.coerceIn(w * 0.15f, w * 0.85f)
        val startY = h * 0.55f
        // Fingers moving down -> content should move down, so swipe the screen upward, and vice versa.
        val distance = (h * 0.22f).coerceAtLeast(dp(120).toFloat())
        val endY = if (deltaY > 0) startY - distance else startY + distance
        val path = Path().apply { moveTo(x, startY); lineTo(x, endY) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 220)
        dispatchGestureSafely(GestureDescription.Builder().addStroke(stroke).build())
    }

    private fun dispatchGestureSafely(description: GestureDescription) {
        runCatching { dispatchGesture(description, null, null) }
            .onFailure { Log.w("AirHand", "dispatchGesture failed", it) }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onDestroy() {
        GestureBus.listener = null
        dragStroke = null
        if (::cursor.isInitialized) runCatching { wm.removeView(cursor) }
        super.onDestroy()
    }

    companion object {
        private const val STROKE_SEGMENT_MS = 50L
    }
}
