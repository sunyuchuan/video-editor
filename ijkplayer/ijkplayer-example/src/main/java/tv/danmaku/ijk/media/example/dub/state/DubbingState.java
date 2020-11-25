package tv.danmaku.ijk.media.example.dub.state;

import android.view.View;

import tv.danmaku.ijk.media.example.dub.XmDub;

/**
 * Create by felix.chen on 2018/1/16.
 *
 * @author felix.chen
 */

public class DubbingState extends AbstractDubState {
    public DubbingState(XmDub xmDub) {
        super(xmDub);
    }


    @Override
    public void pauseDub() {

        mVideoView.pause();
        mBackgroundAudio.pause();
        mRecordPlayIjkView.pause();
        mAudioRecorder.pauseRecord();
        mAudioWaveView.setVisibility(View.VISIBLE);
        mAudioWaveView.setWavePath(null);
        mXmDub.setDubState(new DubPauseState(mXmDub));
    }

    @Override
    public int getState() {
        return DUBBING_STATE;
    }
}
