package com.xmly.media.camera.view.detection;

import android.os.Build;
import android.util.Log;

import java.lang.ref.WeakReference;
import tv.danmaku.ijk.media.player.IjkLibLoader;

/**
 * Created by sunyc on 19-5-27.
 */

public class XMMtcnnNcnn {
    private static final String TAG = "XMMtcnnNcnn";
    private static boolean mIsLibLoaded = false;
    private static XMMtcnnNcnn mXMMtcnnNcnn = null;
    private long mNativeXMMtcnnNcnn = 0;

    private static final IjkLibLoader sLocalLibLoader = new IjkLibLoader() {
        @Override
        public void loadLibrary(String libName) throws UnsatisfiedLinkError, SecurityException {
            String ABI = Build.CPU_ABI;
            Log.i(TAG, "ABI " + ABI + " libName " +libName);
            System.loadLibrary(libName + "-" + ABI);
        }
    };

    private static void loadLibrariesOnce(IjkLibLoader libLoader) {
        synchronized (XMMtcnnNcnn.class) {
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

    public static XMMtcnnNcnn getInstance() {
        if (mXMMtcnnNcnn == null) {
            synchronized (XMMtcnnNcnn.class) {
                if (mXMMtcnnNcnn == null) {
                    mXMMtcnnNcnn = new XMMtcnnNcnn();
                }
            }
        }
        return mXMMtcnnNcnn;
    }

    private XMMtcnnNcnn()
    {
        loadLibrariesOnce(sLocalLibLoader);
        if (mIsLibLoaded) {
            native_setup(new WeakReference<XMMtcnnNcnn>(this));
        }
    }

    public void model_init(String model_path) {
        if (!mIsLibLoaded) {
            Log.i(TAG, "model_init : mIsLibLoaded is false");
            return;
        }

        if (model_path == null) {
            return;
        }

        _model_init(model_path);
    }

    public void enable(boolean enable) {
        if (!mIsLibLoaded) {
            Log.i(TAG, "enable : mIsLibLoaded is false");
            return;
        }

        _enable(enable);
    }

    public void NV21toABGR(byte[] yuv, int width, int height, byte[] gl_out,
                           int rotate, boolean flipHorizontal, boolean flipVertical)
    {
        if (!mIsLibLoaded) {
            Log.i(TAG, "NV21toABGR : mIsLibLoaded is false");
            return;
        }

        _NV21toABGR(yuv, width, height, gl_out, rotate, flipHorizontal, flipVertical);
    }

    public int[] get_face_position() {
        if (!mIsLibLoaded) {
            Log.i(TAG, "get_face_position : mIsLibLoaded is false");
            return null;
        }

        return _get_rect();
    }

    public void stop() {
        if (!mIsLibLoaded) {
            Log.i(TAG, "stop : mIsLibLoaded is false");
            return;
        }

        _stop();
    }

    public void release() {
        mXMMtcnnNcnn = null;
        if (!mIsLibLoaded) {
            Log.i(TAG, "release : mIsLibLoaded is false");
            return;
        }

        _release();
    }

    public void glMapBufferRange(int target, int offset, int length,
                                     int access, int w, int h,
                                     int pixelStride, int rowPadding, int format) {
        _glMapBufferRange(target, offset, length, access, w, h, pixelStride, rowPadding, format);
    }

    public native void glReadPixels(int x, int y, int width, int height, int format, int type);
    private native void _glMapBufferRange(int target, int offset, int length, int access, int w, int h,
                                          int pixelStride, int rowPadding, int format);
    private native void _release();
    private native void native_finalize();
    private native void _stop();
    private native int[] _get_rect();
    private native void _NV21toABGR(byte[] yuv, int width, int height, byte[] gl_out,
                                    int rotate, boolean flipHorizontal, boolean flipVertical);
    private native void _enable(boolean enable);
    private native void _model_init(String model_path);
    private native void native_setup(Object CameraRecoder_this);

    @Override
    protected void finalize() throws Throwable {
        Log.i(TAG, "finalize");
        try {
            if (mIsLibLoaded) {
                native_finalize();
            }
        } finally {
            super.finalize();
        }
    }
}
