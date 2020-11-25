/*
 * Copyright (C) 2012 CyberAgent
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.xmly.media.gles.filter;

import android.graphics.Bitmap;
import android.opengl.GLES20;

import com.xmly.media.gles.utils.OpenGlUtils;
import java.nio.FloatBuffer;

public class GPUImageFaceStickerFilter extends GPUImageFilter {
    private static final String TAG = "FaceStickerFilter";
    private static final String VERTEX_SHADER = "" +
            "precision highp float;\n" +
            "attribute vec4 position;\n" +
            "attribute vec4 inputTextureCoordinate;\n" +
            " \n" +
            "varying vec2 textureCoordinate;\n" +
            "varying highp vec4 g_position;\n" +
            " \n" +
            "void main()\n" +
            "{\n" +
            "    gl_Position = position;\n" +
            "    textureCoordinate = inputTextureCoordinate.xy;\n" +
            "    g_position = gl_Position;\n" +
            "}";
    public static final String FRAGMENT_SHADER = "" +
            "precision highp float;\n" +
            "varying highp vec2 textureCoordinate;\n" +
            "varying highp vec4 g_position;\n" +
            "\n" +
            "uniform lowp sampler2D inputImageTexture;\n" +
            "uniform lowp sampler2D headImageTexture;\n" +
            "uniform highp vec2 leftBottom;\n" +
            "uniform highp vec2 rightTop;\n" +
            "\n" +
            "void main()\n" +
            "{\n" +
            "    lowp vec4 rgba2;\n" +
            "    lowp vec4 rgba1;\n" +
            "    highp vec2 textureCoordinate2use;\n" +
            "    if (g_position.x >= leftBottom.x && g_position.y >= leftBottom.y\n" +
            "        && g_position.x <= rightTop.x && g_position.y <= rightTop.y) {\n" +
            "        textureCoordinate2use = vec2((g_position.x - leftBottom.x) / (rightTop.x - leftBottom.x),\n" +
            "            1.0 - (g_position.y - leftBottom.y) / (rightTop.y - leftBottom.y));\n" +
            "        rgba2 = texture2D(headImageTexture, textureCoordinate2use);\n" +
            "        rgba1 = texture2D(inputImageTexture, textureCoordinate);\n" +
            "        gl_FragColor = rgba2 * rgba2.a + rgba1 * (1.0 - rgba2.a);\n" +
            "    } else {\n" +
            "        gl_FragColor = texture2D(inputImageTexture, textureCoordinate);\n" +
            "    }\n" +
            "}\n";
    private int mHeadGLUniformTexture;
    private int mLeftBottom;
    private int mRightTop;

    public int mSubTexture = OpenGlUtils.NO_TEXTURE;
    private int bitmap_w = 0;
    private int bitmap_h = 0;
    private float[] mInputBuffer = new float[4];
    private FloatBuffer mLeftBottomBuffer = null;
    private FloatBuffer mRightTopBuffer = null;

    public GPUImageFaceStickerFilter() {
        this(VERTEX_SHADER, FRAGMENT_SHADER);
    }

    public GPUImageFaceStickerFilter(String vertexShader, String fragmentShader) {
        super(vertexShader, fragmentShader);
        float rectangle[] = {0.6f, 0.6f, 0.95f, 0.95f};
        setRectangleCoordinate(rectangle);
    }

    @Override
    public void onInit() {
        super.onInit();

        mHeadGLUniformTexture = GLES20.glGetUniformLocation(getProgram(), "headImageTexture");
        mLeftBottom = GLES20.glGetUniformLocation(getProgram(), "leftBottom");
        mRightTop = GLES20.glGetUniformLocation(getProgram(), "rightTop");
    }

    private void coordinateTransform(float[] rect, int angle, float[] origin, int h)
    {
        double pi = 3.1415926;
        float[] point = new float[4];
        double cos_r = Math.cos(pi / 180.0 * angle);
        double sin_r = Math.sin(pi / 180.0 * angle);
        float x2 = origin[0];
        float y2 = origin[1];

        for (int i = 0; i < 4; i += 2) {
            float x1 = rect[i];
            float y1 = rect[i+1];
            point[i] = (float) ((x1 - x2) * cos_r - (y1 - y2) * sin_r + x2);
            point[i+1] = (float) ((x1 - x2) * sin_r + (y1 - y2) * cos_r + y2);
        }

        for(int i = 0; i < 2; i++) {
            if(point[i] < point[i+2]) {
                rect[i] = point[i];
                rect[i+2] = point[i+2];
            } else {
                rect[i] = point[i+2];
                rect[i+2] = point[i];
            }
        }
    }

    private synchronized void rectCoordinateTransform(float[] inBuffer) {
        float buffer[] = {inBuffer[0], inBuffer[1], inBuffer[2], inBuffer[3]};

        //float[] origin = new float[]{0.5f, 0.5f};
        //coordinateTransform(buffer, 270, origin, 1);

        buffer[0] *= mOutputWidth;
        buffer[1] *= mOutputHeight;
        buffer[2] *= mOutputWidth;
        buffer[3] *= mOutputHeight;

        if(bitmap_w != 0 && bitmap_h != 0) {
            float logo_rect_aspect_ratio = (buffer[2] - buffer[0])/(buffer[3] - buffer[1]);
            float logo_image_aspect_ratio = (float)bitmap_w / (float)bitmap_h;
            if (logo_image_aspect_ratio > logo_rect_aspect_ratio) {
                buffer[1] = buffer[3] - (buffer[2] - buffer[0]) / logo_image_aspect_ratio;
            } else {
                buffer[0] = buffer[2] - (buffer[3] - buffer[1]) * logo_image_aspect_ratio;
            }
        }

        buffer[0] = (buffer[0]/mOutputWidth)*2 -1.0f;
        buffer[1] = (buffer[1]/mOutputHeight)*2 -1.0f;
        buffer[2] = (buffer[2]/mOutputWidth)*2 -1.0f;
        buffer[3] = (buffer[3]/mOutputHeight)*2 -1.0f;

        if(mLeftBottomBuffer == null)
            mLeftBottomBuffer = FloatBuffer.allocate(2);
        float[] left = {buffer[0], buffer[1]};
        mLeftBottomBuffer.put(left);
        mLeftBottomBuffer.flip();
        mLeftBottomBuffer.position(0);

        if(mRightTopBuffer == null)
            mRightTopBuffer = FloatBuffer.allocate(2);
        float[] right = {buffer[2], buffer[3]};
        mRightTopBuffer.put(right);
        mRightTopBuffer.flip();
        mRightTopBuffer.position(0);
    }

    @Override
    public void onOutputSizeChanged(final int width, final int height) {
        super.onOutputSizeChanged(width, height);
        rectCoordinateTransform(mInputBuffer);
    }

    public void setBitmap(final Bitmap bitmap) {
        if (bitmap != null && bitmap.isRecycled()) {
            return;
        }

        runOnDraw(new Runnable() {
            public void run() {
                if (bitmap == null || bitmap.isRecycled()) {
                    return;
                }

                bitmap_w = bitmap.getWidth();
                bitmap_h = bitmap.getHeight();
                rectCoordinateTransform(mInputBuffer);
                GLES20.glActiveTexture(GLES20.GL_TEXTURE3);
                mSubTexture = OpenGlUtils.loadTexture(bitmap, mSubTexture, true);
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mSubTexture != OpenGlUtils.NO_TEXTURE) {
            GLES20.glDeleteTextures(1, new int[]{mSubTexture}, 0);
            mSubTexture = OpenGlUtils.NO_TEXTURE;
        }
    }

    @Override
    protected void onDrawArraysPre() {
        if (mSubTexture != OpenGlUtils.NO_TEXTURE) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE3);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mSubTexture);
            GLES20.glUniform1i(mHeadGLUniformTexture, 3);
        }
        GLES20.glUniform2fv(mLeftBottom, 1, mLeftBottomBuffer);
        GLES20.glUniform2fv(mRightTop, 1, mRightTopBuffer);
    }

    @Override
    public void setRectangleCoordinate(float buffer[]) {
        if (buffer == null) {
            return;
        }
        mInputBuffer[0] = buffer[0];
        mInputBuffer[1] = buffer[1];
        mInputBuffer[2] = buffer[2];
        mInputBuffer[3] = buffer[3];
        rectCoordinateTransform(mInputBuffer);
    }
}