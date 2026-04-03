# 📘 Ultra-Detailed Project Guide (Line-by-Line)

This guide explains **every single line of code** in your project. We've included the full source code for every file with detailed explanations in English and Bengali.

---

## 🏗 1. `AndroidManifest.xml` (The Permission Map)
এই ফাইলটি অ্যান্ড্রয়েডকে বলে আমাদের অ্যাপের কী কী "Permission" প্রয়োজন।

```xml
<manifest ...>
    <!-- 1. Usage stats permission allows reading app usage data -->
    <!-- অ্যাপ কতক্ষণ চলেছে তা জানার জন্য এই পারমিশনটি জরুরি -->
    <uses-permission android:name="android.permission.PACKAGE_USAGE_STATS" tools:ignore="ProtectedPermissions"/>

    <!-- 2. System Alert Window shows overlay over other apps -->
    <!-- অন্য অ্যাপের ওপর কোনো উইন্ডো দেখানোর জন্য এটি প্রয়োজন -->
    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW"/>

    <!-- 3. Foreground Service keeps the service alive in background -->
    <!-- ব্যাকগ্রাউন্ডে সার্ভিসটিকে সচল রাখার জন্য এটি প্রয়োজন -->
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>

    <application ...>
        <!-- 4. Registering the Background Service -->
        <!-- সার্ভিসটিকে এখানে রেজিস্টার করা হয় যাতে অ্যান্ড্রয়েড একে চিনতে পারে -->
        <service android:name=".BackgroundUsageService"
            android:foregroundServiceType="specialUse"
            android:exported="false"/>
    </application>
</manifest>
```

---

## ⚙️ 2. `BackgroundUsageService.kt` (The Heart)
এটিই আসল কাজ করে। এটি ব্যাকগ্রাউন্ডে সবসময় সচল থাকে এবং অ্যাপ মনিটর করে।

### **১. Companion Object (Memory & Persistence)**
```kotlin
companion object {
    private const val PREFS_NAME = "ScreenTimePrefs" // অ্যাপের মেমোরি ফাইলের নাম
    private const val KEY_RESET_TIME = "manual_reset_time" // ডাটা সেভ রাখার জন্য কি (Key)
    
    // resetUsage: এটি বর্তমান সময়টি মেমোরিতে সেভ করে রাখে যাতে পরে ক্যালকুলেশন সেখান থেকে শুরু করা যায়।
    fun resetUsage(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_RESET_TIME, System.currentTimeMillis()).apply()
    }
    
    // getManualResetTime: মেমোরি থেকে সেই সেভ করা সময়টি পড়ে নিয়ে আসে।
    fun getManualResetTime(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(KEY_RESET_TIME, 0L)
    }
}
```

### **২. variables & Initialization**
*   `timer`: এটি সময়মতো চেক করার জন্য ব্যবহার হয়।
*   `windowManager`: স্ক্রিনের ওপর ওভারলে দেখানোর জন্য অ্যান্ড্রয়েড সিস্টেম সার্ভিস।
*   `overlayView`: ব্লকার স্ক্রিনের ডিজাইন (XML) লোড রাখার জন্য ভেরিয়েবল।
*   `targetPackages`: এখানে আমরা সেই সব অ্যাপের নাম রাখি যাদের আমরা ব্লক করতে চাই (যেমন: Facebook)।
*   `dailyLimitSeconds`: কতক্ষণ পর ব্লক হবে (এখানে ১২০ সেকেন্ড বা ২ মিনিট)।
*   `justDismissedApp`: এটি মনে রাখে আপনি এখনই "OK" ক্লিক করেছেন কিনা, যাতে সাথে সাথে আবার ব্লকার না আসে।

