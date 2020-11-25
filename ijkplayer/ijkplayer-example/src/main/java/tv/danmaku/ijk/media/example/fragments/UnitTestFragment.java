package tv.danmaku.ijk.media.example.fragments;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import com.xmly.media.co_production.VideoSynthesis;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.ArrayList;

import tv.danmaku.ijk.media.example.R;
import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;

import static android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM;
import static android.media.MediaCodec.INFO_OUTPUT_FORMAT_CHANGED;

public class UnitTestFragment extends Fragment implements View.OnClickListener {
    private final static String TAG = "UnitTestFragment";
    /**
     * control
     */
    private Button mHwAACEncoderTest;
    private Button mDubTest;
    private Button mPipTest;
    private Button mConcatTest;
    private Button mBurnSubtitleTest;
    private Button mPipSubtitleTest;

    /**
     * dub test
     */
    private IjkMediaPlayer mPlayer;
    private long mCostTime;
    private MyHandler mHandler;

    private final static int UPDATE_HW_ENCODER = 1;
    private final static int UPDATE_DUB_PROGRESS = 2;
    private final static int UPDATE_DUB_SUCCESS = 3;
    private final static int UPDATE_DUB_ERROR = 4;
    private final static int UPDATE_FFCMD_PROGRESS = 5;
    private final static int UPDATE_FFCMD_SUCCESS = 6;
    private final static int UPDATE_FFCMD_ERROR = 7;

    private final static int PIP = 1;
    private final static int CONCAT = 2;
    private final static int BURNSUB = 3;
    private final static int PIPSUB = 4;
    private boolean mPipClick;
    private boolean mConcatClick;
    private boolean mBurnsubClick;
    private boolean mPipSubClick;

    private long mStartTime;
    public static UnitTestFragment newInstance() {
        UnitTestFragment f = new UnitTestFragment();
        return f;
    }

    public UnitTestFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        ViewGroup viewGroup = (ViewGroup) inflater.inflate(R.layout.fragment_unit_test, container, false);
        mHwAACEncoderTest = (Button) viewGroup.findViewById(R.id.hw_aac_encoder_test);
        mHwAACEncoderTest.setOnClickListener(this);

        mDubTest = (Button) viewGroup.findViewById(R.id.dub_test);
        mDubTest.setOnClickListener(this);

        mPipTest = (Button) viewGroup.findViewById(R.id.pip_test);
        mPipTest.setOnClickListener(this);

        mConcatTest = (Button) viewGroup.findViewById(R.id.concat_test);
        mConcatTest.setOnClickListener(this);

        mBurnSubtitleTest = (Button) viewGroup.findViewById(R.id.burn_subtitle_test);
        mBurnSubtitleTest.setOnClickListener(this);

        mPipSubtitleTest = (Button) viewGroup.findViewById(R.id.pip_subtitle_test);
        mPipSubtitleTest.setOnClickListener(this);

