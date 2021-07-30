package com.wtf1943.flutter_facial_recognition.arcface.faceServer;

import com.arcsoft.face.ErrorInfo;
import com.arcsoft.face.FaceEngine;
import com.arcsoft.face.FaceInfo;
import com.arcsoft.face.LivenessInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FaceLivenessDetectRunnable implements Runnable{
    /**
     * 活体检测引擎为空
     */
    private static final int ERROR_FL_ENGINE_IS_NULL = -3;
    private FaceInfo faceInfo;
    private int width;
    private int height;
    private int format;
    private Integer trackId;
    private byte[] nv21Data;
    private LivenessType livenessType;
    private FaceListener faceListener;
    private FaceEngine flEngine;

    public FaceLivenessDetectRunnable(byte[] nv21Data, FaceInfo faceInfo, int width, int height, int format, Integer trackId, LivenessType livenessType,FaceListener faceListener,FaceEngine flEngine) {
        if (nv21Data == null) {
            return;
        }
        this.nv21Data = nv21Data;
        this.faceInfo = new FaceInfo(faceInfo);
        this.width = width;
        this.height = height;
        this.format = format;
        this.trackId = trackId;
        this.livenessType = livenessType;
        this.faceListener = faceListener;
        this.flEngine = flEngine;
    }

    @Override
    public void run() {
        if (faceListener != null && nv21Data != null) {
            if (flEngine != null) {
                List<LivenessInfo> livenessInfoList = new ArrayList<>();
                int flCode;
                synchronized (flEngine) {
                    if (livenessType == LivenessType.RGB) {
                        flCode = flEngine.process(nv21Data, width, height, format, Arrays.asList(faceInfo), FaceEngine.ASF_LIVENESS);
                    } else {
                        flCode = flEngine.processIr(nv21Data, width, height, format, Arrays.asList(faceInfo), FaceEngine.ASF_IR_LIVENESS);
                    }
                }
                if (flCode == ErrorInfo.MOK) {
                    if (livenessType == LivenessType.RGB) {
                        flCode = flEngine.getLiveness(livenessInfoList);
                    } else {
                        flCode = flEngine.getIrLiveness(livenessInfoList);
                    }
                }

                if (flCode == ErrorInfo.MOK && livenessInfoList.size() > 0) {
                    faceListener.onFaceLivenessInfoGet(livenessInfoList.get(0), trackId, flCode);
                } else {
                    faceListener.onFaceLivenessInfoGet(null, trackId, flCode);
                    faceListener.onFail(new Exception("fl failed errorCode is " + flCode));
                }
            } else {
                faceListener.onFaceLivenessInfoGet(null, trackId, ERROR_FL_ENGINE_IS_NULL);
                faceListener.onFail(new Exception("fl failed ,frEngine is null"));
            }
        }
        nv21Data = null;
    }
}
