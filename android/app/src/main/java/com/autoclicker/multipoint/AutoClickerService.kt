package com.autoclicker.multipoint

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.PointF
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Core Accessibility Service responsible for dispatching synchronized multi-touch clicks.
 * In Android 7.0+ (API 24+), GestureDescription allows multiple strokes to be scheduled
 * with startTime = 0, executing true simultaneous multi-touch touches across the screen.
 */
class AutoClickerService : AccessibilityService() {

    companion object {
        private const val TAG = "AutoClickerService"
        var instance: AutoClickerService? = null
            private set

        val isServiceRunning: Boolean
            get() = instance != null
    }

    private var floatingWidgetManager: FloatingWidgetManager? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isClickingActive = false

    // Configurable click settings
    var clickIntervalMs: Long = 1000L   // Time between click waves (ms)
    var clickDurationMs: Long = 50L     // Duration of each touch stroke (ms)
    var repeatLimit: Int = 0            // 0 = Infinite, > 0 = repeat N times
    private var currentRepeatCount = 0

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "AutoClickerService connected")
        floatingWidgetManager = FloatingWidgetManager(this)
        floatingWidgetManager?.showControlMenu()
    }

    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) {
        // No event interception needed; we only perform gestures
    }

    override fun onInterrupt() {
        Log.w(TAG, "AutoClickerService interrupted")
        stopClicking()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopClicking()
        floatingWidgetManager?.removeAllViews()
        floatingWidgetManager = null
        instance = null
        Log.d(TAG, "AutoClickerService destroyed")
    }

    /**
     * Start the automated clicking loop.
     */
    fun startClicking() {
        if (isClickingActive) return
        val points = floatingWidgetManager?.getPointCoordinates() ?: emptyList()
        if (points.isEmpty()) {
            Log.w(TAG, "No click points registered.")
            return
        }

        isClickingActive = true
        currentRepeatCount = 0
        scheduleNextWave()
        floatingWidgetManager?.updatePlayButtonState(true)
    }

    /**
     * Stop the automated clicking loop.
     */
    fun stopClicking() {
        isClickingActive = false
        mainHandler.removeCallbacksAndMessages(null)
        floatingWidgetManager?.updatePlayButtonState(false)
    }

    fun isClicking(): Boolean = isClickingActive

    private fun scheduleNextWave() {
        if (!isClickingActive) return

        if (repeatLimit in 1..currentRepeatCount) {
            stopClicking()
            return
        }

        val points = floatingWidgetManager?.getPointCoordinates() ?: emptyList()
        if (points.isNotEmpty()) {
            dispatchSimultaneousMultiTouch(points)
        }

        currentRepeatCount++
        mainHandler.postDelayed({
            if (isClickingActive) {
                scheduleNextWave()
            }
        }, clickIntervalMs)
    }

    /**
     * Dispatches simultaneous touches at all registered coordinates.
     * Every stroke starts at startTime = 0L, ensuring that Android dispatches
     * them concurrently as a multi-finger touch.
     */
    private fun dispatchSimultaneousMultiTouch(points: List<PointF>) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || points.isEmpty()) return

        val builder = GestureDescription.Builder()
        val duration = clickDurationMs.coerceAtLeast(10L)

        // Android typically supports up to 10 simultaneous strokes per gesture
        val activePoints = points.take(10)

        for (point in activePoints) {
            val path = Path().apply {
                moveTo(point.x, point.y)
            }
            // startTime = 0L is the key to simultaneous execution!
            val stroke = GestureDescription.StrokeDescription(path, 0L, duration)
            builder.addStroke(stroke)
        }

        try {
            val gesture = builder.build()
            dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    super.onCompleted(gestureDescription)
                    Log.v(TAG, "Simultaneous click completed on ${activePoints.size} points.")
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    super.onCancelled(gestureDescription)
                    Log.w(TAG, "Simultaneous click gesture cancelled.")
                }
            }, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error dispatching simultaneous gesture: ${e.message}", e)
        }
    }
}
