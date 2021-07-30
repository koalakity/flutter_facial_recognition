package com.wtf1943.flutter_facial_recognition;

import android.app.Activity;
import android.content.Context;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.wtf1943.flutter_facial_recognition.arcface.faceServer.FaceServer;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry.Registrar;
import io.flutter.view.TextureRegistry;

/** FlutterFacialRecognitionPlugin */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class FlutterFacialRecognitionPlugin implements FlutterPlugin, MethodCallHandler, ActivityAware {
  /// The MethodChannel that will the communication between Flutter and native Android
  ///
  /// This local reference serves to register the plugin with the Flutter Engine and unregister it
  /// when the Flutter Engine is detached from the Activity
  private MethodChannel channel;
  private Context applicationContext;
  private TextureRegistry textureRegistry;
  private SurfaceTexture surfaceTexture;
  private Activity activity;
  @Override
  public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
    channel = new MethodChannel(flutterPluginBinding.getBinaryMessenger(), "flutter_facial_recognition");
    channel.setMethodCallHandler(this);
    applicationContext = flutterPluginBinding.getApplicationContext();
    textureRegistry = flutterPluginBinding.getTextureRegistry();
  }

  @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
  @Override
  public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
    switch (call.method){
      case "getPlatformVersion":
        result.success("Android " + android.os.Build.VERSION.RELEASE);
        break;
      case "activeEngine":
        result.success(FaceServer.getInstance().activeEngine(applicationContext,"4DUAVy2jV4jsQHAdQmxCz3Zy2MBr6gtcTGDFRunFgASQ","DJcBV4pg1gxcWgyrFBpRfHUkmwGYZr6pqeivzwoMfdZL"));
        break;
      case "init":
        initFaceServer(result);
        break;
      case "faceRegister":
        FaceServer.getInstance().faceRegister(result);
        break;
      case "faceSearch":
        FaceServer.getInstance().faceSearch(result);
        break;
      case "destroy":
        FaceServer.getInstance().unInit();
        break;
      default: result.notImplemented();
    }
  }

  private void initFaceServer(Result result){
    WindowManager wm = (WindowManager) (applicationContext.getSystemService(Context.WINDOW_SERVICE));
    Point point = new Point();
    wm.getDefaultDisplay().getRealSize(point);
    DisplayMetrics outMetrics = new DisplayMetrics();
    wm.getDefaultDisplay().getMetrics(outMetrics);
    Log.i("aaaaa",outMetrics.widthPixels + "");
    int width = point.x;
    int height = point.y;
    TextureRegistry.SurfaceTextureEntry surfaceTextureEntry = textureRegistry.createSurfaceTexture();
    surfaceTexture = surfaceTextureEntry.surfaceTexture();
    //宽高 相反
    surfaceTexture.setDefaultBufferSize(width,height);
    long textureId = surfaceTextureEntry.id();
    Log.i("textureId is :",textureId+"");
    try {
      boolean rs = FaceServer.getInstance().init(applicationContext);
      Log.i("FaceServer init","init result:"+rs);
      FaceServer.getInstance().initEngine(applicationContext,surfaceTextureEntry.surfaceTexture(),new Point(height,width),wm);
    } catch (Exception e) {
      e.printStackTrace();
    }
    result.success(textureId);
  }

  @Override
  public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
    channel.setMethodCallHandler(null);
  }

  @Override
  public void onAttachedToActivity(@NonNull ActivityPluginBinding activityPluginBinding) {
    activity = activityPluginBinding.getActivity();
  }

  @Override
  public void onDetachedFromActivityForConfigChanges() {

  }

  @Override
  public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding activityPluginBinding) {
    onAttachedToActivity(activityPluginBinding);
  }

  @Override
  public void onDetachedFromActivity() {

  }
}
