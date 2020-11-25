package tv.danmaku.ijk.media.example.dub.state;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.View;

import java.lang.ref.WeakReference;

import tv.danmaku.ijk.media.example.dub.XmDub;
import tv.danmaku.ijk.media.player.IMediaPlayer;

/**
 * Create by felix.chen on 2018/1/16.
 *
 * @author felix.chen
 */

public class PreviewDubState extends AbstractDubState {
    private static final int WHAT_GET_POSITION = 0;
    private MyHandler mMyHandler;

    public PreviewDubState(XmDub xmDub) {
        super(xmDub);
        mMyHandler = new MyHandler(this,Looper.getMainLooper());
        mMyHandler.sendEmptyMessage(WHAT_GET_POSITION);
    }

    @Override
    public void pausePreviewDub() {
        mXmDub.setSeekCompleteListener(new IMediaPlayer.OnSeekCompleteListener() {
            @Override
            public void onSeekComplete(IMediaPlayer mp) {
                mXmDub.setSeekCompleteListener(null);
                mBackgroundAudio.pause();
                mVideoView.pause();
                mRecordPlayIjkView.pause();
                mXmDub.setDubState(new DubPauseState(mXmDub));
            }
        });
        mVideoView.seekTo((int)mAudioRecorder.getTotalTimeMills());
    }

    @Override
    public int getState() {
        return PREVIEW_DUB_STATE;
    }

    private class MyHandler extends Handler {
        private WeakReference<PreviewDubState> mWeakReference;

        public MyHandler(PreviewDubState previewDubState, Looper looper) {
            super(looper);
            mWeakReference = new WeakReference<>(previewDubState);
        }

        @Override
        public void handleMessage(Message message) {
            PreviewDubState previewDubState = mWeakReference.get();
            if (previewDubState == null) {
                return;
            }
            switch (message.what) {
                case WHAT_GET_POSITION:
                    if ((previewDubState.mVideoView.getCurrentPosition()
                            >= previewDubState.mAudioRecorder.getTotalTimeMills())
                            && previewDubState.getState() == PREVIEW_DUB_STATE) {
                        previewDubState.pausePreviewDub();
                        previewDubState.mVideoView
                                .seekTo((int)previewDubState.mAudioRecorder.getTotalTimeMills());
                        this.removeCallbacksAndMessages(null);
                    } else {
                        mAudioWaveView.setCurPercentPosition(
                                (100.0f*(float)previewDubState.mVideoView.getCurrentPosition())
                                            /previewDubState.mAudioRecorder.getTotalTimeMills());
                        previewDubState.mMyHandler
                                .sendEmptyMessageDelayed(WHAT_GET_POSITION,200);
                    }
                    break;
                default:
                    break;
            }
        }
    }
}
