package com.xmly.media.video.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.hardware.Camera;
import android.media.MediaPlayer;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import android.util.Log;

import com.xmly.media.camera.view.recorder.IXMCameraRecorderListener;
import com.xmly.media.camera.view.recorder.XMMediaRecorder;
import com.xmly.media.camera.view.recorder.XMMediaRecorderParams;
import com.xmly.media.camera.view.utils.CameraManager;
import com.xmly.media.camera.view.utils.ICameraCallback;
import com.xmly.media.camera.view.utils.XMFilterType;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created by sunyc on 19-3-1.
 */

public class XMPlayerView extends GLSurfaceView implements ICameraCallback {
    private static final String TAG = "XMPlayerView";
    private static final float VIEW_ASPECT_RATIO = 4f / 3f;
    private static final int SLEEP_TIME = 100; //ms
    private static final int FPS = 25;
    private float videoAspectRatio = VIEW_ASPECT_RATIO;
    private CameraManager mCamera = null;
    private XMPlayer mPlayer = null;
    private XMPlayerRenderer mRenderer = null;
    private XMMediaRecorder mRecorder = null;
    private XMMediaRecorderParams params = new XMMediaRecorderParams();
    private boolean useSoftEncoder = true;
    private boolean hasAudio = false;
    private boolean hasVideo = true;
    private volatile boolean isPlaying = false;
    private volatile boolean mImageReaderPrepared = false;
    private volatile boolean mXMMediaRecorderPrepared = false;
    private int mVideoWidth = 0;
    private int mVideoHeight = 0;
    private int mCameraPreviewWidth = 0;
    private int mCameraPreviewHeight = 0;
    private int mCameraOuptutFps = 0;
    private Rect mRect = null;
    private SubtitleParser mSubtitleParser = null;
    private Subtitle.TextCanvasParam mTextCanvasParam = null;
    private volatile boolean mSubThreadAbort = false;
    private ArrayList<Subtitle> mSubtitleList = null;
    private Subtitle mSubtitle = null;
    private volatile int mSubIndex = 0;

    private void initView() {
        mCamera = new CameraManager();
        mCamera.setCameraCallback(this);
        mCamera.setListener(onXMPlayerRecorderListener);
        mPlayer = new XMPlayer();
        mPlayer.setOnCompletionListener(mOnCompletionListener);
        mPlayer.setOnInfoListener(mOnInfoListener);
        mRecorder = new XMMediaRecorder(useSoftEncoder, hasAudio, hasVideo);
        mRecorder.setListener(onXMPlayerRecorderListener);
        mRenderer = new XMPlayerRenderer(getContext().getApplicationContext(), mRecorder);
        mRenderer.setSurfacePreparedListener(mPlayer);
        mRenderer.setListener(onXMPlayerRecorderListener);
        mSubtitleParser = new SubtitleParser();
    }

    public XMPlayerView(Context context) {
        this(context, null);
    }

