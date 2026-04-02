# Flutter Screen Time Tracker & App Blocker

A robust Android application built with Flutter and Kotlin that monitors app usage in the background and blocks specific applications (like Facebook) once a time limit is reached.

## 🚀 Key Features
- **Real-Time Monitoring**: Tracks the exact duration of the current app session with high precision.
- **Native App Blocking**: Shows a full-screen "System Alert" overlay that prevents interaction with the blocked app.
- **Recurring Intervals**: Triggers every 2 minutes of active use, ensuring a fair but firm limit.
- **Background Service**: Uses an Android Foreground Service to keep tracking alive even when the main app is closed.
- **Smart Detection**: Uses `UsageStatsManager` and `queryUsageStats` for reliable foreground app detection.

---

## 🛠 How It Works
The project uses three core Android APIs:
1. **`UsageStatsManager`**: To detect which app is currently in the foreground and query historical usage data.
2. **`WindowManager`**: To draw a native overlay *over* other applications (like Facebook) when the limit is reached.
3. **`Foreground Service`**: To ensure the monitoring logic continues to run in the background with a persistent notification.

---

## 📦 Installation & Setup

### 1. Requirements
- Flutter SDK
- Android SDK (API 26+)
- Physical Android Device (recommended for testing background services)

### 2. How to Setup Permissions on Your Phone
For the app to work, you MUST manually enable these settings on your Android device:

#### **A. Usage Access (To Track Other Apps)**
1. Open **Settings** on your phone.
2. Go to **Apps** (or **Privacy** on some devices).
3. Tap **Special App Access**.
4. Tap **Usage Access**.
5. Find this app in the list and toggle **Permit usage access** to **ON**.

#### **B. Display Over Other Apps (To Show the Blocker)**
1. Open **Settings**.
2. Go to **Apps** -> **Special App Access**.
3. Tap **Display over other apps** (or **Appear on top**).
4. Find this app and toggle **Allow display over other apps** to **ON**.

#### **C. Battery Optimization (To Prevent App from Being Killed)**
1. Open **Settings** -> **Apps**.
2. Find this app and tap on it.
3. Tap **Battery**.
4. Select **Unrestricted** (this ensures the background service stays alive).

#### **D. Auto-start (MIUI/Xiaomi Only)**
1. Long press the app icon on your home screen and tap **App info**.
2. Toggle **Auto-start** to **ON**.
3. Under **Battery saver**, select **No restrictions**.

---

## 📝 Detailed Step-by-Step Guide (From Scratch)

Follow these steps in the exact order to build this feature in any Flutter project:

### Step 1: Configure Android Permissions
**File:** `android/app/src/main/AndroidManifest.xml`

Inside `<manifest>`, add:
```xml
<uses-permission android:name="android.permission.PACKAGE_USAGE_STATS" tools:ignore="ProtectedPermissions"/>
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE"/>
```

Inside `<application>`, register the tracking service:
```xml
<service android:name=".BackgroundUsageService"
    android:foregroundServiceType="specialUse"
    android:exported="false"/>
```

### Step 2: Create the Blocker UI (XML)
**File:** `android/app/src/main/res/layout/overlay_layout.xml`

This layout will appear *over* other apps like Facebook.
```xml
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="#E6000000"
    android:gravity="center"
    android:orientation="vertical"
    android:padding="20dp">

    <TextView
        android:id="@+id/overlay_title"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="⏰ Time's Up!"
        android:textColor="#FFFFFF"
        android:textSize="24sp"
        android:textStyle="bold" />

    <Button
        android:id="@+id/overlay_button"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginTop="20dp"
        android:text="OK, Exit App" />
</LinearLayout>
```

### Step 3: Implement the Background Service (Kotlin)
**File:** `android/app/src/main/kotlin/.../BackgroundUsageService.kt`

This is the "brain" of the app. It tracks session time and shows the overlay.
```kotlin
class BackgroundUsageService : Service() {
    private var sessionStartTimeMs: Long = 0
    private val limitSeconds = 120L // 2 minutes

    private fun startMonitoring() {
        Timer().scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                val currentApp = getForegroundApp()
                if (isTargetApp(currentApp)) {
                    if (sessionStartTimeMs == 0L) sessionStartTimeMs = System.currentTimeMillis()
                    val duration = (System.currentTimeMillis() - sessionStartTimeMs) / 1000
                    if (duration >= limitSeconds) {
                        showOverlay()
                    }
                } else {
                    sessionStartTimeMs = 0L
                }
            }
        }, 0, 2000)
    }
}
```

### Step 4: Configure the Method Channel (Full Code)
**File:** `android/app/src/main/kotlin/.../MainActivity.kt`

