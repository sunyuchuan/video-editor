package com.xmly.media.camera.view.gpuimage.filter;

import android.opengl.GLES20;
import android.util.Log;

/**
 * Created by sunyc on 19-5-16.
 */

public class GPUImageImageSwitchFilter extends GPUImageFilter {
    private static final String TAG = "GPUImageImageSwitchFilter";
    public static final String VERTEX_SHADER = "" +
            "attribute vec4 position;\n" +
            "attribute vec4 inputTextureCoordinate;\n" +
            "uniform mat4 um4_Rotate;\n" +
            " \n" +
            "varying vec2 textureCoordinate;\n" +
            " \n" +
            "void main()\n" +
            "{\n" +
            "    gl_Position = um4_Rotate * position;\n" +
            "    textureCoordinate = inputTextureCoordinate.xy;\n" +
            "}";

    private int mGLUniformRotateMatrix;
    private double step = 0.0;
    private double scale = 0.0;

    public GPUImageImageSwitchFilter() {
        this(VERTEX_SHADER, NO_FILTER_FRAGMENT_SHADER);
    }

    public GPUImageImageSwitchFilter(String vertexShader, String fragmentShader) {
        super(vertexShader, fragmentShader);
    }

    @Override
    public void onInit() {
        super.onInit();
        mGLUniformRotateMatrix = GLES20.glGetUniformLocation(getProgram(), "um4_Rotate");
    }

    @Override
    public void onInitialized() {
        super.onInitialized();
        step = 0.0;
        scale = 0.0;
    }

    private void loadRotateMatrix(float[] matrix, double angle, double aspect, double scale)
    {
        double pi = 3.1415926;
        double radian = (angle / 180.0f) * pi;
        double cos_r = Math.cos(radian);
        double sin_r = Math.sin(radian);
        double dot_x = 0.0;
        double dot_y = 0.0;
        double offset_x = (1.0-cos_r)*dot_x + sin_r*dot_y;
        double offset_y = (1.0-cos_r)*dot_y - sin_r*dot_x;

        matrix[0] = (float) (cos_r * scale);
        matrix[1] = (float) ((-sin_r) * aspect * scale);
        matrix[2] = 0.0f;
        matrix[3] = 0.0f;

        matrix[4] = (float) ((sin_r / aspect) * scale);
        matrix[5] = (float) (cos_r * scale);
        matrix[6] = 0.0f;
        matrix[7] = 0.0f;

        matrix[8] = 0.0f;
        matrix[9] = 0.0f;
        matrix[10] = 1.0f;
        matrix[11] = 0.0f;

        matrix[12] = 0.0f;
        matrix[13] = 0.0f;
        matrix[14] = 0.0f;
        matrix[15] = 1.0f;
    }

    @Override
    protected void onDrawArraysPre() {
        if (step < 360.0) {
            step += 30.0;
            scale += 30.0 / 360.0;
            if (step > 360.0) {
                step = 360.0;
                scale = 1.0;
            }
            float[] rotate_um4 = new float[16];
            loadRotateMatrix(rotate_um4, step, (double) mOutputWidth / (double) mOutputHeight, scale);
            GLES20.glUniformMatrix4fv(mGLUniformRotateMatrix, 1, false, rotate_um4, 0);
        } else {
            float[] rotate_um4 = new float[16];
            loadRotateMatrix(rotate_um4, 0.0, (double) mOutputWidth / (double) mOutputHeight, scale);
            GLES20.glUniformMatrix4fv(mGLUniformRotateMatrix, 1, false, rotate_um4, 0);
        }
    }
}
