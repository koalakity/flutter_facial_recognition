import Flutter
import UIKit

public class SwiftFlutterFacialRecognitionPlugin: NSObject, FlutterPlugin {
  public static func register(with registrar: FlutterPluginRegistrar) {
    let channel = FlutterMethodChannel(name: "flutter_facial_recognition", binaryMessenger: registrar.messenger())
    let instance = SwiftFlutterFacialRecognitionPlugin()
    registrar.addMethodCallDelegate(instance, channel: channel)
  }

  public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
    result("iOS " + UIDevice.current.systemVersion)
  }
}