        mHandler = new MyHandler(this);
        return viewGroup;
    }

    private static class MyHandler extends Handler {
        private final WeakReference<UnitTestFragment> mFrament;

        public MyHandler(UnitTestFragment fragment){
            mFrament = new WeakReference<>(fragment);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            UnitTestFragment fragment = mFrament.get();
            if (fragment != null){
                switch (msg.what) {
                    case UPDATE_HW_ENCODER:
                        fragment.mHwAACEncoderTest.setClickable(true);
                        fragment.mHwAACEncoderTest.setText("Hw Encoder Cost Time " + fragment.mCostTime + " ms ");
                        break;
                    case UPDATE_DUB_PROGRESS:
                        fragment.mDubTest.setText("Current Mux Progress ---> " + msg.arg1);
                        break;
                    case UPDATE_DUB_SUCCESS:
                        fragment.mDubTest.setClickable(true);
                        fragment.mDubTest.setText("Dub Cost Time :" + msg.arg1 + " ms ");
                        break;
                    case UPDATE_DUB_ERROR:
                        fragment.mDubTest.setClickable(true);
                        fragment.mDubTest.setText("Dub Error ---> " + msg.arg1);
                        break;
                    case UPDATE_FFCMD_PROGRESS:
                        if (msg.arg1 == PIP) {
                            fragment.mPipTest.setText("Current Pip Progress ---> " + msg.arg2);
                        } else if (msg.arg1 == CONCAT) {
                            fragment.mConcatTest.setText("Current Concat Progress ---> " + msg.arg2);
                        } else if (msg.arg1 == BURNSUB) {
                            fragment.mBurnSubtitleTest.setText("Current BurnSubtitle Progress ---> " + msg.arg2);
                        } else if (msg.arg1 == PIPSUB) {
                            fragment.mPipSubtitleTest.setText("Current PipSubtitle Progress ---> " + msg.arg2);
                        }
                        break;
                    case UPDATE_FFCMD_SUCCESS:
                        if (msg.arg1 == PIP) {
                            fragment.mPipTest.setClickable(true);
                            fragment.mPipTest.setText("Pip Cost Time :" + msg.arg2 + " ms ");
                        } else if (msg.arg1 == CONCAT) {
                            fragment.mConcatTest.setClickable(true);
                            fragment.mConcatTest.setText("Concat Cost Time :" + msg.arg2 + "ms");
                        } else if (msg.arg1 == BURNSUB) {
                            fragment.mBurnSubtitleTest.setClickable(true);
                            fragment.mBurnSubtitleTest.setText("BurnSubtitle Cost Time :" + msg.arg2 + "ms");
                        } else if (msg.arg1 == PIPSUB) {
                            fragment.mPipSubtitleTest.setClickable(true);
                            fragment.mPipSubtitleTest.setText("PipSubtitle Cost Time :" + msg.arg2 + "ms");
                        }
                        break;
                    case UPDATE_FFCMD_ERROR:
                        if (msg.arg1 == PIP) {
                            fragment.mPipTest.setClickable(true);
                            fragment.mPipTest.setText("Pip Error ---> " + msg.arg2);
                        } else if (msg.arg1 == CONCAT) {
                            fragment.mConcatTest.setClickable(true);
                            fragment.mConcatTest.setText("Concat Error ---> " + msg.arg2);
                        } else if (msg.arg1 == BURNSUB) {
                            fragment.mBurnSubtitleTest.setClickable(true);
                            fragment.mBurnSubtitleTest.setText("BurnSub Error ---> " + msg.arg2);
                        } else if (msg.arg1 == PIPSUB) {
                            fragment.mPipSubtitleTest.setClickable(true);
                            fragment.mPipSubtitleTest.setText("PipSub Error ---> " + msg.arg2);
                        }
                        break;
                    default:
                        break;
                }
            }
        }
    }

    private IMediaPlayer.OnInfoListener mInfoListener = new IMediaPlayer.OnInfoListener() {
        public boolean onInfo(IMediaPlayer mp, int arg1, int arg2) {
            Message msg = new Message();
            switch (arg1) {
                case IMediaPlayer.MEDIA_INFO_MUX_SUCCESS:
                    Log.d(TAG, "mux success release player");
                    if (mPlayer != null)
                        mPlayer.release();
                    msg.what = UPDATE_DUB_SUCCESS;
                    msg.arg1 = arg2;
                    mHandler.sendMessage(msg);
                    break;
                case IMediaPlayer.MEDIA_INFO_MUX_PROGRESS:
                    Log.d(TAG, "mux success " + arg2);
                    msg.what = UPDATE_DUB_PROGRESS;
                    msg.arg1 = arg2;
                    mHandler.sendMessage(msg);
                    break;
                default:
                    break;
            }
            return true;
        }
    };

    private IMediaPlayer.OnErrorListener mErrorListener = new IMediaPlayer.OnErrorListener() {
        @Override
        public boolean onError(IMediaPlayer mp, int what, int extra) {
            if (mPlayer != null) {
                Log.d(TAG, "mux failed release player also");
                mPlayer.release();
                Message msg = new Message();
                msg.what = UPDATE_DUB_ERROR;
                msg.arg1 = extra;
                mHandler.sendMessage(msg);
            }
            return false;
        }
    };

    private VideoSynthesis.IVideoSynthesisListener mOnVideoSynthesisListener = new VideoSynthesis.IVideoSynthesisListener() {
        @Override
        public void onStarted() {
            Log.i(TAG, "VideoSynthesis started");
        }

        @Override
        public void onStopped() {
            Log.i(TAG, "VideoSynthesis stopped");
        }

        @Override
        public void onProgress(int progress) {
            Log.i(TAG, "VideoSynthesis progress : " + progress);
            Message msg = new Message();
            msg.what = UPDATE_FFCMD_PROGRESS;
            if (mPipClick) {
                msg.arg1 = PIP;
            } else if (mConcatClick) {
                msg.arg1 = CONCAT;
            } else if (mBurnsubClick) {
                msg.arg1 = BURNSUB;
            } else if(mPipSubClick) {
                msg.arg1 = PIPSUB;
            }
            msg.arg2 = progress;
            mHandler.sendMessage(msg);
        }

        @Override
        public void onCompleted() {
            Message msg = new Message();
            msg.what = UPDATE_FFCMD_SUCCESS;
            if (mPipClick) {
                msg.arg1 = PIP;
            } else if (mConcatClick) {
                msg.arg1 = CONCAT;
            } else if (mBurnsubClick) {
                msg.arg1 = BURNSUB;
            } else if(mPipSubClick) {
                msg.arg1 = PIPSUB;
            }
            msg.arg2 = (int)(System.currentTimeMillis() - mStartTime);
            mHandler.sendMessage(msg);
            mPipClick = false;
            mConcatClick = false;
            mBurnsubClick = false;
            mPipSubClick = false;
            Log.i(TAG, "VideoSynthesis completed");
        }

        @Override
        public void onError() {
            Message msg = new Message();
            msg.what = UPDATE_FFCMD_ERROR;
            if (mPipClick) {
                msg.arg1 = PIP;
            } else if (mConcatClick) {
                msg.arg1 = CONCAT;
            } else if (mBurnsubClick) {
                msg.arg1 = BURNSUB;
            } else if(mPipSubClick) {
                msg.arg1 = PIPSUB;
            }
            msg.arg2 = 0;
            mHandler.sendMessage(msg);
            mPipClick = false;
            mConcatClick = false;
            mBurnsubClick = false;
            mPipSubClick = false;
            Log.e(TAG, "VideoSynthesis error");
        }
    };

    @Override
    public void onDetach() {
        super.onDetach();
    }

    @Override
    public void onStop() {
        super.onStop();
        VideoSynthesis.getInstance().release();
    }

    /**
     * maybe can goto video activity !
     */
    @Override
    public void onClick(final View v) {
        VideoSynthesis.getInstance().setZerolatency(false);
        ArrayList<VideoSynthesis.MetaData> metaDataList = new ArrayList<>();
        String pipOutputpath = "/sdcard/pipout.mp4";
        metaDataList.clear();

        VideoSynthesis.MetaData rawVideoMetaData = new VideoSynthesis.MetaData();
        rawVideoMetaData.mType = VideoSynthesis.RAW_VIDEO_TYPE;
        rawVideoMetaData.mPath = "/sdcard/y_bg.mp4";
        metaDataList.add(rawVideoMetaData);

        VideoSynthesis.MetaData cameraMetaData = new VideoSynthesis.MetaData();
        cameraMetaData.mType = VideoSynthesis.CAMERA_VIDEO_TYPE;
        cameraMetaData.mPath = "/sdcard/y_test.mp4";
        cameraMetaData.mRect = new VideoSynthesis.Rect(0.01333f, 0.6123348f, 0.292f, 0.9559f);
        metaDataList.add(cameraMetaData);

        VideoSynthesis.MetaData watermarkMetaData = new VideoSynthesis.MetaData();
        watermarkMetaData.mType = VideoSynthesis.WATERMARK_TYPE;
        watermarkMetaData.mPath = "/sdcard/watermark_white.jpg";
        metaDataList.add(watermarkMetaData);

        switch (v.getId()) {
            case R.id.hw_aac_encoder_test:
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        String outHwAacPath = "/sdcard/hw_jsyan.aac";
                        String wavPath = "/sdcard/test.wav";
                        encoderAac(wavPath, outHwAacPath);
                        Message msg = new Message();
                        msg.what = UPDATE_HW_ENCODER;
                        mHandler.sendMessage(msg);
                    }
                }).start();
                mHwAACEncoderTest.setClickable(false);
                mHwAACEncoderTest.setText("Hw Encoder is working");
                break;
            case R.id.dub_test:
                mDubTest.setClickable(false);
                /**
                 * test data
                 */
                String localVideoPath = "/sdcard/video.mp4";
                String localBackgroundAudioPath = "/sdcard/bg.m4a";
                String localWav = "/sdcard/test.wav";
                String duboutPath = "/sdcard/dubout.mp4";

                mPlayer = new IjkMediaPlayer();

                /**
                 * bg---human---video
                 */
                String[] inputPaths = new String[]{ localBackgroundAudioPath, localWav, localVideoPath };
                mPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec", 1);
                mPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "human-volume", "0.7f");
                mPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "background-volume", "0.3f");

                mPlayer.setOnInfoListener(mInfoListener);
                mPlayer.setOnErrorListener(mErrorListener);
                mPlayer.muxStream(inputPaths, duboutPath);
                break;

            case R.id.pip_test:
                try {
                    VideoSynthesis.getInstance().pipMergeVideo(metaDataList, pipOutputpath, mOnVideoSynthesisListener);
                    mPipTest.setClickable(false);
                    mPipClick = true;
                    mStartTime = System.currentTimeMillis();
                } catch (IllegalArgumentException e) {
                    e.printStackTrace();
                } catch (IllegalStateException e) {
                    e.printStackTrace();
                }
                break;

            case R.id.concat_test:
                ArrayList<String> videoConcatPathList = new ArrayList<>();
                String concatOutputpath = "/sdcard/concatout.mp4";
                videoConcatPathList.clear();
                videoConcatPathList.add("/sdcard/bg.mp4");
                videoConcatPathList.add("/sdcard/bg.mp4");
                videoConcatPathList.add("/sdcard/bg.mp4");
                videoConcatPathList.add("/sdcard/bg.mp4");

                try {
                    VideoSynthesis.getInstance().videoConcat(videoConcatPathList, concatOutputpath, mOnVideoSynthesisListener);
                    mConcatTest.setClickable(false);
                    mConcatClick = true;
                    mStartTime = System.currentTimeMillis();
                } catch (IllegalArgumentException e) {
                    e.printStackTrace();
                } catch (IllegalStateException e) {
                    e.printStackTrace();
                }
                break;

            case R.id.burn_subtitle_test:
                String rawVideo = "/sdcard/sub/video.mp4";
                String burnSubtitleOutputpath = "/sdcard/sub/burnsubtitleout.mp4";
                VideoSynthesis.SubParams burnSubtitleParams = new VideoSynthesis.SubParams();
                burnSubtitleParams.mSrtPath = "/sdcard/sub/subtitle.srt";
                burnSubtitleParams.mFontPath = "/sdcard/arphic";
                burnSubtitleParams.mFontName = "FZHei-B01S";
                burnSubtitleParams.mFontSize = 23;
                burnSubtitleParams.mSubMarginV = 80;
                burnSubtitleParams.mImageAPath = "/sdcard/dog.png";
                burnSubtitleParams.mImageBPath = null;
                burnSubtitleParams.mImageCPath = null;
                burnSubtitleParams.mImageDPath = null;
                burnSubtitleParams.mInterval = 0.6f;
                burnSubtitleParams.mScaleRatio = 1.2f;

                try {
                    VideoSynthesis.getInstance().burnSubtitle(rawVideo, burnSubtitleParams, burnSubtitleOutputpath, mOnVideoSynthesisListener);
                    mBurnSubtitleTest.setClickable(false);
                    mBurnsubClick = true;
                    mStartTime = System.currentTimeMillis();
                } catch (IllegalArgumentException e) {
                    e.printStackTrace();
                } catch (IllegalStateException e) {
                    e.printStackTrace();
                }
                break;

            case R.id.pip_subtitle_test:
                String pipSubOutputpath = "/sdcard/pipsubout.mp4";
                String subOutputpath = "/sdcard/subout.mp4";
                VideoSynthesis.SubParams subParams = new VideoSynthesis.SubParams();
                subParams.mSrtPath = "/sdcard/sub/subtitle.srt";
                subParams.mFontPath = "/sdcard/arphic";
                subParams.mFontName = "FZHei-B01S";
                subParams.mFontSize = 23;
                subParams.mSubMarginV = 80;
                subParams.mImageAPath = "/sdcard/dog.png";
                subParams.mImageBPath = null;
                subParams.mImageCPath = null;
                subParams.mImageDPath = null;
                subParams.mInterval = 0.6f;
                subParams.mScaleRatio = 1.2f;

                try {
                    VideoSynthesis.getInstance().pipMergeVideoWithSubtitle(metaDataList, subParams, subOutputpath, pipSubOutputpath, mOnVideoSynthesisListener);
                    mPipSubtitleTest.setClickable(false);
                    mPipSubClick = true;
                    mStartTime = System.currentTimeMillis();
                } catch (IllegalArgumentException e) {
                    e.printStackTrace();
                } catch (IllegalStateException e) {
                    e.printStackTrace();
                }
                break;
            default:
                break;
        }
    }

    private int toUnsignedInt32LittleEndian(byte[] bytes, int offset) {
        return ((bytes[offset + 3] & 0xff) << 24) | ((bytes[offset + 2] & 0xff) << 16) | ((bytes[offset + 1] & 0xff) << 8) | (bytes[offset] & 0xff);
    }

    private int toUnsignedInt16(byte[] bytes, int offset) {
        return ((bytes[offset + 1] & 0xff) << 8) | (bytes[offset] & 0xff);
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    private void encoderAac(String wavpath, String aacpath) {
        File wavFile = new File(wavpath);
        File aacFile = new File(aacpath);
        if (aacFile.exists()) {
            aacFile.delete();
        }
        byte[] inbuffer = new byte[2048];
        int size = 0;

        try {
            aacFile.createNewFile();
            InputStream is = new FileInputStream(wavFile);
            OutputStream os = new FileOutputStream(aacFile);

            //let's read wave header 44 bytes
            size = is.read(inbuffer, 0, 44);
            if (size != 44) {
                Log.d(TAG, "read wave header failed ");
                return;
            }
            int offset = 0;
            //riff
            if (inbuffer[offset] != 'R' || inbuffer[offset + 1] != 'I' || inbuffer[offset + 2] != 'F' || inbuffer[offset + 3] != 'F') {
                Log.d(TAG, "this not a riff file");
                return;
            }
            offset += 4;
            //4 bytes riff chunk size
            int riffcksize = toUnsignedInt32LittleEndian(inbuffer, offset);
            Log.d(TAG, "riff size " + riffcksize);
            offset += 4;
            //wave
            if (inbuffer[offset] != 'W' || inbuffer[offset + 1] != 'A' || inbuffer[offset + 2] != 'V' || inbuffer[offset + 3] != 'E') {
                Log.d(TAG, "this not a wave file");
                return;
            }
            offset += 4;
            //'fmt '
            if (inbuffer[offset] != 'f' || inbuffer[offset + 1] != 'm' || inbuffer[offset + 2] != 't' || inbuffer[offset + 3] != ' ') {
                Log.d(TAG, "this not a wave fmt chunk");
                return;
            }
            offset += 4;
            int fmtcksize = toUnsignedInt32LittleEndian(inbuffer, offset);
            Log.d(TAG, "fmt chunk size " + fmtcksize);
            offset += 4;
            int pcmformat = toUnsignedInt16(inbuffer, offset);
            if (pcmformat != 1) {
                Log.d(TAG, "this is not a pcm wave " + pcmformat);
                return;
            }
            offset += 2;
            int channel = toUnsignedInt16(inbuffer, offset);
            Log.d(TAG, "channel " + channel);
            offset += 2;
            int SamplesPerSec = toUnsignedInt32LittleEndian(inbuffer, offset);
            Log.d(TAG, "SamplesPerSec " + SamplesPerSec + "  " + inbuffer[offset + 3] + inbuffer[offset + 2] + inbuffer[offset + 1] + inbuffer[offset]);
            offset += 4;
            int BytesPerSec = toUnsignedInt32LittleEndian(inbuffer, offset);
            Log.d(TAG, "BytesPerSec " + BytesPerSec);
            offset += 4;
            int BlockAlign = toUnsignedInt16(inbuffer, offset);
            Log.d(TAG, "BlockAlign  " + BlockAlign);
            offset += 2;
            int BitsPerSample = toUnsignedInt16(inbuffer, offset);
            Log.d(TAG, "BitsPerSample  " + BitsPerSample);
            offset += 2;
            //data
            if (inbuffer[offset] != 'd' || inbuffer[offset + 1] != 'a' || inbuffer[offset + 2] != 't' || inbuffer[offset + 3] != 'a') {
                Log.d(TAG, "have no data chunk return");
                return;
            }
            offset += 4;
            int datacksize = toUnsignedInt32LittleEndian(inbuffer, offset);
            offset += 4;
            Log.d(TAG, "data chunk size " + datacksize + "offset " + offset);

            MediaCodec mediaCodec;
            try {
                mediaCodec = MediaCodec.createEncoderByType("audio/mp4a-latm");
            } catch (Exception e) {
                e.printStackTrace();
                return;
            }

            // AAC 硬编码器
            MediaFormat format = new MediaFormat();
            format.setString(MediaFormat.KEY_MIME, "audio/mp4a-latm");
            format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, channel); //声道数（这里是数字）
            format.setInteger(MediaFormat.KEY_SAMPLE_RATE, SamplesPerSec); //采样率
            format.setInteger(MediaFormat.KEY_BIT_RATE, 64000); //目标码率
            format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);

            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();//记录编码完成的buffer的信息
            mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);// MediaCodec.CONFIGURE_FLAG_ENCODE 标识为编码器
            mediaCodec.start();
            //2 channel * (16bit / 8 = 2bytes) * 1024 samples = 1 frame 22-23ms one frame
            int frameSize = 2 * 2 * 1024;
            ByteBuffer inputBuffer;
            ByteBuffer outputBuffer;
            byte[] framebuffer = new byte[frameSize];
            int outputBufferIndex = MediaCodec.INFO_OUTPUT_FORMAT_CHANGED;
            int inputBufferIndex = -1;
            int readSize = 0;
            int totalSize = 0;
            boolean flush_encoder = false;
            boolean quit = false;
            long begin = System.currentTimeMillis();

            ByteBuffer[] inputBuffers = mediaCodec.getInputBuffers();
            ByteBuffer[] outputBuffers = mediaCodec.getOutputBuffers();

            while (!quit) {
                if (!flush_encoder) {
                    readSize = is.read(framebuffer, 0, frameSize);
                    if (readSize >= 0) {
                        totalSize += readSize;
                    } else if (readSize == -1) {
                        flush_encoder = true;
                        Log.d(TAG, "end of stream");
                    } else {
                        Log.e(TAG, "read file error");
                        break;
                    }

                    //  <0一直等待可用的byteBuffer 索引;=0 马上返回索引 ;>0 等待相应的毫秒数返回索引
                    inputBufferIndex = mediaCodec.dequeueInputBuffer(-1); //一直等待（阻塞）
                    if (inputBufferIndex >= 0) {
                        if (flush_encoder) {
                            mediaCodec.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            Log.d(TAG, "flush encoder");
                        } else {
                            inputBuffer = inputBuffers[inputBufferIndex];
                            inputBuffer.clear();
                            inputBuffer.put(framebuffer, 0, readSize);
                            mediaCodec.queueInputBuffer(inputBufferIndex, 0, readSize, 0, 0);
                        }
                    }
                }

                //获取已经编码成的buffer的索引  0表示马上获取 ，>0表示最多等待多少毫秒获取
                outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo,0);
                while (outputBufferIndex >= 0) {
                    //------------添加头信息--------------
                    int outBufferSize = bufferInfo.size;
                    int outPacketSize = outBufferSize + 7; // 7 is ADTS size
                    byte[] outData = new byte[outPacketSize];

                    outputBuffer = outputBuffers[outputBufferIndex];
                    outputBuffer.position(bufferInfo.offset);
                    outputBuffer.limit(bufferInfo.offset + bufferInfo.size);

                    // it's a codec config such as channel/samplerate and so on
                    if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        Log.d(TAG, "codec special data");
                        /*
                         * 5 bits: object type
                         * if (object type == 31)
                         *      6 bits + 32: object type
                         * 4 bits: frequency index
                         * if (frequency index == 15)
                         *      24 bits: frequency
                         * 4 bits: channel configuration
                         * var bits: AOT Specific Config
                         * object type :
                         * 1: AAC Main
                         * 2: AAC LC (Low Complexity)
                         * frequency index 4 is 44100
                         * ref link:https://wiki.multimedia.cx/index.php?title=MPEG-4_Audio
                         */
                        outputBuffer.get(outData, 0, outBufferSize);
                        Log.d(TAG, "codec special data size " + outBufferSize);
                        int objectType = ((outData[0] & 0xf8) >> 3);
                        Log.d(TAG, "start 5 bit object type " + objectType);
                        if (objectType != 31) {
                            Log.d(TAG, "frequency index is " + (((outData[0] & 0x07) << 1)|(outData[1] & 0x80)));
                        }
                        Log.d(TAG, "channel config " + ((outData[1] & 0x78) >> 3));
                    } else if ((bufferInfo.flags & BUFFER_FLAG_END_OF_STREAM) != 0) {
                        Log.d(TAG, "output buffer eof quit");
                        quit = true;
                        break;
                    } else {
                        if (flush_encoder) {
                            Log.d(TAG, "output buffer have data");
                        }
                        addADTStoPacket(outData, outPacketSize, SamplesPerSec, channel);
                        outputBuffer.get(outData,7, outBufferSize);
                        try {
                            os.write(outData);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    mediaCodec.releaseOutputBuffer(outputBufferIndex, false);
                    outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo,0);
                }
            }

            long end = System.currentTimeMillis();
            mCostTime = end - begin;
            System.out.println("Hw Encoder Cost Time " + mCostTime);
            mediaCodec.stop();
            mediaCodec.release();
        } catch (IOException e) {
            Log.d(TAG, "create aac file failed\n");
            return;
        }
    }

    private void addADTStoPacket(byte[] packet, int packetLen, int sampleInHz, int chanCfgCounts) {
        int profile = 2; // AAC LC
        int freqIdx = 8; // 16KHz    39=MediaCodecInfo.CodecProfileLevel.AACObjectELD;

        switch (sampleInHz) {
            case 8000: {
                freqIdx = 11;
                break;
            }
            case 16000: {
                freqIdx = 8;
                break;
            }
            case 44100: {
                freqIdx = 4;
                break;
            }
            default:
                break;
        }
        int chanCfg = chanCfgCounts; // CPE
        // fill in ADTS data
        packet[0] = (byte) 0xFF;
        packet[1] = (byte) 0xF9;
        packet[2] = (byte) (((profile - 1) << 6) + (freqIdx << 2) + (chanCfg >> 2));
        packet[3] = (byte) (((chanCfg & 3) << 6) + (packetLen >> 11));
        packet[4] = (byte) ((packetLen & 0x7FF) >> 3);
        packet[5] = (byte) (((packetLen & 7) << 5) + 0x1F);
        packet[6] = (byte) 0xFC;
    }
}
