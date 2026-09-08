package com.pinder.airhand

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Turns one hand's 21 MediaPipe landmarks into [GestureEvent]s.
 *
 * Recognized shapes, checked in priority order each frame:
 *  1. Open palm (all 4 fingers up)              -> pause/resume toggle
 *  2. Fist held ~[HOLD_CONFIRM_MS]               -> Back (one-shot, cooldown)
 *  3. Thumbs-up held ~[HOLD_CONFIRM_MS]          -> Home (one-shot, cooldown)
 *  4. Index + middle up, moving vertically       -> Scroll
 *  5. Index up                                   -> Cursor move
 *     + thumb/index pinch: quick = Tap, held = DragStart/DragMove/DragEnd
 *
 * All thresholds are prototype defaults (see README "Prototype limitations")
 * and will likely need tuning for your hand size, distance from the camera,
 * and lighting.
 */
class GestureEngine {
    private var smoothX = 0.5f
    private var smoothY = 0.5f
    private var initialized = false

    private var paused = false
    private var lastScrollY: Float? = null

    // Pinch -> tap/drag state machine.
    private var pinchWasDown = false
    private var pinchDownAtMs = 0L
    private var dragging = false
    private var lastTapMs = 0L

    // Fist -> Back / Thumbs-up -> Home hold-to-confirm state.
    private var fistHoldStartMs = 0L
    private var thumbsUpHoldStartMs = 0L
    private var lastBackMs = 0L
    private var lastHomeMs = 0L

    fun process(hand: List<NormalizedLandmark>) {
        if (hand.size < 21) return

        val wrist = hand[0]
        val thumbTip = hand[4]
        val indexTip = hand[8]
        val indexPip = hand[6]
        val middleTip = hand[12]
        val middlePip = hand[10]
        val ringTip = hand[16]
        val ringPip = hand[14]
        val pinkyTip = hand[20]
        val pinkyPip = hand[18]
        val indexMcp = hand[5]
        val pinkyMcp = hand[17]

        val indexUp = indexTip.y() < indexPip.y()
        val middleUp = middleTip.y() < middlePip.y()
        val ringUp = ringTip.y() < ringPip.y()
        val pinkyUp = pinkyTip.y() < pinkyPip.y()
        val fingersCurled = !indexUp && !middleUp && !ringUp && !pinkyUp

        val palmWidth = distance(indexMcp, pinkyMcp).coerceAtLeast(0.04f)
        val pinchRatio = distance(thumbTip, indexTip) / palmWidth
        val pinchDown = pinchRatio < 0.42f

        val now = System.currentTimeMillis()

        // --- 1. Open palm: pause/resume everything else. ---
        val openPalm = indexUp && middleUp && ringUp && pinkyUp
        if (openPalm) {
            if (!paused) GestureBus.publish(GestureEvent.Pause(true))
            paused = true
            resetTransientState()
            return
        } else if (paused) {
            paused = false
            GestureBus.publish(GestureEvent.Pause(false))
        }

        // --- 2. Fist held briefly: Back. ---
        val isFistShape = fingersCurled && !pinchDown
        if (isFistShape) {
            if (fistHoldStartMs == 0L) fistHoldStartMs = now
            if (now - fistHoldStartMs > HOLD_CONFIRM_MS && now - lastBackMs > ACTION_COOLDOWN_MS) {
                GestureBus.publish(GestureEvent.Back)
                lastBackMs = now
            }
            thumbsUpHoldStartMs = 0L
            resetPinchAndScrollState()
            return
        } else {
            fistHoldStartMs = 0L
        }

        // --- 3. Thumb extended upward, other fingers curled, held briefly: Home. ---
        val thumbExtended = distance(thumbTip, indexMcp) / palmWidth > 0.9f
        val thumbAbovePalm = thumbTip.y() < indexMcp.y() && thumbTip.y() < wrist.y()
        val isThumbsUpShape = fingersCurled && thumbExtended && thumbAbovePalm
        if (isThumbsUpShape) {
            if (thumbsUpHoldStartMs == 0L) thumbsUpHoldStartMs = now
            if (now - thumbsUpHoldStartMs > HOLD_CONFIRM_MS && now - lastHomeMs > ACTION_COOLDOWN_MS) {
                GestureBus.publish(GestureEvent.Home)
                lastHomeMs = now
            }
            resetPinchAndScrollState()
            return
        } else {
            thumbsUpHoldStartMs = 0L
        }

        // --- 4. Index + middle raised, moving vertically: scroll. ---
        if (indexUp && middleUp && !ringUp && !pinkyUp) {
            val centerY = (indexTip.y() + middleTip.y()) / 2f
            val previous = lastScrollY
            if (previous != null) {
                val dy = centerY - previous
                if (abs(dy) > 0.012f) GestureBus.publish(GestureEvent.Scroll(dy))
            }
            lastScrollY = centerY
            resetPinchState()
            return
        }
        lastScrollY = null

        // --- 5. Cursor position (smoothed). Mirror X so motion feels natural. ---
        val targetX = 1f - indexTip.x()
        val targetY = indexTip.y()
        if (!initialized) {
            smoothX = targetX; smoothY = targetY; initialized = true
        } else {
            smoothX += CURSOR_SMOOTHING * (targetX - smoothX)
            smoothY += CURSOR_SMOOTHING * (targetY - smoothY)
        }
        if (indexUp) GestureBus.publish(GestureEvent.Cursor(smoothX, smoothY))

        // Pinch: quick pinch = tap (fires on release), held pinch = drag.
        if (pinchDown && !pinchWasDown) {
            pinchDownAtMs = now
        }
        if (pinchDown) {
            if (!dragging && now - pinchDownAtMs > DRAG_HOLD_MS) {
                dragging = true
                GestureBus.publish(GestureEvent.DragStart(smoothX, smoothY))
            } else if (dragging) {
                GestureBus.publish(GestureEvent.DragMove(smoothX, smoothY))
            }
        } else if (pinchWasDown) {
            if (dragging) {
                GestureBus.publish(GestureEvent.DragEnd(smoothX, smoothY))
                dragging = false
            } else if (now - lastTapMs > TAP_COOLDOWN_MS) {
                GestureBus.publish(GestureEvent.Tap(smoothX, smoothY))
                lastTapMs = now
            }
        }
        pinchWasDown = pinchDown
    }

    private fun resetTransientState() {
        resetPinchState()
        lastScrollY = null
        fistHoldStartMs = 0L
        thumbsUpHoldStartMs = 0L
    }

    private fun resetPinchAndScrollState() {
        resetPinchState()
        lastScrollY = null
    }

    /** Cleanly ends an in-progress drag before switching to another gesture shape. */
    private fun resetPinchState() {
        if (dragging) GestureBus.publish(GestureEvent.DragEnd(smoothX, smoothY))
        dragging = false
        pinchWasDown = false
    }

    private fun distance(a: NormalizedLandmark, b: NormalizedLandmark): Float =
        hypot(a.x() - b.x(), a.y() - b.y())

    companion object {
        private const val CURSOR_SMOOTHING = 0.28f
        private const val DRAG_HOLD_MS = 220L
        private const val TAP_COOLDOWN_MS = 300L
        private const val HOLD_CONFIRM_MS = 450L
        private const val ACTION_COOLDOWN_MS = 1200L
    }
}
