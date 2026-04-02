package com.example.methode_channel_lifecycle

import android.app.*
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.*
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.core.app.NotificationCompat
import java.util.*

/**
 * Foreground Service that monitors app usage in the background using UsageStatsManager.
 * It shows a native overlay when a time limit is reached for specific apps.
 */
class BackgroundUsageService : Service() {

    private var timer: Timer? = null
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    
    // Target apps to monitor (e.g., Facebook)
    private val targetPackages = listOf("com.facebook.katana", "com.facebook.lite")

    // Maps each package to its "baseline" usage count
    // When current - baseline >= limit, we trigger the popup and reset baseline
    private val baselineUsageMap = mutableMapOf<String, Long>()
    
    // Keeps track of the app currently in foreground to avoid frequent baseline resets
    private var lastObservedApp: String? = null
    
    // The recurring interval (2 minutes = 120 seconds)
    private val recurringLimitSeconds = 120L 
    
    // Tracks the exact time (in ms) when the user entered the current target app
    private var sessionStartTimeMs: Long = 0

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        createNotificationChannel()
        startForeground(1, createNotification("Monitoring: 2-minute blocker active"))
        startMonitoring()
        
        Log.d("BG_SERVICE", "Service Created - Real-time Session Tracker active ✅")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Real-time monitoring that tracks how long the user has been 
     * CONTINUOUSLY using a target app.
     */
    private fun startMonitoring() {
        timer = Timer()
        timer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                val currentApp = getForegroundApp()
                
                // Case 1: A target app (Facebook) is in the foreground
                if (currentApp != null && targetPackages.contains(currentApp)) {
                    val now = System.currentTimeMillis()
                    
                    // If this is a NEW session (just opened or switched to this app)
                    if (currentApp != lastObservedApp || sessionStartTimeMs == 0L) {
                        sessionStartTimeMs = now
                        lastObservedApp = currentApp
                        Log.d("BG_SERVICE", "Session started for $currentApp at $now")
                        return 
                    }

                    // Calculate real-time duration of the current session
                    val sessionDurationSeconds = (now - sessionStartTimeMs) / 1000
                    
                    Log.d("BG_SERVICE", "$currentApp live session: $sessionDurationSeconds / $recurringLimitSeconds sec")
                    
                    // If 2 minutes of CONTINUOUS usage has passed
                    if (sessionDurationSeconds >= recurringLimitSeconds) {
                        Handler(Looper.getMainLooper()).post {
                            showBlockOverlay(currentApp, sessionDurationSeconds)
                            // Reset session start time to allow another 2 minutes if they re-enter
                            sessionStartTimeMs = System.currentTimeMillis() 
                        }
                    }
                } else {
                    // Case 2: No target app is in foreground. 
                    // Reset session tracking so it starts over next time they enter Facebook.
                    if (currentApp != null && !targetPackages.contains(currentApp)) {
                        lastObservedApp = null
                        sessionStartTimeMs = 0L
                    }
                }
            }
        }, 0, 2000) // Poll every 2 seconds for high precision
    }

    /**
     * More robust way to get the current foreground app using queryUsageStats.
     */
    private fun getForegroundApp(): String? {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val time = System.currentTimeMillis()
        
        // Query stats for the last minute to find the most recently used app
        val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, time - 60000, time)
        
        if (stats == null || stats.isEmpty()) return null
        
        var recentApp: android.app.usage.UsageStats? = null
        for (usageStat in stats) {
            if (recentApp == null || usageStat.lastTimeUsed > recentApp.lastTimeUsed) {
                recentApp = usageStat
            }
        }
        
        return recentApp?.packageName
    }

    /**
     * Calculates the daily accumulated usage time for a given package.
     */
    private fun getAppUsageTime(packageName: String): Long {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        
        // Sum up all usage for this package starting from midnight today
        val usageStats = usageStatsManager.queryAndAggregateUsageStats(calendar.timeInMillis, System.currentTimeMillis())
        return usageStats[packageName]?.totalTimeInForeground ?: 0L
    }

    /**
     * Displays a full-screen WindowManager overlay that blocks interaction with the target app.
     * Calculated based on the REAL-TIME duration of the current app session.
     */
    private fun showBlockOverlay(appName: String, secondsUsed: Long) {
        if (overlayView != null) return // Prevent multiple overlays

        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        overlayView = inflater.inflate(R.layout.overlay_layout, null)

        // Customize overlay UI components
        val titleText = overlayView?.findViewById<TextView>(R.id.overlay_title)
        val minutes = secondsUsed / 60
        titleText?.text = "⏰ Time's Up!\nYou've been using $appName for $minutes min."
        
        val okButton = overlayView?.findViewById<Button>(R.id.overlay_button)
        okButton?.setOnClickListener {
            removeOverlay()
            exitToHome() // Redirect user to Home screen
        }

        // Configure WindowManager parameters for a full-screen "System Alert" overlay
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT, 
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        try {
            windowManager?.addView(overlayView, params)
            Log.d("BG_SERVICE", "Block overlay shown for $appName ($secondsUsed s) 🚫")
        } catch (e: Exception) {
            Log.e("BG_SERVICE", "Error showing overlay: ${e.message}")
        }
    }

    /**
     * Removes the overlay from the WindowManager.
     */
    private fun removeOverlay() {
        if (overlayView != null) {
            windowManager?.removeView(overlayView)
            overlayView = null
            Log.d("BG_SERVICE", "Overlay removed ✅")
        }
    }

    /**
     * Forces a redirect to the Home screen, effectively closing the current app.
     */
    private fun exitToHome() {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
        Log.d("BG_SERVICE", "Exiting to Home screen 🏠")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "UsageMonitor",
                "App Usage Monitor",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(content: String): Notification {
        return NotificationCompat.Builder(this, "UsageMonitor")
            .setContentTitle("Screen Time Tracker")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
        removeOverlay()
    }
}
