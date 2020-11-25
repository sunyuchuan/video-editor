package com.xmly.media.camera.view.recorder;

/**
 * Created by sunyc on 19-4-3.
 */

public class XMMediaRecorderParams {
    public static final int FALSE = 0;
    public static final int TRUE = 1;
    public int width;
    public int height;
    public int bitrate = 700000;
    public int fps = 25;
    public int CFR = FALSE; //constant frame rate
    public float gop_size = 1.0f;// In seconds
    public int crf = 23;
    public int multiple = 1000; //time_base of stream is multiple * fps
    public int max_b_frames = 0;
    public String output_path;
    public String preset = "veryfast";
    public String tune = "zerolatency";

    public XMMediaRecorderParams() {
    }

    public void setSize(int w, int h) {
        width = w;
        height = h;
    }

    public void setBitrate(int bitrate) {
        this.bitrate = bitrate;
    }

    public void setFps(int fps) {
        this.fps = fps;
    }

    public void setGopsize(int time) {
        this.gop_size = time;
    }

    public void setCrf(int crf) {
        this.crf = crf;
    }

    public void setMultiple(int multiple) {
        this.multiple = multiple;
    }

    public void setMaxBFrames(int max_b_frames) {
        this.max_b_frames = max_b_frames;
    }

    public void setOutputPath(String output_path) {
        this.output_path = output_path;
    }

    public void setPreset(String preset) {
        this.preset = preset;
    }

    public void setTune(String tune) {
        this.tune = tune;
    }
}