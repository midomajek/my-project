package com.autoclicker.multipoint

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.PointF
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Manages all floating overlay views:
 * 1. The floating control toolbar (Add, Remove, Play/Stop, Settings, Close)
 * 2. The draggable target points (Point 1, Point 2, Point 3...)
 */
class FloatingWidgetManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val clickPoints = CopyOnWriteArrayList<ClickPoint>()
    private var controlMenuView: View? = null
    private var pointCounter = 0

    private val overlayWindowType: Int
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

    /**
     * Show the main floating toolbar on the screen.
     */
    @SuppressLint("InflateParams")
    fun showControlMenu() {
        if (controlMenuView != null) return

        val inflater = LayoutInflater.from(context)
        val menu = inflater.inflate(R.layout.floating_control_menu, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayWindowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 30
            y = 200
        }

        setupMenuControls(menu)
        setupDraggable(menu, params)

        try {
            windowManager.addView(menu, params)
            controlMenuView = menu
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error showing floating menu: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupMenuControls(menu: View) {
        val btnPlay = menu.findViewById<ImageButton>(R.id.btn_play_pause)
        val btnAdd = menu.findViewById<ImageButton>(R.id.btn_add_point)
        val btnRemove = menu.findViewById<ImageButton>(R.id.btn_remove_point)
        val btnClose = menu.findViewById<ImageButton>(R.id.btn_close)

        btnPlay.setOnClickListener {
            val service = AutoClickerService.instance ?: return@setOnClickListener
            if (service.isClicking()) {
                service.stopClicking()
            } else {
                if (clickPoints.isEmpty()) {
                    Toast.makeText(context, "Please add at least one point first (+)", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                service.startClicking()
            }
        }

        btnAdd.setOnClickListener {
            addClickPoint()
        }

        btnRemove.setOnClickListener {
            removeLastClickPoint()
        }

        btnClose.setOnClickListener {
            AutoClickerService.instance?.stopClicking()
            removeAllViews()
        }
    }

    /**
     * Updates Play/Pause button icon based on running state.
     */
    fun updatePlayButtonState(isPlaying: Boolean) {
        controlMenuView?.findViewById<ImageButton>(R.id.btn_play_pause)?.let { btn ->
            btn.setImageResource(if (isPlaying) R.drawable.ic_stop else R.drawable.ic_play)
        }
    }

    /**
     * Adds a new draggable target point on the screen.
     */
    @SuppressLint("InflateParams")
    fun addClickPoint() {
        pointCounter++
        val pointId = pointCounter

        val inflater = LayoutInflater.from(context)
        val pointView = inflater.inflate(R.layout.floating_point_target, null)
        val tvNumber = pointView.findViewById<TextView>(R.id.tv_point_number)
        tvNumber.text = pointId.toString()

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayWindowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // Stagger position of each new point
            x = 200 + (pointId % 5) * 60
            y = 300 + (pointId % 5) * 80
        }

        val clickPoint = ClickPoint(
            id = pointId,
            view = pointView,
            screenX = params.x + 40f, // Center offset
            screenY = params.y + 40f
        )

        setupDraggablePoint(pointView, params, clickPoint)

        try {
            windowManager.addView(pointView, params)
            clickPoints.add(clickPoint)
            Toast.makeText(context, "Added Point #$pointId", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            pointCounter--
        }
    }

    /**
     * Removes the last added target point.
     */
    fun removeLastClickPoint() {
        if (clickPoints.isEmpty()) return
        val lastPoint = clickPoints.removeAt(clickPoints.size - 1)
        try {
            windowManager.removeView(lastPoint.view)
            Toast.makeText(context, "Removed Point #${lastPoint.id}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Returns the current screen coordinates (X, Y center) of all active points.
     */
    fun getPointCoordinates(): List<PointF> {
        return clickPoints.map { PointF(it.screenX, it.screenY) }
    }

    /**
     * Removes all floating views and cleans up.
     */
    fun removeAllViews() {
        for (point in clickPoints) {
            try {
                windowManager.removeView(point.view)
            } catch (_: Exception) {}
        }
        clickPoints.clear()
        pointCounter = 0

        controlMenuView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {}
            controlMenuView = null
        }
    }

    /**
     * Allows dragging views around the screen.
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun setupDraggable(view: View, params: WindowManager.LayoutParams) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(view, params)
                    true
                }
                else -> false
            }
        }
    }

    /**
     * Sets up touch handling for each draggable target point, updating center coordinates.
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun setupDraggablePoint(view: View, params: WindowManager.LayoutParams, clickPoint: ClickPoint) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(view, params)

                    // Target center position calculation
                    val centerX = params.x + (view.width / 2f).coerceAtLeast(40f)
                    val centerY = params.y + (view.height / 2f).coerceAtLeast(40f)
                    clickPoint.screenX = centerX
                    clickPoint.screenY = centerY
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val centerX = params.x + (view.width / 2f).coerceAtLeast(40f)
                    val centerY = params.y + (view.height / 2f).coerceAtLeast(40f)
                    clickPoint.screenX = centerX
                    clickPoint.screenY = centerY
                    true
                }
                else -> false
            }
        }
    }
}
