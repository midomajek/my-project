package com.autoclicker.multipoint

import android.view.View

/**
 * Represents an individual draggable click target on the screen.
 * Stores its unique ID, visual view, and screen coordinates.
 */
data class ClickPoint(
    val id: Int,
    val view: View,
    var screenX: Float = 0f,
    var screenY: Float = 0f
)
