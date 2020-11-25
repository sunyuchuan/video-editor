package com.xmly.media.camera.view.utils;

/**
 * Created by why8222 on 2016/2/25.
 */
public enum XMFilterType {
    NONE(-1),
    FILTER_BEAUTY(0),
    FILTER_FACE_STICKER(1),
    FILTER_SKETCH(2),
    FILTER_SEPIA(3),
    FILTER_INVERT(4),
    FILTER_VIGNETTE(5),
    FILTER_LAPLACIAN(6),
    FILTER_GLASS_SPHERE(7),
    FILTER_CRAYON(8),
    FILTER_MIRROR(9),
    FILTER_BEAUTY_OPTIMIZATION(10),
    FILTER_LOGO(11),
    FILTER_IMAGE_SWITCH(12),
    FILTER_BLINDS(13),
    FILTER_FADE_IN_OUT(14);

    private final int value;

    XMFilterType(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
