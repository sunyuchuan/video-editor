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

import android.os.StrictMode;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.Proxy;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.NoRouteToHostException;
import java.net.ProtocolException;
import java.util.HashMap;
import java.util.Map;

import tv.danmaku.ijk.media.player.misc.IMediaDataSource;

/** @hide */
public class HttpMediaDataSource implements IMediaDataSource {
    private static final String TAG = "HttpMediaDataSource";
    private static final boolean VERBOSE = false;

    private long mCurrentOffset = -1;
    private URL mURL = null;
    private Map<String, String> mHeaders = null;
    private HttpURLConnection mConnection = null;
    private long mTotalSize = -1;
    private InputStream mInputStream = null;
    private String mPath = "";

    private boolean mAllowCrossDomainRedirect = true;
    private boolean mAllowCrossProtocolRedirect = true;

    // from com.squareup.okhttp.internal.http
    private final static int HTTP_TEMP_REDIRECT = 307;
    private final static int MAX_REDIRECTS = 20;
    private final static int READ_TIMEOUT = 1000;
    private final static int CONNECT_TIMEOUT = 1000;
    private final boolean mIsLiveFlv;

    public HttpMediaDataSource(String path) {
        if (CookieHandler.getDefault() == null) {
            CookieHandler.setDefault(new CookieManager());
        }
        mPath = path;
        if (mPath.contains("http") && mPath.contains("flv")) {
            mIsLiveFlv = true;
        } else {
            mIsLiveFlv = false;
        }
    }

    @Override
    public void open(String uri, String headers) {
        if (VERBOSE) {
            Log.d(TAG, "connect: uri=" + uri + ", headers=" + headers);
        }

        try {
            disconnect();
            mAllowCrossDomainRedirect = true;
            mURL = new URL(uri);
            mHeaders = convertHeaderStringToMap(headers);
        } catch (MalformedURLException e) {
            Log.d(TAG, "MalformedURLException");
        }
    }

    private boolean parseBoolean(String val) {
        try {
            return Long.parseLong(val) != 0;
        } catch (NumberFormatException e) {
            return "true".equalsIgnoreCase(val) ||
                "yes".equalsIgnoreCase(val);
        }
    }

    /* returns true iff header is internal */
    private boolean filterOutInternalHeaders(String key, String val) {
        if ("android-allow-cross-domain-redirect".equalsIgnoreCase(key)) {
            mAllowCrossDomainRedirect = parseBoolean(val);
            // cross-protocol redirects are also controlled by this flag
            mAllowCrossProtocolRedirect = mAllowCrossDomainRedirect;
        } else {
            return false;
        }
        return true;
    }

    private Map<String, String> convertHeaderStringToMap(String headers) {
        HashMap<String, String> map = new HashMap<String, String>();

        String[] pairs = headers.split("\r\n");
        for (String pair : pairs) {
            int colonPos = pair.indexOf(":");
            if (colonPos >= 0) {
                String key = pair.substring(0, colonPos);
                String val = pair.substring(colonPos + 1);

                if (!filterOutInternalHeaders(key, val)) {
                    map.put(key, val);
                }
            }
        }

        return map;
    }

    private void disconnect() {
        teardownConnection();
        mHeaders = null;
        mURL = null;
        mPath = null;
    }

    private void teardownConnection() {
        if (mConnection != null) {
            mInputStream = null;

            mConnection.disconnect();
            mConnection = null;

            mCurrentOffset = -1;
        }
    }

    private static final boolean isLocalHost(URL url) {
        if (url == null) {
            return false;
        }

        String host = url.getHost();

        if (host == null) {
            return false;
        }

        try {
            if (host.equalsIgnoreCase("localhost")) {
                return true;
            }
            //if (InetAddress.parseNumericAddress(host).isLoopbackAddress()) {
            //    return true;
            //}
        } catch (IllegalArgumentException iex) {
        }
        return false;
    }

