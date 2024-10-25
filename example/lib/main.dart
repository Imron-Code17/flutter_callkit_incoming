import 'package:flutter/material.dart';
import 'package:flutter_callkit_incoming/entities/android_params.dart';
import 'package:flutter_callkit_incoming/entities/call_kit_params.dart';
import 'package:flutter_callkit_incoming/entities/notification_params.dart';
import 'package:flutter_callkit_incoming/flutter_callkit_incoming.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  // This widget is the root of your application.
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Flutter Demo',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.deepPurple),
        useMaterial3: true,
      ),
      home: const MyHomePage(),
    );
  }
}

class MyHomePage extends StatefulWidget {
  const MyHomePage({super.key});

  @override
  State<MyHomePage> createState() => _MyHomePageState();
}

class _MyHomePageState extends State<MyHomePage> {
  Future<void> showCallkitIncoming(String uuid) async {
    final params = CallKitParams(
        id: uuid,
        title: 'Incoming Lead',
        subtitle: 'Verde Two',
        senderName: 'Budi Gunawan',
        senderMessage:
            'Halo saya tertarik dengan Apartment tipe studio verde two lorem ipsum dolor sit amet lorem',
        appName: 'Program',
        avatar: 'https://i.pravatar.cc/100',
        handle: '0123456789',
        type: 0,
        duration: 30000,
        textFollowUp: 'Follow Up',
        textDecline: 'Decline',
        textLater: 'Later',
        missedCallNotification: const NotificationParams(
          showNotification: true,
          isShowCallback: false,
          // callbackText: 'Call back',
        ),
        extra: <String, dynamic>{'userId': '1a2b3c4d'},
        headers: <String, dynamic>{'apiKey': 'Abc@123!', 'platform': 'flutter'},
        android: const AndroidParams(
            isCustomNotification: true,
            isShowLogo: false,
            ringtonePath: 'system_ringtone_default',
            backgroundColor: '#232323',
            backgroundUrl: 'assets/test.png',
            actionColor: '#4CAF50',
            textColor: '#ffffff',
            isShowFullLockedScreen: true));
    await FlutterCallkitIncoming.showCallkitIncoming(params);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      floatingActionButton:
          FloatingActionButton(onPressed: () => showCallkitIncoming("TQ262")),
    );
  }
}
