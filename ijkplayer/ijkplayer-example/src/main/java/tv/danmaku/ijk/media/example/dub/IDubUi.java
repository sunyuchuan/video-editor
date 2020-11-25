package tv.danmaku.ijk.media.example.dub;

import tv.danmaku.ijk.media.example.record.AudioRecorder;
import tv.danmaku.ijk.media.example.widget.media.IjkVideoView;
import tv.danmaku.ijk.media.example.widget.waveform.AudioWaveView;

/**
 * Create by felix.chen on 2018/1/16.
 *
 * @author felix.chen
 */

public interface IDubUi {
    IjkVideoView getVideoView();

    IjkVideoView getBackgroundAudio();

    IjkVideoView getRecordPlayIjkView();

    AudioWaveView getAudioWaveView();

    AudioRecorder getAudioRecorder();
}
