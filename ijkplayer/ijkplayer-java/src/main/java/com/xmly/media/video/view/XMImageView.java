package com.xmly.media.video.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import android.util.Log;

import com.xmly.media.camera.view.CameraView;
import com.xmly.media.camera.view.gpuimage.filter.GPUImageFilterFactory;
import com.xmly.media.camera.view.recorder.IXMCameraRecorderListener;
import com.xmly.media.camera.view.recorder.XMMediaRecorder;
import com.xmly.media.camera.view.recorder.XMMediaRecorderParams;
import com.xmly.media.camera.view.utils.XMFilterType;
import java.util.HashMap;

/**
 * Created by sunyc on 19-5-16.
 */

public class XMImageView extends GLSurfaceView {
    private static final String TAG = "XMImageView";
    private static final int DURATION = 4;
    private static final int IMAGE_NUM = 5;
    private XMImageRenderer mRenderer = null;
    private XMMediaRecorder mRecorder = null;
    private XMMediaRecorderParams params = new XMMediaRecorderParams();
    private CameraView.ICameraViewListener mListener = null;
    private boolean useSoftEncoder = true;
    private boolean hasAudio = false;
    private boolean hasVideo = true;
    private volatile boolean mImageReaderPrepared = false;
    private volatile boolean mXMMediaRecorderPrepared = false;
    private int mOutputWidth = 0;
    private int mOutputHeight = 0;
    private volatile boolean mRefreshThreadAbort = false;
    private int mRefreshRate = 15;
    private long mRefreshTime = 0l;
    private Object mLock = new Object();
    private volatile boolean isRunning = false;
    private volatile boolean isRecording = false;
    private XMFilterType mXMFilterType = XMFilterType.NONE;
    private String mImagePath = null;

    private void initView() {
        mRecorder = new XMMediaRecorder(useSoftEncoder, hasAudio, hasVideo);
        mRecorder.setListener(onXMPlayerRecorderListener);
        mRenderer = new XMImageRenderer(getContext().getApplicationContext(), mRecorder);
        mRenderer.setListener(onXMPlayerRecorderListener, onXMImageListener);
    }

    public XMImageView(Context context) {
        this(context, null);
    }

