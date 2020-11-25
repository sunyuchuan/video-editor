package tv.danmaku.ijk.media.example.dub.state;

import tv.danmaku.ijk.media.example.dub.XmDub;

/**
 * Create by felix.chen on 2018/1/16.
 *
 * @author felix.chen
 */

public class InitDubState extends AbstractDubState{
    public InitDubState(XmDub xmDub) {
        super(xmDub);
    }

    @Override
    public void startDub() {
        mBackgroundAudio.start();
        mRecordPlayIjkView.pause();
        mVideoView.start();
        mAudioRecorder.startRecord();
        mXmDub.setDubState(new DubbingState(mXmDub));
    }

    @Override
    public int getState() {
        return INIT_DUB_STATE;
    }
}