### **৩. Lifecycle Methods (সার্ভিস কখন কী করবে)**
*   **`onCreate()`**: সার্ভিসটি চালু হওয়ার সাথে সাথে এটি কল হয়। এখানে `windowManager` সেট করা হয় এবং মনিটরিং শুরু হয়।
*   **`onStartCommand()`**: এটি `START_STICKY` রিটার্ন করে। এর মানে হলো, অ্যান্ড্রয়েড যদি কখনো মেমোরির অভাবে অ্যাপটি বন্ধ করে দেয়, তবে মেমোরি খালি হলেই অ্যাপটি আবার অটোমেটিক চালু হবে।
*   **`onBind()`**: এটি `null` রিটার্ন করে কারণ আমাদের এই সার্ভিসের সাথে অন্য কোনো অ্যাপ কানেক্ট করার প্রয়োজন নেই।
*   **`onDestroy()`**: সার্ভিস বন্ধ করার সময় এটি সব ক্লিনিং (Timer stop, Overlay remove) করে।

### **৪. Core Monitoring Logic (মনিটরিং করার আসল কোড)**
```kotlin
private fun startMonitoring() {
    timer = Timer()
    // প্রতি ৩ সেকেন্ডে একবার এই লুপটা চলবে
    timer?.scheduleAtFixedRate(object : TimerTask() {
        override fun run() {
            val currentApp = getForegroundApp() // বর্তমানে কোন অ্যাপ ইউজ হচ্ছে তা দেখে

            // জাস্ট রিসেট করা অ্যাপটি রিসেট করে যাতে আবার ঢোকা যায়
            if (currentApp != justDismissedApp) {
                justDismissedApp = null
            }
            
            // যদি এটি টার্গেট অ্যাপ হয় এবং আমরা এটাকে জাস্ট মাত্র বন্ধ না করে থাকি
            if (currentApp != null && targetPackages.contains(currentApp)) {
                if (currentApp == justDismissedApp) return

                val dailyUsageMs = getAppUsageTime(currentApp) // অ্যাপটি আজকের দিনে কতক্ষণ চলেছে তা বের করে
                val dailyUsageSeconds = dailyUsageMs / 1000 // সেকেন্ডে রূপান্তর
                
                // যদি ডেইলি লিমিট পার হয়ে যায় (১২০ সেকেন্ডের বেশি)
                if (dailyUsageSeconds >= dailyLimitSeconds) {
                    Handler(Looper.getMainLooper()).post {
                        showBlockOverlay(currentApp, dailyUsageSeconds) // ব্লকার স্ক্রিন দেখায়
                    }
                } else {
                    Handler(Looper.getMainLooper()).post { removeOverlay() } // লিমিট শেষ না হলে দেখায় না
                }
            } else {
                Handler(Looper.getMainLooper()).post { removeOverlay() } // অন্য অ্যাপে গেলে ব্লকার সরায়
            }
        }
    }, 0, 3000)
}
```

### **৫. Finding the Foreground App (বর্তমানে কোন অ্যাপ চলছে)**
*   **`getForegroundApp()`**: এটি ফোনের রিসেন্ট ইভেন্টগুলো দেখে। গত ১ মিনিটের মধ্যে সবচেয়ে শেষে ব্যবহৃত অ্যাপের নাম এটি পাঠায়। এটাই আমাদের অ্যাপের "চক্ষু"।

### **৬. Calculating Usage Time (ক্যালকুলেশন পদ্ধতি)**
*   **`getAppUsageTime()`**: এটি আজকের শুরু (Midnight) অথবা আপনার শেষ ক্লিক করা "Reset" বাটন এর সময় থেকে এখন পর্যন্ত অ্যাপটি কতক্ষণ চলেছে তা সেকেন্ড দর সেকেন্ড যোগ করে বের করে। এটি খুবই নিখুঁত।

### **৭. The Blocker UI (ব্লকার স্ক্রিন দেখানো)**
*   **`showBlockOverlay()`**: এটি ফোনের মেমোরি থেকে `overlay_layout.xml` ফাইলটি রিড করে এবং পুরো স্ক্রিন জুড়ে একটি কালো পর্দার মতো বিছিয়ে দেয়। এখানে "OK" বাটনের ভেতর `exitToHome()` ফাংশন থাকে যা ক্লিক করলে আপনাকে ফেসবুক থেকে বের করে ফোনের মেইন স্ক্রিনে নিয়ে যাবে।

---

## 🌉 3. `MainActivity.kt` (The Communication Hub)
ফ্লাটার আর অ্যান্ড্রয়েড এর মধ্যে কথা বলার মাধ্যম। এটি ফ্লাটার থেকে কমান্ড গ্রহণ করে এবং অ্যান্ড্রয়েড সিস্টেমের সাথে যোগাযোগ করে।

