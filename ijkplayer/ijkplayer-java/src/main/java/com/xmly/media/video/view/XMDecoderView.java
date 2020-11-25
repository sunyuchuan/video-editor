package com.xmly.media.video.view;

import android.annotation.TargetApi;
import android.content.Context;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;

import com.xmly.media.camera.view.recorder.IXMCameraRecorderListener;
import com.xmly.media.camera.view.recorder.XMMediaRecorder;
import com.xmly.media.camera.view.recorder.XMMediaRecorderParams;
import com.xmly.media.camera.view.utils.XMFilterType;

import java.io.IOException;
import java.util.HashMap;

/**
 * Created by sunyc on 19-3-1.
 */

public class XMDecoderView extends GLSurfaceView {
    private static final String TAG = "XMDecoderView";
    private static final int FPS = 25;
    private XMDecoderRenderer mRenderer = null;
    private XMMediaRecorder mRecorder = null;
    private XMMediaRecorderParams params = new XMMediaRecorderParams();
    private boolean useSoftEncoder = true;
    private boolean hasAudio = false;
    private boolean hasVideo = true;
    private volatile boolean mImageReaderPrepared = false;
    private volatile boolean mXMMediaRecorderPrepared = false;
    private int mVideoWidth = 0;
    private int mVideoHeight = 0;
    private int mVideoFps = 0;

    private XMDecoder mDecoder = null;
    private SpeedControlCallback mSpeedControlCallback = null;

    private XMDecoder.DecoderFeedback mFeedback = new XMDecoder.DecoderFeedback() {
        @Override
        public void decodeStarted() {
            Log.i(TAG, "decoder started");
        }

        @Override
        public void decodeStopped() {
            Log.i(TAG, "decoder stopped");
            stop();
        }
    };

    private String mStreamPath = null;
    private String mOutputPath = null;
    private Surface mSurface = null;
    private volatile boolean mDecoderRunning = false;

    private ISurfacePreparedListener mSurfacePreparedListener = new ISurfacePreparedListener() {
        @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
        @Override
        public void surfacePrepared(Surface surface) {
            if (mSurface != null) {
                mSurface.release();
            }

            try {
                mSurface = surface;

                mSpeedControlCallback.init();
                mDecoder.setDataSource(mStreamPath, surface);
                mDecoder.setLoopMode(false);
                mVideoFps = mDecoder.getVideoFps();
                mSpeedControlCallback.setFixedPlaybackRate(mVideoFps);

                mVideoWidth = mDecoder.getVideoWidth();
                mVideoHeight = mDecoder.getVideoHeight();
                if (mRenderer != null) {
                    mRenderer.onVideoSizeChanged(mVideoWidth, mVideoHeight);
                }

                startRecorder(mOutputPath, mVideoWidth, mVideoHeight);
                mDecoder.start();
            } catch (IOException e) {
                if (surface != null) {
                    surface.release();
                }
                mSurface = null;
                stop();
                e.printStackTrace();
            }
        }
    };

    private void initView() {
        mSpeedControlCallback = new SpeedControlCallback();
        try {
            mDecoder = new XMDecoder(mSpeedControlCallback, mFeedback);
        } catch (IOException e) {
            e.printStackTrace();
        }
        mRecorder = new XMMediaRecorder(useSoftEncoder, hasAudio, hasVideo);
        mRecorder.setListener(onXMPlayerRecorderListener);
        mRenderer = new XMDecoderRenderer(getContext().getApplicationContext(), mRecorder);
        mRenderer.setSurfacePreparedListener(mSurfacePreparedListener);
        mRenderer.setListener(onXMPlayerRecorderListener);
        mSpeedControlCallback.setRecorder(mRecorder);
        mSpeedControlCallback.setRenderer(mRenderer);
    }

    public XMDecoderView(Context context) {
        this(context, null);
    }

