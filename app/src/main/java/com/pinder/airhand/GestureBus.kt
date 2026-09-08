package com.pinder.airhand

sealed interface GestureEvent {
    data class Cursor(val x: Float, val y: Float) : GestureEvent
    data class Tap(val x: Float, val y: Float) : GestureEvent
    data class DragStart(val x: Float, val y: Float) : GestureEvent
    data class DragMove(val x: Float, val y: Float) : GestureEvent
    data class DragEnd(val x: Float, val y: Float) : GestureEvent
    data class Scroll(val deltaY: Float) : GestureEvent
    data class Pause(val paused: Boolean) : GestureEvent
    data object Back : GestureEvent
    data object Home : GestureEvent
}

object GestureBus {
    @Volatile var listener: ((GestureEvent) -> Unit)? = null
    fun publish(event: GestureEvent) { listener?.invoke(event) }
}