### **১. `onCreate()`**
এটি অ্যাপটি ওপেন করার সাথে সাথে কল হয়। আমরা এখানে বর্তমানে কোনো এক্সট্রা কাজ রাখিনি কারণ সব কাজ এখন ব্যাকগ্রাউন্ড সার্ভিস হ্যান্ডেল করে।

### **২. `configureFlutterEngine()` (The Bridge Setup)**
এটি ফ্লাটারের সবচেয়ে গুরুত্বপূর্ণ ফাংশন। এখানে আমরা **MethodChannel** তৈরি করি।
```kotlin
methodChannel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "screen_time")
```
*   **কেন?** "screen_time" হলো একটি ইউনিক নাম। ফ্লাটার যখন এই নামে কোনো মেসেজ পাঠাবে, তখন এই `MainActivity` তা শুনতে পাবে।

### **৩. `setMethodCallHandler` (Listening to Flutter)**
এটি একটি সুইচের মতো কাজ করে। ফ্লাটার থেকে আসা প্রতিটি রিকোয়েস্ট এখানে চেক করা হয়:
*   **`checkUsagePermission`**: ইউজার পারমিশন দিয়েছে কিনা তা চেক করে।
*   **`requestUsagePermission`**: পারমিশন না থাকলে সেটিংস পেজ ওপেন করে।
*   **`checkOverlayPermission`**: ফেসবুকের ওপরে ব্লকার দেখানোর পারমিশন আছে কিনা চেক করে।
*   **`startBackgroundService`**: আমাদের `BackgroundUsageService` কে চালু করে।
*   **`getUsageTime`**: আজকের ফেসবুক ব্যবহারের সময় (মিনিট) ফ্লাটারে পাঠায়।
*   **`resetUsage`**: ডাটাবেস বা মেমোরি রিসেট করতে সার্ভিসকে নির্দেশ দেয়।

### **৪. `hasUsageStatsPermission()`**
```kotlin
private fun hasUsageStatsPermission(): Boolean {
    val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
    return mode == AppOpsManager.MODE_ALLOWED
}
```
*   **লাইন বাই লাইন ব্যাখ্যা:** 
    1.  প্রথমে আমরা `AppOpsManager` কে ডাকি। 
    2.  তাকে জিজ্ঞেস করি যে আমাদের এই স্পেসিফিক অ্যাপের আইডি (`myUid`) এর জন্য `OPSTR_GET_USAGE_STATS` পারমিশনটি দেওয়া আছে কি না। 
    3.  যদি সেটি `MODE_ALLOWED` হয়, তবেই অ্যাপ অন্য অ্যাপের ব্যবহারের সময় দেখতে পারবে।

### **৫. `getAppUsageTime()`**
এটি ফ্লাটার স্ক্রিনে সময় দেখানোর জন্য ডাটা ক্যালকুলেট করে।
```kotlin
val midnight = calendar.timeInMillis
val manualReset = BackgroundUsageService.getManualResetTime(this)
val startTime = Math.max(midnight, manualReset) // রাত ১২টা অথবা রিসেট করার সময়ের মধ্যে যেটা লেটেস্ট সেটা নেয়
```
*   **কেন?** যাতে ইউজার একদম সঠিক সময় দেখতে পায় যে সে ফেসবুক কতক্ষণ ধরে ইউজ করছে।

---

---

## 👁 4. `AppUsageService.kt` (The Observer)
এটি একটি **Accessibility Service**। যদিও আমরা মেইন কাজ `BackgroundUsageService`-এ করি, তবুও এটি ফোনের উইন্ডো চেঞ্জগুলো নজরে রাখতে সাহায্য করে।

### **১. Role of Accessibility Service**
অ্যান্ড্রয়েডে এটি একটি বিশেষ সার্ভিস যা মূলত প্রতিবন্ধী ব্যক্তিদের সাহায্য করার জন্য তৈরি। তবে এটি ডেভেলপারদের জন্য খুব শক্তিশালী কারণ এটি ফোনের প্রতিটি স্ক্রিন চেঞ্জ (Window Change) ধরতে পারে।

