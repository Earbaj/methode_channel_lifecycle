import 'package:flutter/services.dart';

/**
 * ScreenTimeService handles the communication between Flutter and Android Native
 * for usage statistics and background monitoring.
 */
class ScreenTimeService {
  // Define the MethodChannel with a unique name matching the Android side
  static const MethodChannel _channel = MethodChannel('screen_time');

  /**
   * Initializes the MethodChannel handler to listen for messages from native code.
   * [onShowPopup] is a callback triggered when a limit is reached.
   */
  static void init(Function(String) onShowPopup) {
    print("ScreenTimeService: Initializing...");
    _channel.setMethodCallHandler((call) async {
      print("ScreenTimeService: Received call: ${call.method} with arguments: ${call.arguments}");
      
      // Handle the 'showPopup' method called from Android
      if (call.method == "showPopup") {
        onShowPopup(call.arguments.toString());
      }
    });
    print("ScreenTimeService: MethodCallHandler set ✅");
  }

  /**
   * Checks if the "Usage Access" permission is granted on Android.
   */
  static Future<bool> checkPermission() async {
    try {
      final bool hasPermission = await _channel.invokeMethod('checkUsagePermission');
      return hasPermission;
    } on PlatformException catch (e) {
      print("Error checking permission: ${e.message}");
      return false;
    }
  }

  /**
   * Opens the Android system settings page for the user to grant Usage Access.
   */
  static Future<void> requestUsagePermission() async {
    try {
      await _channel.invokeMethod('requestUsagePermission');
    } on PlatformException catch (e) {
      print("Error requesting usage permission: ${e.message}");
    }
  }

  /**
   * Checks if the "Draw over other apps" (overlay) permission is granted.
   */
  static Future<bool> checkOverlayPermission() async {
    try {
      return await _channel.invokeMethod('checkOverlayPermission');
    } on PlatformException catch (e) {
      print("Error checking overlay permission: ${e.message}");
      return false;
    }
  }

  /**
   * Opens the Android system settings page for the user to grant Overlay permission.
   */
  static Future<void> requestOverlayPermission() async {
    try {
      await _channel.invokeMethod('requestOverlayPermission');
    } on PlatformException catch (e) {
      print("Error requesting overlay permission: ${e.message}");
    }
  }

  /**
   * Starts the Background Monitoring Foreground Service.
   */
  static Future<bool> startBackgroundService() async {
    try {
      return await _channel.invokeMethod('startBackgroundService');
    } on PlatformException catch (e) {
      print("Error starting background service: ${e.message}");
      return false;
    }
  }

  /**
   * Stops the Background Monitoring Foreground Service.
   */
  static Future<bool> stopBackgroundService() async {
    try {
      return await _channel.invokeMethod('stopBackgroundService');
    } on PlatformException catch (e) {
      print("Error stopping background service: ${e.message}");
      return false;
    }
  }

  /**
   * Retrieves the total usage time for a specific app today in milliseconds.
   * Default is Facebook.
   */
  static Future<int> getUsageTime({String packageName = "com.facebook.katana"}) async {
    try {
      final int usageTime = await _channel.invokeMethod('getUsageTime', {
        'packageName': packageName,
      });
      return usageTime;
    } on PlatformException catch (e) {
      print("Error getting usage time: ${e.message}");
      return 0;
    }
  }

  /**
   * Resets the usage counter for today (useful for testing).
   */
  static Future<bool> resetUsage() async {
    try {
      return await _channel.invokeMethod('resetUsage');
    } on PlatformException catch (e) {
      print("Error resetting usage: ${e.message}");
      return false;
    }
  }
}