package com.wtf1943.flutter_facial_recognition.arcface.faceServer;

import android.util.Log;

public class ImageUtil {
    /**
     * 将Y:U:V == 4:2:2的数据转换为nv21
     *
     * @param y      Y 数据
     * @param u      U 数据
     * @param v      V 数据
     * @param nv21   生成的nv21，需要预先分配内存
     * @param stride 步长
     * @param height 图像高度
     */
    public static void yuv422ToYuv420sp(byte[] y, byte[] u, byte[] v, byte[] nv21, int stride, int height) {
        System.arraycopy(y, 0, nv21, 0, y.length);
        // 注意，若length值为 y.length * 3 / 2 会有数组越界的风险，需使用真实数据长度计算
        int length = y.length + u.length / 2 + v.length / 2;
        int uIndex = 0, vIndex = 0;
        for (int i = stride * height; i < length; i += 2) {
            nv21[i] = v[vIndex];
            nv21[i + 1] = u[uIndex];
            vIndex += 2;
            uIndex += 2;
        }
    }

    /**
     * 将Y:U:V == 4:1:1的数据转换为nv21
     *
     * @param y      Y 数据
     * @param u      U 数据
     * @param v      V 数据
     * @param nv21   生成的nv21，需要预先分配内存
     * @param stride 步长
     * @param height 图像高度
     */
    public static void yuv420ToYuv420sp(byte[] y, byte[] u, byte[] v, byte[] nv21, int stride, int height) {
        System.arraycopy(y, 0, nv21, 0, y.length);
        // 注意，若length值为 y.length * 3 / 2 会有数组越界的风险，需使用真实数据长度计算
        int length = y.length + u.length + v.length;
        int uIndex = 0, vIndex = 0;
        for (int i = stride * height; i < length; i++) {
            nv21[i] = v[vIndex++];
            nv21[i + 1] = u[uIndex++];
        }
    }

    /**
     * NV21裁剪  算法效率 3ms
     *
     * @param src    源数据
     * @param width  源宽
     * @param height 源高
     * @param left   顶点坐标
     * @param top    顶点坐标
     * @param clip_w 裁剪后的宽
     * @param clip_h 裁剪后的高
     * @return 裁剪后的数据
     */
    public static byte[] clipNV21(byte[] src, int width, int height, int left, int top, int clip_w, int clip_h) {
        if (left > width || top > height || left + clip_w > width || top + clip_h > height) {
            return null;
        }
        //取偶
        int x = left / 4 * 4, y = top / 4 * 4;
        int w = clip_w / 4 * 4, h = clip_h / 4 * 4;
        int y_unit = w * h;
        int uv = y_unit / 2;
        byte[] nData = new byte[y_unit + uv];
        int uv_index_dst = w * h - y / 2 * w;
        int uv_index_src = width * height + x;
        for (int i = y; i < y + h; i++) {
            System.arraycopy(src, i * width + x, nData, (i - y) * w, w);//y内存块复制
            if (i % 2 == 0) {
                System.arraycopy(src, uv_index_src + (i >> 1) * width, nData, uv_index_dst + (i >> 1) * w, w);//uv内存块复制
            }
        }
        return nData;
    }
}