### **২. Functions Breakdown**
*   **`onServiceConnected()`**: যখন আপনি ফোনের সেটিংস থেকে এই সার্ভিসটি অন করেন, তখন এটি কল হয়। এর মাধ্যমে আমরা জানতে পারি সার্ভিসটি এখন সচল।
    ```kotlin
    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("ACCESS", "Service Connected ✅") // লগ (Log) এ সাকসেস মেসেজ দেখায়
    }
    ```

*   **`onAccessibilityEvent(event)`**: এটি এই ক্লাসের সবচেয়ে গুরুত্বপূর্ণ ফাংশন। ফোনে যখনই কোনো নতুন অ্যাপ খোলে বা বন্ধ হয়, তখন এই ফাংশনটি অটোমেটিক কল হয়।
    ```kotlin
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return // যদি কোনো ডাটা না থাকে তবে কোডটি এখানেই থেমে যাবে
        
        // TYPE_WINDOW_STATE_CHANGED মানে হলো স্ক্রিনে উইন্ডো বদলে গেছে (নতুন অ্যাপ এসেছে)
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString() // নতুন ওপেন হওয়া অ্যাপের প্যাকেজ নাম দেয়
            Log.d("ACCESS", "Window State Changed: $packageName")
        }
    }
    ```
    *   **কেন এটি প্রয়োজন?** মাঝেমধ্যে `BackgroundUsageService` যদি অ্যাপ ডিটেক্ট করতে দেরি করে, তবে এই Accessibility সার্ভিসটি খুব দ্রুত বলে দিতে পারে ইউজার এখন কোন অ্যাপে আছে।

*   **`onInterrupt()`**: যদি সিস্টেম কোনো কারণে এই সার্ভিসটি বন্ধ করে দেয়, তবে এই ফাংশনটি কল হয়। আমরা এখানে শুধু একটি লগ (Log) রেখেছি যাতে আমরা বুঝতে পারি সার্ভিসটি আর কাজ করছে না।

---

---

## 🛰 5. `lib/screene_time_service.dart` (The Dart Service)
অ্যান্ড্রয়েডের সাথে কথা বলার জন্য ফ্লাটারের এই সার্ভিসটি আমরা ব্যবহার করি। এটি একটি প্লাগইনের মতো কাজ করে।

### **১. MethodChannel Definition**
```dart
static const MethodChannel _channel = MethodChannel('screen_time');
```
*   **কেন?** এই ইউনিক নামটি (`screen_time`) অবশ্যই অ্যান্ড্রয়েড এবং ফ্লাটার উভয় দিকে একই হতে হবে, নাহলে তারা একে অপরের সাথে কথা বলতে পারবে না।

### **২. Functions Breakdown (প্রতিটি মেথডের কাজ)**

*   **`init(onShowPopup)`**: এটি অ্যান্ড্রয়েড থেকে আসা মেসেজ শোনার জন্য ব্যবহৃত হয়।
    ```dart
    static void init(Function(String) onShowPopup) {
      _channel.setMethodCallHandler((call) async {
        if (call.method == "showPopup") {
          onShowPopup(call.arguments.toString()); // অ্যান্ড্রয়েড যখন বলবে 'showPopup', ফ্লাটার তখন একটি ডায়ালগ দেখাবে।
        }
      });
    }
    ```

*   **`checkPermission()`**: এটি অ্যান্ড্রয়েডকে জিজ্ঞেস করে যে ইউজার "Usage Access" পারমিশন দিয়েছে কি না। এটি `true` অথবা `false` রিটার্ন করে।

*   **`requestUsagePermission()`**: এটি ইউজারকে সরাসরি ফোনের সেটিংস পেজে নিয়ে যায় যাতে সে পারমিশনটি অন করতে পারে।

*   **`checkOverlayPermission()`**: এটি চেক করে যে অন্য অ্যাপের ওপরে ব্লকার দেখানোর (Overlay) পারমিশন আছে কি না।

*   **`startBackgroundService()`**: এটিই সেই বাটন বা ফাংশন যা অ্যান্ড্রয়েডের `BackgroundUsageService` কে ঘুম থেকে জাগিয়ে তোলে এবং মনিটরিং শুরু করতে বলে।