```kotlin
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
        var methodChannel: MethodChannel? = null
    }

    private var timer: Timer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startUsageTimer()
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        methodChannel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "screen_time")

        methodChannel?.setMethodCallHandler { call, result ->
            when (call.method) {
                "checkUsagePermission" -> result.success(hasUsageStatsPermission())
                "requestUsagePermission" -> {
                    startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    result.success(null)
                }
                "checkOverlayPermission" -> result.success(Settings.canDrawOverlays(this))
                "requestOverlayPermission" -> {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                    result.success(null)
                }
                "startBackgroundService" -> {
                    val intent = Intent(this, BackgroundUsageService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(intent)
                    } else {
                        startService(intent)
                    }
                    result.success(true)
                }
                "stopBackgroundService" -> {
                    stopService(Intent(this, BackgroundUsageService::class.java))
                    result.success(true)
                }
                "getUsageTime" -> result.success(getAppUsageTime())
                else -> result.notImplemented()
            }
        }
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun getAppUsageTime(): Long {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }
        val usageStats = usageStatsManager.queryAndAggregateUsageStats(calendar.timeInMillis, System.currentTimeMillis())
        return usageStats[packageName]?.totalTimeInForeground ?: 0L
    }

    private var inAppBaselineUsageMs: Long = 0
    private fun startUsageTimer() {
        timer?.cancel()
        timer = Timer()
        timer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                if (hasUsageStatsPermission()) {
                    val currentUsageMs = getAppUsageTime()
                    if (inAppBaselineUsageMs == 0L) {
                        inAppBaselineUsageMs = currentUsageMs
                        return
                    }
                    val relativeUsageSeconds = (currentUsageMs - inAppBaselineUsageMs) / 1000
                    if (relativeUsageSeconds >= 120) {
                        runOnUiThread {
                            methodChannel?.invokeMethod("showPopup", "Your App")
                            inAppBaselineUsageMs = currentUsageMs
                        }
                    }
                }
            }
        }, 0, 5000)
    }

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
    }
}
```

### Step 5: Implement the Tracker Service (Full Code)
**File:** `android/app/src/main/kotlin/.../BackgroundUsageService.kt`

```kotlin
package com.example.methode_channel_lifecycle

import android.app.*
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

class BackgroundUsageService : Service() {

    private var timer: Timer? = null
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private val targetPackages = listOf("com.facebook.katana", "com.facebook.lite")
    private var lastObservedApp: String? = null
    private var sessionStartTimeMs: Long = 0
    private val recurringLimitSeconds = 120L 

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(1, createNotification("Monitoring: 2-minute blocker active"))
        startMonitoring()
    }

    private fun startMonitoring() {
        timer = Timer()
        timer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                val currentApp = getForegroundApp()
                if (currentApp != null && targetPackages.contains(currentApp)) {
                    val now = System.currentTimeMillis()
                    if (currentApp != lastObservedApp || sessionStartTimeMs == 0L) {
                        sessionStartTimeMs = now
                        lastObservedApp = currentApp
                        return 
                    }
                    val duration = (now - sessionStartTimeMs) / 1000
                    if (duration >= recurringLimitSeconds) {
                        Handler(Looper.getMainLooper()).post {
                            showBlockOverlay(currentApp, duration)
                            sessionStartTimeMs = System.currentTimeMillis() 
                        }
                    }
                } else if (currentApp != null && !targetPackages.contains(currentApp)) {
                    lastObservedApp = null
                    sessionStartTimeMs = 0L
                }
            }
        }, 0, 2000)
    }

    private fun getForegroundApp(): String? {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val time = System.currentTimeMillis()
        val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, time - 60000, time)
        return stats?.maxByOrNull { it.lastTimeUsed }?.packageName
    }

    private fun showBlockOverlay(appName: String, secondsUsed: Long) {
        if (overlayView != null) return
        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        overlayView = inflater.inflate(R.layout.overlay_layout, null)
        overlayView?.findViewById<TextView>(R.id.overlay_title)?.text = "⏰ Time's Up!\nYou've been using $appName for ${secondsUsed/60} min."
        overlayView?.findViewById<Button>(R.id.overlay_button)?.setOnClickListener {
            removeOverlay()
            exitToHome()
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT, 
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        windowManager?.addView(overlayView, params)
    }

    private fun removeOverlay() {
        overlayView?.let { windowManager?.removeView(it); overlayView = null }
    }

    private fun exitToHome() {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("UsageMonitor", "App Usage Monitor", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
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
    
    override fun onBind(intent: Intent?): IBinder? = null
}
```

### Step 6: Create the Flutter Service Interface
**File:** `lib/screen_time_service.dart`

```dart
import 'package:flutter/services.dart';

class ScreenTimeService {
  static const MethodChannel _channel = MethodChannel('screen_time');

  static Future<bool> checkUsagePermission() async {
    return await _channel.invokeMethod('checkUsagePermission');
  }

  static Future<void> requestUsagePermission() async {
    await _channel.invokeMethod('requestUsagePermission');
  }

  static Future<bool> checkOverlayPermission() async {
    return await _channel.invokeMethod('checkOverlayPermission');
  }

  static Future<void> requestOverlayPermission() async {
    await _channel.invokeMethod('requestOverlayPermission');
  }

  static Future<void> startBackgroundService() async {
    await _channel.invokeMethod('startBackgroundService');
  }

  static Future<void> stopBackgroundService() async {
    await _channel.invokeMethod('stopBackgroundService');
  }
}
```

---

## 👨‍💻 How to Block Other Apps
To add more apps to the block list, update the `targetPackages` list in `BackgroundUsageService.kt`:
```kotlin
private val targetPackages = listOf(
    "com.facebook.katana", // Facebook
    "com.instagram.android", // Instagram
    "com.google.android.youtube", // YouTube
    "com.whatsapp" // WhatsApp
)
```

---

## 🚀 Summary
By following these steps, you create a system that runs forever in the background, watches what app is open, and draws a native "gate" over it when your custom rules are met!
