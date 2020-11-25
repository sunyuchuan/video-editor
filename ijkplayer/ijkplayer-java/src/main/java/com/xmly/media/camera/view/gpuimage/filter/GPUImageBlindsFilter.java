package com.xmly.media.camera.view.gpuimage.filter;

import android.graphics.Bitmap;
import android.opengl.GLES20;

import com.xmly.media.camera.view.utils.OpenGlUtils;
import com.xmly.media.video.view.XMImageView;

/**
 * Created by sunyc on 19-5-21.
 */

public class GPUImageBlindsFilter extends GPUImageFilter {
    private static final String TAG = "GPUImageBlindsFilter";
    public static final String FRAGMENT_SHADER = "" +
            "varying highp vec2 textureCoordinate;\n" +
            " \n" +
            "uniform float unitWidth;\n" +
            "uniform float offset;\n" +
            "uniform lowp sampler2D inputImageTexture;\n" +
            "uniform lowp sampler2D inputImageTexture1;\n" +
            " \n" +
            "void main()\n" +
            "{\n" +
            "     float modPart = mod(textureCoordinate.x, unitWidth);\n" +
            "     float solidPart = offset * unitWidth;\n" +
            "     if (modPart < solidPart)\n" +
            "     {\n" +
            "         gl_FragColor = texture2D(inputImageTexture1, vec2(textureCoordinate.x, 1.0 - textureCoordinate.y));\n" +
            "     } else {\n" +
            "         gl_FragColor = texture2D(inputImageTexture, textureCoordinate);\n" +
            "     }\n" +
            "}";
    private int mGLUniformSubTexture;
    private int mGLUnitWidth;
    private int mGLOffset;

    private volatile int mSubTexture = OpenGlUtils.NO_TEXTURE;
    private volatile float mUnitWidth;
    private volatile float mOffset;
    private volatile boolean mCompleted = false;
    private XMImageView.IXMImageListener mListener = null;

    public GPUImageBlindsFilter() {
        this(NO_FILTER_VERTEX_SHADER, FRAGMENT_SHADER);
    }

    public GPUImageBlindsFilter(String vertexShader, String fragmentShader) {
        super(vertexShader, fragmentShader);
    }

    @Override
    public void onInit() {
        super.onInit();

        mGLUniformSubTexture = GLES20.glGetUniformLocation(getProgram(), "inputImageTexture1");
        mGLUnitWidth = GLES20.glGetUniformLocation(getProgram(), "unitWidth");
        mGLOffset = GLES20.glGetUniformLocation(getProgram(), "offset");
    }

    @Override
    public void onInitialized() {
        super.onInitialized();
        mUnitWidth = 0.1f;
        mOffset = 0.0f;
        mCompleted = false;
    }

    @Override
    public void setBitmap(final Bitmap bitmap) {
        if (bitmap != null && bitmap.isRecycled()) {
            return;
        }

        runOnDraw(new Runnable() {
            public void run() {
                if (bitmap == null || bitmap.isRecycled()) {
                    return;
                }

                GLES20.glActiveTexture(GLES20.GL_TEXTURE3);
                mSubTexture = OpenGlUtils.loadTexture(bitmap, mSubTexture, true);
                mUnitWidth = 0.1f;
                mOffset = 0.0f;
                mCompleted = false;
            }
        });
    }

    @Override
    protected void onDrawArraysPre() {
        if (mSubTexture != OpenGlUtils.NO_TEXTURE) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE3);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mSubTexture);
            GLES20.glUniform1i(mGLUniformSubTexture, 3);
            if (mOffset < 1.0f) {
                mOffset += 0.05f;
            } else if (!mCompleted) {
                if (mListener != null) mListener.onImageSwitchCompleted();
                mCompleted = true;
            }
        }

        GLES20.glUniform1f(mGLUnitWidth, mUnitWidth);
        GLES20.glUniform1f(mGLOffset, mOffset);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        GLES20.glDeleteTextures(1, new int[]{mSubTexture}, 0);
        mSubTexture = OpenGlUtils.NO_TEXTURE;
    }

    public void setBlindsWinNumber(final float winNumber) {
        mUnitWidth = 1.0f / winNumber;
        setFloat(mGLUnitWidth, mUnitWidth);
    }

    public void setBlindsOffset(final float offset) {
        mOffset = offset;
        setFloat(mGLOffset, mOffset);
    }

    public void setListener(XMImageView.IXMImageListener l) {
        mListener = l;
    }
}