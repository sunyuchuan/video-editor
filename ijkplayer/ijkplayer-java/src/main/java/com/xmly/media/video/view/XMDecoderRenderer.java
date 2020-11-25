package com.xmly.media.video.view;

import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ConfigurationInfo;
import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.util.Log;
import android.view.Surface;

import com.xmly.media.camera.view.gpuimage.filter.GPUImageFilter;
import com.xmly.media.camera.view.gpuimage.filter.GPUImageFilterFactory;
import com.xmly.media.camera.view.gpuimage.filter.GPUImagePboFilter;
import com.xmly.media.camera.view.gpuimage.filter.GPUImagePlayerInputFilter;
import com.xmly.media.camera.view.recorder.IXMCameraRecorderListener;
import com.xmly.media.camera.view.recorder.XMMediaRecorder;
import com.xmly.media.camera.view.utils.GPUImageParams;
import com.xmly.media.camera.view.utils.OpenGlUtils;
import com.xmly.media.camera.view.utils.Rotation;
import com.xmly.media.camera.view.utils.TextureRotationUtil;
import com.xmly.media.camera.view.utils.XMFilterType;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.LinkedList;
import java.util.Queue;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Created by sunyc on 19-4-3.
 */

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class XMDecoderRenderer implements GLSurfaceView.Renderer, SurfaceTexture.OnFrameAvailableListener {
    private static final String TAG = "XMDecoderRenderer";
    private FloatBuffer mGLCubeBuffer;
    private FloatBuffer mGLTextureBuffer;
    private FloatBuffer mDefaultGLCubeBuffer;
    private FloatBuffer mDefaultGLTextureBuffer;
    private GLSurfaceView mGLSurfaceView = null;
    private SurfaceTexture mSurfaceTexture = null;
    private GPUImagePlayerInputFilter mPlayerRendererInputFilter = null;
    private GPUImageFilter mFilter = null;
    private GPUImagePboFilter mPboFilter = null;
    private XMFilterType mFilterType = XMFilterType.NONE;
    private ISurfacePreparedListener onSurfacePreparedListener;
    private IXMCameraRecorderListener mListener;
    private final Queue<Runnable> mRunOnDraw;
    private boolean updateTexImage = false;
    private int mGLTextureId = OpenGlUtils.NO_TEXTURE;
    private int mOutputWidth;
    private int mOutputHeight;
    private int mVideoWidth; //Faster encoding when it is even
    private int mVideoHeight;
    private volatile boolean isEncoding = false;
    private volatile int mDrawFrameNums = 0;

    private static final float CUBE[] = {
            -1.0f, -1.0f,
            1.0f, -1.0f,
            -1.0f, 1.0f,
            1.0f, 1.0f,
    };

    private void initBuffer() {
        mGLCubeBuffer = ByteBuffer.allocateDirect(CUBE.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        mGLCubeBuffer.put(CUBE).position(0);

        mGLTextureBuffer = ByteBuffer.allocateDirect(TextureRotationUtil.TEXTURE_NO_ROTATION.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        mGLTextureBuffer.put(TextureRotationUtil.TEXTURE_NO_ROTATION).position(0);

        mDefaultGLCubeBuffer = ByteBuffer.allocateDirect(CUBE.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        mDefaultGLCubeBuffer.put(CUBE).position(0);

        mDefaultGLTextureBuffer = ByteBuffer.allocateDirect(TextureRotationUtil.TEXTURE_NO_ROTATION.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        mDefaultGLTextureBuffer.put(TextureRotationUtil.getRotation(Rotation.ROTATION_180, true, false)).position(0);
    }

    private boolean supportsOpenGLES2(final Context context) {
        final ActivityManager activityManager = (ActivityManager)
                context.getSystemService(Context.ACTIVITY_SERVICE);
        final ConfigurationInfo configurationInfo =
                activityManager.getDeviceConfigurationInfo();
        return configurationInfo.reqGlEsVersion >= 0x20000;
    }

    public XMDecoderRenderer(final Context context, XMMediaRecorder recorder) {
        if (!supportsOpenGLES2(context)) {
            throw new IllegalStateException("OpenGL ES 2.0 is not supported on this phone.");
        }
        GPUImageParams.context = context;

        if(mPlayerRendererInputFilter != null)
            mPlayerRendererInputFilter.destroy();
        mPlayerRendererInputFilter = new GPUImagePlayerInputFilter();
        if(mFilter != null)
            mFilter.destroy();
        mFilter = GPUImageFilterFactory.CreateFilter(mFilterType);
        if(mPboFilter != null)
            mPboFilter.destroy();
        mPboFilter = new GPUImagePboFilter();
        mPboFilter.setNativeRecorder(recorder);

        mRunOnDraw = new LinkedList<Runnable>();
        initBuffer();
    }

    public void setGLSurfaceView(final GLSurfaceView view) {
        if (mGLSurfaceView != view) {
            mGLSurfaceView = view;
            mGLSurfaceView.setEGLContextClientVersion(2);
            mGLSurfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0);
            mGLSurfaceView.getHolder().setFormat(PixelFormat.RGBA_8888);
            mGLSurfaceView.setRenderer(this);
            mGLSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
            mGLSurfaceView.requestRender();
        } else {
            Log.w(TAG, "GLSurfaceView already exists");
        }
    }

    public void setListener(IXMCameraRecorderListener l) {
        mListener = l;
    }

    @Override
    public void onSurfaceCreated(final GL10 unused, final EGLConfig config) {
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1);
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        mPlayerRendererInputFilter.init();
        mFilter.init();
        mPboFilter.init();
    }

    @Override
    public void onSurfaceChanged(final GL10 gl, final int width, final int height) {
        mOutputWidth = align(width, 2);
        mOutputHeight = align(height, 2);
        mPlayerRendererInputFilter.onOutputSizeChanged(mVideoWidth, mVideoHeight);
        mFilter.onOutputSizeChanged(mVideoWidth, mVideoHeight);
        mFilter.onInputSizeChanged(mVideoWidth, mVideoHeight);
        mPboFilter.onOutputSizeChanged(mVideoWidth, mVideoHeight);
        adjustImageScaling();
    }

    @Override
    public void onDrawFrame(final GL10 gl) {
        if (isEncoding) {
            GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
            runAll(mRunOnDraw);

            mDefaultGLTextureBuffer.put(TextureRotationUtil.getRotation(Rotation.ROTATION_180, true, false)).position(0);
            int texture = mPlayerRendererInputFilter.onDrawToTexture(mGLTextureId, mDefaultGLCubeBuffer, mDefaultGLTextureBuffer);
            mDefaultGLTextureBuffer.put(TextureRotationUtil.getRotation(Rotation.NORMAL, false, true)).position(0);
            texture = mFilter.onDrawToTexture(texture, mDefaultGLCubeBuffer, mDefaultGLTextureBuffer);
            mPboFilter.onDrawToPbo(texture, mDefaultGLCubeBuffer, mDefaultGLTextureBuffer);
        }

        synchronized (this) {
            if (mSurfaceTexture != null && updateTexImage) {
                mSurfaceTexture.updateTexImage();
                updateTexImage = false;
            }
        }

        addDrawFrameNums();
    }

    @Override
    synchronized public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        requestRender();
        updateTexImage = true;
    }

    private synchronized void setDrawFrameNums(int num) {
        mDrawFrameNums = num;
    }

    private synchronized void addDrawFrameNums() {
        mDrawFrameNums ++;
    }

    public synchronized int getDrawFrameNums() {
        return mDrawFrameNums;
    }

    public void setFilter(final XMFilterType filtertype) {
        runOnDraw(mRunOnDraw, new Runnable() {
            @Override
            public void run() {
                if (mFilter != null) {
                    mFilter.destroy();
                }
                mFilter = GPUImageFilterFactory.CreateFilter(filtertype);
                mFilter.init();
                GLES20.glUseProgram(mFilter.getProgram());
                mFilter.onOutputSizeChanged(mVideoWidth, mVideoHeight);
                mFilter.onInputSizeChanged(mVideoWidth, mVideoHeight);
            }
        });
        mFilterType = filtertype;
        requestRender();
    }

    public void onVideoSizeChanged(int width, int height) {
        mVideoWidth = width;
        mVideoHeight = height;
        runOnDraw(mRunOnDraw, new Runnable() {
            @Override
            public void run() {
                mPlayerRendererInputFilter.onOutputSizeChanged(mVideoWidth, mVideoHeight);
                mFilter.onOutputSizeChanged(mVideoWidth, mVideoHeight);
                mFilter.onInputSizeChanged(mVideoWidth, mVideoHeight);
                mPboFilter.onOutputSizeChanged(mVideoWidth, mVideoHeight);
            }
        });
        adjustImageScaling();
    }

    public void changeVideoEncoderStatus(final boolean isEncoding) {
        this.isEncoding = isEncoding;
        runOnDraw(mRunOnDraw, new Runnable() {
            @Override
            public void run() {
                if (isEncoding) {
                    mListener.onImageReaderPrepared();
                }
            }
        });
    }

    public void startPutData(boolean isPutting) {
        mPboFilter.startPutData(isPutting);
    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    public void prepareVideoSurface() {
        setDrawFrameNums(0);
        if (mGLTextureId != OpenGlUtils.NO_TEXTURE) {
            GLES20.glDeleteTextures(1, new int[]{mGLTextureId}, 0);
            mGLTextureId = OpenGlUtils.NO_TEXTURE;
        }
        mGLTextureId = OpenGlUtils.getTexturesID();
        mSurfaceTexture = new SurfaceTexture(mGLTextureId);
        mSurfaceTexture.setOnFrameAvailableListener(this);

        Surface surface = new Surface(mSurfaceTexture);
        onSurfacePreparedListener.surfacePrepared(surface);
    }

    public void setSurfacePreparedListener(ISurfacePreparedListener onSurfacePreparedListener) {
        this.onSurfacePreparedListener = onSurfacePreparedListener;
    }

    public void release() {
        setDrawFrameNums(0);
        startPutData(false);
        changeVideoEncoderStatus(false);

        if (mGLTextureId != OpenGlUtils.NO_TEXTURE) {
            GLES20.glDeleteTextures(1, new int[]{mGLTextureId}, 0);
            mGLTextureId = OpenGlUtils.NO_TEXTURE;
        }
    }

    private void requestRender() {
        if (mGLSurfaceView != null) {
            mGLSurfaceView.requestRender();
        }
    }

    private int align(int x, int align) {
        return ((( x ) + (align) - 1) / (align) * (align));
    }

    private void adjustImageScaling() {
        float outputWidth = mOutputWidth;
        float outputHeight = mOutputHeight;

        float ratio1 = outputWidth / mVideoWidth;
        float ratio2 = outputHeight / mVideoHeight;
        float ratioMax = Math.max(ratio1, ratio2);
        int imageWidthNew = Math.round(mVideoWidth * ratioMax);
        int imageHeightNew = Math.round(mVideoHeight * ratioMax);

        float ratioWidth = imageWidthNew / outputWidth;
        float ratioHeight = imageHeightNew / outputHeight;

        float[] cube = CUBE;
        float[] textureCords = TextureRotationUtil.TEXTURE_NO_ROTATION;

        float distHorizontal = (1 - 1 / ratioWidth) / 2;
        float distVertical = (1 - 1 / ratioHeight) / 2;
        textureCords = new float[] {
                addDistance(textureCords[0], distHorizontal), addDistance(textureCords[1], distVertical),
                addDistance(textureCords[2], distHorizontal), addDistance(textureCords[3], distVertical),
                addDistance(textureCords[4], distHorizontal), addDistance(textureCords[5], distVertical),
                addDistance(textureCords[6], distHorizontal), addDistance(textureCords[7], distVertical),
        };

        mGLCubeBuffer.clear();
        mGLCubeBuffer.put(cube).position(0);
        mGLTextureBuffer.clear();
        mGLTextureBuffer.put(textureCords).position(0);
    }

    private float addDistance(float coordinate, float distance) {
        return coordinate == 0.0f ? distance : 1 - distance;
    }

    private void cleanAll(Queue<Runnable> queue) {
        synchronized (queue) {
            while (!queue.isEmpty()) {
                queue.poll();
            }
        }
    }

    private void runAll(Queue<Runnable> queue) {
        synchronized (queue) {
            while (!queue.isEmpty()) {
                queue.poll().run();
            }
        }
    }

    private void runOnDraw(Queue<Runnable> queue, final Runnable runnable) {
        synchronized (queue) {
            queue.add(runnable);
        }
    }
}
