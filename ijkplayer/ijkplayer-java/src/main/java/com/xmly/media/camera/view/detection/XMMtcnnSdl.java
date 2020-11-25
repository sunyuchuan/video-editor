package com.xmly.media.camera.view.detection;

import android.graphics.Bitmap;
import android.os.Build;
import android.util.Log;

import tv.danmaku.ijk.media.player.IjkLibLoader;

/**
 * Created by sunyc on 2019/05/08.
 */

public class XMMtcnnSdl {
    private static final String TAG = "XMMtcnnSdl";
    private static boolean mIsLibLoaded = false;
    private static XMMtcnnSdl mXMMtcnnSdl = null;

    private static final IjkLibLoader sLocalLibLoader = new IjkLibLoader() {
        @Override
        public void loadLibrary(String libName) throws UnsatisfiedLinkError, SecurityException {
            String ABI = Build.CPU_ABI;
            Log.i(TAG, "ABI " + ABI + " libName " +libName);
            System.loadLibrary(libName + "-" + ABI);
        }
    };

    private static void loadLibrariesOnce(IjkLibLoader libLoader) {
        synchronized (XMMtcnnSdl.class) {
            if (!mIsLibLoaded) {
                if (libLoader == null)
                    libLoader = sLocalLibLoader;

                if (Build.CPU_ABI.equals("armeabi-v7a") || Build.CPU_ABI.equals("arm64-v8a")) {
                    libLoader.loadLibrary("ijksdl");
                    libLoader.loadLibrary("ijkffmpeg");
                    libLoader.loadLibrary("mtcnn_ncnn");
                    libLoader.loadLibrary("xmmtcnn_ncnn");
                    mIsLibLoaded = true;
                }
            }
        }
    }

    public static XMMtcnnSdl getInstance() {
        if (mXMMtcnnSdl == null) {
            synchronized (XMMtcnnSdl.class) {
                if (mXMMtcnnSdl == null) {
                    mXMMtcnnSdl = new XMMtcnnSdl();
                }
            }
        }
        return mXMMtcnnSdl;
    }

    private XMMtcnnSdl()
    {
        loadLibrariesOnce(sLocalLibLoader);
    }

    //人脸检测模型导入
    public native boolean FaceDetectionModelInit(String faceDetectionModelPath);

    //人脸检测
    public native int[] FaceDetect(byte[] imageDate, int imageWidth , int imageHeight, int imageChannel, int format);

    public native int[] MaxFaceDetect(byte[] imageDate, int imageWidth , int imageHeight, int imageChannel, int format);

    //人脸检测模型反初始化
    public native boolean FaceDetectionModelUnInit();

    //检测的最小人脸设置
    public native boolean SetMinFaceSize(int minSize);

    //线程设置
    public native boolean SetThreadsNumber(int threadsNumber);

    //循环测试次数
    public native boolean SetTimeCount(int timeCount);

    public static native void NV21toABGR(byte[] yuv, int width, int height, byte[] gl_out);

    public static native void ABGRScale(byte[] src, int src_w, int src_h, byte[] dst, int dst_w, int dst_h);

    public static native void ABGRRotate(byte[] input, int w, int h, int degrees);

    public static native void ABGRFlipHoriz(byte[] input, byte[] output, int w, int h);

    public native boolean SqueezeNcnn_Init(byte[] param, byte[] bin, byte[] words);

    public native String SqueezeNcnn_Detect(Bitmap bitmap);
}
