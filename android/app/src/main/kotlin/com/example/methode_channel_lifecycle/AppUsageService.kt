package com.example.methode_channel_lifecycle

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.util.Log

class AppUsageService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("ACCESS", "Service Connected ✅ - Accessibility service is now RUNNING")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString()
            Log.d("ACCESS", "Window State Changed: $packageName")
            // Redundant timer logic removed. Using BackgroundUsageService for monitoring.
        }
    }

    override fun onInterrupt() {
        Log.d("ACCESS", "Service Interrupted ❗")
    }
}