package com.example.methode_channel_lifecycle

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.WindowManager
import android.view.LayoutInflater
import android.graphics.PixelFormat
import android.util.Log
import android.os.Handler
import android.os.Looper

class AppUsageService : AccessibilityService() {

    private var currentApp: String? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var runnable: Runnable? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("ACCESS", "Service Connected ✅ - Accessibility service is now RUNNING")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString()
            Log.d("ACCESS", "Window State Changed: $packageName")

            if (packageName != null && packageName != currentApp) {
                currentApp = packageName
                // Avoid monitoring our own app
                if (packageName != applicationContext.packageName) {
                    startTimer(packageName)
                } else {
                    Log.d("ACCESS", "Ignoring our own app: $packageName")
                    stopTimer()
                }
            }
        }
    }

    private fun stopTimer() {
        runnable?.let { mainHandler.removeCallbacks(it) }
        runnable = null
    }

    private fun startTimer(packageName: String) {
        stopTimer()

        Log.d("ACCESS", "Starting 1 min timer for $packageName")
        
        runnable = Runnable {
            Log.d("ACCESS", "60 seconds reached for $packageName ⏰")

            // Show overlay on UI thread
            mainHandler.post {
                showOverlay()
            }

            // Call Method Channel
            MainActivity.methodChannel?.let { channel ->
                Log.d("ACCESS", "Invoking showPopup on Method Channel...")
                mainHandler.post {
                    channel.invokeMethod("showPopup", packageName)
                }
            } ?: Log.e("ACCESS", "MainActivity.methodChannel is NULL! Is the app running?")
        }

        runnable?.let { mainHandler.postDelayed(it, 60_000) }
    }

    override fun onInterrupt() {
        Log.d("ACCESS", "Service Interrupted ❗")
    }

    private fun showOverlay() {
        try {
            val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            val view = LayoutInflater.from(this).inflate(R.layout.overlay_layout, null)

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )

            windowManager.addView(view, params)
            Log.d("ACCESS", "Overlay added successfully ✅")
        } catch (e: Exception) {
            Log.e("ACCESS", "Failed to show overlay: ${e.message}")
        }
    }
}