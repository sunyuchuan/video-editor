package com.xmly.media.camera.view.gpuimage.filter;

import android.annotation.TargetApi;
import android.opengl.GLES20;
import android.opengl.GLES30;
import android.os.Build;
import android.util.Log;

import com.xmly.media.camera.view.recorder.XMMediaRecorder;
import com.xmly.media.camera.view.utils.OpenGlUtils;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

/**
 * Created by sunyc on 19-3-21.
 */

public class GPUImagePboFilter extends GPUImageFilter {
    private static final String TAG = "GPUImagePboFilter";
    private int[] mPboBuffers;
    private int mPboSize;
    private static final int PIXEL_STRIDE = 4;//RGBA 4字节
    private final int mAlign = 8;//mAlign字节对齐
    private int mRowStride;
    private int mPboIndex = -1;
    private int mPboNextIndex = -1;
    private int mOutputWidth;
    private int mOutputHeight;
    private int mInputWidth;
    private int mInputHeight;
    private boolean mFirstBindPbo = true;
    private XMMediaRecorder mNativeRecorder = null;
    private volatile boolean isPutting = false;
    private ByteBuffer mOutputBuffer;
    byte[] mData = null;

    public GPUImagePboFilter() {
        super(NO_FILTER_VERTEX_SHADER, NO_FILTER_FRAGMENT_SHADER);
    }

    private void destroyPboBuffers() {
        if (mPboBuffers != null) {
            GLES30.glDeleteBuffers(mPboBuffers.length, mPboBuffers, 0);
            mPboBuffers = null;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        destroyPboBuffers();
    }

    @Override
    public void onInputSizeChanged(final int width, final int height) {
        super.onInputSizeChanged(width, height);
        mInputWidth = width;
        mInputHeight = height;
    }

    @Override
    public void onOutputSizeChanged(int width, int height) {
        super.onOutputSizeChanged(width, height);
        if (mPboBuffers != null && (mOutputWidth != width || mOutputHeight != height)) {
            destroyPboBuffers();
        }
        if (mPboBuffers == null) {
            mRowStride = (width * PIXEL_STRIDE + (mAlign - 1)) & ~(mAlign - 1);
            mPboSize = mRowStride * height;
            mPboBuffers = new int[2];

            for(int i = 0; i < 2; i++) {
                GLES30.glGenBuffers(1, mPboBuffers, i);
                GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, mPboBuffers[i]);
                GLES30.glBufferData(GLES30.GL_PIXEL_PACK_BUFFER, mPboSize, null, GLES30.GL_STATIC_READ);
                GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, 0);
            }
        }
        mOutputWidth = width;
        mOutputHeight = height;
        mOutputBuffer = ByteBuffer.allocate(mOutputWidth * mOutputHeight * 4);
        mData = new byte[mPboSize];
    }

    public void setNativeRecorder(XMMediaRecorder recorder) {
        mNativeRecorder = recorder;
    }

    public void startPutData(boolean putting) {
        this.isPutting = putting;
    }

    private void glReadPixels() {
        if (mOutputBuffer != null) {
            GLES20.glReadPixels(0, 0, mOutputWidth, mOutputHeight, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, mOutputBuffer);
            mNativeRecorder.put(mOutputBuffer.array(), mOutputWidth, mOutputHeight, PIXEL_STRIDE, 0, 0, false, false);
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    private void bindPboToReadPixels() {
        mPboIndex = (mPboIndex + 1) % 2;
        mPboNextIndex = (mPboIndex + 1) % 2;
        //GLES30.glReadBuffer(GLES30.GL_BACK);
        GLES30.glPixelStorei(GLES30.GL_PACK_ALIGNMENT, mAlign);
        //GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, mFrameBuffers[0]);
        //GLES30.glReadBuffer(GLES30.GL_COLOR_ATTACHMENT0);
        GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, mPboBuffers[mPboIndex]);
        XMMediaRecorder.glReadPixels(0, 0, mRowStride / PIXEL_STRIDE, mOutputHeight, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE);

        if (mFirstBindPbo) {//第一帧没有数据,退出
            GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, 0);
            mFirstBindPbo = false;
            return;
        }

        GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, mPboBuffers[mPboNextIndex]);
        int rowPadding = mRowStride - PIXEL_STRIDE * mOutputWidth;
        mNativeRecorder.glMapBufferRange_put(GLES30.GL_PIXEL_PACK_BUFFER, 0, mPboSize, GLES30.GL_MAP_READ_BIT,
                mOutputWidth, mOutputHeight, PIXEL_STRIDE, rowPadding);
        GLES30.glUnmapBuffer(GLES30.GL_PIXEL_PACK_BUFFER);
        GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, 0);
    }

    public int onDrawToPbo(final int textureId, final FloatBuffer cubeBuffer,
                               final FloatBuffer textureBuffer) {
        if (mFrameBuffers == null || mPboBuffers == null)
            return OpenGlUtils.NO_TEXTURE;
        GLES20.glViewport(0, 0, mFrameWidth, mFrameHeight);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBuffers[0]);
        GLES20.glUseProgram(mGLProgId);
        runPendingOnDrawTasks();
        if (!isInitialized()) {
            return OpenGlUtils.NOT_INIT;
        }

        cubeBuffer.position(0);
        GLES20.glVertexAttribPointer(mGLAttribPosition, 2, GLES20.GL_FLOAT, false, 0, cubeBuffer);
        GLES20.glEnableVertexAttribArray(mGLAttribPosition);
        textureBuffer.position(0);
        GLES20.glVertexAttribPointer(mGLAttribTextureCoordinate, 2, GLES20.GL_FLOAT, false, 0,
                textureBuffer);
        GLES20.glEnableVertexAttribArray(mGLAttribTextureCoordinate);
        if (textureId != OpenGlUtils.NO_TEXTURE) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
            GLES20.glUniform1i(mGLUniformTexture, 0);
        }
        onDrawArraysPre();
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glDisableVertexAttribArray(mGLAttribPosition);
        GLES20.glDisableVertexAttribArray(mGLAttribTextureCoordinate);
        if (mNativeRecorder != null && isPutting) {
            bindPboToReadPixels();
            //glReadPixels();
        }
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glViewport(0, 0, mOutputWidth, mOutputHeight);
        return mFrameBufferTextures[0];
    }
}