    public XMPlayerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initView();
    }

    public void setSurfaceView() {
        if(mRenderer != null)
            mRenderer.setGLSurfaceView(this);
    }

    public void drawSubtitle(String str, String headImage) {
        if (mRenderer != null && mSubtitleParser != null) {
            Bitmap bmp = mSubtitleParser.drawTextToBitmap(str, headImage);
            if (bmp != null) {
                mRenderer.loadSubBitmap(bmp);
            } else {
                invisibleSubtitle();
            }
        }
    }

    public long getCurrentPosition() {
        if (mPlayer != null) {
            return mPlayer.getCurrentPosition();
        }

        return 0;
    }

    public long getDuration() {
        if (mPlayer != null) {
            return mPlayer.getDuration();
        }

        return 0;
    }

    private void setSubtitleParam(Subtitle.TextCanvasParam param, ArrayList<Subtitle> list) {
        mSubtitleList = list;
        if (mSubtitleParser != null) {
            mSubtitleParser.initSubtitleParser(param);
        }
    }

    public void start(String videoPath, String outputPath, Subtitle.TextCanvasParam param, ArrayList<Subtitle> list) throws IllegalStateException {
        synchronized (this) {
            if (getStatus()) {
                Log.d(TAG, "startPlayer : player is runing, pls waiting player stop");
                throw new IllegalStateException();
            }

            if (mPlayer != null) {
                mPlayer.init();
                try {
                    mPlayer.setDataSource(videoPath);
                } catch (Exception e) {
                    Log.e(TAG, "Exception " + e.toString());
                    return;
                }
                mVideoWidth = mPlayer.getVideoWidth();
                mVideoHeight = mPlayer.getVideoHeight();
                if (param != null) {
                    param.setCanvasSize(mVideoWidth, mVideoHeight);
                }
                setSubtitleParam(param, list);
                setPipRectCoordinate(mRect);
                startRecorder(outputPath, mVideoWidth, mVideoHeight);
                if (mRenderer != null) {
                    mRenderer.prepareVideoSurface();
                    mRenderer.onVideoSizeChanged(mVideoWidth, mVideoHeight);
                }
                calculateVideoAspectRatio(mVideoWidth, mVideoHeight);
            }

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

    private void stopPlayer() {
        if (mPlayer != null) {
            mPlayer.stop();
            mPlayer.release();
        }
        if (mRenderer != null) {
            mRenderer.releaseVideoSurface();
        }

        mVideoWidth = 0;
        mVideoHeight = 0;
        setPipRectCoordinate(mRect);
        if (mCameraPreviewHeight != 0 && mRect != null) {
            float aspectRatio = (mRect.right - mRect.left) / (mRect.top - mRect.bottom);
            calculateVideoAspectRatio((int) (aspectRatio * mCameraPreviewHeight), mCameraPreviewHeight);
        }
    }

    public void stop() {
        setSubThreadAbort(true);
        synchronized (this) {
            stopPlayer();
            stopEncoder();
            stopRecorder();
            //stopCameraPreview();
        }
    }

    private void releaseSubtitleParser() {
        if (mSubtitleParser != null) {
            mSubtitleParser.release();
            mSubtitleParser = null;
        }
    }

    private void releaseCamera() {
        if (mCamera != null)
            mCamera.releaseInstance();
    }

    private void releaseRenderer() {
        if (mRenderer != null) {
            mRenderer.release();
            mRenderer = null;
        }
    }

    private void releasePlayer() {
        if(mPlayer != null) {
            mPlayer.release();
            mPlayer = null;
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
        setSubThreadAbort(true);
        synchronized (this) {
            releaseSubtitleParser();
            releaseEncoder();
            releaseRecorder();
            releasePlayer();
            releaseRenderer();
            releaseCamera();
        }
    }

    public void setWindowRotation(int windowRotation) {
        if (mCamera != null)
            mCamera.setWindowRotation(windowRotation);
    }

    public void setExpectedFps(int fps) {
        if (mCamera != null)
            mCamera.setExpectedFps(fps);
    }

    public void setExpectedResolution(int w, int h) {
        if (mCamera != null)
            mCamera.setExpectedResolution(w, h);
    }

    /*Turn on camera preview*/
    public void startCameraPreview() {
        if(mRenderer != null)
            mRenderer.cleanRunOnSetupCamera();

        if (mCamera != null)
            mCamera.onResume();

        //clearAnimation();
        //setVisibility(VISIBLE);
    }

    /*stop camera preview*/
    public void stopCameraPreview() {
        mRect = null;
        mCameraPreviewWidth = 0;
        mCameraPreviewHeight = 0;
        mCameraOuptutFps = 0;

        if(mRenderer != null) {
            mRenderer.cleanRunOnSetupCamera();
            mRenderer.releaseCamera();
        }

        //setVisibility(GONE);
        //clearAnimation();

        if (mCamera != null)
            mCamera.onRelease();
    }

    /*Switch to front or rear camera*/
    public void switchCamera() {
        if (mCamera != null) {
            mCamera.switchCamera();
            requestRender();
        }
    }

    @Override
    public void setUpCamera(final Camera camera, final int degrees, final boolean flipHorizontal,
                            final boolean flipVertical) {
        mCameraPreviewWidth = camera.getParameters().getPreviewSize().width;
        mCameraPreviewHeight = camera.getParameters().getPreviewSize().height;

        if (!getStatus()) {
            if (mRect != null) {
                float aspectRatio = (mRect.right - mRect.left) / (mRect.top - mRect.bottom);
                calculateVideoAspectRatio((int) (aspectRatio * mCameraPreviewHeight), mCameraPreviewHeight);
            }
        }

        int[] range = new int[2];
        camera.getParameters().getPreviewFpsRange(range);
        mCameraOuptutFps = range[0] / 1000;
        if(range[1] != range[0])
        {
            Log.w(TAG, "camera output fps is dynamic, range from " + range[0] + " to " + range[1]);
            mCameraOuptutFps = 15;
        }
        Log.i(TAG, "PreviewSize = " + mCameraPreviewWidth + "x" + mCameraPreviewHeight + " mCameraOuptutFps " + mCameraOuptutFps);

        if (mRenderer != null)
            mRenderer.setUpCamera(camera, degrees, flipHorizontal, flipVertical);
    }

    public void setPipRectCoordinate(Rect rect) {
        if (rect == null) {
            Log.e(TAG, "Rect is null");
            return;
        }

        mRect = rect;
        float[] buffer = new float[4];
        buffer[0] = rect.left;
        buffer[1] = 1.0f - rect.top;
        buffer[2] = rect.right;
        buffer[3] = 1.0f - rect.bottom;

        if (mRenderer != null) {
            float aspectRatio = (buffer[2] - buffer[0]) / (buffer[3] - buffer[1]);
            mRenderer.setCameraOutputAspectRatio(aspectRatio);
        }

        if (mVideoWidth != 0) {
            buffer[2] = (mVideoHeight * (buffer[2] - buffer[0]) + mVideoWidth * buffer[0]) / mVideoWidth;
        }
        if(mRenderer != null) {
            mRenderer.setPipRectCoordinate(buffer);
        }
    }

    public void setFilter(final XMFilterType filtertype) {
        Log.i(TAG,"setFilter filter type " + filtertype);
        if (mRenderer != null) {
            mRenderer.setFilter(filtertype);
        }
    }

    private void calculateVideoAspectRatio(int videoWidth, int videoHeight) {
        if (videoWidth > 0 && videoHeight > 0) {
            videoAspectRatio = (float) videoWidth / videoHeight;
        }

        requestLayout();
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        double currentAspectRatio = (double) widthSize / heightSize;
        if (currentAspectRatio > videoAspectRatio) {
            widthSize = (int) (heightSize * videoAspectRatio);
        } else {
            heightSize = (int) (widthSize / videoAspectRatio);
        }

        super.onMeasure(MeasureSpec.makeMeasureSpec(widthSize, widthMode),
                MeasureSpec.makeMeasureSpec(heightSize, heightMode));
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
            mImageReaderPrepared = false;
            mXMMediaRecorderPrepared = false;

            HashMap<String, String> config = new HashMap<String, String>();
            config.put("width", String.valueOf(outputWidth));
            config.put("height", String.valueOf(outputHeight));
            config.put("bit_rate", String.valueOf(mPlayer.getVideoBitRate()));
            config.put("fps", String.valueOf(mPlayer.getVideoFps()));
            config.put("gop_size", String.valueOf((int) (params.gop_size * mPlayer.getVideoFps())));
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

    private void invisibleSubtitle() {
        if (mRenderer != null) {
            mRenderer.stopSubtitle();
        }
    }

    private void initSubThread() {
        setSubThreadAbort(false);
        mSubIndex = 0;
        if (mSubtitleList != null && mSubIndex < mSubtitleList.size()) {
            mSubtitle = mSubtitleList.get(mSubIndex);
        }
    }

    private synchronized void setSubThreadAbort(boolean abort) {
        mSubThreadAbort = abort;
    }

    private synchronized boolean getSubThreadAbort() {
        return mSubThreadAbort;
    }

    private void setStatus(boolean running) {
        isPlaying = running;
    }

    private boolean getStatus() {
        return isPlaying;
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

    class SubtitleThread extends Thread {
        @Override
        public void run() {
            int prevStartTime = -1;
            int prevEndTime = -1;
            while (!getSubThreadAbort()) {
                if (mSubtitle != null) {
                    if (getCurrentPosition() >= mSubtitle.start_time && getCurrentPosition() <= mSubtitle.end_time) {
                        if (prevStartTime != mSubtitle.start_time) {
                            prevStartTime = mSubtitle.start_time;
                            drawSubtitle(mSubtitle.str, mSubtitle.headImagePath);
                        }
                    } else if (getCurrentPosition() >= mSubtitle.end_time) {
                        if (prevEndTime != mSubtitle.end_time) {
                            prevEndTime = mSubtitle.end_time;
                            invisibleSubtitle();
                            mSubIndex++;
                            if (mSubIndex < mSubtitleList.size()) {
                                mSubtitle = mSubtitleList.get(mSubIndex);
                            } else {
                                Log.i(TAG, "mSubIndex is invalid");
                                break;
                            }
                        }
                    }
                } else {
                    Log.i(TAG, "mSubtitle null");
                    break;
                }

                try {
                    Thread.sleep(SLEEP_TIME, 0);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            Log.i(TAG, "SubtitleThread exit");
        }
    }

    private MediaPlayer.OnInfoListener mOnInfoListener =
            new MediaPlayer.OnInfoListener() {
                @Override
                public boolean onInfo(MediaPlayer mp, int what, int extra) {
                    Log.i(TAG, "what " + what + ", extra " + extra);
                    switch (what) {
                        case IMediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START:
                            initSubThread();
                            Thread thread = new SubtitleThread();
                            thread.start();

                            if (mRenderer != null) {
                                mRenderer.startPutData(true);
                            }
                            break;
                        default:
                            break;
                    }
                    return true;
                }
            };

    private MediaPlayer.OnCompletionListener mOnCompletionListener =
            new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    Log.i(TAG, "onCompletion");
                    stop();
                }
            };

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
