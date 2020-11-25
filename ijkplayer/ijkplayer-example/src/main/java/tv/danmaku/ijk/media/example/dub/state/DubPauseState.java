package tv.danmaku.ijk.media.example.dub.state;

import android.view.View;

import tv.danmaku.ijk.media.example.dub.XmDub;
import tv.danmaku.ijk.media.player.IMediaPlayer;

/**
 * Create by felix.chen on 2018/1/16.
 *
 * @author felix.chen
 */

public class DubPauseState extends AbstractDubState {
    public DubPauseState(XmDub xmDub) {
        super(xmDub);
    }

    @Override
    public void startDub() {
        if (mVideoView.getDuration()<=mVideoView.getCurrentPosition()){
            return;
        }
        mVideoView.start();
        mRecordPlayIjkView.pause();
        mBackgroundAudio.start();
        mAudioRecorder.startRecord();
        mXmDub.setDubState(new DubbingState(mXmDub));
        mAudioWaveView.setVisibility(View.GONE);
    }


    @Override
    public void startPreviewDub() {
        if (mAudioRecorder.getCurrentPositionMills() + 1000
                >=mAudioRecorder.getTotalTimeMills()){
            mXmDub.setSeekCompleteListener(new IMediaPlayer.OnSeekCompleteListener() {
                @Override
                public void onSeekComplete(IMediaPlayer mp) {
                    mXmDub.setSeekCompleteListener(null);
                    mVideoView.start();
                    mRecordPlayIjkView.start();
                    mBackgroundAudio.start();
                    mXmDub.setDubState(new PreviewDubState(mXmDub));
                }
            });
            mVideoView.seekTo(0);
        } else {
            mVideoView.start();
            mRecordPlayIjkView.start();
            mBackgroundAudio.start();
            mXmDub.setDubState(new PreviewDubState(mXmDub));
        }
        mAudioWaveView.setVisibility(View.VISIBLE);
    }

    @Override
    public int getState() {
        return DUB_PAUSE_STATE;
    }
}
