import 'package:flutter/material.dart';
import 'package:methode_channel_lifecycle/screene_time_service.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      title: 'Screen Time App',
      theme: ThemeData(
        brightness: Brightness.dark,
        primarySwatch: Colors.deepPurple,
      ),
      home: const HomeScreen(),
    );
  }
}

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  bool _usagePermission = false;
  bool _overlayPermission = false;
  bool _isServiceRunning = false;
  int _usageMinutes = 0;

  @override
  void initState() {
    super.initState() ;
    _initService();
    _refreshStatus();
  }

  void _initService() {
    ScreenTimeService.init((appName) {
      _showLimitPopup(appName);
    });
  }

  Future<void> _refreshStatus() async {
    bool usage = await ScreenTimeService.checkPermission();
    bool overlay = await ScreenTimeService.checkOverlayPermission();
    int time = await ScreenTimeService.getUsageTime();
    setState(() {
      _usagePermission = usage;
      _overlayPermission = overlay;
      _usageMinutes = time ~/ 60000;
    });
  }

  void _showLimitPopup(String appName) {
    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (context) => AlertDialog(
        title: const Text("⏰ Limit Reached"),
        content: Text("You have used $appName for 1 minute today."),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text("OK"),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text("Usage Monitor Pro")),
      body: SingleChildScrollView(
        child: Padding(
          padding: const EdgeInsets.all(20.0),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              _buildStatusCard(
                "Usage Access", 
                _usagePermission, 
                () => ScreenTimeService.requestUsagePermission()
              ),
              const SizedBox(height: 16),
              _buildStatusCard(
                "Draw Over Other Apps", 
                _overlayPermission, 
                () => ScreenTimeService.requestOverlayPermission()
              ),
              const SizedBox(height: 32),
              Container(
                padding: const EdgeInsets.all(20),
                decoration: BoxDecoration(
                  color: Colors.white10,
                  borderRadius: BorderRadius.circular(15),
                ),
                child: Column(
                  children: [
                    const Text("Background Monitoring", style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
                    const SizedBox(height: 8),
                    Text("Target: Facebook, Lite", style: TextStyle(color: Colors.grey[400])),
                    const SizedBox(height: 20),
                    SwitchListTile(
                      title: Text(_isServiceRunning ? "Service is RUNNING" : "Service is STOPPED"),
                      value: _isServiceRunning,
                      onChanged: (_usagePermission && _overlayPermission) ? (val) async {
                        if (val) {
                          bool success = await ScreenTimeService.startBackgroundService();
                          setState(() => _isServiceRunning = success);
                        } else {
                          bool success = await ScreenTimeService.stopBackgroundService();
                          setState(() => _isServiceRunning = !success);
                        }
                      } : null,
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 32),
              Center(
                child: Text(
                  "Today's usage (This app): $_usageMinutes min",
                  style: const TextStyle(fontSize: 16),
                ),
              ),
              const SizedBox(height: 20),
              ElevatedButton.icon(
                onPressed: _refreshStatus,
                icon: const Icon(Icons.refresh),
                label: const Text("Refresh Permissions"),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildStatusCard(String title, bool granted, VoidCallback onRequest) {
    return Card(
      child: ListTile(
        title: Text(title),
        subtitle: Text(granted ? "Permission Granted" : "Permission Required", 
          style: TextStyle(color: granted ? Colors.green : Colors.orange)),
        trailing: granted ? const Icon(Icons.check_circle, color: Colors.green) 
                         : ElevatedButton(onPressed: onRequest, child: const Text("Fix")),
      ),
    );
  }
}