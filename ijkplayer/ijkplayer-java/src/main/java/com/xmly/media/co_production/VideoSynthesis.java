package com.xmly.media.co_production;

import android.text.TextUtils;
import android.util.Log;

import com.xmly.media.FFmpegMediaMetadataRetriever;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by sunyc on 18-11-27.
 */

public class VideoSynthesis {
    public static final String RAW_VIDEO_TYPE = "rawVideo";
    public static final String CAMERA_VIDEO_TYPE = "cameraVideo";
    public static final String WATERMARK_TYPE = "watermark";
    public static final int XM_LOG_UNKNOWN = 0;
    public static final int XM_LOG_DEFAULT = 1;
    public static final int XM_LOG_VERBOSE = 2;
    public static final int XM_LOG_DEBUG = 3;
    public static final int XM_LOG_INFO = 4;
    public static final int XM_LOG_WARN = 5;
    public static final int XM_LOG_ERROR = 6;
    public static final int XM_LOG_FATAL = 7;
    public static final int XM_LOG_SILENT = 8;
    private static final int PIXEL_DIFF = 2;//Alignment must be 2.
    private static final String TAG = "VideoSynthesis";
    private FFmpegCommand mFFcmd;
    private IVideoSynthesisListener mListener;
    public static final int PURE_AUDIO = 0;
    public static final int PURE_VIDEO = 1;
    public static final int AUDIO_VIDEO = 2;
    private volatile static VideoSynthesis sInstance = null;

    private List<String> mCmdParams;
    private long mDurationRef;
    private String mConcatFilePath;
    private volatile boolean mRunning = false;
    private boolean mZerolatencyDisable = false;

    public static VideoSynthesis getInstance() {
        if (sInstance == null) {
            synchronized (VideoSynthesis.class) {
                if (sInstance == null) {
                    sInstance = new VideoSynthesis();
                }
            }
        }
        return sInstance;
    }

    /**
     *
     * @param disable if true will turn off the encoder's Zerolatency,
     *                will increase the encoding speed
     *                and reduce the video file bitrate.
     *                But it will take up more memory.
     */
    public void setZerolatency(boolean disable) {
        this.mZerolatencyDisable = disable;
    }

    private VideoSynthesis() {
        mFFcmd = new FFmpegCommand();
        mFFcmd.setListener(mOnFFMpegCmdListener);
        mFFcmd.setLogLevel(XM_LOG_WARN);
    }

    /**
     * Video stitching synthesis of the same encoding format
     * @param inputVideoList List of videos that need to be stitched
     * @param outputpath The video path of the generated video, including the file name
     * @param l IVideoSynthesisListener, Callback.
     * @throws IllegalArgumentException
     * @throws IllegalStateException
     */
    public void videoConcat(List<String> inputVideoList, String outputpath, IVideoSynthesisListener l) throws IllegalArgumentException,IllegalStateException {
        synchronized (this) {
            Log.i(TAG, "videoConcat output path " + outputpath);
            if ((inputVideoList == null || TextUtils.isEmpty(outputpath))) {
                Log.e(TAG, "videoConcat : 1 Input Params is inValid, exit");
                throw new IllegalArgumentException();
            }

            if (getStatus()) {
                Log.d(TAG, "videoConcat : ffmpeg instance is runing, pls waiting ffmpeg end");
                throw new IllegalStateException();
            }

            for (String s : inputVideoList) {
                try {
                    FFmpegMediaMetadataRetriever r = new FFmpegMediaMetadataRetriever();
                    r.setDataSource(s);
                    FFmpegMediaMetadataRetriever.Metadata data = r.getMetadata();
                    mDurationRef += data.getLong(FFmpegMediaMetadataRetriever.METADATA_KEY_DURATION);
                } catch (IllegalArgumentException e) {
                    Log.e(TAG, "videoConcat : 2 Input Params is inValid, exit");
                    throw new IllegalArgumentException();
                }
            }

            mListener = l;
            mConcatFilePath = createConcatListFile(inputVideoList, outputpath);
            if (mConcatFilePath == null) {
                Log.e(TAG, "videoConcat : ffmpeg concat input file creation failed");
                throw new IllegalArgumentException();
            }

            int concatType = PURE_VIDEO;
            mCmdParams = new ArrayList<>();
            mCmdParams.add("ffmpeg");
            mCmdParams.add("-f");
            mCmdParams.add("concat");
            mCmdParams.add("-safe");
            mCmdParams.add("0");
            mCmdParams.add("-i");
            mCmdParams.add(mConcatFilePath);
            if (concatType == PURE_VIDEO) {
                mCmdParams.add("-an");
                mCmdParams.add("-c:v");
                mCmdParams.add("copy");
            } else if (concatType == PURE_AUDIO) {
                mCmdParams.add("-vn");
                mCmdParams.add("-c:a");
                mCmdParams.add("copy");
            } else {
                mCmdParams.add("-an");
                mCmdParams.add("-c:v");
                mCmdParams.add("copy");
            }
            mCmdParams.add("-movflags");
            mCmdParams.add("faststart");
            mCmdParams.add("-f");
            mCmdParams.add("mp4");
            mCmdParams.add("-y");
            mCmdParams.add(outputpath);
            start();
        }
    }

