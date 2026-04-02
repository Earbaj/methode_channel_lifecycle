package com.example.methode_channel_lifecycle

import io.flutter.embedding.android.FlutterActivity
import android.os.Bundle
import android.util.Log
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.os.Process
import android.os.Build
import java.util.*

class MainActivity : FlutterActivity() {

    companion object {
        // Static MethodChannel to allow background services to invoke Flutter methods
        var methodChannel: MethodChannel? = null
    }

    private var timer: Timer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Start a local timer to check this app's usage while it's in the foreground
        startUsageTimer()
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        // Initialize the MethodChannel for communication with Flutter
        methodChannel = MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            "screen_time"
        )

        // Set up listeners for calls coming FROM Flutter
        methodChannel?.setMethodCallHandler { call, result ->
            when (call.method) {
                "checkUsagePermission" -> {
                    result.success(hasUsageStatsPermission())
                }
                "requestUsagePermission" -> {
                    // Open Android settings for Usage Access
                    startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    result.success(null)
                }
                "checkOverlayPermission" -> {
                    // Check if "Draw over other apps" is allowed
                    result.success(Settings.canDrawOverlays(this))
                }
                "requestOverlayPermission" -> {
                    // Open Android settings for Overlay permission
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                    startActivity(intent)
                    result.success(null)
                }
                "startBackgroundService" -> {
                    // Start the Foreground Service for background monitoring
                    val intent = Intent(this, BackgroundUsageService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(intent)
                    } else {
                        startService(intent)
                    }
                    result.success(true)
                }
                "stopBackgroundService" -> {
                    // Stop the monitoring service
                    val intent = Intent(this, BackgroundUsageService::class.java)
                    stopService(intent)
                    result.success(true)
                }
                "getUsageTime" -> {
                    // Return today's usage time for this app
                    result.success(getAppUsageTime())
                }
                else -> {
                    result.notImplemented()
                }
            }
        }

        Log.d("MAIN", "MethodChannel initialized with UsageStats support ✅")
    }

    // Step 1: Check if the user has granted the "Usage Access" permission
    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    // Step 2: Query UsageStatsManager to get today's foreground time for this app (packageName)
    private fun getAppUsageTime(): Long {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        
        // Set calendar to start of today (midnight)
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        
        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()

        // Query and aggregate stats for the current day
        val usageStats = usageStatsManager.queryAndAggregateUsageStats(startTime, endTime)
        val stats = usageStats[packageName]
        return stats?.totalTimeInForeground ?: 0L
    }

    private var inAppBaselineUsageMs: Long = 0
    private val inAppRecurringLimitSeconds = 120L // 2 minutes

    // Step 3: Run a periodic timer to check usage while the Activity is alive
    private fun startUsageTimer() {
        timer?.cancel()
        timer = Timer()
        timer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                if (hasUsageStatsPermission()) {
                    val currentUsageMs = getAppUsageTime()
                    
                    // Initialize baseline if not set
                    if (inAppBaselineUsageMs == 0L) {
                        inAppBaselineUsageMs = currentUsageMs
                        return
                    }

                    val relativeUsageSeconds = (currentUsageMs - inAppBaselineUsageMs) / 1000
                    Log.d("MAIN", "In-app usage session: $relativeUsageSeconds / $inAppRecurringLimitSeconds seconds")

                    // Trigger popup every 2 minutes of active use
                    if (relativeUsageSeconds >= inAppRecurringLimitSeconds) {
                        runOnUiThread {
                            methodChannel?.invokeMethod("showPopup", "Your App")
                            // Reset baseline for the next interval
                            inAppBaselineUsageMs = currentUsageMs
                        }
                    }
                }
            }
        }, 0, 5000) // Poll every 5 seconds
    }

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
    }
}