package com.wtf1943.flutter_facial_recognition.arcface.faceServer;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.graphics.Point;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;

import com.arcsoft.face.ActiveFileInfo;
import com.arcsoft.face.ErrorInfo;
import com.arcsoft.face.FaceEngine;
import com.arcsoft.face.FaceFeature;
import com.arcsoft.face.FaceInfo;
import com.arcsoft.face.FaceSimilar;
import com.arcsoft.face.LivenessInfo;
import com.arcsoft.face.enums.DetectFaceOrientPriority;
import com.arcsoft.face.enums.DetectMode;
import com.arcsoft.face.enums.RuntimeABI;
import com.arcsoft.imageutil.ArcSoftImageFormat;
import com.arcsoft.imageutil.ArcSoftImageUtil;
import com.arcsoft.imageutil.ArcSoftImageUtilError;
import com.arcsoft.imageutil.ArcSoftRotateDegree;
import com.wtf1943.flutter_facial_recognition.R;
import com.wtf1943.flutter_facial_recognition.model.FacePreviewInfo;
import com.wtf1943.flutter_facial_recognition.model.FaceRegisterInfo;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import io.flutter.plugin.common.MethodChannel;
import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

/**
 * 人脸库操作类，包含注册和搜索
 */
public class FaceServer {
    private static final String TAG = "FaceServer";
    public static final String IMG_SUFFIX = ".jpg";
    private static FaceEngine faceEngine = null;
    private static FaceServer faceServer = null;
    private static List<FaceRegisterInfo> faceRegisterInfoList;
    public static String ROOT_PATH;
    private FaceHelper faceHelper;
    private boolean isFaceDetected = false;
    private boolean livenessPass = false;
    private CameraCaptureSession _cameraCaptureSession;
    private CameraDevice _cameraDevice;
    private Handler backgroundHandler;
    private Camera2Helper camera2Helper;
    private WindowManager wm;

    private static final int MAX_DETECT_NUM = 5;
    /**
     * 当FR成功，活体未成功时，FR等待活体的时间
     */
    private static final int WAIT_LIVENESS_INTERVAL = 100;
    /**
     * 失败重试间隔时间（ms）
     */
    private static final long FAIL_RETRY_INTERVAL = 1000;
    /**
     * 出错重试最大次数
     */
    private static final int MAX_RETRY_TIME = 3;

    private int faceSearchRetryTimes = 0;

    private int ftInitCode = -1;
    private int frInitCode = -1;
    private int flInitCode = -1;
    /**
     * VIDEO模式人脸检测引擎，用于预览帧人脸追踪
     */
    private FaceEngine ftEngine;
    /**
     * 用于特征提取的引擎
     */
    private FaceEngine frEngine;
    /**
     * IMAGE模式活体检测引擎，用于预览帧人脸活体检测
     */
    private FaceEngine flEngine;
    /**
     * 存放注册图的目录
     */
    public static final String SAVE_IMG_DIR = "register" + File.separator + "imgs";
    /**
     * 存放特征的目录
     */
    private static final String SAVE_FEATURE_DIR = "register" + File.separator + "features";

    private HandlerThread handlerThread;

    /**
     * -1:do nothing  0: face register 1:face detect
     */
    private int bizType = -1;

    private MethodChannel.Result result;

    public int getBizType() {
        return bizType;
    }

    public void setBizType(int bizType) {
        this.bizType = bizType;
    }

    /**
     * 是否正在搜索人脸，保证搜索操作单线程进行
     */
    private boolean isProcessing = false;

    public static FaceServer getInstance() {
        if (faceServer == null) {
            synchronized (FaceServer.class) {
                if (faceServer == null) {
                    faceServer = new FaceServer();
                    faceServer.startBackgroundThread();
                }
            }
        }
        return faceServer;
    }