    /**
     * picture-in-picture video synthesis
     * @param list input videos and watermarks etc
     * @param output The video path of the generated video, including the file name
     * @param l IVideoSynthesisListener, Callback.
     * @throws IllegalArgumentException
     * @throws IllegalStateException
     */
    public void pipMergeVideo(List<MetaData> list, String output, IVideoSynthesisListener l) throws IllegalArgumentException,IllegalStateException {
        synchronized (this) {
            Log.i(TAG, "pipMergeVideo output path " + output);
            MetaData rawVideoMetaData = null;
            MetaData cameraVideoMetaData = null;
            MetaData watermarkMetaData = null;
            final int bitrateDefault = 700;
            final int resDefault = 540*960;
            for (MetaData data : list) {
                if (data.mType.equals(RAW_VIDEO_TYPE)) {
                    rawVideoMetaData = data;
                } else if (data.mType.equals(CAMERA_VIDEO_TYPE)) {
                    cameraVideoMetaData = data;
                } else if (data.mType.equals(WATERMARK_TYPE)) {
                    watermarkMetaData = data;
                }
            }

            if (rawVideoMetaData == null
                    || cameraVideoMetaData == null
                    || cameraVideoMetaData.mRect == null
                    || watermarkMetaData == null
                    || TextUtils.isEmpty(rawVideoMetaData.mPath)
                    || TextUtils.isEmpty(cameraVideoMetaData.mPath)
                    || TextUtils.isEmpty(watermarkMetaData.mPath)
                    || TextUtils.isEmpty(output)) {
                Log.e(TAG, "Pip : Input Params is inValid, exit");
                throw new IllegalArgumentException();
            }

            PipParams params = CalculatePipParameters(rawVideoMetaData, cameraVideoMetaData);
            if (params == null) {
                Log.e(TAG, "Pip : Input Params is inValid Calc PipParams Error, exit");
                throw new IllegalArgumentException();
            }

            if (getStatus()) {
                Log.d(TAG, "Pip : ffmpeg instance is runing, pls waiting ffmpeg end");
                throw new IllegalStateException();
            }

            mListener = l;
            mDurationRef = params.raw_duration;
            mCmdParams = new ArrayList<>();
            mCmdParams.add("ffmpeg");
            mCmdParams.add("-i");
            mCmdParams.add(rawVideoMetaData.mPath);
            mCmdParams.add("-i");
            mCmdParams.add(cameraVideoMetaData.mPath);
            mCmdParams.add("-filter_complex");
            mCmdParams.add("[0:v] fps=15,scale="+params.raw_w+":"+params.raw_h+" [base];" +
                            "[1:v] fps=15,scale="+params.camera_w+":"+params.camera_h +
                            ",pad='w=iw+"+2*PIXEL_DIFF+":h=ih+"+2*PIXEL_DIFF+":x=(ow-iw)/2:y=(oh-ih)/2:color=0xFFFFFF'"+" [camera];" +
                            "[base][camera] overlay=repeatlast=0:x="+params.camera_overlay_x+":y="+params.camera_overlay_y+" [vout]");
            mCmdParams.add("-map");
            mCmdParams.add("[vout]");
            mCmdParams.add("-c:v");
            mCmdParams.add("libx264");
            if (!mZerolatencyDisable) {
                mCmdParams.add("-tune");
                mCmdParams.add("zerolatency");
            }
            mCmdParams.add("-r");
            mCmdParams.add("15");
            mCmdParams.add("-force_key_frames");
            mCmdParams.add("expr:gte(t,n_forced*5)");
            mCmdParams.add("-pix_fmt");
            mCmdParams.add("yuv420p");
            mCmdParams.add("-vb");
            mCmdParams.add((int) (((float) (params.raw_w * params.raw_h) / (float) resDefault) * bitrateDefault) + "k");
            mCmdParams.add("-bf");
            mCmdParams.add("0");
            mCmdParams.add("-shortest");
            mCmdParams.add("-movflags");
            mCmdParams.add("faststart");
            mCmdParams.add("-preset");
            mCmdParams.add("veryfast");
            mCmdParams.add("-crf");
            mCmdParams.add("23.0");
            mCmdParams.add("-f");
            mCmdParams.add("mp4");
            mCmdParams.add("-y");
            mCmdParams.add(output);
            start();
        }
    }