    private void seekTo(long offset) throws IOException {
        teardownConnection();

        try {
            int response;
            int redirectCount = 0;

            URL url = mURL;

            // do not use any proxy for localhost (127.0.0.1)
            boolean noProxy = isLocalHost(url);

            while (true) {
                if (noProxy) {
                    mConnection = (HttpURLConnection)url.openConnection(Proxy.NO_PROXY);
                } else {
                    mConnection = (HttpURLConnection)url.openConnection();
                }

                //int connectTimeout = SystemProperties.getInt("debug.http.connect.timeout", 2 * 1000);
                mConnection.setConnectTimeout(CONNECT_TIMEOUT);
                //int readTimeout = SystemProperties.getInt("debug.http.read.timeout", 5 * 1000);
                mConnection.setReadTimeout(READ_TIMEOUT);
                //Log.d(TAG, "http connect timeout " + connectTimeout + " read timeout " + readTimeout);

                // handle redirects ourselves if we do not allow cross-domain redirect
                mConnection.setInstanceFollowRedirects(mAllowCrossDomainRedirect);

                if (mHeaders != null) {
                    for (Map.Entry<String, String> entry : mHeaders.entrySet()) {
                        mConnection.setRequestProperty(
                                entry.getKey(), entry.getValue());
                    }
                }

                if (offset > 0) {
                    mConnection.setRequestProperty(
                            "Range", "bytes=" + offset + "-");
                }

                response = mConnection.getResponseCode();
                if (response != HttpURLConnection.HTTP_MULT_CHOICE &&
                        response != HttpURLConnection.HTTP_MOVED_PERM &&
                        response != HttpURLConnection.HTTP_MOVED_TEMP &&
                        response != HttpURLConnection.HTTP_SEE_OTHER &&
                        response != HTTP_TEMP_REDIRECT) {
                    // not a redirect, or redirect handled by HttpURLConnection
                    break;
                }

                if (++redirectCount > MAX_REDIRECTS) {
                    throw new NoRouteToHostException("Too many redirects: " + redirectCount);
                }

                String method = mConnection.getRequestMethod();
                if (response == HTTP_TEMP_REDIRECT &&
                        !method.equals("GET") && !method.equals("HEAD")) {
                    // "If the 307 status code is received in response to a
                    // request other than GET or HEAD, the user agent MUST NOT
                    // automatically redirect the request"
                    throw new NoRouteToHostException("Invalid redirect");
                }
                String location = mConnection.getHeaderField("Location");
                if (location == null) {
                    throw new NoRouteToHostException("Invalid redirect");
                }
                url = new URL(mURL /* TRICKY: don't use url! */, location);
                if (!url.getProtocol().equals("https") &&
                        !url.getProtocol().equals("http")) {
                    throw new NoRouteToHostException("Unsupported protocol redirect");
                }
                boolean sameProtocol = mURL.getProtocol().equals(url.getProtocol());
                if (!mAllowCrossProtocolRedirect && !sameProtocol) {
                    throw new NoRouteToHostException("Cross-protocol redirects are disallowed");
                }
                boolean sameHost = mURL.getHost().equals(url.getHost());
                if (!mAllowCrossDomainRedirect && !sameHost) {
                    throw new NoRouteToHostException("Cross-domain redirects are disallowed");
                }

                if (response != HTTP_TEMP_REDIRECT) {
                    // update effective URL, unless it is a Temporary Redirect
                    mURL = url;
                }
            }

            if (mAllowCrossDomainRedirect) {
                // remember the current, potentially redirected URL if redirects
                // were handled by HttpURLConnection
                mURL = mConnection.getURL();
            }

            if (response == HttpURLConnection.HTTP_PARTIAL) {
                // Partial content, we cannot just use getContentLength
                // because what we want is not just the length of the range
                // returned but the size of the full content if available.

                String contentRange =
                    mConnection.getHeaderField("Content-Range");

                mTotalSize = -1;
                if (contentRange != null) {
                    // format is "bytes xxx-yyy/zzz
                    // where "zzz" is the total number of bytes of the
                    // content or '*' if unknown.

                    int lastSlashPos = contentRange.lastIndexOf('/');
                    if (lastSlashPos >= 0) {
                        String total =
                            contentRange.substring(lastSlashPos + 1);

                        try {
                            mTotalSize = Long.parseLong(total);
                        } catch (NumberFormatException e) {
                        }
                    }
                }
            } else if (response == HttpURLConnection.HTTP_CREATED) {
                mTotalSize = mConnection.getContentLength();
                Log.d(TAG, "http status code is 201 !" + mTotalSize);
            } else if (response != HttpURLConnection.HTTP_OK) {
                Log.d(TAG, "error wtf response is " + response);
                throw new IOException();
            } else {
                mTotalSize = mConnection.getContentLength();
            }

            //disable it by jsyan
            /*if (offset > 0 && response != HttpURLConnection.HTTP_PARTIAL) {
                // Some servers simply ignore "Range" requests and serve
                // data from the start of the content.
                throw new ProtocolException();
            }*/

            mInputStream =
                new BufferedInputStream(mConnection.getInputStream());

            mCurrentOffset = offset;
        } catch (IOException e) {
            mTotalSize = -1;
            mInputStream = null;
            mConnection = null;
            mCurrentOffset = -1;

            if (VERBOSE) {
                e.printStackTrace();
                Log.e(TAG, "connect may be have some problem, let't throw exception " + e);
            }
            throw e;
        }
    }