    /**
     * 初始化
     *
     * @param context 上下文对象
     * @return 是否初始化成功
     */
    public boolean init(Context context) {
        synchronized (this) {
            if (faceEngine == null && context != null) {
                faceEngine = new FaceEngine();
                int engineCode = faceEngine.init(context, DetectMode.ASF_DETECT_MODE_IMAGE, DetectFaceOrientPriority.ASF_OP_0_ONLY, 16, 1, FaceEngine.ASF_FACE_RECOGNITION | FaceEngine.ASF_FACE_DETECT);
                if (engineCode == ErrorInfo.MOK) {
                    initFaceList(context);
                    return true;
                } else {
                    faceEngine = null;
                    Log.e(TAG, "init: failed! code = " + engineCode);
                    return false;
                }
            }
            return false;
        }
    }

    /**
     * 销毁
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void unInit() {
        synchronized (this) {
            camera2Helper.release();
            unInitEngine();
            if (faceRegisterInfoList != null) {
                faceRegisterInfoList.clear();
                faceRegisterInfoList = null;
            }
            if (faceEngine != null) {
                faceEngine.unInit();
                faceEngine = null;
            }
            this.isFaceDetected = false;
            this.faceSearchRetryTimes = 0;
        }
    }

    /**
     * 初始化人脸特征数据以及人脸特征数据对应的注册图
     *
     * @param context 上下文对象
     */
    private void initFaceList(Context context) {
        synchronized (this) {
            if (ROOT_PATH == null) {
                ROOT_PATH = context.getFilesDir().getAbsolutePath();
            }
            Log.i("aaaaaaaaaaaaaaaaaaa",ROOT_PATH);
            File featureDir = new File(ROOT_PATH + File.separator + SAVE_FEATURE_DIR);
            if (!featureDir.exists() || !featureDir.isDirectory()) {
                return;
            }
            File[] featureFiles = featureDir.listFiles();
            if (featureFiles == null || featureFiles.length == 0) {
                return;
            }
            faceRegisterInfoList = new ArrayList<>();
            for (File featureFile : featureFiles) {
                try {
                    FileInputStream fis = new FileInputStream(featureFile);
                    byte[] feature = new byte[FaceFeature.FEATURE_SIZE];
                    fis.read(feature);
                    fis.close();
                    faceRegisterInfoList.add(new FaceRegisterInfo(feature, featureFile.getName()));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }




    /**
     * 用于预览时注册人脸
     *
     * @param context  上下文对象
     * @param nv21     NV21数据
     * @param width    NV21宽度
     * @param height   NV21高度
     * @param faceInfo {@link FaceEngine#detectFaces(byte[], int, int, int, List)}获取的人脸信息
     * @param name     保存的名字，若为空则使用时间戳
     * @return 是否注册成功
     */
    public boolean registerNv21(Context context, byte[] nv21, int width, int height, FaceInfo faceInfo, String name) {
        synchronized (this) {
            if (faceEngine == null || context == null || nv21 == null || width % 4 != 0 || nv21.length != width * height * 3 / 2) {
                Log.e(TAG, "registerNv21: invalid params");
                return false;
            }

            if (ROOT_PATH == null) {
                ROOT_PATH = context.getFilesDir().getAbsolutePath();
            }
            //特征存储的文件夹
            File featureDir = new File(ROOT_PATH + File.separator + SAVE_FEATURE_DIR);
            if (!featureDir.exists() && !featureDir.mkdirs()) {
                Log.e(TAG, "registerNv21: can not create feature directory");
                return false;
            }
            //图片存储的文件夹
            File imgDir = new File(ROOT_PATH + File.separator + SAVE_IMG_DIR);
            if (!imgDir.exists() && !imgDir.mkdirs()) {
                Log.e(TAG, "registerNv21: can not create image directory");
                return false;
            }
            FaceFeature faceFeature = new FaceFeature();
            //特征提取
            int code = faceEngine.extractFaceFeature(nv21, width, height, FaceEngine.CP_PAF_NV21, faceInfo, faceFeature);
            if (code != ErrorInfo.MOK) {
                Log.e(TAG, "registerNv21: extractFaceFeature failed , code is " + code);
                return false;
            } else {

                String userName = name == null ? String.valueOf(System.currentTimeMillis()) : name;
                try {
                    // 保存注册结果（注册图、特征数据）
                    // 为了美观，扩大rect截取注册图
                    Rect cropRect = getBestRect(width, height, faceInfo.getRect());
                    if (cropRect == null) {
                        Log.e(TAG, "registerNv21: cropRect is null!");
                        return false;
                    }

                    cropRect.left &= ~3;
                    cropRect.top &= ~3;
                    cropRect.right &= ~3;
                    cropRect.bottom &= ~3;

                    File file = new File(imgDir + File.separator + userName + IMG_SUFFIX);


                    // 创建一个头像的Bitmap，存放旋转结果图
                    Bitmap headBmp = getHeadImage(nv21, width, height, faceInfo.getOrient(), cropRect, ArcSoftImageFormat.NV21);

                    FileOutputStream fosImage = new FileOutputStream(file);
                    headBmp.compress(Bitmap.CompressFormat.JPEG, 100, fosImage);
                    fosImage.close();


                    FileOutputStream fosFeature = new FileOutputStream(featureDir + File.separator + userName);
                    fosFeature.write(faceFeature.getFeatureData());
                    fosFeature.close();

                    //内存中的数据同步
                    if (faceRegisterInfoList == null) {
                        faceRegisterInfoList = new ArrayList<>();
                    }
                    faceRegisterInfoList.add(new FaceRegisterInfo(faceFeature.getFeatureData(), userName));
                    return true;
                } catch (IOException e) {
                    e.printStackTrace();
                    return false;
                }
            }
        }

    }

    /**
     * 用于注册照片人脸
     *
     * @param context 上下文对象
     * @param bgr24   bgr24数据
     * @param width   bgr24宽度
     * @param height  bgr24高度
     * @param name    保存的名字，若为空则使用时间戳
     * @return 是否注册成功
     */
    public boolean registerBgr24(Context context, byte[] bgr24, int width, int height, String name) {
        synchronized (this) {
            if (faceEngine == null || context == null || bgr24 == null || width % 4 != 0 || bgr24.length != width * height * 3) {
                Log.e(TAG, "registerBgr24:  invalid params");
                return false;
            }

            if (ROOT_PATH == null) {
                ROOT_PATH = context.getFilesDir().getAbsolutePath();
            }
            //特征存储的文件夹
            File featureDir = new File(ROOT_PATH + File.separator + SAVE_FEATURE_DIR);
            if (!featureDir.exists() && !featureDir.mkdirs()) {
                Log.e(TAG, "registerBgr24: can not create feature directory");
                return false;
            }
            //图片存储的文件夹
            File imgDir = new File(ROOT_PATH + File.separator + SAVE_IMG_DIR);
            if (!imgDir.exists() && !imgDir.mkdirs()) {
                Log.e(TAG, "registerBgr24: can not create image directory");
                return false;
            }
            //人脸检测
            List<FaceInfo> faceInfoList = new ArrayList<>();
            int code = faceEngine.detectFaces(bgr24, width, height, FaceEngine.CP_PAF_BGR24, faceInfoList);
            if (code == ErrorInfo.MOK && faceInfoList.size() > 0) {
                FaceFeature faceFeature = new FaceFeature();

                //特征提取
                code = faceEngine.extractFaceFeature(bgr24, width, height, FaceEngine.CP_PAF_BGR24, faceInfoList.get(0), faceFeature);
                String userName = name == null ? String.valueOf(System.currentTimeMillis()) : name;
                try {
                    //保存注册结果（注册图、特征数据）
                    if (code == ErrorInfo.MOK) {
                        //为了美观，扩大rect截取注册图
                        Rect cropRect = getBestRect(width, height, faceInfoList.get(0).getRect());
                        if (cropRect == null) {
                            Log.e(TAG, "registerBgr24: cropRect is null");
                            return false;
                        }

                        cropRect.left &= ~3;
                        cropRect.top &= ~3;
                        cropRect.right &= ~3;
                        cropRect.bottom &= ~3;

                        File file = new File(imgDir + File.separator + userName + IMG_SUFFIX);
                        FileOutputStream fosImage = new FileOutputStream(file);


                        // 创建一个头像的Bitmap，存放旋转结果图
                        Bitmap headBmp = getHeadImage(bgr24, width, height, faceInfoList.get(0).getOrient(), cropRect, ArcSoftImageFormat.BGR24);
                        // 保存到本地
                        headBmp.compress(Bitmap.CompressFormat.JPEG, 100, fosImage);
                        fosImage.close();

                        // 保存特征数据
                        FileOutputStream fosFeature = new FileOutputStream(featureDir + File.separator + userName);
                        fosFeature.write(faceFeature.getFeatureData());
                        fosFeature.close();

                        // 内存中的数据同步
                        if (faceRegisterInfoList == null) {
                            faceRegisterInfoList = new ArrayList<>();
                        }
                        faceRegisterInfoList.add(new FaceRegisterInfo(faceFeature.getFeatureData(), userName));
                        return true;
                    } else {
                        Log.e(TAG, "registerBgr24: extract face feature failed, code is " + code);
                        return false;
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    return false;
                }
            } else {
                Log.e(TAG, "registerBgr24: no face detected, code is " + code);
                return false;
            }
        }

    }

    /**
     * 截取合适的头像并旋转，保存为注册头像
     *
     * @param originImageData 原始的BGR24数据
     * @param width           BGR24图像宽度
     * @param height          BGR24图像高度
     * @param orient          人脸角度
     * @param cropRect        裁剪的位置
     * @param imageFormat     图像格式
     * @return 头像的图像数据
     */
    private Bitmap getHeadImage(byte[] originImageData, int width, int height, int orient, Rect cropRect, ArcSoftImageFormat imageFormat) {
        byte[] headImageData = ArcSoftImageUtil.createImageData(cropRect.width(), cropRect.height(), imageFormat);
        int cropCode = ArcSoftImageUtil.cropImage(originImageData, headImageData, width, height, cropRect, imageFormat);
        if (cropCode != ArcSoftImageUtilError.CODE_SUCCESS) {
            throw new RuntimeException("crop image failed, code is " + cropCode);
        }

        //判断人脸旋转角度，若不为0度则旋转注册图
        byte[] rotateHeadImageData = null;
        int rotateCode;
        int cropImageWidth;
        int cropImageHeight;
        // 90度或270度的情况，需要宽高互换
        if (orient == FaceEngine.ASF_OC_90 || orient == FaceEngine.ASF_OC_270) {
            cropImageWidth = cropRect.height();
            cropImageHeight = cropRect.width();
        } else {
            cropImageWidth = cropRect.width();
            cropImageHeight = cropRect.height();
        }
        ArcSoftRotateDegree rotateDegree = null;
        switch (orient) {
            case FaceEngine.ASF_OC_90:
                rotateDegree = ArcSoftRotateDegree.DEGREE_270;
                break;
            case FaceEngine.ASF_OC_180:
                rotateDegree = ArcSoftRotateDegree.DEGREE_180;
                break;
            case FaceEngine.ASF_OC_270:
                rotateDegree = ArcSoftRotateDegree.DEGREE_90;
                break;
            case FaceEngine.ASF_OC_0:
            default:
                rotateHeadImageData = headImageData;
                break;
        }
        // 非0度的情况，旋转图像
        if (rotateDegree != null){
            rotateHeadImageData = new byte[headImageData.length];
            rotateCode = ArcSoftImageUtil.rotateImage(headImageData, rotateHeadImageData, cropRect.width(), cropRect.height(), rotateDegree, imageFormat);
            if (rotateCode != ArcSoftImageUtilError.CODE_SUCCESS) {
                throw new RuntimeException("rotate image failed, code is " + rotateCode);
            }
        }
        // 将创建一个Bitmap，并将图像数据存放到Bitmap中
        Bitmap headBmp = Bitmap.createBitmap(cropImageWidth, cropImageHeight, Bitmap.Config.RGB_565);
        if (ArcSoftImageUtil.imageDataToBitmap(rotateHeadImageData, headBmp, imageFormat) != ArcSoftImageUtilError.CODE_SUCCESS) {
            throw new RuntimeException("failed to transform image data to bitmap");
        }
        return headBmp;
    }

    /**
     * 在特征库中搜索
     *
     * @param faceFeature 传入特征数据
     * @return 比对结果
     */
    public CompareResult getTopOfFaceLib(FaceFeature faceFeature) {
        if (faceEngine == null || isProcessing || faceFeature == null || faceRegisterInfoList == null || faceRegisterInfoList.size() == 0) {
            return null;
        }
        FaceFeature tempFaceFeature = new FaceFeature();
        FaceSimilar faceSimilar = new FaceSimilar();
        float maxSimilar = 0;
        int maxSimilarIndex = -1;
        isProcessing = true;
        for (int i = 0; i < faceRegisterInfoList.size(); i++) {
            tempFaceFeature.setFeatureData(faceRegisterInfoList.get(i).getFeatureData());
            faceEngine.compareFaceFeature(faceFeature, tempFaceFeature, faceSimilar);
            if (faceSimilar.getScore() > maxSimilar) {
                maxSimilar = faceSimilar.getScore();
                maxSimilarIndex = i;
            }
        }
        isProcessing = false;
        if (maxSimilarIndex != -1) {
            return new CompareResult(faceRegisterInfoList.get(maxSimilarIndex).getName(), maxSimilar);
        }
        return null;
    }

    /**
     * 将图像中需要截取的Rect向外扩张一倍，若扩张一倍会溢出，则扩张到边界，若Rect已溢出，则收缩到边界
     *
     * @param width   图像宽度
     * @param height  图像高度
     * @param srcRect 原Rect
     * @return 调整后的Rect
     */
    private static Rect getBestRect(int width, int height, Rect srcRect) {
        if (srcRect == null) {
            return null;
        }
        Rect rect = new Rect(srcRect);

        // 原rect边界已溢出宽高的情况
        int maxOverFlow = Math.max(-rect.left, Math.max(-rect.top, Math.max(rect.right - width, rect.bottom - height)));
        if (maxOverFlow >= 0) {
            rect.inset(maxOverFlow, maxOverFlow);
            return rect;
        }

        // 原rect边界未溢出宽高的情况
        int padding = rect.height() / 2;

        // 若以此padding扩张rect会溢出，取最大padding为四个边距的最小值
        if (!(rect.left - padding > 0 && rect.right + padding < width && rect.top - padding > 0 && rect.bottom + padding < height)) {
            padding = Math.min(Math.min(Math.min(rect.left, width - rect.right), height - rect.bottom), rect.top);
        }
        rect.inset(-padding, -padding);
        return rect;
    }

    /**
     * 激活引擎
     * @param context
     * @param appId
     * @param sdkKey
     */
    public int activeEngine(final Context context, final String appId, final String sdkKey) {
        return  FaceEngine.activeOnline(context, appId, sdkKey);
    }
    /**
     * 初始化引擎
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void initEngine(final Context context, SurfaceTexture surfaceTexture,Point point,WindowManager wm) throws CameraAccessException {
        ftEngine = new FaceEngine();
        ftInitCode = ftEngine.init(context, DetectMode.ASF_DETECT_MODE_VIDEO, ConfigUtil.getFtOrient(context),
                16, MAX_DETECT_NUM, FaceEngine.ASF_FACE_DETECT);

        frEngine = new FaceEngine();
        frInitCode = frEngine.init(context, DetectMode.ASF_DETECT_MODE_IMAGE, DetectFaceOrientPriority.ASF_OP_0_ONLY,
                16, MAX_DETECT_NUM, FaceEngine.ASF_FACE_RECOGNITION);

        flEngine = new FaceEngine();
        flInitCode = flEngine.init(context, DetectMode.ASF_DETECT_MODE_IMAGE, DetectFaceOrientPriority.ASF_OP_0_ONLY,
                16, MAX_DETECT_NUM, FaceEngine.ASF_LIVENESS);

        Log.i(TAG, "initEngine:  init: " + ftInitCode);

        if (ftInitCode != ErrorInfo.MOK) {
            String error = String.format("初始化失败，错误码为:%d",flInitCode);
            Log.i(TAG, "initEngine: " + error);
        }
        if (frInitCode != ErrorInfo.MOK) {
            String error = String.format("初始化失败，错误码为:%d",frInitCode);
            Log.i(TAG, "initEngine: " + error);
        }
        if (flInitCode != ErrorInfo.MOK) {
            String error = String.format("初始化失败，错误码为:%d",flInitCode);
            Log.i(TAG, "initEngine: " + error);
        }

        final ThreadExecutor threadExecutor = new ThreadExecutor();
        final FaceListener faceListener = new FaceListener() {
            @Override
            public void onFail(Exception e) {

            }

            @Override
            public void onFaceFeatureInfoGet(@Nullable FaceFeature faceFeature, Integer requestId, Integer errorCode) {

            }

            @Override
            public void onFaceLivenessInfoGet(@Nullable LivenessInfo livenessInfo, Integer requestId, Integer errorCode) {
                if(livenessInfo != null){
                    livenessPass = true;
                    Log.i("onFaceLivenessInfoGet","livenessInfo:" + livenessInfo.getLiveness());
                }else {
                    Log.i("onFaceLivenessInfoGet","not livenessInfo");
                }
            }
        };

        faceHelper = new FaceHelper.Builder()
                .ftEngine(ftEngine)
                .frEngine(frEngine)
                .flEngine(flEngine)
                .frQueueSize(MAX_DETECT_NUM)
                .flQueueSize(MAX_DETECT_NUM)
                .faceListener(faceListener)
                .build();

        Camera2Listener camera2Listener = new Camera2Listener() {
            private long nextDetectTime = 0;
            private byte[] nv21;
            @Override
            public void onCameraOpened(CameraDevice cameraDevice, String cameraId, Size previewSize, int displayOrientation, boolean isMirror) {

            }

            @Override
            public void onPreview(byte[] y, byte[] u, byte[] v, Size previewSize, int stride) {
                Long currentTime = System.currentTimeMillis();
                if(currentTime < nextDetectTime || bizType < 0 || isFaceDetected){
                    return;
                }else {
                    nextDetectTime = currentTime + 500;
                }
                if (nv21 == null) {
                    nv21 = new byte[stride * previewSize.getHeight() * 3 / 2];
                }
                // 回传数据是YUV422
                if (y.length / u.length == 2) {
                    ImageUtil.yuv422ToYuv420sp(y, u, v, nv21, stride, previewSize.getHeight());
                }
                // 回传数据是YUV420
                else if (y.length / u.length == 4) {
                    ImageUtil.yuv420ToYuv420sp(y, u, v, nv21, stride, previewSize.getHeight());
                }


                //图片裁剪
                List<FacePreviewInfo> facePreviewInfoList =  faceHelper.onPreviewFrame(nv21,previewSize);
                Log.i("onPreviewFrame","faceSize" + facePreviewInfoList.size());
                if(facePreviewInfoList.size()==0){
                    // do nothing
                }else if(!livenessPass){
                    threadExecutor.getFlExecutor().execute(new FaceLivenessDetectRunnable(nv21,facePreviewInfoList.get(0).getFaceInfo(),previewSize.getWidth(),previewSize.getHeight(),FaceEngine.CP_PAF_NV21,facePreviewInfoList.get(0).getTrackId(),LivenessType.RGB,faceListener,flEngine));
                }else if(!checkFaceLocation(facePreviewInfoList.get(0).getFaceInfo().getRect(),previewSize.getWidth(),previewSize.getHeight(),Double.valueOf(previewSize.getHeight())/Double.valueOf(1080))){
                    return;
                } else if(bizType == 0){
                    final boolean registerResult = registerNv21(context,nv21,previewSize.getWidth(),previewSize.getHeight(),facePreviewInfoList.get(0).getFaceInfo(),"local");
                    Log.i("registerNv21","registerResult is " + registerResult);
                    if(registerResult && !isFaceDetected){
                        isFaceDetected = true;
                        ContextCompat.getMainExecutor(context).execute(new Runnable() {
                            @Override
                            public void run() {
                                result.success(registerResult?1:0);
                            }
                        });
                    }

                }else if(bizType ==1){
                    FaceFeature faceFeature = new FaceFeature();
                    frEngine.extractFaceFeature(nv21, previewSize.getWidth(), previewSize.getHeight(), FaceEngine.CP_PAF_NV21, facePreviewInfoList.get(0).getFaceInfo(), faceFeature);
                    searchFace(faceFeature,context);
                }
            }

            @Override
            public void onCameraClosed() {

            }

            @Override
            public void onCameraError(Exception e) {

            }
        };
        camera2Helper = new Camera2Helper.Builder()
                .cameraListener(camera2Listener)
                .maxPreviewSize(point)
                .minPreviewSize(point)
                .specificCameraId(1+"")
                .context(context)
                .previewOn(surfaceTexture)
                .previewViewSize(point)
                .rotation(wm.getDefaultDisplay().getRotation())
                .rotation(0)
                .build();
        camera2Helper.start();
    }

    private boolean checkFaceLocation(Rect rect,int width,int height,double scale){
        Log.i("limlit","right limit:" + width);
        if(rect.right>width){//1920
            return false;
        }
        Log.i("limlit","left limit:" + 720*scale);
        if(rect.left< (width - 720*scale)){ //1200
            return false;
        }
        Log.i("limlit","bottom limit:" + height);
        if(rect.bottom> height){//1080
            return false;
        }
        Log.i("limlit","top limit:" + 240*scale);
        if(rect.top < 240*scale){ //240
            return false;
        }
        return true;
    }

    /**
     * 销毁引擎，faceHelper中可能会有特征提取耗时操作仍在执行，加锁防止crash
     */
    private void unInitEngine() {
        if (ftInitCode == ErrorInfo.MOK && ftEngine != null) {
            synchronized (ftEngine) {
                int ftUnInitCode = ftEngine.unInit();
                Log.i(TAG, "unInitEngine: " + ftUnInitCode);
            }
        }
        if (frInitCode == ErrorInfo.MOK && frEngine != null) {
            synchronized (frEngine) {
                int frUnInitCode = frEngine.unInit();
                Log.i(TAG, "unInitEngine: " + frUnInitCode);
            }
        }
        if (flInitCode == ErrorInfo.MOK && flEngine != null) {
            synchronized (flEngine) {
                int flUnInitCode = flEngine.unInit();
                Log.i(TAG, "unInitEngine: " + flUnInitCode);
            }
        }
    }



    public void startBackgroundThread() {
        handlerThread = new HandlerThread("CameraBackground");
        handlerThread.start();
        backgroundHandler = new Handler(handlerThread.getLooper());
    }

    private void searchFace(final FaceFeature frFace, final Context context) {
        Observable
                .create(new ObservableOnSubscribe<CompareResult>() {
                    @Override
                    public void subscribe(ObservableEmitter<CompareResult> emitter) {
                        CompareResult compareResult = FaceServer.getInstance().getTopOfFaceLib(frFace);
                        emitter.onNext(compareResult);

                    }
                })
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<CompareResult>() {
                    @Override
                    public void onSubscribe(Disposable d) {

                    }

                    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
                    @Override
                    public void onNext(CompareResult compareResult) {
                        if (compareResult == null || compareResult.getUserName() == null) {
                            Log.i("searchFace","compareResult is null");
                            return;
                        }
                        if(compareResult.getSimilar() > 0.8F && !isFaceDetected){
                            Log.i("searchFace","searchFace success similar is " + compareResult.getSimilar());
                            isFaceDetected = true;
                            ContextCompat.getMainExecutor(context).execute(new Runnable() {
                                @Override
                                public void run() {
                                    result.success(0);
                                }
                            });
                        }else {
                            faceSearchRetryTimes++;
                            if(faceSearchRetryTimes > 4 && !isFaceDetected) {
                                isFaceDetected = true;
                                ContextCompat.getMainExecutor(context).execute(new Runnable() {
                                    @Override
                                    public void run() {
                                        result.success(-1);
                                    }
                                });
                            }
                        }

//
                    }

                    @Override
                    public void onError(Throwable e) {

                    }

                    @Override
                    public void onComplete() {

                    }
                });
    }
    public void faceRegister(MethodChannel.Result result){
        this.result = result;
        this.bizType = 0;
    }

    public void faceSearch(MethodChannel.Result result){
        this.result = result;
        this.bizType = 1;
    }
}
