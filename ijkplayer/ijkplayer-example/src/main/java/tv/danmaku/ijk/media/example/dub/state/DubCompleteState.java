package tv.danmaku.ijk.media.example.dub.state;

import tv.danmaku.ijk.media.example.dub.XmDub;

/**
 * Create by felix.chen on 2018/1/16.
 *
 * @author felix.chen
 */

public class DubCompleteState extends AbstractDubState {
    public DubCompleteState(XmDub xmDub) {
        super(xmDub);
    }

    @Override
    public int getState() {
        return 0;
    }
}