*   **`getUsageTime(packageName)`**: এটি সবচেয়ে বেশি ব্যবহৃত হয়। এটি নির্দিষ্ট কোনো অ্যাপের (যেমন: Facebook) আজকের ব্যবহারের সময় মিলি-সেকেন্ডে নিয়ে আসে।
    ```dart
    final int usageTime = await _channel.invokeMethod('getUsageTime', {
      'packageName': packageName,
    });
    ```

*   **`resetUsage()`**: এটি অ্যান্ড্রয়েডকে নির্দেশ দেয় আজকের ব্যবহারের ডাটা ক্লিয়ার করে দিতে। এটি মূলত টেস্টিং এর কাজের জন্য আমরা ব্যবহার করি।

---

---

## 🎨 6. `lib/main.dart` (The UI & Visuals)
বুটন এবং টাইম দেখানোর জন্য।

```dart
// টাইম রিফ্রেশ করার ফাংশন
Future<void> _refreshStatus() async {
  int time = await ScreenTimeService.getUsageTime(packageName: "com.facebook.katana");
  setState(() {
    _usageMinutes = time ~/ 60000; // টাইম আপডেট করে স্ক্রিনে দেখায়
  });
}

// রিসেট বুটন ক্লিক করলে কী হয়
ElevatedButton(
    onPressed: () async {
        await ScreenTimeService.resetUsage(); // মেমোরি ক্লিয়ার করে
        _refreshStatus(); // স্ক্রিন ডাটা রিফ্রেশ করে
    },
    child: Text("Reset Usage for Testing")
)
```

---

## �️ Implementation Roadmap (এক নজরে রোডম্যাপ)
আপনি যদি নতুন করে এই প্রজেক্টটি তৈরি করতে চান, তবে নিচের ধাপগুলো অনুসরণ করুন:

1.  **ধাপ ১ (Manifest):** প্রথমে `AndroidManifest.xml`-এ প্রয়োজনীয় সব পারমিশন এবং সার্ভিসগুলো লিখে ফেলুন।
2.  **ধাপ ২ (Overlay Design):** অ্যান্ড্রয়েড `res/layout` ফোল্ডারে `overlay_layout.xml` ফাইলটি তৈরি করুন যাতে ব্লকার স্ক্রিনটি দেখতে কেমন হবে তা ঠিক করা যায়।
3.  **ধাপ ৩ (Background Engine):** `BackgroundUsageService.kt` ফাইলটি তৈরি করুন এবং এতে সার্ভিস লাইফসাইকেল ও মনিটরিং লজিকগুলো লিখুন।
4.  **ধাপ ৪ (The Bridge):** `MainActivity.kt`-এ গিয়ে `MethodChannel` সেটআপ করুন যাতে ফ্লাটার আপনার অ্যান্ড্রয়েড কোডের সাথে কথা বলতে পারে।
5.  **ধাপ ৫ (Flutter Side):** ফ্লাটারে `screene_time_service.dart` তৈরি করুন যা অ্যান্ড্রয়েড থেকে ডাটা নিয়ে আসার কাজ করবে।
6.  **ধাপ ৬ (UI Design):** সবশেষে `main.dart`-এ সুন্দর একটি ইউজার ইন্টারফেস তৈরি করুন এবং বুটনগুলোর সাথে সার্ভিসগুলো কানেক্ট করে দিন।
7.  **ধাপ ৭ (Run & Test):** অ্যাপটি রান করুন, সব পারমিশনগুলো "Fix" বাটনে ক্লিক করে এলাও করে দিন। এরপর আপনার ব্লকার কাজ করছে কি না তা পরীক্ষা করুন।

---

## 🚀 Final Summary
এই প্রজেক্টটি মূলত একটি **Custom Ecosystem** যেখানে অ্যান্ড্রয়েড এবং ফ্লাটার একে অপরের পরিপূরক হিসেবে কাজ করছে। আপনি যত বেশি কোডগুলো প্র্যাকটিস করবেন, তত বেশি এই সিস্টেমটি আপনার আয়ত্তে আসবে। হ্যাপি কোডিং! 🚀
