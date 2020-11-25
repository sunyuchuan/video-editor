package com.xmly.media.video.view;

import android.annotation.TargetApi;
import android.content.Context;
import android.hardware.Camera;
import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.os.Build;

import com.xmly.media.camera.view.detection.XMMtcnnNcnn;
import com.xmly.media.gles.filter.GPUImageFaceStickerFilter;
import com.xmly.media.gles.filter.GPUImageFilter;
import com.xmly.media.gles.filter.GPUImageFilterFactory;
import com.xmly.media.gles.filter.GPUImageYUY2PixelCopierFilter;
import com.xmly.media.camera.view.recorder.XMMediaRecorder;
import com.xmly.media.gles.utils.OpenGlUtils;
import com.xmly.media.gles.utils.Rotation;
import com.xmly.media.gles.utils.TextureRotationUtil;
import com.xmly.media.gles.utils.XMFilterType;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.Queue;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Created by sunyc on 19-3-1.
 */

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class XMCameraRenderer extends XMBaseRenderer implements Camera.PreviewCallback {
    private static final String TAG = "XMCameraRenderer";
    private XMFilterType mFilterType = XMFilterType.NONE;
    private final Queue<Runnable> mCameraRunOnDraw;
    private int mCameraGLTextureId = OpenGlUtils.NO_TEXTURE;
    private int mCameraPreviewWidth = 960;
    private int mCameraPreviewHeight = 540;
    private int mCameraOutputWidth = 960;
    private int mCameraOutputHeight = 540;
    private int mCameraOuptutFps = 15;
    private Rotation mRotation = Rotation.NORMAL;
    private boolean mFlipHorizontal = false;
    private boolean mFlipVertical = false;

    private byte[] mYuvPreviewFrame;
    private ByteBuffer mRenderBuffer = null;

    public XMCameraRenderer(final Context context, XMMediaRecorder recorder) {
        super(context, recorder);
        releaseFilters();
        initFilterArrays();
        mFilterArrays.put(RenderIndex.RotateIndex, GPUImageFilterFactory.CreateFilter(XMFilterType.NONE));
        mFilterArrays.put(RenderIndex.FilterIndex, GPUImageFilterFactory.CreateFilter(mFilterType));
        mFilterArrays.put(RenderIndex.DisplayIndex, GPUImageFilterFactory.CreateFilter(XMFilterType.NONE));
        mFilterArrays.put(RenderIndex.DownloadIndex, new GPUImageYUY2PixelCopierFilter(mRecorder));
        mCameraRunOnDraw = new LinkedList<Runnable>();
    }

    @Override
    public void onSurfaceCreated(final GL10 unused, final EGLConfig config) {
        super.onSurfaceCreated(unused, config);
        initFilters();
    }

    @Override
    public void onSurfaceChanged(final GL10 gl, final int width, final int height) {
        super.onSurfaceChanged(gl, width, height);
        filtersSizeChanged();

        adjustImageScaling(mCameraOutputWidth, mCameraOutputHeight, mOutputWidth, mOutputHeight,
                Rotation.NORMAL, false, false, mGLTextureBuffer);
    }

    @Override
    public void onDrawFrame(final GL10 gl) {
        super.onDrawFrame(gl);
        runAll(mCameraRunOnDraw);

        int cameraTex = OpenGlUtils.NO_TEXTURE;
        mDefaultGLTextureBuffer.put(TextureRotationUtil.getRotation(Rotation.NORMAL, false, false)).position(0);
        if (mFilterArrays.get(RenderIndex.FilterIndex) != null) {
            if (mFilterArrays.get(RenderIndex.FilterIndex) instanceof GPUImageFaceStickerFilter) {
                mFilterArrays.get(RenderIndex.FilterIndex).setRectangleCoordinate(normalized(XMMtcnnNcnn.getInstance().get_face_position()));
            }
            cameraTex = mFilterArrays.get(RenderIndex.FilterIndex).onDrawToTexture(mGLTextureId, mDefaultGLCubeBuffer, mDefaultGLTextureBuffer);
        }
        if(mFilterArrays.get(RenderIndex.RotateIndex) != null) {
            cameraTex = mFilterArrays.get(RenderIndex.RotateIndex).onDrawToTexture(cameraTex, mDefaultGLCubeBuffer, mDefaultGLTextureBuffer);
        }
        if(mFilterArrays.get(RenderIndex.DisplayIndex) != null) {
            mFilterArrays.get(RenderIndex.DisplayIndex).onDraw(cameraTex, mDefaultGLCubeBuffer, mGLTextureBuffer);
        }
        if (mGPUCopierEnable) {
            if (mFilterArrays.get(RenderIndex.DownloadIndex) != null) {
                mDefaultGLTextureBuffer.put(TextureRotationUtil.getRotation(Rotation.NORMAL, false, true)).position(0);
                mFilterArrays.get(RenderIndex.DownloadIndex).onDrawToTexture(cameraTex, mDefaultGLCubeBuffer, mDefaultGLTextureBuffer);
            }
        }

        synchronized (this) {
            if (mSurfaceTexture != null) {
                mSurfaceTexture.updateTexImage();
            }
        }
    }

    @Override
    public void onPreviewFrame(final byte[] data, final Camera camera) {
        final Camera.Size previewSize = camera.getParameters().getPreviewSize();
        if (mRenderBuffer == null) {
            mRenderBuffer = ByteBuffer.allocate(previewSize.width * previewSize.height * 4);
        }

        if (mCameraRunOnDraw.isEmpty()) {
            if (Build.CPU_ABI.equals("armeabi-v7a") || Build.CPU_ABI.equals("arm64-v8a")) {
                XMMtcnnNcnn.getInstance().NV21toABGR(data, previewSize.width, previewSize.height, mRenderBuffer.array(), mRotation.asInt(), mFlipHorizontal, mFlipVertical);
            } else {
                mRecorder.NV21toABGR(data, previewSize.width, previewSize.height, mRenderBuffer.array(), mRotation.asInt(), mFlipHorizontal, mFlipVertical);
            }
            camera.addCallbackBuffer(mYuvPreviewFrame);

            runOnDraw(mCameraRunOnDraw, new Runnable() {
                @Override
                public void run() {
                    mGLTextureId = OpenGlUtils.loadTexture(mRenderBuffer, mCameraOutputWidth, mCameraOutputHeight, mGLTextureId);
                }
            });

            requestRender();
        } else {
            camera.addCallbackBuffer(mYuvPreviewFrame);
        }
    }

    @Override
    synchronized public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        requestRender();
        updateTexImage = true;
    }

    public void setUpCamera(final Camera camera, final int degrees, final boolean flipHorizontal,
                            final boolean flipVertical) {
        cleanAll(mCameraRunOnDraw);
        runOnDraw(mCameraRunOnDraw, new Runnable() {
            @TargetApi(Build.VERSION_CODES.HONEYCOMB)
            @Override
            public void run() {
                if (camera != null) {
                    synchronized (camera) {
                        final Camera.Size previewSize = camera.getParameters().getPreviewSize();
                        mCameraPreviewWidth = previewSize.width;
                        mCameraPreviewHeight = previewSize.height;
                        Rotation rotation = Rotation.NORMAL;
                        switch (degrees) {
                            case 90:
                                rotation = Rotation.ROTATION_90;
                                mCameraOutputWidth = mCameraPreviewHeight;
                                mCameraOutputHeight = mCameraPreviewWidth;
                                break;
                            case 180:
                                rotation = Rotation.ROTATION_180;
                                mCameraOutputWidth = mCameraPreviewWidth;
                                mCameraOutputHeight = mCameraPreviewHeight;
                                break;
                            case 270:
                                rotation = Rotation.ROTATION_270;
                                mCameraOutputWidth = mCameraPreviewHeight;
                                mCameraOutputHeight = mCameraPreviewWidth;
                                break;
                            default:
                                mCameraOutputWidth = mCameraPreviewWidth;
                                mCameraOutputHeight = mCameraPreviewHeight;
                                break;
                        }
                        filtersSizeChanged();
                        setRotation(rotation, flipHorizontal, flipVertical);

                        if (mCameraGLTextureId != OpenGlUtils.NO_TEXTURE) {
                            GLES20.glDeleteTextures(1, new int[]{mCameraGLTextureId}, 0);
                            mCameraGLTextureId = OpenGlUtils.NO_TEXTURE;
                        }

                        int[] textures = new int[1];
                        GLES20.glGenTextures(1, textures, 0);
                        mCameraGLTextureId = textures[0];
                        mYuvPreviewFrame = new byte[previewSize.width * previewSize.height * 3 / 2];
                        mSurfaceTexture = new SurfaceTexture(mCameraGLTextureId);
                        //mSurfaceTexture.setOnFrameAvailableListener(XMCameraRenderer.this);
                        try {
                            camera.setPreviewTexture(mSurfaceTexture);
                            camera.addCallbackBuffer(mYuvPreviewFrame);
                            camera.setPreviewCallbackWithBuffer(XMCameraRenderer.this);
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

    @Override
    public void setFilter(final XMFilterType filtertype) {
        runOnDraw(mRunOnDraw, new Runnable() {
            @Override
            public void run() {
                if (mFilterArrays.get(RenderIndex.FilterIndex) != null) {
                    mFilterArrays.get(RenderIndex.FilterIndex).destroy();
                }
                GPUImageFilter filter = GPUImageFilterFactory.CreateFilter(filtertype);
                filter.init();
                GLES20.glUseProgram(filter.getProgram());
                filter.onInputSizeChanged(mCameraOutputWidth, mCameraOutputHeight);
                filter.onOutputSizeChanged(mCameraOutputWidth, mCameraOutputHeight);
                mFilterArrays.put(RenderIndex.FilterIndex, filter);
            }
        });

        mFilterType = filtertype;
        if (filtertype == XMFilterType.FILTER_FACE_STICKER) {
            XMMtcnnNcnn.getInstance().enable(true);
        } else {
            XMMtcnnNcnn.getInstance().enable(false);
        }

        requestRender();
    }

    public int getCameraOutputWidth() {
        return mCameraOutputWidth;
    }

    public int getCameraOutputHeight() {
        return mCameraOutputHeight;
    }

    public void cleanCameraRunOnDraw() {
        if(mCameraRunOnDraw != null) {
            cleanAll(mCameraRunOnDraw);
        }
    }

    @Override
    public void release() {
        super.release();
        if (mCameraGLTextureId != OpenGlUtils.NO_TEXTURE) {
            GLES20.glDeleteTextures(1, new int[]{mCameraGLTextureId}, 0);
            mCameraGLTextureId = OpenGlUtils.NO_TEXTURE;
        }
    }

    private void setRotation(final Rotation rotation,
                             final boolean flipHorizontal, final boolean flipVertical) {
        mRotation = rotation;
        if(rotation == Rotation.ROTATION_90 || rotation == Rotation.ROTATION_270) {
            mFlipHorizontal = flipVertical;
            mFlipVertical = flipHorizontal;
        } else {
            mFlipHorizontal = flipHorizontal;
            mFlipVertical = flipVertical;
        }

        adjustImageScaling(mCameraOutputWidth, mCameraOutputHeight, mOutputWidth, mOutputHeight,
                Rotation.NORMAL, false, false, mGLTextureBuffer);
    }

    private float[] normalized(int[] rect) {
        if (rect == null) {
            return null;
        }

        float[] dstBuffer = new float[4];

        dstBuffer[0] = (float)rect[0] / (float)rect[4];
        dstBuffer[1] = (float)(rect[5] - rect[3]) / (float)rect[5];
        dstBuffer[2] = (float)rect[2] / (float)rect[4];
        dstBuffer[3] = (float)(rect[5] - rect[1]) / (float)rect[5];

        return dstBuffer;
    }

    private void filtersSizeChanged() {
        if (mFilterArrays.get(RenderIndex.RotateIndex) != null) {
            mFilterArrays.get(RenderIndex.RotateIndex).onOutputSizeChanged(mCameraOutputWidth, mCameraOutputHeight);
        }
        if (mFilterArrays.get(RenderIndex.FilterIndex) != null) {
            mFilterArrays.get(RenderIndex.FilterIndex).onInputSizeChanged(mCameraOutputWidth, mCameraOutputHeight);
            mFilterArrays.get(RenderIndex.FilterIndex).onOutputSizeChanged(mCameraOutputWidth, mCameraOutputHeight);
        }
        if (mFilterArrays.get(RenderIndex.DisplayIndex) != null) {
            mFilterArrays.get(RenderIndex.DisplayIndex).onOutputSizeChanged(mOutputWidth, mOutputHeight);
        }
        if (mFilterArrays.get(RenderIndex.DownloadIndex) != null) {
            mFilterArrays.get(RenderIndex.DownloadIndex).onOutputSizeChanged(mCameraOutputWidth, mCameraOutputHeight);
        }
    }
}
