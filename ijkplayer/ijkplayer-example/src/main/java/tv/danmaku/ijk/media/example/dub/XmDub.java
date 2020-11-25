package tv.danmaku.ijk.media.example.dub;

import android.os.Handler;
import android.os.Looper;
import tv.danmaku.ijk.media.example.dub.state.IDubState;
import tv.danmaku.ijk.media.example.dub.state.InitDubState;
import tv.danmaku.ijk.media.example.record.AudioRecorder;
import tv.danmaku.ijk.media.example.widget.media.IjkVideoView;
import tv.danmaku.ijk.media.example.widget.waveform.AudioWaveView;
import tv.danmaku.ijk.media.player.IMediaPlayer;

import static tv.danmaku.ijk.media.example.dub.state.IDubState.DUBBING_STATE;
import static tv.danmaku.ijk.media.example.dub.state.IDubState.DUB_PAUSE_STATE;
import static tv.danmaku.ijk.media.example.dub.state.IDubState.INIT_DUB_STATE;
import static tv.danmaku.ijk.media.example.dub.state.IDubState.PREVIEW_DUB_STATE;

/**
 * Create by felix.chen on 2018/1/16.
 *
 * @author felix.chen
 */

public class XmDub{
    private static final String TAG = "XmDub";

    public void setDubStateChangeListener(IDubStateChangeListener dubStateChangeListener) {
        mDubStateChangeListener = dubStateChangeListener;
    }

    public void setSeekCompleteListener(IMediaPlayer.OnSeekCompleteListener seekCompleteListener) {
        mSeekCompleteListener = seekCompleteListener;
    }

    public interface IDubStateChangeListener {
        void onDubStateChange(IDubState dubState);
    }
    private IjkVideoView mVideoView;
    private IjkVideoView mBackgroundAudio, mRecordPlayIjkView;
    private AudioWaveView mAudioWaveView;
    private IDubState mDubState;
    private IDubStateChangeListener mDubStateChangeListener;
    private AudioRecorder mAudioRecorder;

    private IMediaPlayer.OnSeekCompleteListener mSeekCompleteListener;

    public XmDub(IDubUi dubUi) {
        mVideoView = dubUi.getVideoView();
        mBackgroundAudio = dubUi.getBackgroundAudio();
        mRecordPlayIjkView = dubUi.getRecordPlayIjkView();
        mAudioWaveView = dubUi.getAudioWaveView();
        mAudioRecorder = dubUi.getAudioRecorder();
    }

    public void init(){
        mDubState = new InitDubState(this);
        mVideoView.setOnCompletionListener(new IMediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(IMediaPlayer mp) {
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        pauseDub();
                    }
                });
            }
        });
        mVideoView.setSeekCompleteListener(new IMediaPlayer.OnSeekCompleteListener() {
            @Override
            public void onSeekComplete(IMediaPlayer mp) {
                long currentPosition = mp.getCurrentPosition();
                mAudioRecorder.seekTo(currentPosition);
                mRecordPlayIjkView.seekTo((int) currentPosition);
                mBackgroundAudio.seekTo((int) currentPosition);
                mAudioWaveView.setCurPercentPosition(
                        (100.0f*(float)mVideoView.getCurrentPosition())
                                /mAudioRecorder.getTotalTimeMills());
                if (mSeekCompleteListener != null){
                    mSeekCompleteListener.onSeekComplete(mp);
                }
            }
        });
    }

    public IjkVideoView getVideoView() {
        return mVideoView;
    }

    public IjkVideoView getBackgroundAudio() {
        return mBackgroundAudio;
    }

    public IjkVideoView getRecordPlayIjkView() {
        return mRecordPlayIjkView;
    }

    public AudioWaveView getAudioWaveView() {
        return mAudioWaveView;
    }

    public AudioRecorder getAudioRecorder(){
        return mAudioRecorder;
    }

    public void setDubState(IDubState dubState) {
        mDubState = dubState;
        notifyDubStateChange();
    }

    private void notifyDubStateChange() {
        if (mDubStateChangeListener != null) {
            mDubStateChangeListener.onDubStateChange(mDubState);
        }
    }

    public void startDub() {
        mDubState.startDub();
    }

    public void pauseDub() {
        mDubState.pauseDub();
    }

    public void startPreviewDub() {
        mDubState.startPreviewDub();
    }

    public void pausePreviewDub() {
        mDubState.pausePreviewDub();
    }

    public void startOrPausePreview(){
        if (mDubState.getState() == DUB_PAUSE_STATE){
            startPreviewDub();
        } else if (mDubState.getState() == PREVIEW_DUB_STATE){
            pausePreviewDub();
        }
    }

    public void dubOrPause(){
        if (mDubState.getState() == DUBBING_STATE){
            pauseDub();
        } else if (mDubState.getState() == DUB_PAUSE_STATE
                || mDubState.getState() == INIT_DUB_STATE){
            startDub();
        }
    }
}
