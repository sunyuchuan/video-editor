package com.xmly.media.video.view;

import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ConfigurationInfo;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.util.Log;

import com.xmly.media.camera.view.gpuimage.filter.GPUImageBlindsFilter;
import com.xmly.media.camera.view.gpuimage.filter.GPUImageFilter;
import com.xmly.media.camera.view.gpuimage.filter.GPUImageFilterFactory;
import com.xmly.media.camera.view.gpuimage.filter.GPUImageImageSwitchFilter;
import com.xmly.media.camera.view.gpuimage.filter.GPUImagePboFilter;
import com.xmly.media.camera.view.gpuimage.filter.GPUImageImageInputFilter;
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
 * Created by sunyc on 19-5-16.
 */

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class XMImageRenderer implements GLSurfaceView.Renderer {
    private static final String TAG = "XMImageRenderer";
    private FloatBuffer mGLCubeBuffer;
    private FloatBuffer mGLTextureBuffer;
    private FloatBuffer mGLVideoTextureBuffer;
    private FloatBuffer mDefaultGLCubeBuffer;
    private FloatBuffer mDefaultGLTextureBuffer;
    private GLSurfaceView mGLSurfaceView = null;
    private GPUImageImageInputFilter mInputFilter = null;
    private GPUImageFilter mFilter = null;
    private GPUImageFilter mLogoFilter = null;
    private GPUImageFilter mRenderFilter = null;
    private GPUImageFilter mRotateFilter = null;
    private GPUImagePboFilter mPboFilter = null;
    private XMFilterType mFilterType = XMFilterType.NONE;
    private IXMCameraRecorderListener mListener;
    private XMImageView.IXMImageListener mImageListener;
    private XMMediaRecorder mXMMediaRecorder = null;
    private final Queue<Runnable> mRunOnDraw;
    private int mOutputWidth;
    private int mOutputHeight;
    private int mVideoWidth;
    private int mVideoHeight;
    private int mImageWidth;
    private int mImageHeight;
    private boolean mIsPutting = false;

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

        mGLVideoTextureBuffer = ByteBuffer.allocateDirect(TextureRotationUtil.TEXTURE_NO_ROTATION.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        mGLVideoTextureBuffer.put(TextureRotationUtil.TEXTURE_NO_ROTATION).position(0);

        mDefaultGLCubeBuffer = ByteBuffer.allocateDirect(CUBE.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        mDefaultGLCubeBuffer.put(CUBE).position(0);

        mDefaultGLTextureBuffer = ByteBuffer.allocateDirect(TextureRotationUtil.TEXTURE_NO_ROTATION.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        mDefaultGLTextureBuffer.put(TextureRotationUtil.getRotation(Rotation.NORMAL, false, false)).position(0);
    }

    private boolean supportsOpenGLES2(final Context context) {
        final ActivityManager activityManager = (ActivityManager)
                context.getSystemService(Context.ACTIVITY_SERVICE);
        final ConfigurationInfo configurationInfo =
                activityManager.getDeviceConfigurationInfo();
        return configurationInfo.reqGlEsVersion >= 0x20000;
    }

    public XMImageRenderer(final Context context, XMMediaRecorder recorder) {
        if (!supportsOpenGLES2(context)) {
            throw new IllegalStateException("OpenGL ES 2.0 is not supported on this phone.");
        }
        GPUImageParams.context = context;
        mXMMediaRecorder = recorder;

        if(mInputFilter != null)
            mInputFilter.destroy();
        mInputFilter = new GPUImageImageInputFilter();
        if(mFilter != null)
            mFilter.destroy();
        mFilter = GPUImageFilterFactory.CreateFilter(mFilterType);
        if(mLogoFilter != null)
            mLogoFilter.destroy();
        mLogoFilter = GPUImageFilterFactory.CreateFilter(XMFilterType.NONE);
        if(mRotateFilter != null)
            mRotateFilter.destroy();
        mRotateFilter = GPUImageFilterFactory.CreateFilter(XMFilterType.NONE);
        if(mRenderFilter != null)
            mRenderFilter.destroy();
        mRenderFilter = GPUImageFilterFactory.CreateFilter(XMFilterType.NONE);

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

    public void setListener(IXMCameraRecorderListener l, XMImageView.IXMImageListener image_l) {
        mListener = l;
        mImageListener = image_l;
    }

    @Override
    public void onSurfaceCreated(final GL10 unused, final EGLConfig config) {
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1);
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        mInputFilter.init();
        mFilter.init();
        mLogoFilter.init();
        mRotateFilter.init();
        mRenderFilter.init();
    }

    @Override
    public void onSurfaceChanged(final GL10 gl, final int width, final int height) {
        mOutputWidth = align(width, 2);
        mOutputHeight = align(height, 2);
        mInputFilter.onOutputSizeChanged(mImageWidth, mImageHeight);
        mFilter.onOutputSizeChanged(mImageWidth, mImageHeight);
        mLogoFilter.onOutputSizeChanged(mVideoWidth, mVideoHeight);
        mRotateFilter.onOutputSizeChanged(mVideoWidth, mVideoHeight);
        mRenderFilter.onOutputSizeChanged(mOutputWidth, mOutputHeight);

        adjustImageScaling(mVideoWidth, mVideoHeight, mOutputWidth, mOutputHeight,
                Rotation.NORMAL, false, false, mGLTextureBuffer);
    }

    @Override
    public void onDrawFrame(final GL10 gl) {
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        runAll(mRunOnDraw);

        int texture = OpenGlUtils.NO_TEXTURE;
        mDefaultGLTextureBuffer.put(TextureRotationUtil.getRotation(Rotation.NORMAL, false, false)).position(0);
        texture = mInputFilter.onDrawToTexture(texture, mDefaultGLCubeBuffer, mDefaultGLTextureBuffer);
        texture = mFilter.onDrawToTexture(texture, mDefaultGLCubeBuffer, mDefaultGLTextureBuffer);
        texture = mLogoFilter.onDrawToTexture(texture, mGLCubeBuffer, mGLVideoTextureBuffer);
        texture = mRotateFilter.onDrawToTexture(texture, mDefaultGLCubeBuffer, mDefaultGLTextureBuffer);
        mRenderFilter.onDraw(texture, mGLCubeBuffer, mGLTextureBuffer);

        if (mPboFilter != null && mIsPutting) {
            mDefaultGLTextureBuffer.put(TextureRotationUtil.getRotation(Rotation.NORMAL, false, true)).position(0);
            mPboFilter.onDrawToPbo(texture, mDefaultGLCubeBuffer, mDefaultGLTextureBuffer);
        }
    }

    public void setVideoSize(int w, int h) {
        mVideoWidth = w;
        mVideoHeight = h;

        runOnDraw(mRunOnDraw, new Runnable() {
            @Override
            public void run() {
                mLogoFilter.onOutputSizeChanged(mVideoWidth, mVideoHeight);
                mRotateFilter.onOutputSizeChanged(mVideoWidth, mVideoHeight);
                if (mPboFilter != null) {
                    mPboFilter.onOutputSizeChanged(mVideoWidth, mVideoHeight);
                }
            }
        });
        adjustImageScaling(mImageWidth, mImageHeight, mVideoWidth, mVideoHeight,
                Rotation.NORMAL, false, false, mGLVideoTextureBuffer);

        adjustImageScaling(mVideoWidth, mVideoHeight, mOutputWidth, mOutputHeight,
                Rotation.NORMAL, false, false, mGLTextureBuffer);
    }

    public void setBlindsImage(Bitmap bmp) {
        mImageWidth = bmp.getWidth();
        mImageHeight = bmp.getHeight();
        runOnDraw(mRunOnDraw, new Runnable() {
            @Override
            public void run() {
                mInputFilter.onOutputSizeChanged(mImageWidth, mImageHeight);
                mFilter.onOutputSizeChanged(mImageWidth, mImageHeight);
            }
        });

        adjustImageScaling(mImageWidth, mImageHeight, mVideoWidth, mVideoHeight,
                Rotation.NORMAL, false, false, mGLVideoTextureBuffer);

        if (mFilter instanceof GPUImageBlindsFilter) {
            mFilter.setBitmap(bmp);
        }
    }

    public void setImage(Bitmap bmp) {
        mImageWidth = bmp.getWidth();
        mImageHeight = bmp.getHeight();
        runOnDraw(mRunOnDraw, new Runnable() {
            @Override
            public void run() {
                mInputFilter.onOutputSizeChanged(mImageWidth, mImageHeight);
                mFilter.onOutputSizeChanged(mImageWidth, mImageHeight);
            }
        });

        adjustImageScaling(mImageWidth, mImageHeight, mVideoWidth, mVideoHeight,
                Rotation.NORMAL, false, false, mGLVideoTextureBuffer);

        if (mFilter instanceof GPUImageImageSwitchFilter) {
            mFilter.onInitialized();
        }
        mInputFilter.setBitmap(bmp);
    }

    public void setLogo(final Bitmap bmp, final float[] rect) {
        runOnDraw(mRunOnDraw, new Runnable() {
            @Override
            public void run() {
                if (mLogoFilter != null) {
                    mLogoFilter.destroy();
                }
                mLogoFilter = GPUImageFilterFactory.CreateFilter(XMFilterType.FILTER_LOGO);
                mLogoFilter.init();
                GLES20.glUseProgram(mLogoFilter.getProgram());
                mLogoFilter.onOutputSizeChanged(mVideoWidth, mVideoHeight);
                mLogoFilter.onInputSizeChanged(mVideoWidth, mVideoHeight);
                mLogoFilter.setBitmap(bmp);
                mLogoFilter.setRectangleCoordinate(rect);
            }
        });
    }

    public void setFilter(final XMFilterType filtertype) {
        runOnDraw(mRunOnDraw, new Runnable() {
            @Override
            public void run() {
                if (mFilter != null) {
                    mFilter.destroy();
                }
                mFilter = GPUImageFilterFactory.CreateFilter(filtertype);
                if (mFilter instanceof GPUImageBlindsFilter) {
                    ((GPUImageBlindsFilter) mFilter).setListener(mImageListener);
                }
                mFilter.init();
                GLES20.glUseProgram(mFilter.getProgram());
                mFilter.onOutputSizeChanged(mImageWidth,mImageHeight);
                mFilter.onInputSizeChanged(mImageWidth, mImageHeight);
            }
        });
        mFilterType = filtertype;
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
                        mPboFilter.onInputSizeChanged(mVideoWidth, mVideoHeight);
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

    public int getOutputWidth() {
        return mOutputWidth;
    }

    public int getOutputHeight() {
        return mOutputHeight;
    }

    public void startPutData(boolean isPutting) {
        mIsPutting = isPutting;
        if (mPboFilter != null) {
            mPboFilter.startPutData(isPutting);
        }
    }

    public void release() {
        startPutData(false);
        changeVideoEncoderStatus(false);
    }

    public void cleanRunOnDraw() {
        if(mRunOnDraw != null)
            cleanAll(mRunOnDraw);
    }

    public void requestRender() {
        if (mGLSurfaceView != null) {
            mGLSurfaceView.requestRender();
        }
    }

    public void setRotationParam(final Rotation rotation, final boolean flipHorizontal,
                                  final boolean flipVertical) {
        if(rotation == Rotation.ROTATION_90 || rotation == Rotation.ROTATION_270) {
            setRotation(rotation, flipVertical, flipHorizontal);
        } else {
            setRotation(rotation, flipHorizontal, flipVertical);
        }
    }

    private void setRotation(final Rotation rotation,
                             final boolean flipHorizontal, final boolean flipVertical) {
        adjustImageScaling(mImageWidth, mImageHeight, mOutputWidth, mOutputHeight,
                rotation, flipHorizontal, flipVertical, mGLTextureBuffer);
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

    private int align(int x, int align) {
        return ((( x ) + (align) - 1) / (align) * (align));
    }
}
