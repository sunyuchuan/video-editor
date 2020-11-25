package tv.danmaku.ijk.media.example.record;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by HXL on 16/8/11.
 * 用于实现录音   暂停录音
 */
public class AudioRecorder {
    //音频输入-麦克风
    private final static int AUDIO_INPUT = MediaRecorder.AudioSource.MIC;
    //采用频率
    //44100是目前的标准，但是某些设备仍然支持22050，16000，11025
    //采样频率一般共分为22.05KHz、44.1KHz、48KHz三个等级
    private final static int AUDIO_SAMPLE_RATE = 44100;
    //wav头信息长度
    private final static int WAV_HEARD_SIZE = 44;
    //声道双声道
    private final static int AUDIO_CHANNEL = AudioFormat.CHANNEL_IN_STEREO;
    //编码
    private final static int AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    // 缓冲区字节大小
    private int bufferSizeInBytes = 0;

    //录音对象
    private AudioRecord mAudioRecord;

    //录音状态
    private Status mStatus = Status.STATUS_INIT;

    //录音文件
    private File mFile;

    //线程池
    private ExecutorService mExecutorService;

    //录音监听
    private RecordStreamListener mListener;

    //覆盖录音的偏移量
    private long mOffset = WAV_HEARD_SIZE;

    private final static String TAG = AudioRecorder.class.getName();

    public AudioRecorder() {
        mExecutorService = Executors.newCachedThreadPool();
    }

    /**
     * 创建默认的录音对象
     *
     * @param filePath wav文件路径
     */
    public void createDefaultAudio(String filePath) {
        // 获得缓冲区字节大小
        bufferSizeInBytes = AudioRecord.getMinBufferSize(AUDIO_SAMPLE_RATE,
                AUDIO_CHANNEL, AUDIO_ENCODING);
        mAudioRecord = new AudioRecord(AUDIO_INPUT, AUDIO_SAMPLE_RATE, AUDIO_CHANNEL, AUDIO_ENCODING, bufferSizeInBytes);
        mFile = new File(filePath);
        if (mFile.exists()) {
            Log.e(TAG, "file exists can't create file pls use another name or delete the file");
        }
        //先写创建一个空文件
        writeWavHeader(mFile, 0);
        mStatus = Status.STATUS_INIT;
    }

    /**
     * 开始录音
     */
    public void startRecord() {
        if (mStatus == Status.STATUS_START) {
            throw new IllegalStateException("正在录音");
        }
        if (mStatus == Status.STATUS_STOP) {
            throw new IllegalStateException("need createDefaultAudio first");
        }
        Log.d("AudioRecorder", "===startRecord===" + mAudioRecord.getState());
        mAudioRecord.startRecording();

        //将录音状态设置成正在录音状态
        mStatus = Status.STATUS_START;

        //使用线程池管理线程
        mExecutorService.execute(new Runnable() {
            @Override
            public void run() {
                writeDataToFile(mFile);
            }
        });
    }

    /**
     * 暂停录音
     */
    public void pauseRecord() {
        Log.d("AudioRecorder", "===pauseRecord===");
        if (mStatus != Status.STATUS_START) {
//            throw new IllegalStateException("没有在录音");
        } else {
            mAudioRecord.stop();
            mStatus = Status.STATUS_PAUSE;
        }
    }

    /**
     * 停止录音
     */
    public void stopRecord() {
        Log.d("AudioRecorder", "===stopRecord===");
        if (mStatus == Status.STATUS_INIT || mStatus == Status.STATUS_STOP) {
            //throw new IllegalStateException("录音尚未开始");
            Log.d(TAG, "record not start");
            return;
        } else {
            mAudioRecord.stop();
            mAudioRecord.release();
            mStatus = Status.STATUS_STOP;
            mAudioRecord = null;
        }
    }

