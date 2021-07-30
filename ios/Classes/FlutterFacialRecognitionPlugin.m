#import "FlutterFacialRecognitionPlugin.h"
#if __has_include(<flutter_facial_recognition/flutter_facial_recognition-Swift.h>)
#import <flutter_facial_recognition/flutter_facial_recognition-Swift.h>
#else
// Support project import fallback if the generated compatibility header
// is not copied when this plugin is created as a library.
// https://forums.swift.org/t/swift-static-libraries-dont-copy-generated-objective-c-header/19816
#import "flutter_facial_recognition-Swift.h"
#endif

@implementation FlutterFacialRecognitionPlugin
+ (void)registerWithRegistrar:(NSObject<FlutterPluginRegistrar>*)registrar {
  [SwiftFlutterFacialRecognitionPlugin registerWithRegistrar:registrar];
}
@end
