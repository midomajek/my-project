package com.autoclicker.multipoint

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * Main Activity of the Multi-Point Simultaneous Auto Clicker.
 * Handles permission checks:
 * 1. Overlay Permission (SYSTEM_ALERT_WINDOW)
 * 2. Accessibility Service Permission (BIND_ACCESSIBILITY_SERVICE)
 */
class MainActivity : AppCompatActivity() {

    private lateinit var tvOverlayStatus: TextView
    private lateinit var tvAccessibilityStatus: TextView
    private lateinit var btnGrantOverlay: Button
    private lateinit var btnGrantAccessibility: Button
    private lateinit var btnStartFloatingMenu: Button
    private lateinit var etInterval: EditText
    private lateinit var etDuration: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvOverlayStatus = findViewById(R.id.tv_overlay_status)
        tvAccessibilityStatus = findViewById(R.id.tv_accessibility_status)
        btnGrantOverlay = findViewById(R.id.btn_grant_overlay)
        btnGrantAccessibility = findViewById(R.id.btn_grant_accessibility)
        btnStartFloatingMenu = findViewById(R.id.btn_start_floating)
        etInterval = findViewById(R.id.et_interval)
        etDuration = findViewById(R.id.et_duration)

        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatuses()
    }

    private fun setupListeners() {
        btnGrantOverlay.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            } else {
                Toast.makeText(this, "Overlay permission not required on this Android version", Toast.LENGTH_SHORT).show()
            }
        }

        btnGrantAccessibility.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            Toast.makeText(
                this,
                "ابحث عن 'Multi-Point Auto Clicker' وقم بتفعيلها",
                Toast.LENGTH_LONG
            ).show()
        }

        btnStartFloatingMenu.setOnClickListener {
            if (!hasOverlayPermission()) {
                Toast.makeText(this, "يرجى منح إذن الظهور فوق التطبيقات أولاً", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!isAccessibilityServiceEnabled()) {
                Toast.makeText(this, "يرجى تفعيل خدمة إمكانية الوصول أولاً", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Apply user config
            val interval = etInterval.text.toString().toLongOrNull() ?: 1000L
            val duration = etDuration.text.toString().toLongOrNull() ?: 50L
            AutoClickerService.instance?.clickIntervalMs = interval.coerceAtLeast(100L)
            AutoClickerService.instance?.clickDurationMs = duration.coerceIn(10L, 500L)

            // Minimize app and show floating widget
            moveTaskToBack(true)
            Toast.makeText(this, "تم تفعيل القائمة العائمة بنجاح!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updatePermissionStatuses() {
        val hasOverlay = hasOverlayPermission()
        val hasAccessibility = isAccessibilityServiceEnabled()

        tvOverlayStatus.text = if (hasOverlay) "ممنوح (Granted) ✓" else "مطلوب (Required) ✗"
        tvOverlayStatus.setTextColor(if (hasOverlay) 0xFF4CAF50.toInt() else 0xFFF44336.toInt())
        btnGrantOverlay.visibility = if (hasOverlay) View.GONE else View.VISIBLE

        tvAccessibilityStatus.text = if (hasAccessibility) "مفعل (Active) ✓" else "معطل (Inactive) ✗"
        tvAccessibilityStatus.setTextColor(if (hasAccessibility) 0xFF4CAF50.toInt() else 0xFFF44336.toInt())
        btnGrantAccessibility.visibility = if (hasAccessibility) View.GONE else View.VISIBLE

        btnStartFloatingMenu.isEnabled = hasOverlay && hasAccessibility
    }

    private fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        return AutoClickerService.isServiceRunning
    }
}