    private void writeWavHeader(File file, int pcmSize) {
        //update wave header
        // 填入参数，比特率等等。这里用的是16位单声道 441000 hz
        WaveHeader header = new WaveHeader();
        // 长度字段 = 内容的大小（TOTAL_SIZE) +
        // 头部字段的大小(不包括前面4字节的标识符RIFF以及fileLength本身的4字节)
        header.fileLength = pcmSize + (44 - 8);
        header.FmtHdrLeth = 16;
        header.BitsPerSample = 16;
        header.Channels = 2;
        header.FormatTag = 0x0001;
        header.SamplesPerSec = 44100;
        header.BlockAlign = (short) (header.Channels * header.BitsPerSample / 8);
        header.AvgBytesPerSec = header.BlockAlign * header.SamplesPerSec;
        header.DataHdrLeth = pcmSize;
        try {
            byte[] h = header.getHeader();
            RandomAccessFile wavfile = new RandomAccessFile(file, "rw");
            wavfile.seek(0);
            wavfile.write(h, 0, h.length);
            wavfile.close();
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "write wav header failed");
        }
    }

    //覆盖录音的起始点
    public void seekTo(long positionMills) {
        int pcmSize = (int)(AUDIO_SAMPLE_RATE * positionMills / 1000 * 2 * 2);
        mOffset = pcmSize + WAV_HEARD_SIZE;
    }

    public long getCurrentPositionMills() {
        float sec = (float)(mOffset - 44) / (AUDIO_SAMPLE_RATE * 2 * 2);
        return (long)(sec * 1000);
    }

    public long getTotalTimeMills(){
        float totalSizeFloat = (float) (mFile.length() - WAV_HEARD_SIZE)
                / (AUDIO_SAMPLE_RATE * 2 * 2);
        return (long)(totalSizeFloat * 1000);
    }

    public long setOffsetByPercent(float percent){
        if (0.0 <= percent && percent <= 100.0f
                && mFile != null && mFile.exists()){
            float selectSizeFloat = (mFile.length() - WAV_HEARD_SIZE) * percent / 100.0f;
            long selectSizeLong = ((long)selectSizeFloat)/2*2;
            mOffset = selectSizeLong + WAV_HEARD_SIZE;
        }
        return getCurrentPositionMills();
    }

    /**
     * 将音频信息写入文件
     */
    private void writeDataToFile(File file) {
        // new一个byte数组用来存一些字节数据，大小为缓冲区大小
        byte[] audiodata = new byte[bufferSizeInBytes];
        int readsize;
        RandomAccessFile wavfile = null;
        try {
            wavfile = new RandomAccessFile(file, "rw");
            wavfile.seek(mOffset);
        } catch (Exception e) {
            e.printStackTrace();
        }
        while (mStatus == Status.STATUS_START) {
            readsize = mAudioRecord.read(audiodata, 0, bufferSizeInBytes);
            if (AudioRecord.ERROR_INVALID_OPERATION != readsize && wavfile != null) {
                try {
                    //如果有pcm效果处理，可以放到这里，不能阻塞
                    if (mListener != null) {
                        mListener.onRecording(audiodata, 0, audiodata.length);
                    }
                    wavfile.write(audiodata);
                } catch (IOException e) {
                    Log.e("AudioRecorder", e.getMessage());
                }
            }
            mOffset += readsize;
        }
        if (mListener != null) {
            mListener.finishRecord();
        }
        try {
            //得到写入的pcm的长度,更新文件头
            int pcmSize = (int)wavfile.length() - 44;
            writeWavHeader(mFile, pcmSize);
        } catch (IOException e) {
            Log.e("AudioRecorder", e.getMessage());
        }
    }

    /**
     * 录音对象的状态
     */
     public enum Status {
        //初始状态
        STATUS_INIT,
        //录音
        STATUS_START,
        //暂停
        STATUS_PAUSE,
        //停止
        STATUS_STOP
    }

    /**
     * 获取录音对象的状态
     *
     * @return
     */
    public Status getStatus() {
        return mStatus;
    }

    public void setListener(RecordStreamListener listener) {
        this.mListener = listener;
    }
}