package com.xmly.media.video.view;

import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ConfigurationInfo;
import android.graphics.Bitmap;
import android.hardware.Camera;
import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.util.Log;
import android.view.Surface;

import com.xmly.media.camera.view.gpuimage.filter.GPUImageCameraInputFilter;
import com.xmly.media.camera.view.gpuimage.filter.GPUImageFilter;
import com.xmly.media.camera.view.gpuimage.filter.GPUImageFilterFactory;
import com.xmly.media.camera.view.gpuimage.filter.GPUImageMixFilter;
import com.xmly.media.camera.view.gpuimage.filter.GPUImagePIPFilter;
import com.xmly.media.camera.view.gpuimage.filter.GPUImagePboFilter;
import com.xmly.media.camera.view.gpuimage.filter.GPUImagePlayerInputFilter;
import com.xmly.media.camera.view.recorder.IXMCameraRecorderListener;
import com.xmly.media.camera.view.recorder.XMMediaRecorder;
import com.xmly.media.camera.view.utils.GPUImageParams;
import com.xmly.media.camera.view.utils.OpenGlUtils;
import com.xmly.media.camera.view.utils.Rotation;
import com.xmly.media.camera.view.utils.TextureRotationUtil;
import com.xmly.media.camera.view.utils.XMFilterType;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.LinkedList;
import java.util.Queue;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Created by sunyc on 19-3-1.
 */

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class XMPlayerRenderer implements GLSurfaceView.Renderer, SurfaceTexture.OnFrameAvailableListener {
    private static final String TAG = "XMPlayerRenderer";
    private FloatBuffer mGLCubeBuffer;
    private FloatBuffer mGLTextureBuffer;
    private FloatBuffer mCameraGLTextureBuffer;
    private FloatBuffer mDefaultGLCubeBuffer;
    private FloatBuffer mDefaultGLTextureBuffer;
    private GLSurfaceView mGLSurfaceView = null;
    private SurfaceTexture mCameraSurfaceTexture = null;
    private SurfaceTexture mSurfaceTexture = null;
    private GPUImageCameraInputFilter mCameraInputFilter = null;
    private GPUImageFilter mCameraFilter = null;
    private GPUImagePlayerInputFilter mPlayerRendererInputFilter = null;
    private GPUImagePIPFilter mPipFilter = null;
    private GPUImageMixFilter mMixFilter = null;
    private GPUImageFilter mRenderFilter = null;
    private GPUImageFilter mRotateFilter = null;
    private GPUImagePboFilter mPboFilter = null;
    private XMFilterType mFilterType = XMFilterType.NONE;
    private ISurfacePreparedListener onSurfacePreparedListener;
    private IXMCameraRecorderListener mListener;
    private XMMediaRecorder mXMMediaRecorder = null;
    private final Queue<Runnable> mRunOnSetupCamera;
    private final Queue<Runnable> mRunOnDraw;
    private boolean updateTexImage = false;
    private int mCameraGLTextureId = OpenGlUtils.NO_TEXTURE;
    private int mGLTextureId = OpenGlUtils.NO_TEXTURE;
    private int mOutputWidth;
    private int mOutputHeight;
    private int mVideoWidth;
    private int mVideoHeight;
    private boolean mIsPutting = false;
    private int mCameraPreviewWidth = 960;
    private int mCameraPreviewHeight = 540;
    private int mCameraOuputWidth = 960;
    private int mCameraOuputHeight = 540;
    private float mCameraOutputAspectRatio = 960f/540f;
    private int mCameraOuptutFps = 15;
    private Rotation mRotation = Rotation.NORMAL;
    private boolean mFlipHorizontal = false;
    private boolean mFlipVertical = false;
    private volatile boolean mEnableSubtitle = false;

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

        mCameraGLTextureBuffer = ByteBuffer.allocateDirect(TextureRotationUtil.TEXTURE_NO_ROTATION.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        mCameraGLTextureBuffer.put(TextureRotationUtil.TEXTURE_NO_ROTATION).position(0);

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

    public XMPlayerRenderer(final Context context, XMMediaRecorder recorder) {
        if (!supportsOpenGLES2(context)) {
            throw new IllegalStateException("OpenGL ES 2.0 is not supported on this phone.");
        }
        GPUImageParams.context = context;
        mXMMediaRecorder = recorder;

        if(mCameraInputFilter != null)
            mCameraInputFilter.destroy();
        mCameraInputFilter = new GPUImageCameraInputFilter();
        if(mCameraFilter != null)
            mCameraFilter.destroy();
        mCameraFilter = GPUImageFilterFactory.CreateFilter(mFilterType);
        if(mPlayerRendererInputFilter != null)
            mPlayerRendererInputFilter.destroy();
        mPlayerRendererInputFilter = new GPUImagePlayerInputFilter();
        if(mPipFilter != null)
            mPipFilter.destroy();
        mPipFilter = new GPUImagePIPFilter();
        if(mMixFilter != null)
            mMixFilter.destroy();
        mMixFilter = new GPUImageMixFilter();
        if(mRotateFilter != null)
            mRotateFilter.destroy();
        mRotateFilter = GPUImageFilterFactory.CreateFilter(XMFilterType.NONE);
        if(mRenderFilter != null)
            mRenderFilter.destroy();
        mRenderFilter = GPUImageFilterFactory.CreateFilter(XMFilterType.NONE);

        mRunOnSetupCamera = new LinkedList<Runnable>();
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
        mCameraInputFilter.init();
        mCameraFilter.init();
        mPlayerRendererInputFilter.init();
        mPipFilter.init();
        mMixFilter.init();
        mRotateFilter.init();
        mRenderFilter.init();
    }

    @Override
    public void onSurfaceChanged(final GL10 gl, final int width, final int height) {
        mOutputWidth = align(width, 2);
        mOutputHeight = align(height, 2);
        mCameraOuputWidth = (int) (mCameraOuputHeight * mCameraOutputAspectRatio);
        mCameraInputFilter.onOutputSizeChanged(mCameraOuputWidth, mCameraOuputHeight);
        mCameraFilter.onOutputSizeChanged(mCameraOuputWidth, mCameraOuputHeight);
        mCameraFilter.onInputSizeChanged(mCameraOuputWidth, mCameraOuputHeight);
        mPlayerRendererInputFilter.onOutputSizeChanged(mVideoWidth, mVideoHeight);
        mPipFilter.onOutputSizeChanged(mVideoWidth, mVideoHeight);
        mMixFilter.onOutputSizeChanged(mVideoWidth, mVideoHeight);
        mRotateFilter.onOutputSizeChanged(mVideoWidth, mVideoHeight);
        mRenderFilter.onOutputSizeChanged(mOutputWidth, mOutputHeight);

        adjustImageScaling(mCameraPreviewWidth, mCameraPreviewHeight, mCameraOuputWidth, mCameraOuputHeight,
                mRotation, mFlipHorizontal, mFlipVertical, mCameraGLTextureBuffer);
        adjustImageScaling(mVideoWidth, mVideoHeight, mOutputWidth, mOutputHeight,
                Rotation.NORMAL, false, false, mGLTextureBuffer);
    }

    @Override
    public void onDrawFrame(final GL10 gl) {
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        runAll(mRunOnSetupCamera);
        runAll(mRunOnDraw);

        float[] mtx = new float[16];
        int cameraTex = OpenGlUtils.NO_TEXTURE, texture = OpenGlUtils.NO_TEXTURE;
        if (mCameraSurfaceTexture != null) {
            mCameraSurfaceTexture.getTransformMatrix(mtx);
            mCameraInputFilter.setTextureTransformMatrix(mtx);
            cameraTex = mCameraInputFilter.onDrawToTexture(mCameraGLTextureId, mDefaultGLCubeBuffer, mCameraGLTextureBuffer);
            mDefaultGLTextureBuffer.put(TextureRotationUtil.getRotation(Rotation.ROTATION_180, false, false)).position(0);
            cameraTex = mCameraFilter.onDrawToTexture(cameraTex, mDefaultGLCubeBuffer, mDefaultGLTextureBuffer);
        }

        if (mSurfaceTexture != null) {
            mDefaultGLTextureBuffer.put(TextureRotationUtil.getRotation(Rotation.ROTATION_180, true, false)).position(0);
            texture = mPlayerRendererInputFilter.onDrawToTexture(mGLTextureId, mDefaultGLCubeBuffer, mDefaultGLTextureBuffer);
            synchronized (this) {
                if (mEnableSubtitle) {
                    mDefaultGLTextureBuffer.put(TextureRotationUtil.getRotation(Rotation.NORMAL, false, false)).position(0);
                    texture = mMixFilter.onDrawToTexture(texture, mDefaultGLCubeBuffer, mDefaultGLTextureBuffer);
                    texture = mRotateFilter.onDrawToTexture(texture, mDefaultGLCubeBuffer, mDefaultGLTextureBuffer);
                }
            }
        }

        if (mCameraSurfaceTexture != null && texture != OpenGlUtils.NO_TEXTURE) {
            mPipFilter.setVideoTextureId(texture);
            mDefaultGLTextureBuffer.put(TextureRotationUtil.getRotation(Rotation.NORMAL, false, false)).position(0);
            texture = mPipFilter.onDrawToTexture(cameraTex, mDefaultGLCubeBuffer, mDefaultGLTextureBuffer);
            texture = mRotateFilter.onDrawToTexture(texture, mDefaultGLCubeBuffer, mDefaultGLTextureBuffer);
        }

        if (texture == OpenGlUtils.NO_TEXTURE) {
            mDefaultGLTextureBuffer.put(TextureRotationUtil.getRotation(Rotation.NORMAL, false, false)).position(0);
            mRenderFilter.onDraw(cameraTex, mDefaultGLCubeBuffer, mDefaultGLTextureBuffer);
        } else {
            mRenderFilter.onDraw(texture, mGLCubeBuffer, mGLTextureBuffer);
        }

        if (mPboFilter != null && mIsPutting) {
            mDefaultGLTextureBuffer.put(TextureRotationUtil.getRotation(Rotation.NORMAL, false, true)).position(0);
            mPboFilter.onDrawToPbo(texture, mDefaultGLCubeBuffer, mDefaultGLTextureBuffer);
        }

        synchronized (this) {
            if (mCameraSurfaceTexture == null) {
                if (mSurfaceTexture != null && updateTexImage) {
                    mSurfaceTexture.updateTexImage();
                    updateTexImage = false;
                }
            } else {
                if (updateTexImage) {
                    if (mSurfaceTexture != null) {
                        mSurfaceTexture.updateTexImage();
                    }
                    mCameraSurfaceTexture.updateTexImage();
                    updateTexImage = false;
                }
            }
        }
    }

    @Override
    synchronized public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        requestRender();
        updateTexImage = true;
    }

    public void setUpCamera(final Camera camera, final int degrees, final boolean flipHorizontal,
                            final boolean flipVertical) {
        cleanAll(mRunOnSetupCamera);
        runOnDraw(mRunOnSetupCamera, new Runnable() {
            @TargetApi(Build.VERSION_CODES.HONEYCOMB)
            @Override
            public void run() {
                if (camera != null) {
                    synchronized (camera) {
                        final Camera.Size previewSize = camera.getParameters().getPreviewSize();
                        mCameraPreviewWidth = previewSize.width;
                        mCameraPreviewHeight = previewSize.height;
                        mCameraOuputHeight = mCameraPreviewHeight;
                        mCameraOuputWidth = (int) (mCameraOuputHeight * mCameraOutputAspectRatio);
                        mCameraInputFilter.destroyFramebuffers();
                        mCameraInputFilter.onOutputSizeChanged(mCameraOuputWidth, mCameraOuputHeight);
                        mCameraFilter.destroyFramebuffers();
                        mCameraFilter.onOutputSizeChanged(mCameraOuputWidth, mCameraOuputHeight);
                        mCameraFilter.onInputSizeChanged(mCameraOuputWidth, mCameraOuputHeight);
                        Rotation rotation = Rotation.NORMAL;
                        switch (degrees) {
                            case 90:
                                rotation = Rotation.ROTATION_90;
                                break;
                            case 180:
                                rotation = Rotation.ROTATION_180;
                                break;
                            case 270:
                                rotation = Rotation.ROTATION_270;
                                break;
                        }
                        setRotationParam(rotation, flipHorizontal, flipVertical);

                        if (mCameraGLTextureId != OpenGlUtils.NO_TEXTURE) {
                            GLES20.glDeleteTextures(1, new int[]{mCameraGLTextureId}, 0);
                            mCameraGLTextureId = OpenGlUtils.NO_TEXTURE;
                        }
                        mCameraGLTextureId = OpenGlUtils.getTexturesID();
                        mCameraSurfaceTexture = new SurfaceTexture(mCameraGLTextureId);
                        if (mSurfaceTexture != null) {
                            mSurfaceTexture.setOnFrameAvailableListener(null);
                        }
                        mCameraSurfaceTexture.setOnFrameAvailableListener(XMPlayerRenderer.this);
                        try {
                            camera.setPreviewTexture(mCameraSurfaceTexture);
                            camera.startPreview();
                        } catch (IOException e) {
                            mListener.onPreviewError();
                            e.printStackTrace();
                        }
                    }
                }
            }
        });
        mListener.onPreviewStarted();
        requestRender();
    }

    public void setCameraOutputAspectRatio(float aspectRatio) {
        mCameraOutputAspectRatio = aspectRatio;
    }

    public void setPipRectCoordinate(float[] buffer) {
        if(mPipFilter != null) {
            mPipFilter.setRectangleCoordinate(buffer);
        }
    }

    public void setFilter(final XMFilterType filtertype) {
        runOnDraw(mRunOnDraw, new Runnable() {
            @Override
            public void run() {
                if (mCameraFilter != null) {
                    mCameraFilter.destroy();
                }
                mCameraFilter = GPUImageFilterFactory.CreateFilter(filtertype);
                mCameraFilter.init();
                GLES20.glUseProgram(mCameraFilter.getProgram());
                mCameraFilter.onOutputSizeChanged(mCameraOuputWidth, mCameraOuputHeight);
                mCameraFilter.onInputSizeChanged(mCameraOuputWidth, mCameraOuputHeight);
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
                mPipFilter.onOutputSizeChanged(mVideoWidth, mVideoHeight);
                mMixFilter.onOutputSizeChanged(mVideoWidth, mVideoHeight);
                mRotateFilter.onOutputSizeChanged(mVideoWidth, mVideoHeight);
                if (mPboFilter != null)
                    mPboFilter.onOutputSizeChanged(mVideoWidth, mVideoHeight);
            }
        });
        adjustImageScaling(mVideoWidth, mVideoHeight, mOutputWidth, mOutputHeight,
                Rotation.NORMAL, false, false, mGLTextureBuffer);
    }

    public void changeVideoEncoderStatus(final boolean isEncoding) {
        runOnDraw(mRunOnDraw, new Runnable() {
            @Override
            public void run() {
                if (isEncoding) {
                    if(mPboFilter == null) {
                        mPboFilter = new GPUImagePboFilter();
                        mPboFilter.init();
                        GLES20.glUseProgram(mPboFilter.getProgram());
                        mPboFilter.onOutputSizeChanged(mVideoWidth, mVideoHeight);
                        mPboFilter.setNativeRecorder(mXMMediaRecorder);
                        mPboFilter.startPutData(mIsPutting);
                        mListener.onImageReaderPrepared();
                    }
                } else {
                    if(mPboFilter != null) {
                        mPboFilter.destroy();
                        mPboFilter = null;
                    }
                }
            }
        });
    }

    public void startPutData(boolean isPutting) {
        mIsPutting = isPutting;
        if (mPboFilter != null) {
            mPboFilter.startPutData(isPutting);
        }
    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    public void prepareVideoSurface() {
        if (mGLTextureId != OpenGlUtils.NO_TEXTURE) {
            GLES20.glDeleteTextures(1, new int[]{mGLTextureId}, 0);
            mGLTextureId = OpenGlUtils.NO_TEXTURE;
        }
        mGLTextureId = OpenGlUtils.getTexturesID();
        mSurfaceTexture = new SurfaceTexture(mGLTextureId);
        if (mCameraSurfaceTexture == null) {
            mSurfaceTexture.setOnFrameAvailableListener(this);
        }

        Surface surface = new Surface(mSurfaceTexture);
        onSurfacePreparedListener.surfacePrepared(surface);
    }

    public void setSurfacePreparedListener(ISurfacePreparedListener onSurfacePreparedListener) {
        this.onSurfacePreparedListener = onSurfacePreparedListener;
    }

    public void loadSubBitmap(final Bitmap bitmap) {
        runOnDraw(mRunOnDraw, new Runnable() {
            @Override
            public void run() {
                synchronized (this) {
                    mMixFilter.setBitmap(bitmap);
                    mEnableSubtitle = true;
                }
            }
        });
    }

    public void stopSubtitle() {
        synchronized (this) {
            mEnableSubtitle = false;
        }
    }

    public void releaseCamera() {
        if (mCameraSurfaceTexture != null) {
            mCameraSurfaceTexture.setOnFrameAvailableListener(null);
        }
        if (mSurfaceTexture != null) {
            mSurfaceTexture.setOnFrameAvailableListener(this);
            requestRender();
        }

        if (mCameraGLTextureId != OpenGlUtils.NO_TEXTURE) {
            GLES20.glDeleteTextures(1, new int[]{mCameraGLTextureId}, 0);
            mCameraGLTextureId = OpenGlUtils.NO_TEXTURE;
        }
        mCameraSurfaceTexture = null;

        runOnDraw(mRunOnSetupCamera, new Runnable() {
            @Override
            public void run() {
                mCameraInputFilter.destroyFramebuffers();
                mCameraFilter.destroyFramebuffers();
            }
        });
    }

    public void releaseVideoSurface() {
        if (mGLTextureId != OpenGlUtils.NO_TEXTURE) {
            GLES20.glDeleteTextures(1, new int[]{mGLTextureId}, 0);
            mGLTextureId = OpenGlUtils.NO_TEXTURE;
        }
        mSurfaceTexture = null;
    }

    public void release() {
        startPutData(false);
        changeVideoEncoderStatus(false);

        releaseCamera();
        releaseVideoSurface();
    }

    private void requestRender() {
        if (mGLSurfaceView != null) {
            mGLSurfaceView.requestRender();
        }
    }

    private int align(int x, int align) {
        return ((( x ) + (align) - 1) / (align) * (align));
    }

    private void setRotationParam(final Rotation rotation, final boolean flipHorizontal,
                                  final boolean flipVertical) {
        if(rotation == Rotation.ROTATION_90 || rotation == Rotation.ROTATION_270) {
            setRotation(rotation, flipVertical, flipHorizontal);
        } else {
            setRotation(rotation, flipHorizontal, flipVertical);
        }
    }

    private void setRotation(final Rotation rotation,
                             final boolean flipHorizontal, final boolean flipVertical) {
        mFlipHorizontal = flipHorizontal;
        mFlipVertical = flipVertical;
        mRotation = rotation;

        adjustImageScaling(mCameraPreviewWidth, mCameraPreviewHeight, mCameraOuputWidth, mCameraOuputHeight,
                rotation, flipHorizontal, flipVertical, mCameraGLTextureBuffer);
    }

    private void adjustImageScaling(int input_w, int input_h, int output_w, int output_h,
                                    Rotation rotation, boolean flipHorizontal, boolean flipVertical,
                                    FloatBuffer textureBuffer) {
        if (input_w == 0 || input_h == 0 || output_w == 0 || output_h == 0) {
            return;
        }

        float outputWidth = output_w;
        float outputHeight = output_h;
        if (rotation == Rotation.ROTATION_270 || rotation == Rotation.ROTATION_90) {
            outputWidth = output_h;
            outputHeight = output_w;
        }

        float ratio1 = outputWidth / input_w;
        float ratio2 = outputHeight / input_h;
        float ratioMax = Math.max(ratio1, ratio2);
        int imageWidthNew = Math.round(input_w * ratioMax);
        int imageHeightNew = Math.round(input_h * ratioMax);

        float ratioWidth = imageWidthNew / outputWidth;
        float ratioHeight = imageHeightNew / outputHeight;

        float[] textureCords = TextureRotationUtil.getRotation(rotation, flipHorizontal, flipVertical);
        float distHorizontal = (1 - 1 / ratioWidth) / 2;
        float distVertical = (1 - 1 / ratioHeight) / 2;
        textureCords = new float[]{
                addDistance(textureCords[0], distHorizontal), addDistance(textureCords[1], distVertical),
                addDistance(textureCords[2], distHorizontal), addDistance(textureCords[3], distVertical),
                addDistance(textureCords[4], distHorizontal), addDistance(textureCords[5], distVertical),
                addDistance(textureCords[6], distHorizontal), addDistance(textureCords[7], distVertical),
        };

        textureBuffer.clear();
        textureBuffer.put(textureCords).position(0);
    }

    private float addDistance(float coordinate, float distance) {
        return coordinate == 0.0f ? distance : 1 - distance;
    }

    public void cleanRunOnSetupCamera() {
        if(mRunOnSetupCamera != null)
            cleanAll(mRunOnSetupCamera);
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
