/*
 * Copyright (C) 2015 Zhang Rui <bbcallen@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package tv.danmaku.ijk.media.example.widget.media;

import java.io.IOException;
import java.net.SocketTimeoutException;

import android.util.Log;

import tv.danmaku.ijk.media.player.misc.IMediaDataSource;

public class RtmpMediaDataSource implements IMediaDataSource {
    private String mPath;
    private static final String TAG = "RtmpMediaDataSource";
    private SyncRtmpClient mClient;
    private String[] mRtmp;
    private boolean mIsPlay;

    public RtmpMediaDataSource(String path) {
        Log.d(TAG, "RtmpMediaDataSource");
        mPath = path;
        mIsPlay = false;
    }

    @Override
    public void open(String path, String header) throws IOException {
       //mClient = new RtmpConnection("livestreaming.itworkscdn.net", 1935, "smc4sportslive", "smc4tv_360p");
       //mClient = new RtmpConnection("live.hkstv.hk.lxdns.com", 1935, "live", "hks");
       //mClient = new RtmpConnection("www.scbtv.cn", 1935, "live", "new");
       //mClient = new RtmpConnection("ftv.sun0769.com", 1935, "dgrtv1", "mp4:b1");
       mRtmp = urlParser(path);
       if (mRtmp == null) {
           return;
       }
    }

    @Override
    public int readAt(long position, byte[] buffer, int size) throws IOException {
        int n = -1;
        /*if (offset == -1) {
            mIsPlay = false;
            Log.d(TAG, "need reconnnect");
        }*/
        if (!mIsPlay) {
            mClient = new SyncRtmpClient(mRtmp[0], Integer.parseInt(mRtmp[1]), mRtmp[2], mRtmp[3]);
            mClient.connect();
            mIsPlay = true;
        }
        if (mClient != null) {
            try {
                n = mClient.readAvData(buffer, 0);
                Log.d(TAG, "read data " + n + " size " + size + " position " + position);
            } catch (SocketTimeoutException e) {
                Log.d(TAG, "read timeout again");
                return 0;
            } catch (IOException e) {
                Log.d(TAG, "IOException Maybe connect broken need retry" + e);
                throw e;
            }
        }
        return n;
    }

    @Override
    public long getSize() throws IOException {
        //only support rtmp live stream,can't getsize
        return -1;
    }

    @Override
    public void close() throws IOException {

    }

    @Override
    public String getUri() {
        return mPath;
    }

    private String[] urlParser(String url) {
        if (url == null) {
            return null;
        }
        final String protocol = "rtmp://";

        //host port app playpath
        String[] ret = new String[4];

        String newurl;
        //find String after rtmp://
        if (url.contains(protocol)) {
           newurl = url.substring(protocol.length());
        } else {
           newurl = url;
        }

        Log.d(TAG, "url drop rtmp is " + newurl);

        String[] split = newurl.split("/");
        if (split[0].contains(":")) {
            ret[0] = split[0].split(":")[0];
            ret[1] = split[0].split(":")[1];
        } else {
            ret[0] = split[0];
            ret[1] = "1935";
        }

        ret[2] = split[1];
        ret[3] = split[2];
        Log.d(TAG, "host " + ret[0] + " port " + ret[1] + " app " + ret[2] + " playpath " + ret[3]);
        return ret;
    }
}