    public XMImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initView();
    }

    public void setSurfaceView() {
        synchronized (mLock) {
            if (mRenderer != null)
                mRenderer.setGLSurfaceView(this);
        }
    }

    public void setListener(CameraView.ICameraViewListener l) {
        synchronized (mLock) {
            mListener = l;
        }
    }

    /**
     * stop image preview
     */
    public void stopPreview() {
        synchronized (mLock) {
            if (!getRefreshStatus()) {
                onXMPlayerRecorderListener.onPreviewStopped();
                return;
            }

            setRefreshThreadAbort(true);
            while (getRefreshStatus()) {
                try {
                    mLock.wait();
                } catch (InterruptedException ie) {
                    Log.e(TAG, "InterruptedException " + ie.toString());
                }
            }
        }
        onXMPlayerRecorderListener.onPreviewStopped();
    }

    /**
     * start image preview
     * @param firstImagePath the first image of path,The first one is displayed by default
     * @param outWidth output video of width
     * @param outHeight output video of height
     * @param outFps output video of frame_rate
     * @throws IllegalStateException
     */
    public void startPreview(String firstImagePath, int outWidth, int outHeight, int outFps) throws IllegalStateException {
        synchronized (mLock) {
            if (getRefreshStatus()) {
                Log.w(TAG, "refresh thread already running");
                onXMPlayerRecorderListener.onPreviewStarted();
                return;
            }

            Log.i(TAG, "startPreview refresh rate is " + outFps);
            mOutputWidth = align(outWidth, 2);
            mOutputHeight = align(outHeight, 2);
            if (mRenderer != null) {
                mRenderer.setVideoSize(mOutputWidth, mOutputHeight);
            }

            setImage_l(firstImagePath);
            initRefreshThread(outFps);
            Thread thread = new RefreshThread();
            thread.start();
            while (!getRefreshStatus()) {
                try {
                    mLock.wait();
                } catch (InterruptedException ie) {
                    Log.e(TAG,"InterruptedException " + ie.toString());
                }
            }
        }
        onXMPlayerRecorderListener.onPreviewStarted();
    }

    private void stopEncoder() {
        if (mRenderer != null) {
            mRenderer.startPutData(false);
        }
        setEncoderStatus(false);
    }

    public void stop() {
        stopPreview();
        synchronized (mLock) {
            stopEncoder();
        }
        stopRecorder();
    }

    private void releaseRenderer() {
        if (mRenderer != null) {
            mRenderer.release();
            mRenderer = null;
        }
    }

    private void releaseRecorder() {
        if (mRecorder != null) {
            mRecorder.release();
            mRecorder.setListener(null);
            mRecorder = null;
        }
    }

    private void releaseEncoder() {
        if (mRenderer != null) {
            mRenderer.startPutData(false);
        }
        setEncoderStatus(false);
    }

    public void release() {
        stop();
        synchronized (mLock) {
            releaseEncoder();
            releaseRecorder();
            releaseRenderer();
            mListener = null;
        }
    }

    public void setLogo(String logoPath, Rect rect) {
        synchronized (mLock) {
            if (logoPath == null || rect == null)
                return;

            float[] logoRect = new float[]{rect.left, rect.bottom, rect.right, rect.top};
            if (mRenderer != null) {
                Bitmap bmp = GPUImageFilterFactory.decodeBitmap(logoPath);
                mRenderer.setLogo(bmp, logoRect);
            }
        }
    }

    private void setImage_l(String imagePath) {
        if (imagePath == null)
            return;

        if (mRenderer != null) {
            Bitmap bmp = GPUImageFilterFactory.decodeBitmap(imagePath);
            mRenderer.setImage(bmp);
        }
    }

    private void setBlindsImage_l(String imagePath) {
        if (imagePath == null)
            return;

        if (mRenderer != null) {
            Bitmap bmp = GPUImageFilterFactory.decodeBitmap(imagePath);
            mRenderer.setBlindsImage(bmp);
        }
    }

    public void setImage(String imagePath) {
        synchronized (mLock) {
            if (mXMFilterType != XMFilterType.FILTER_BLINDS
                    && mXMFilterType != XMFilterType.FILTER_FADE_IN_OUT) {
                setImage_l(imagePath);
            } else {
                setBlindsImage_l(imagePath);
            }
            mImagePath = imagePath;
        }
    }

    public void setFilter(final XMFilterType filtertype) {
        synchronized (mLock) {
            Log.i(TAG, "setFilter filter type " + filtertype);
            if (mRenderer != null) {
                mRenderer.setFilter(filtertype);
            }
            mXMFilterType = filtertype;
        }
    }

    private void setEncoderStatus(boolean status) {
        if (mRenderer != null) {
            mRenderer.changeVideoEncoderStatus(status);
        }
    }

    private void startRecorder_l(String outputPath, int outputWidth, int outputHeight) {
        if (mRecorder != null) {
            Log.i(TAG, "startRecorder outputPath " + outputPath);
            setEncoderStatus(true);
            mImageReaderPrepared = false;
            mXMMediaRecorderPrepared = false;

            HashMap<String, String> config = new HashMap<String, String>();
            config.put("width", String.valueOf(outputWidth));
            config.put("height", String.valueOf(outputHeight));
            config.put("bit_rate", String.valueOf((int) (700000 * ((float) (outputWidth * outputHeight) / (float) (540 * 960)))));
            config.put("fps", String.valueOf(mRefreshRate));
            config.put("gop_size", String.valueOf((int) (params.gop_size * mRefreshRate)));
            config.put("crf", String.valueOf(params.crf));
            config.put("multiple", String.valueOf(params.multiple));
            config.put("max_b_frames", String.valueOf(params.max_b_frames));
            config.put("CFR", String.valueOf(params.FALSE));
            config.put("output_filename", outputPath);
            config.put("preset", params.preset);
            config.put("tune", params.tune);
            if (!mRecorder.setConfigParams(config)) {
                Log.e(TAG, "setConfigParams failed, exit");
                setEncoderStatus(false);
                if (mRenderer != null) {
                    mRenderer.startPutData(false);
                }
                config.clear();
                return;
            }

            config.clear();
            mRecorder.prepareAsync();
        }
    }

    public void stopRecorder() {
        synchronized (mLock) {
            if (!getRecordStatus()) {
                Log.w(TAG, "Recorder is stopped, exit");
                onXMPlayerRecorderListener.onRecorderStopped();
                return;
            }

            stopEncoder();
            if (mRecorder != null) {
                mRecorder.stop();
            }
        }
    }

    /**
     * start video recording
     * @param outputPath output video of path
     * @param outputWidth output video of width
     * @param outputHeight output video of height
     */
    public void startRecorder(String outputPath, int outputWidth, int outputHeight) {
        synchronized (mLock) {
            if (getRecordStatus()) {
                Log.w(TAG, "Recorder is running, exit");
                onXMPlayerRecorderListener.onRecorderStarted();
                return;
            }

            mOutputWidth = align(outputWidth, 2);
            mOutputHeight = align(outputHeight, 2);
            if (mRenderer != null) {
                mRenderer.setVideoSize(mOutputWidth, mOutputHeight);
            }

            startRecorder_l(outputPath, mOutputWidth, mOutputHeight);
            setRecordStatus(true);
        }
    }

    private boolean getRecordStatus() {
        return isRecording;
    }

    private void setRecordStatus(boolean running) {
        isRecording = running;
    }

    private boolean getRefreshStatus() {
        return isRunning;
    }

    private void setRefreshStatus(boolean running) {
        isRunning = running;
    }

    private boolean getRefreshThreadAbort() {
        return mRefreshThreadAbort;
    }

    private void setRefreshThreadAbort(boolean abort) {
        mRefreshThreadAbort = abort;
    }

    private void initRefreshThread(int fps) {
        setRefreshThreadAbort(false);
        mRefreshRate = fps;
        mRefreshTime = 0l;
    }

    private int align(int x, int align)
    {
        return ((( x ) + (align) - 1) / (align) * (align));
    }

    private IXMCameraRecorderListener onXMPlayerRecorderListener = new IXMCameraRecorderListener() {
        @Override
        public void onImageReaderPrepared() {
            Log.i(TAG, "onImageReaderPrepared");
            mImageReaderPrepared = true;
            if(mXMMediaRecorderPrepared)
                mRecorder.start();
        }

        @Override
        public void onRecorderPrepared() {
            Log.i(TAG, "onRecorderPrepared");
            mXMMediaRecorderPrepared = true;
            if(mImageReaderPrepared)
                mRecorder.start();
        }

        @Override
        public void onRecorderStarted() {
            Log.i(TAG, "onRecorderStarted");
            if (mListener != null)
                mListener.onRecorderStarted();

            synchronized (mLock) {
                if(mRenderer != null) {
                    mRenderer.startPutData(true);
                }
            }
        }

        @Override
        public void onRecorderStopped() {
            Log.i(TAG, "onRecorderStopped");
            synchronized (mLock) {
                setRecordStatus(false);
            }

            if (mListener != null)
                mListener.onRecorderStopped();
        }

        @Override
        public void onRecorderError() {
            Log.e(TAG, "onRecorderError");
            synchronized (mLock) {
                setRecordStatus(false);
            }

            if (mListener != null)
                mListener.onRecorderError();
        }

        @Override
        public void onPreviewStarted() {
            Log.i(TAG, "onPreviewStarted");
            if (mListener != null)
                mListener.onPreviewStarted();
        }

        @Override
        public void onPreviewStopped() {
            Log.i(TAG, "onPreviewStopped");
            if (mListener != null)
                mListener.onPreviewStopped();
        }

        @Override
        public void onPreviewError() {
            Log.e(TAG, "onPreviewError");
            if (mListener != null)
                mListener.onPreviewError();
        }
    };

    private IXMImageListener onXMImageListener = new IXMImageListener() {
        @Override
        public void onImageSwitchCompleted() {
            synchronized (mLock) {
                setImage_l(mImagePath);
            }
        }
    };

    public interface IXMImageListener {
        void onImageSwitchCompleted();
    }

    class RefreshThread extends Thread {
        @Override
        public void run() {
            synchronized (mLock) {
                setRefreshStatus(true);
                mLock.notify();
            }

            while (!getRefreshThreadAbort()) {
                try {
                    Thread.sleep((int) (1000 / mRefreshRate), 0);
                    mRefreshTime ++;
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                requestRender();
            }

            synchronized (mLock) {
                setRefreshStatus(false);
                mLock.notify();
            }
            Log.i(TAG, "RefreshThread exit");
        }
    }

    public static class Rect {
        public float left;
        public float bottom;
        public float right;
        public float top;

        public Rect() {
        }

        public Rect(float left, float bottom, float right, float top) {
            this.left = left;
            this.bottom = bottom;
            this.right = right;
            this.top = top;
        }
    }
}
