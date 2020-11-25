package com.xmly.media.camera.view.gpuimage.filter;

/**
 * Created by sunyc on 19-5-21.
 */

public class GPUImageFadeInOutFilter extends GPUImageBlindsFilter {
    private static final String TAG = "GPUImageFadeInOutFilter";
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
            "     lowp vec4 rgba1;\n" +
            "     lowp vec4 rgba2;\n" +
            "     rgba1 = texture2D(inputImageTexture1, vec2(textureCoordinate.x, 1.0 - textureCoordinate.y));\n" +
            "     rgba2 = texture2D(inputImageTexture, textureCoordinate);\n" +
            "     gl_FragColor = rgba1 * offset + rgba2 * (1.0 - offset);\n" +
            "}";

    public GPUImageFadeInOutFilter() {
        this(NO_FILTER_VERTEX_SHADER, FRAGMENT_SHADER);
    }

    public GPUImageFadeInOutFilter(String vertexShader, String fragmentShader) {
        super(vertexShader, fragmentShader);
    }
}
