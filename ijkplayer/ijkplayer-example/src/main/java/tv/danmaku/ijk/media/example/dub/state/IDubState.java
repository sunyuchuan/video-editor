package tv.danmaku.ijk.media.example.dub.state;

/**
 * Create by felix.chen on 2018/1/16.
 *
 * @author felix.chen
 */

public interface IDubState {
    int INIT_DUB_STATE = 0;
    int DUBBING_STATE = 1;
    int DUB_PAUSE_STATE = 2;
    int PREVIEW_DUB_STATE = 3;
    int DUB_COMPLETE_STATE = 4;
    void startDub();
    void pauseDub();
    void startPreviewDub();
    void pausePreviewDub();
    int getState();
}
