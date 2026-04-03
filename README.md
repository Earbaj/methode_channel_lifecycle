# Flutter Screen Time Tracker & App Blocker

A robust Android application built with Flutter and Kotlin that monitors app usage in the background and blocks specific applications (like Facebook) once a time limit is reached.

---
### 📖 New to this?
Check out our [**Beginner's Guide**](file:///home/earbaj/StudioProjects/methode_channel_lifecycle/BEGINNER_GUIDE.md) for a step-by-step breakdown of how the whole project works and the logic behind it!
---

## 🚀 Key Features
- **Daily App Blocking**: Once a user hits the 2-minute limit today, the app is blocked for the rest of the day.
- **Native App Blocking**: Shows a full-screen "System Alert" overlay that prevents interaction with the blocked app.
- **Immediate Re-Blocking**: If an already-blocked app is reopened, the blocker appears immediately.
- **Auto-Dismiss Overlay**: The blocker overlay automatically disappears when the user exits the target app.
- **Manual Reset**: Includes a "Reset Usage" feature for testing or bypassing the limit for the rest of the day.
- **Background Service**: Uses an Android Foreground Service to keep tracking alive even when the main app is closed.
- **Smart Detection**: Uses `UsageStatsManager` and `UsageEvents` for robust foreground app detection and accurate timing.

---

## 🛠 How It Works
The project uses three core Android APIs:
1. **`UsageStatsManager`**: To query the total foreground time used for the current day.
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
        android:text="⏰ Daily Limit Reached!"
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

This service monitors the total daily usage.
```kotlin
class BackgroundUsageService : Service() {
    private val limitSeconds = 120L // 2 minute daily limit

    private fun startMonitoring() {
        Timer().scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                val currentApp = getForegroundApp()
                if (isTargetApp(currentApp)) {
                    val dailyUsageMs = getAppUsageTime(currentApp)
                    if (dailyUsageMs / 1000 >= limitSeconds) {
                        showOverlay() // Block immediately if daily limit reached
                    }
                }
            }
        }, 0, 3000)
    }
}
```

### Step 4: Configure the Method Channel (Full Code)
**File:** `android/app/src/main/kotlin/.../MainActivity.kt`

This file is the main entry point of our Android app. It handles requests from Flutter and runs a periodic timer to track daily usage of the tracker app itself.

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Foreground monitoring is handled by BackgroundUsageService
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
                "getUsageTime" -> {
                    val pkg = call.argument<String>("packageName") ?: "com.facebook.katana"
                    result.success(getAppUsageTime(pkg))
                }
                "resetUsage" -> {
                    BackgroundUsageService.resetUsage(this)
                    result.success(true)
                }
                else -> result.notImplemented()
            }
        }
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun getAppUsageTime(targetPackage: String): Long {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }
        val midnight = calendar.timeInMillis
        val manualReset = BackgroundUsageService.getManualResetTime(this)
        val startTime = Math.max(midnight, manualReset)

        val usageStats = usageStatsManager.queryAndAggregateUsageStats(startTime, System.currentTimeMillis())
        return usageStats[targetPackage]?.totalTimeInForeground ?: 0L
    }
}
```

#### 🔍 Methods & Purposes in MainActivity:
1. **`configureFlutterEngine`**: This is the mandatory setup function. It creates the **MethodChannel**, which acts as a "bridge" allowing Dart (Flutter) and Kotlin (Android) to talk to each other.
2. **`setMethodCallHandler`**: This is a "Listener". It waits for Flutter to send commands like "start the service" or "check permissions" and directs them to the right Kotlin functions.
3. **`hasUsageStatsPermission`**: A security check. It asks the Android system if the user has allowed this app to see the usage data of other apps.
4. **`getAppUsageTime`**: This function queries the `UsageStatsManager`. It looks at the phone's history from midnight today until now to see how many total milliseconds **this specific app** has been used.
5. **`startUsageTimer`**: A watchdog timer that runs every 5 seconds. It checks the daily usage and triggers the Flutter popup if the 2-minute limit is reached for the Tracker app itself.
6. **`isPopupTriggeredToday`**: A "Flag" (boolean). It ensures the "Limit Reached" dialog only shows once per day in the Flutter UI, preventing it from popping up every 5 seconds.

---

### Step 5: Implement the Tracker Service (Full Code)
**File:** `android/app/src/main/kotlin/.../BackgroundUsageService.kt`

This is the core "Engine" of the blocker. It runs as a Foreground Service, meaning it stays alive even if the user closes all apps.

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

    companion object {
        private const val PREFS_NAME = "ScreenTimePrefs"
        private const val KEY_RESET_TIME = "manual_reset_time"
        
        fun resetUsage(context: Context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putLong(KEY_RESET_TIME, System.currentTimeMillis()).apply()
        }
        
        fun getManualResetTime(context: Context): Long = 
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getLong(KEY_RESET_TIME, 0L)
    }

    private fun startMonitoring() {
        timer = Timer()
        timer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                val currentApp = getForegroundApp()
                if (currentApp != justDismissedApp && targetPackages.contains(currentApp)) {
                    val dailyUsageMs = getAppUsageTime(currentApp!!)
                    if (dailyUsageMs/1000 >= dailyLimitSeconds) {
                        showBlockOverlay(currentApp, dailyUsageMs/1000)
                    } else {
                        removeOverlay()
                    }
                } else if (currentApp != targetPackages.contains(currentApp)) {
                    removeOverlay()
                }
            }
        }, 0, 3000)
    }

    private fun getAppUsageTime(packageName: String): Long {
        val midnight = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }.timeInMillis
        val startTime = Math.max(midnight, getManualResetTime(this))
        // Logic to sum usage from UsageEvents...
        return totalTimeMs
    }
}
```