    public XMDecoderView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initView();
    }

    public void setSurfaceView() {
        if(mRenderer != null)
            mRenderer.setGLSurfaceView(this);
    }

    /**
     * start offline processing video
     * @param videoPath input video path
     * @param outputPath output video path
     * @throws IllegalStateException
     */
    public void start(String videoPath, String outputPath) throws IllegalStateException {
        synchronized (this) {
            if (getStatus()) {
                Log.d(TAG, "start : Decoder is runing, pls waiting decoder stop");
                throw new IllegalStateException();
            }

            mStreamPath = videoPath;
            mOutputPath = outputPath;
            mRenderer.prepareVideoSurface();
            setStatus(true);
        }
    }

    private void stopRecorder() {
        if (mRecorder != null) {
            mRecorder.stop();
        }
    }

    private void stopEncoder() {
        if (mRenderer != null) {
            mRenderer.startPutData(false);
        }
        setEncoderStatus(false);
    }

    private void stopDecoder() {
        if (mDecoder != null) {
            mDecoder.requestStop();
            mDecoder.waitForStop();
        }
    }

    /**
     * stop decoder
     */
    public void stop() {
        synchronized (this) {
            stopDecoder();
            stopEncoder();
            stopRecorder();
        }
    }

    private void releaseRecorder() {
        if (mRecorder != null) {
            mRecorder.release();
            mRecorder.setListener(null);
            mRecorder = null;
        }
    }

    private void releaseRenderer() {
        if (mRenderer != null) {
            mRenderer.release();
            mRenderer = null;
        }
    }

    private void releaseEncoder() {
        if (mRenderer != null) {
            mRenderer.startPutData(false);
        }
        setEncoderStatus(false);
    }

    private void releaseDecoder() {
        if (mDecoder != null) {
            mDecoder.requestStop();
            mDecoder.waitForStop();
            mDecoder = null;
        }

        if (mSurface != null) {
            mSurface.release();
            mSurface = null;
        }
    }

    /**
     * release decoder
     */
    public void release() {
        synchronized (this) {
            releaseDecoder();
            releaseEncoder();
            releaseRenderer();
            releaseRecorder();
        }
    }

    /**
     * Configuring video filters
     * @param filtertype filter type
     */
    public void setFilter(final XMFilterType filtertype) {
        Log.i(TAG,"setFilter filter type " + filtertype);
        if (mRenderer != null) {
            mRenderer.setFilter(filtertype);
        }
    }

    private void setEncoderStatus(boolean status) {
        if (mRenderer != null) {
            mRenderer.changeVideoEncoderStatus(status);
        }
    }

    private void startRecorder(String outputPath, int outputWidth, int outputHeight) {
        if (mRecorder != null) {
            Log.i(TAG, "startRecorder outputPath " + outputPath);
            setEncoderStatus(true);
            if (mRenderer != null) {
                mRenderer.startPutData(true);
            }
            mImageReaderPrepared = false;
            mXMMediaRecorderPrepared = false;

            HashMap<String, String> config = new HashMap<String, String>();
            config.put("width", String.valueOf(outputWidth));
            config.put("height", String.valueOf(outputHeight));
            config.put("bit_rate", String.valueOf(params.bitrate));
            config.put("fps", String.valueOf(mVideoFps));
            config.put("gop_size", String.valueOf((int) (params.gop_size * mVideoFps)));
            config.put("crf", String.valueOf(params.crf));
            config.put("multiple", String.valueOf(params.multiple));
            config.put("max_b_frames", String.valueOf(params.max_b_frames));
            config.put("CFR", String.valueOf(params.TRUE));
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

    private void setStatus(boolean running) {
        mDecoderRunning = running;
    }

    private boolean getStatus() {
        return mDecoderRunning;
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
        }

        @Override
        public void onRecorderStopped() {
            Log.i(TAG, "onRecorderStopped");
            synchronized (this) {
                setStatus(false);
            }
        }

        @Override
        public void onRecorderError() {
            Log.e(TAG, "onRecorderError");
            stop();
            synchronized (this) {
                setStatus(false);
            }
        }

        @Override
        public void onPreviewStarted() {
            Log.i(TAG, "onPreviewStarted");
        }

        @Override
        public void onPreviewStopped() {
            Log.i(TAG, "onPreviewStopped");
        }

        @Override
        public void onPreviewError() {
            Log.e(TAG, "onPreviewError");
        }
    };
}
