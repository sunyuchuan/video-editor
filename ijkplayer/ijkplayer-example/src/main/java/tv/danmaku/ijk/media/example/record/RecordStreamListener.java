package tv.danmaku.ijk.media.example.record;

/**
 * Created by jsyan on 17-11-24.
 */

public interface RecordStreamListener {
    void onRecording(byte[] pcmdata, int offset, int pcmdatasize);
    void finishRecord();
}