#### 🔍 Methods & Purposes in BackgroundUsageService:
1. **`onCreate`**: The entry point of the service. It sets up the notification (required for Foreground Services) and starts the monitoring timer.
2. **`startMonitoring`**: This is a "Loop". Every 3 seconds, it checks which app the user is looking at. If it's a target app (like Facebook), it checks the daily limit.
3. **`getForegroundApp`**: This is the "Spy". It looks at the most recent system events to identify exactly which package (app) is currently on the screen.
4. **`showBlockOverlay`**: The "Gatekeeper". It uses the Android **WindowManager** to draw a full-screen UI *over* the blocked app. This UI prevents the user from clicking anything in the restricted app.
5. **`exitToHome`**: The "Enforcer". When the user clicks "OK" on the blocker, this sends a system command to minimize everything and return to the Home screen.
6. **`createNotificationChannel`**: Required by Android 8.0+. It creates a category for our "Monitoring" notification so the user knows why the app is running in the background.

---

### Step 6: Create the Flutter Service Interface
**File:** `lib/screen_time_service.dart`

This Dart class is the "Remote Control" for our Android features. It allows Flutter to trigger Kotlin code.

```dart
import 'package:flutter/services.dart';

class ScreenTimeService {
  // Define the MethodChannel with a unique name matching the Android side
  static const MethodChannel _channel = MethodChannel('screen_time');

  /**
   * Initializes the MethodChannel handler to listen for messages from native code.
   */
  static void init(Function(String) onShowPopup) {
    _channel.setMethodCallHandler((call) async {
      if (call.method == "showPopup") {
        onShowPopup(call.arguments.toString());
      }
    });
  }

  static Future<bool> checkPermission() async {
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

  static Future<bool> startBackgroundService() async {
    return await _channel.invokeMethod('startBackgroundService');
  }

  static Future<bool> stopBackgroundService() async {
    return await _channel.invokeMethod('stopBackgroundService');
  }

  static Future<int> getUsageTime({String packageName = "com.facebook.katana"}) async {
    return await _channel.invokeMethod('getUsageTime', {'packageName': packageName});
  }

  static Future<bool> resetUsage() async {
    return await _channel.invokeMethod('resetUsage');
  }
}
```

#### 🔍 Methods & Purposes in ScreenTimeService:
1. **`MethodChannel`**: The Dart side of the bridge. It **must** have the exact same name (`'screen_time'`) as the one we defined in `MainActivity.kt`, otherwise they won't hear each other.
2. **`init`**: Sets up a "Listener" on the Flutter side. When Android sends the `showPopup` command, this function catches it and runs the provided callback to show the dialog.
3. **`_channel.invokeMethod`**: The "Messenger". It sends a string command (like `'startBackgroundService'`) to the Kotlin side and waits for a response (like `true` or `false`).
4. **`startBackgroundService`**: A simple helper that tells Android to launch our `BackgroundUsageService` so tracking can happen even if the Flutter UI is closed.
5. **`getUsageTime`**: Asks the Android side to return the current day's usage in milliseconds so we can display it on the Flutter screen.

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
By following these steps, you create a system that runs forever in the background, watches what app is open, and draws a native "gate" over it once the daily total usage limit is hit!