    /**
     * burn the srt subtile to video,must have ttf font.
     * picture-in-picture video synthesis with subtitle
     * @param list input videos and watermarks etc
     * @param subParams subtitle params
     * @param sub_output The video path of the generated video, only include subtitles
     * @param sub_pip_output The video path of the generated video, include subtitles and pip
     * @param l IVideoSynthesisListener, Callback.
     * @throws IllegalArgumentException
     * @throws IllegalStateException
     */
    public void pipMergeVideoWithSubtitle(List<MetaData> list, SubParams subParams, String sub_output, String sub_pip_output, IVideoSynthesisListener l) throws IllegalArgumentException,IllegalStateException {
        synchronized (this) {
            Log.i(TAG, "pipMergeVideoWithSubtitle output path sub_output " + sub_output + " sub_pip_output " + sub_pip_output);
            MetaData rawVideoMetaData = null;
            MetaData cameraVideoMetaData = null;
            MetaData watermarkMetaData = null;
            final int bitrateDefault = 700;
            final int resDefault = 540*960;
            for (MetaData data : list) {
                if (data.mType.equals(RAW_VIDEO_TYPE)) {
                    rawVideoMetaData = data;
                } else if (data.mType.equals(CAMERA_VIDEO_TYPE)) {
                    cameraVideoMetaData = data;
                } else if (data.mType.equals(WATERMARK_TYPE)) {
                    watermarkMetaData = data;
                }
            }

            if (rawVideoMetaData == null
                    || cameraVideoMetaData == null
                    || cameraVideoMetaData.mRect == null
                    || watermarkMetaData == null
                    || TextUtils.isEmpty(rawVideoMetaData.mPath)
                    || TextUtils.isEmpty(cameraVideoMetaData.mPath)
                    || TextUtils.isEmpty(watermarkMetaData.mPath)
                    || TextUtils.isEmpty(sub_output)
                    || TextUtils.isEmpty(sub_pip_output)) {
                Log.e(TAG, "Pip : Input Params is inValid, exit");
                throw new IllegalArgumentException();
            }

            PipParams params = CalculatePipParameters(rawVideoMetaData, cameraVideoMetaData);
            if (params == null) {
                Log.e(TAG, "Pip : Input Params is inValid Calc PipParams Error, exit");
                throw new IllegalArgumentException();
            }

            if (subParams.mSrtPath == null || subParams.mFontPath == null || subParams.mFontName == null) {
                Log.e(TAG, "Subtitle : Input Params is inValid, exit");
                throw new IllegalArgumentException();
            }

            if (getStatus()) {
                Log.d(TAG, "Pip : ffmpeg instance is runing, pls waiting ffmpeg end");
                throw new IllegalStateException();
            }

            mListener = l;
            mDurationRef = params.raw_duration;
            mCmdParams = new ArrayList<>();
            mCmdParams.add("ffmpeg");
            mCmdParams.add("-i");
            mCmdParams.add(rawVideoMetaData.mPath);
            mCmdParams.add("-i");
            mCmdParams.add(cameraVideoMetaData.mPath);
            mCmdParams.add("-filter_complex");
            mCmdParams.add("[0:v] fps=15,scale="+params.raw_w+":"+params.raw_h+"," +
                    "subtitles="+subParams.mSrtPath+":imageA="+subParams.mImageAPath+":imageB="+subParams.mImageBPath +
                    ":imageC="+subParams.mImageCPath+":imageD="+subParams.mImageDPath+ ":interval="+subParams.mInterval +
                    ":margin="+(subParams.mScaleRatio-1.0f)/2.0f+":scale_ratio="+subParams.mScaleRatio+":fontsdir="+subParams.mFontPath +
                    ":force_style='FontName="+subParams.mFontName+",FontSize="+subParams.mFontSize+",MarginV="+subParams.mSubMarginV +
                    ",BorderStyle=1"+",Outline="+subParams.mOutline+",Shadow=0,PrimaryColour=&HFFFFFF&,OutlineColour=&H000000&,Spacing=2'"+" [base];" +
                    "[1:v] fps=15,scale="+params.camera_w+":"+params.camera_h +
                    ",pad='w=iw+"+2*PIXEL_DIFF+":h=ih+"+2*PIXEL_DIFF+":x=(ow-iw)/2:y=(oh-ih)/2:color=0xFFFFFF'"+" [camera];" +
                    "[base] split=2[tmp][vout1];" +
                    "[tmp][camera] overlay=repeatlast=0:x="+params.camera_overlay_x+":y="+params.camera_overlay_y+" [vout2]");
            mCmdParams.add("-map");
            mCmdParams.add("[vout1]");
            mCmdParams.add("-c:v");
            mCmdParams.add("libx264");
            if (!mZerolatencyDisable) {
                mCmdParams.add("-tune");
                mCmdParams.add("zerolatency");
            }
            mCmdParams.add("-r");
            mCmdParams.add("15");
            mCmdParams.add("-force_key_frames");
            mCmdParams.add("expr:gte(t,n_forced*5)");
            mCmdParams.add("-pix_fmt");
            mCmdParams.add("yuv420p");
            mCmdParams.add("-vb");
            mCmdParams.add((int) (((float) (params.raw_w * params.raw_h) / (float) resDefault) * bitrateDefault) + "k");
            mCmdParams.add("-bf");
            mCmdParams.add("0");
            mCmdParams.add("-shortest");
            mCmdParams.add("-movflags");
            mCmdParams.add("faststart");
            mCmdParams.add("-preset");
            mCmdParams.add("veryfast");
            mCmdParams.add("-crf");
            mCmdParams.add("23.0");
            mCmdParams.add("-f");
            mCmdParams.add("mp4");
            mCmdParams.add("-y");
            mCmdParams.add(sub_output);

            mCmdParams.add("-map");
            mCmdParams.add("[vout2]");
            mCmdParams.add("-c:v");
            mCmdParams.add("libx264");
            if (!mZerolatencyDisable) {
                mCmdParams.add("-tune");
                mCmdParams.add("zerolatency");
            }
            mCmdParams.add("-r");
            mCmdParams.add("15");
            mCmdParams.add("-force_key_frames");
            mCmdParams.add("expr:gte(t,n_forced*5)");
            mCmdParams.add("-pix_fmt");
            mCmdParams.add("yuv420p");
            mCmdParams.add("-vb");
            mCmdParams.add((int) (((float) (params.raw_w * params.raw_h) / (float) resDefault) * bitrateDefault) + "k");
            mCmdParams.add("-bf");
            mCmdParams.add("0");
            mCmdParams.add("-shortest");
            mCmdParams.add("-movflags");
            mCmdParams.add("faststart");
            mCmdParams.add("-preset");
            mCmdParams.add("veryfast");
            mCmdParams.add("-crf");
            mCmdParams.add("23.0");
            mCmdParams.add("-f");
            mCmdParams.add("mp4");
            mCmdParams.add("-y");
            mCmdParams.add(sub_pip_output);
            start();
        }
    }

