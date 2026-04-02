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

### 2. Permissions
The app requires three critical Android permissions:
- **Usage Access**: `PACKAGE_USAGE_STATS` (To track other apps).
- **Display over other apps**: `SYSTEM_ALERT_WINDOW` (To show the blocker).
- **Foreground Service**: `FOREGROUND_SERVICE` (To run in the background).

---

## 📝 Step-by-Step Creation Guide (From Scratch)

Follow these steps to recreate this project or add these features to your own app:

### Step 1: Android Manifest Setup
Add these permissions to your `android/app/src/main/AndroidManifest.xml`:
```xml
<uses-permission android:name="android.permission.PACKAGE_USAGE_STATS" tools:ignore="ProtectedPermissions"/>
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE"/>
```
And register the service inside the `<application>` tag:
```xml
<service android:name=".BackgroundUsageService"
    android:foregroundServiceType="specialUse"
    android:exported="false"/>
```

### Step 2: Create the Overlay Layout
Create `android/app/src/main/res/layout/overlay_layout.xml`. This is the UI users see when they are blocked. It should include an "Exit" button.

### Step 3: Implement the Background Service
Create `BackgroundUsageService.kt` in your Kotlin package. This service:
- Starts a `Timer` to poll the foreground app every 2 seconds.
- Uses `System.currentTimeMillis()` to track session duration.
- Calls `windowManager.addView(overlayView, params)` to show the blocker.
- Uses a `HOME` intent to exit the blocked app when the user clicks "OK".

### Step 4: Handle Permissions in Flutter
In your Dart code, use `MethodChannel` to:
- Check if `UsageStats` permission is granted.
- Open System Settings if permissions are missing.
- Trigger the "Start/Stop Service" commands on the native side.

### Step 5: Blocking More Apps
To block other apps, simply add their package names to the `targetPackages` list in `BackgroundUsageService.kt`:
```kotlin
private val targetPackages = listOf(
    "com.facebook.katana", 
    "com.instagram.android", 
    "com.twitter.android"
)
```

## ⚠️ Important Note for MIUI (Xiaomi) Users
On some devices (Xiaomi, Oppo, Vivo), you must manually enable **"Auto-start"** and set **"Battery Saver"** to **"No Restrictions"** for the background service to work reliably.

---

## 👨‍💻 Developed with Antigravity
This project demonstrates the power of Flutter MethodChannels and Android Native Services working in harmony.
