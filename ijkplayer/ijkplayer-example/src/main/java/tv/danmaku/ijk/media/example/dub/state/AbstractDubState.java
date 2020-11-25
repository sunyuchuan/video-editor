package tv.danmaku.ijk.media.example.dub.state;

import android.util.Log;

import tv.danmaku.ijk.media.example.dub.XmDub;
import tv.danmaku.ijk.media.example.record.AudioRecorder;
import tv.danmaku.ijk.media.example.widget.media.IjkVideoView;
import tv.danmaku.ijk.media.example.widget.waveform.AudioWaveView;

/**
 * Create by felix.chen on 2018/1/16.
 *
 * @author felix.chen
 */

public abstract class AbstractDubState implements IDubState {
    private static final String TAG = "AbstractDubState";

    protected IjkVideoView mVideoView;
    protected IjkVideoView mBackgroundAudio,mRecordPlayIjkView;
    protected AudioWaveView mAudioWaveView;
    protected XmDub mXmDub;
    protected AudioRecorder mAudioRecorder;

    protected AbstractDubState(XmDub xmDub){
        mXmDub = xmDub;
        mVideoView = xmDub.getVideoView();
        mBackgroundAudio = xmDub.getBackgroundAudio();
        mRecordPlayIjkView = xmDub.getRecordPlayIjkView();
        mAudioWaveView = xmDub.getAudioWaveView();
        mAudioRecorder = xmDub.getAudioRecorder();
    }
    @Override
    public void startDub() {
        Log.e(TAG,"当前状态不能调用该方法：startDub()");
    }

    @Override
    public void pauseDub() {
        Log.e(TAG,"当前状态不能调用该方法：pauseDub()");
    }

    @Override
    public void startPreviewDub() {
        Log.e(TAG,"当前状态不能调用该方法：startPreviewDub()");
    }

    @Override
    public void pausePreviewDub() {
        Log.e(TAG,"当前状态不能调用该方法：pausePreviewDub()");
    }
}