    /**
     * burn the srt subtile to video,must have ttf font.
     * @param rawVideoPath path of original material video
     * @param subParams SubParams object
     * @param outPath where the output file you want store
     * @param l the observer about error/progress and so on
     * @throws IllegalArgumentException
     * @throws IllegalStateException
     */
    public void burnSubtitle(String rawVideoPath, SubParams subParams, String outPath, IVideoSynthesisListener l) throws IllegalArgumentException,IllegalStateException {
        synchronized (this) {
            Log.i(TAG, "burnSubtitle output path " + outPath);
            int fontSize = 36, width = 0, height = 0;
            final int bitrateDefault = 700;
            final int resDefault = 540*960;

            if (rawVideoPath == null || subParams.mSrtPath == null ||
                    subParams.mFontPath == null || subParams.mFontName == null) {
                Log.e(TAG, "burnSubtitle : Input Params is inValid, exit");
                throw new IllegalArgumentException();
            }

            if (getStatus()) {
                Log.d(TAG, "burnSubtitle : ffmpeg instance is runing, pls waiting ffmpeg end");
                throw new IllegalStateException();
            }

            try {
                FFmpegMediaMetadataRetriever r = new FFmpegMediaMetadataRetriever();
                r.setDataSource(rawVideoPath);
                FFmpegMediaMetadataRetriever.Metadata data = r.getMetadata();
                mDurationRef = data.getLong(FFmpegMediaMetadataRetriever.METADATA_KEY_DURATION);
                width = data.getInt(FFmpegMediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
                height = data.getInt(FFmpegMediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "burnSubtitle : Input Params is inValid, exit");
                throw new IllegalArgumentException();
            }

            mListener = l;
            mCmdParams = new ArrayList<>();
            mCmdParams.add("ffmpeg");
            mCmdParams.add("-i");
            mCmdParams.add(rawVideoPath);
            mCmdParams.add("-c:v");
            mCmdParams.add("libx264");
            if (!mZerolatencyDisable) {
                mCmdParams.add("-tune");
                mCmdParams.add("zerolatency");
            }
            mCmdParams.add("-r");
            mCmdParams.add("15");
            mCmdParams.add("-force_key_frames");
            mCmdParams.add("expr:gte(t,n_forced*5)");
            mCmdParams.add("-pix_fmt");
            mCmdParams.add("yuv420p");
            mCmdParams.add("-vb");
            mCmdParams.add((int) (((float) (width * height) / (float) resDefault) * bitrateDefault) + "k");
            mCmdParams.add("-bf");
            mCmdParams.add("0");
            mCmdParams.add("-shortest");
            mCmdParams.add("-movflags");
            mCmdParams.add("faststart");
            mCmdParams.add("-vf");
            mCmdParams.add("subtitles=" + subParams.mSrtPath + ":imageA=" + subParams.mImageAPath +
                    ":imageB=" + subParams.mImageBPath + ":imageC=" + subParams.mImageCPath +
                    ":imageD=" + subParams.mImageDPath + ":interval=" + subParams.mInterval +
                    ":margin=" + (subParams.mScaleRatio-1.0f)/2.0f + ":scale_ratio=" + subParams.mScaleRatio +
                    ":fontsdir=" + subParams.mFontPath + ":force_style='FontName=" + subParams.mFontName +
                    ",FontSize=" + subParams.mFontSize + ",MarginV=" + subParams.mSubMarginV +
                    ",BorderStyle=1"+",Outline="+subParams.mOutline+",Shadow=0,PrimaryColour=&HFFFFFF&,OutlineColour=&H000000&,Spacing=2"+"'");
            mCmdParams.add("-preset");
            mCmdParams.add("veryfast");
            mCmdParams.add("-crf");
            mCmdParams.add("23.0");
            mCmdParams.add("-f");
            mCmdParams.add("mp4");
            mCmdParams.add("-y");
            mCmdParams.add(outPath);
            start();
        }
    }

    private String createConcatListFile(List<String> inputVideoList, String outputpath) {
        String listFilePath = outputpath.substring(0, outputpath.lastIndexOf("/") + 1);
        String listFileName = outputpath.substring(outputpath.lastIndexOf("/") + 1, outputpath.lastIndexOf("."));
        String concatListFilePath = listFilePath + listFileName + ".txt";
        try {
            File file = new File(concatListFilePath);
            if(!file.getParentFile().exists()) {
                if (!file.getParentFile().mkdirs()) {
                    Log.e(TAG, "mkdir " + listFilePath + " create failed");
                    return null;
                }
            }
            if (!file.exists()) {
                if(!file.createNewFile()) {
                    Log.e(TAG, concatListFilePath+ " create failed");
                    return null;
                }
            } else {
                file.delete();
                if(!file.createNewFile()) {
                    Log.e(TAG, concatListFilePath+ " create failed");
                    return null;
                }
            }
            file.setReadable(true, false);
            FileOutputStream outStream = new FileOutputStream(file);
            outStream.write("ffconcat version 1.0\n".getBytes());
            for (String str : inputVideoList)  {
                Log.i(TAG, "ffconcat input video path " + str);
                outStream.write(("file \'" + str + "\'\n").getBytes());
            }
            outStream.close();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        return concatListFilePath;
    }

    private PipParams CalculatePipParameters(MetaData rawVideo, MetaData cameraVideo) {
        PipParams pipParams = new PipParams();

        /* got rawvideo metadata */
        FFmpegMediaMetadataRetriever mediaMetadataRetriever = new FFmpegMediaMetadataRetriever();
        try {
            mediaMetadataRetriever.setDataSource(rawVideo.mPath);
        } catch (Exception e) {
            mediaMetadataRetriever.release();
            e.printStackTrace();
            return null;
        }
        FFmpegMediaMetadataRetriever.Metadata data = mediaMetadataRetriever.getMetadata();
        if (data != null) {
            pipParams.raw_w = data.getInt(FFmpegMediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
            pipParams.raw_h = data.getInt(FFmpegMediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
            pipParams.raw_duration = data.getLong(FFmpegMediaMetadataRetriever.METADATA_KEY_DURATION);
        }
        mediaMetadataRetriever.release();

        if (data == null || pipParams.raw_w <= 0 || pipParams.raw_h <= 0) {
            Log.e(TAG, "Raw Video MetaData w " + pipParams.raw_w + " ,h " + pipParams.raw_h);
            return null;
        }

        /* got camera metadata */
        mediaMetadataRetriever = new FFmpegMediaMetadataRetriever();
        try {
            mediaMetadataRetriever.setDataSource(cameraVideo.mPath);
        } catch (Exception e) {
            mediaMetadataRetriever.release();
            e.printStackTrace();
            return null;
        }
        data = mediaMetadataRetriever.getMetadata();
        if (data != null) {
            pipParams.camera_w = data.getInt(FFmpegMediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
            pipParams.camera_h = data.getInt(FFmpegMediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
            pipParams.camera_duration = data.getLong(FFmpegMediaMetadataRetriever.METADATA_KEY_DURATION);
        }
        mediaMetadataRetriever.release();

        if (data == null || pipParams.camera_w <= 0 || pipParams.camera_h <= 0) {
            Log.e(TAG, "Camera Video MetaData w " + pipParams.camera_w + ",h " + pipParams.camera_h);
            return null;
        }

        /* calc camera and decor coordinate */
        int camera_rect_w = (int) (pipParams.raw_w * (cameraVideo.mRect.right - cameraVideo.mRect.left));
        int camera_rect_h = (int) (pipParams.raw_h * (cameraVideo.mRect.top - cameraVideo.mRect.bottom));
        float camera_rect_aspect_ratio = (float)camera_rect_w/(float)camera_rect_h;
        float camera_video_aspect_ratio = (float)pipParams.camera_w/(float)pipParams.camera_h;
        if (camera_video_aspect_ratio > camera_rect_aspect_ratio) {
            //int camera_overlay_y_diff = camera_rect_h - (int) ((float)camera_rect_w / camera_video_aspect_ratio);
            //mPipParams.camera_overlay_y += camera_overlay_y_diff/2;
            camera_rect_h = (int) ((float)camera_rect_w / camera_video_aspect_ratio);
        } else {
            //int camera_overlay_x_diff = camera_rect_w - (int) ((float)camera_rect_h * camera_video_aspect_ratio);
            //mPipParams.camera_overlay_x += camera_overlay_x_diff/2;
            camera_rect_w = (int) ((float)camera_rect_h * camera_video_aspect_ratio);
        }
        pipParams.camera_w = camera_rect_w;
        pipParams.camera_h = camera_rect_h;

        pipParams.camera_overlay_x = (int) (pipParams.raw_w * cameraVideo.mRect.left) - PIXEL_DIFF;
        pipParams.camera_overlay_y = (int) (pipParams.raw_h - pipParams.raw_h * cameraVideo.mRect.top) - PIXEL_DIFF;
        pipParams.camera_overlay_x = align(pipParams.camera_overlay_x, 2);
        pipParams.camera_overlay_y = align(pipParams.camera_overlay_y, 2);
        return pipParams;
    }

    private int align(int x, int align) {
       return ((( x ) + (align) - 1) / (align) * (align));
    }

    private void start() {
        if (mFFcmd != null) {
            mFFcmd.prepareAsync();
            setStatus(true);
        }
    }

    /**
    *Stop video synthesis in running,
    *including stitching and picture-in-picture
    */
    public void stop() {
        synchronized (this) {
            if (mFFcmd != null) {
                mFFcmd.stop();
            }
        }
    }

    /**
     * Release the instance after stopping the video synthesis
     */
    public void release() {
        synchronized (this) {
            if (mFFcmd != null) {
                mFFcmd.release();
                mFFcmd.setListener(null);
            }
            mFFcmd = null;
            sInstance = null;
        }
    }

    private IFFMpegCommandListener mOnFFMpegCmdListener = new IFFMpegCommandListener() {
        @Override
        public void onInfo(int arg1, int arg2, Object obj) {
            switch (arg1) {
                /*The native layer ffmpeg is ready to start synthesis*/
                case FFmpegCommand.FFCMD_INFO_PREPARED:
                    Log.i(TAG, "XMFFmpegCommand prepared");
                    FFMpegCmdRun();
                    break;

                /*Native layer ffmpeg has started*/
                case FFmpegCommand.FFCMD_INFO_STARTED:
                    Log.i(TAG, "XMFFmpegCommand start");
                    if (mListener != null)
                        mListener.onStarted();
                    break;

                /*video synthetic percentage*/
                case FFmpegCommand.FFCMD_INFO_PROGRESS:
                    if (mListener != null && mDurationRef != 0) {
                        int progress = (int) (100 * ((float) arg2 / (float) mDurationRef));
                        Log.i(TAG, "XMFFmpegCommand progress " + progress);
                        mListener.onProgress(progress);
                    }
                    break;

                /*Native layer ffmpeg video synthesis has stopped*/
                case FFmpegCommand.FFCMD_INFO_STOPPED:
                    Log.i(TAG, "XMFFmpegCommand stop");
                    setStatus(false);

                    if (mListener != null) {
                        mListener.onStopped();
                    }
                    break;

                /*Native layer ffmpeg video synthesis has completed*/
                case FFmpegCommand.FFCMD_INFO_COMPLETED:
                    Log.i(TAG, "XMFFmpegCommand completed");
                    setStatus(false);

                    if (mListener != null) {
                        mListener.onCompleted();
                    }
                    if (!TextUtils.isEmpty(mConcatFilePath)) {
                        File file = new File(mConcatFilePath);
                        if (file.exists())
                            file.delete();
                    }
                    break;
                default:
                    Log.i(TAG, "Unknown message type " + arg1);
                    break;
            }
        }

        @Override
        public void onError(int arg1, int arg2, Object obj) {
            setStatus(false);

            if (mListener != null)
                mListener.onError();
            Log.e(TAG, "XMFFmpegCommand error arg1 " + arg1 + " arg2 " + arg2 + ", please release VideoSynthesis.");
        }
    };

    synchronized private void setStatus(boolean running) {
        mRunning = running;
    }

    private boolean getStatus() {
        return mRunning;
    }

    private void FFMpegCmdRun() {
        if (mCmdParams == null) {
            Log.e(TAG, "input is invalid, FFMpegCmdRun stop");
            if (mListener != null) {
                mListener.onStopped();
            }
            return;
        }
        String[] array = mCmdParams.toArray(new String[0]);
        if (mFFcmd != null && array.length != 0) {
            mFFcmd.start(array.length, array);
        }
    }

    public interface IVideoSynthesisListener {
        /*Native layer ffmepg has started*/
        void onStarted();

        /*Native layer ffmepg has started*/
        void onStopped();

        /*Progress percentage*/
        void onProgress(int progress);

        /*Successful video synthesis*/
        void onCompleted();

        /*Native layer ffmepg error*/
        void onError();
    }

    public static class MetaData {
        /*video type,example RAW_VIDEO_TYPE/CAMERA_VIDEO_TYPE etc.*/
        public String mType;
        /*Video file path*/
        public String mPath;
        /*The position of the picture-in-picture video in the background video*/
        public Rect   mRect;

        public MetaData() {
        }

        public MetaData(String type, String path, Rect rect) {
            mType = type;
            mPath = path;
            mRect = rect;
        }
    }

    /*Rectangular position coordinates*/
    public static class Rect {
        public float left;
        public float bottom;
        public float right;
        public float top;

        public Rect() {
        }

        public Rect(float left, float bottom, float right, float top) {
            this.left = left;
            this.bottom = bottom;
            this.right = right;
            this.top = top;
        }
    }

    private class PipParams {
        public int raw_w;
        public int raw_h;
        public long raw_duration;
        public int camera_w;
        public int camera_h;
        public long camera_duration;
        public int camera_overlay_x;
        public int camera_overlay_y;
    }

    public static class SubParams {
        /*SRT file path*/
        public String mSrtPath;
        /*The path of the font*/
        public String mFontPath;
        /*Format name of font*/
        public String mFontName = "AR PL UKai CN";
        /*Format size of font*/
        public int mFontSize = 23;
        /*Vertical spacing between the subtitle and the bottom of the screen*/
        public int mSubMarginV = 10;
        /*Head image path, maximum support 4*/
        public String mImageAPath;
        public String mImageBPath;
        public String mImageCPath;
        public String mImageDPath;
        /*Horizontal spacing between head image and subtitle, the value is in subtitle height*/
        public float mInterval = 0.3f;
        /*Height Ratio of head image and subtitle*/
        public float mScaleRatio = 1.2f;
        /*The width of the Outline effect*/
        public double mOutline = 0.5;

        public SubParams() {
        }
    }
}
