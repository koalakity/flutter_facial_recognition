import 'dart:async';

import 'package:flutter/services.dart';

class FlutterFacialRecognition {
  static const MethodChannel _channel =
      const MethodChannel('flutter_facial_recognition');

  static Future<String> get platformVersion async {
    final String version = await _channel.invokeMethod('getPlatformVersion');
    return version;
  }

  static Future<int> get activeEngine async {
    final int activityCode = await _channel.invokeMethod('activeEngine');
    return activityCode;
  }

  static Future<int> get init async {
    return await _channel.invokeMethod("init");
  }

  static Future<num> get faceRegister async {
    final int temp = await _channel.invokeMethod("faceRegister");
    return temp;
  }

  static Future<num> get faceSearch async {
    final int temp = await _channel.invokeMethod("faceSearch");
    return temp;
  }

  static Future<num> get destroy async {
    final int temp = await _channel.invokeMethod("destroy");
    return temp;
  }
}