    /* 
     * pramas 1 the file offset;2 buffer pointer; 3 the buffer offset;4 buffer size
     * read timeout is 1 second, avoid the player is bloked, the activity will be anr.
     * if read timeout return 0, will be read again. return -1, the connect is broken, need reconnect.
     * if position smaller than zero is reconnecting!
     */
    @Override
    public int readAt(long offset, byte[] data, int size) throws IOException {
        StrictMode.ThreadPolicy policy =
            new StrictMode.ThreadPolicy.Builder().permitAll().build();

        StrictMode.setThreadPolicy(policy);

        try {
            if (offset != mCurrentOffset /*|| position  == -1*/) {
                seekTo(offset);
            }

            int n = mInputStream.read(data, 0, size);

            if (n >= 0) {
                if (VERBOSE) {
                    Log.d(TAG, "readAt " + offset + " / " + size + " => " + n);
                }
                /*if reconnect and the stream is flv,after reconnect success, pls drop the flv header.
                if (position == -1 && mIsLiveFlv) {
                    if (VERBOSE) {
                        Log.d(TAG, "reconnect success but it's a live flv need drop header !!!");
                    }
                    return 0;
                }*/
                mCurrentOffset += n;
            } else {
                if (VERBOSE) {
                    Log.e(TAG, "readAt " + n + " connect is broken maybe eof or need reconnect");
                }
            }

            //-1 is eof, 0 is no data, let read again.
            return n;
        } catch (ProtocolException e) {
            Log.w(TAG, "readAt " + offset + " / " + size + " => " + e);
            throw e;
        } catch (NoRouteToHostException e) {
            Log.w(TAG, "readAt " + offset + " / " + size + " => " + e);
            throw e;
        } catch (SocketTimeoutException e) {
            Log.d(TAG, "avoid block read time out is 1 second let't read again !!!");
            return 0;
        } catch (IOException e) {
            Log.w(TAG, "unknow exception ! throw " + e);
            throw e;
        }
    }

    @Override
    public long getSize() throws IOException {
        if (mConnection == null) {
            seekTo(0);
        }

        return mTotalSize;
    }

    @Override
    public void close() throws IOException {
        Log.d(TAG, "close disconnect ");
        disconnect();
    }

    @Override
    public String getUri() {
        return mPath;
    }
}
