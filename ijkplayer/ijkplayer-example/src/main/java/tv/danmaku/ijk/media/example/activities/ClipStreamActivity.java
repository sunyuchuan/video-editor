package tv.danmaku.ijk.media.example.activities;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.xmly.media.FFmpegMediaMetadataRetriever;

import iknow.android.utils.callback.SingleCallback;
import iknow.android.utils.thread.BackgroundExecutor;
import iknow.android.utils.thread.UiThreadExecutor;


import java.io.File;
import java.util.ArrayList;

import tv.danmaku.ijk.media.example.R;
import tv.danmaku.ijk.media.example.widget.horizontallistview.HorizontalListView;
import tv.danmaku.ijk.media.example.widget.media.IjkVideoView;
import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;


public class ClipStreamActivity extends AppCompatActivity {
    private IjkVideoView mVideoView;
    private HorizontalListView mGrabList;
    private ImageView mPreview;
    private ImageView mLeft;
    private ImageView mRight;
    private Button mClipButton;
    private EditText mStartTime;
    private EditText mEndTime;
    private FFmpegMediaMetadataRetriever mRetriever;
    private IjkMediaPlayer mMediaPlayer;
    private VideoThumbAdapter mVideoThumbAdapter;
    private TextView mProgressView;
    private static final long one_frame_time = 36 * 1000 * 1000;
    private static final int min_thumbs = 7;

    private boolean mHaveVideo = true;
    private boolean mHaveAudio = true;

    //private final static String mInStreamPath = "/storage/extSdCard/test.mp4";
    //private final static String mInStreamPath = "/sdcard/noframe.mp4";
    //private final static String mInStreamPath = "/sdcard/jsyan.flv";
    //private final static String mInStreamPath = "/sdcard/output.mp4";
    //private final static String mInStreamPath = "/storage/extSdCard/DCIM/Camera/20180507_113251.mp4";
    //private final static String mInStreamPath = "/sdcard/VID_20180403_153906.mp4";
    private String mInStreamPath;
    private final static String mOutStreamPath = "/sdcard/test_out_";

    private final static String TAG = "clip";

    public static Intent newIntent(Context context, String videoPath) {
        Intent intent = new Intent(context, ClipStreamActivity.class);
        intent.putExtra("videoPath", videoPath);
        return intent;
    }

    public static void intentTo(Context context, String videoPath) {
        context.startActivity(newIntent(context, videoPath));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_clip_stream);

        mInStreamPath = getIntent().getStringExtra("videoPath");
        initCtrl();
    }

    private void initCtrl() {
        //for play video
        mVideoView = (IjkVideoView)findViewById(R.id.clip_player);

        mGrabList = (HorizontalListView)findViewById(R.id.clip_grab_list);
        mVideoThumbAdapter = new VideoThumbAdapter(this);
        mGrabList.setAdapter(mVideoThumbAdapter);
        mGrabList.setOnScrollStateChangedListener(onScrollStateChangedListener);

        mProgressView = (TextView)findViewById(R.id.clip_progress);
        backgroundShootVideoThumb(this, Uri.fromFile(new File(mInStreamPath)), new SingleCallback<ArrayList<Bitmap>, Integer>() {
            @Override
            public void onSingleCallback(final ArrayList<Bitmap> bitmap, final Integer interval) {
                UiThreadExecutor.runTask("", new Runnable() {
                    @Override
                    public void run() {
                        mVideoThumbAdapter.addAll(bitmap);
                        mVideoThumbAdapter.notifyDataSetChanged();
                    }
                }, 0L);

            }
        });

        mRetriever = new FFmpegMediaMetadataRetriever();
        try {
            mRetriever.setDataSource(mInStreamPath);
            FFmpegMediaMetadataRetriever.Metadata data = mRetriever.getMetadata();
            String acodec = "";
            String vcodec = "";
            try {
                acodec = data.getString(FFmpegMediaMetadataRetriever.METADATA_KEY_AUDIO_CODEC);
            } catch (IllegalStateException e) {
                e.printStackTrace();
                mHaveAudio = false;
                Log.d(TAG, "no audio");
            }
            try {
                vcodec = data.getString(FFmpegMediaMetadataRetriever.METADATA_KEY_VIDEO_CODEC);
            } catch (IllegalStateException e) {
                e.printStackTrace();
                mHaveVideo = false;
                Log.d(TAG, "no video");
            }

            mPreview = (ImageView)findViewById(R.id.clip_preview_img);
            //get preview image grab from 500s
            mPreview.setImageBitmap(mRetriever.getScaledFrameAtTime(1000 * 1000 * 500, 1080, 500));

            //for debug image diff
            mLeft = (ImageView)findViewById(R.id.left_img);
            mLeft.setImageBitmap(mRetriever.getScaledFrameAtTime(1000 * 1000 * 600, 1080, 500));
            mRight = (ImageView)findViewById(R.id.right_img);
            mRight.setImageBitmap(mRetriever.getScaledFrameAtTime(1000 * 1000 * 600, 1080, 500));
        } catch (Exception e) {
            e.printStackTrace();
        }

        mStartTime = (EditText) findViewById(R.id.clip_start_time);
        mEndTime = (EditText) findViewById(R.id.clip_end_time);

        mClipButton = (Button)findViewById(R.id.clip_button);
        mClipButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String startTimeStr = mStartTime.getText().toString();
                String endTimeStr = mEndTime.getText().toString();

                if (startTimeStr.equals("") || endTimeStr.equals("")) {
                    Toast.makeText(ClipStreamActivity.this, "please input start and end", Toast.LENGTH_LONG).show();
                    return;
                }
                long startTime = Long.parseLong(startTimeStr);
                long endTime = Long.parseLong(endTimeStr);
                if (endTime <= startTime) {
                    Toast.makeText(ClipStreamActivity.this, "end time must bigger than start time", Toast.LENGTH_LONG).show();
                    return;
                }

                String outputPath = mOutStreamPath + startTimeStr + "-" + endTimeStr + ".mp4";
                mMediaPlayer = new IjkMediaPlayer();
                mMediaPlayer.setOnInfoListener(mInfoListener);
                mMediaPlayer.setOnErrorListener(mErrorListener);
                long duration = mMediaPlayer.getDuration();
                if (startTime < duration) {
                    Toast.makeText(ClipStreamActivity.this, "start time smaller than stream duration", Toast.LENGTH_LONG).show();
                    return;
                }
                if (mHaveAudio)
                    mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "save-audio", "1");
                if (mHaveVideo)
                    mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "save-video", "1");
                mMediaPlayer.clipStream(mInStreamPath, outputPath, startTime, endTime);
            }
        });
    }

    private IMediaPlayer.OnInfoListener mInfoListener =
            new IMediaPlayer.OnInfoListener() {
                public boolean onInfo(IMediaPlayer mp, int arg1, int arg2) {
                    switch (arg1) {
                        case IMediaPlayer.MEDIA_INFO_CLIP_SUCCESS:
                            Toast.makeText(ClipStreamActivity.this, "clip success !!!!", Toast.LENGTH_LONG).show();
                            mProgressView.setVisibility(View.GONE);
                            mClipButton.setText("cost "  + arg2 + " ms");
                            mMediaPlayer.setOnInfoListener(null);
                            if (mMediaPlayer != null)
                                mMediaPlayer.release();
                            break;
                        case IMediaPlayer.MEDIA_INFO_CLIP_PROGRESS:
                            mProgressView.setVisibility(View.VISIBLE);
                            mProgressView.setTextSize(60);
                            mProgressView.setText("进度 " + arg2 + "%");
                            break;
                        default:
                            break;
                    }
                    return true;
                }
            };

    private IMediaPlayer.OnErrorListener mErrorListener =
            new IMediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(IMediaPlayer mp, int what, int extra) {
                    if (extra == IMediaPlayer.MEDIA_ERROR_NO_SPACE) {
                        Toast.makeText(ClipStreamActivity.this, "clip failed !!!! " + "no space...", Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(ClipStreamActivity.this, "clip failed !!!! " + "unknow", Toast.LENGTH_LONG).show();
                    }
                    if (mMediaPlayer != null) {
                        Log.d(TAG, "clip failed release player also");
                        mMediaPlayer.release();
                    }
                    return false;
                }
            };

    private class VideoThumbAdapter extends ArrayAdapter<Bitmap> {

        VideoThumbAdapter(Context context) {
            super(context, 0);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            VideoThumbHolder videoThumbHolder;
            if (convertView == null) {
                videoThumbHolder = new VideoThumbHolder();
                convertView = LayoutInflater.from(parent.getContext()).inflate(R.layout.video_thumb_item_layout, null);
                videoThumbHolder.thumb = (ImageView) convertView.findViewById(R.id.thumb);
                convertView.setTag(videoThumbHolder);
            } else {
                videoThumbHolder = (VideoThumbHolder) convertView.getTag();
            }
            videoThumbHolder.thumb.setImageBitmap(getItem(position));
            return convertView;
        }
    }

    private static class VideoThumbHolder {
        public ImageView thumb;
    }

    //for video seek
    private HorizontalListView.OnScrollStateChangedListener onScrollStateChangedListener = new HorizontalListView.OnScrollStateChangedListener() {
        @Override
        public void onScrollStateChanged(ScrollState scrollState) {
            switch (scrollState) {

            }
        }
    };

    private void systemRetriever(final Context context, final Uri videoUri, final SingleCallback<ArrayList<Bitmap>, Integer> callback) {
        final ArrayList<Bitmap> thumbnailList = new ArrayList<>();
        try {
            MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();
            mediaMetadataRetriever.setDataSource(context, videoUri);
            // Retrieve media data use microsecond
            long videoLengthInMs = Long.parseLong(mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)) * 1000;
            //long numThumbs = videoLengthInMs < one_frame_time ? 7 : (videoLengthInMs / one_frame_time);
            long numThumbs = ((videoLengthInMs / one_frame_time) < min_thumbs) ? min_thumbs : (videoLengthInMs / one_frame_time);
            final long interval = videoLengthInMs / numThumbs;

            //每次截取到3帧之后上报
            Log.d(TAG, "system videoLengthInMs " + videoLengthInMs + " numThumbs " + numThumbs + " interval " + interval);
            for (long i = 0; i < numThumbs; i++) {
                Bitmap bitmap = mediaMetadataRetriever.getFrameAtTime(i * interval, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                try {
                    bitmap = Bitmap.createScaledBitmap(bitmap, 200, 200, false);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                thumbnailList.add(bitmap);
                if (thumbnailList.size() == 3) {
                    callback.onSingleCallback((ArrayList<Bitmap>) thumbnailList.clone(), (int) interval);
                    thumbnailList.clear();
                }
            }
            if (thumbnailList.size() > 0) {
                callback.onSingleCallback((ArrayList<Bitmap>) thumbnailList.clone(), (int) interval);
                thumbnailList.clear();
            }
            mediaMetadataRetriever.release();
        } catch (final Throwable e) {
            Thread.getDefaultUncaughtExceptionHandler().uncaughtException(Thread.currentThread(), e);
        }
    }

    private void FFmpegRetriever(final Context context, final Uri videoUri, final SingleCallback<ArrayList<Bitmap>, Integer> callback) {
        final ArrayList<Bitmap> thumbnailList = new ArrayList<>();
        try {
            FFmpegMediaMetadataRetriever mediaMetadataRetriever = new FFmpegMediaMetadataRetriever();
            mediaMetadataRetriever.setDataSource(context, videoUri);
            // Retrieve media data use microsecond
            FFmpegMediaMetadataRetriever.Metadata data = mediaMetadataRetriever.getMetadata();
            long videoLengthInMs = data.getLong(FFmpegMediaMetadataRetriever.METADATA_KEY_DURATION) * 1000;

            //long videoLengthInMs = Long.parseLong(mediaMetadataRetriever.extractMetadata(FFmpegMediaMetadataRetriever.METADATA_KEY_DURATION)) * 1000;
            long numThumbs = ((videoLengthInMs / one_frame_time) < min_thumbs) ? min_thumbs : (videoLengthInMs / one_frame_time);
            final long interval = videoLengthInMs / numThumbs;

            Log.d(TAG, "videoLengthInMs " + videoLengthInMs + " numThumbs " + numThumbs + " interval " + interval);
            //每次截取到3帧之后上报
            for (long i = 0; i < numThumbs; i++) {
                /*Bitmap bitmap = mediaMetadataRetriever.getFrameAtTime(i * interval, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                try {
                    bitmap = Bitmap.createScaledBitmap(bitmap, 200, 200, false);
                } catch (Exception e) {
                    e.printStackTrace();
                }*/
                Bitmap bitmap = mediaMetadataRetriever.getScaledFrameAtTime(i * interval, 200, 200);
                if (bitmap == null) {
                    Log.d(TAG, "bitmap is null " + i + " time " + i * interval);
                }
                //Bitmap bitmap = mediaMetadataRetriever.getFrameAtTime(i * interval);
                thumbnailList.add(bitmap);
                if (thumbnailList.size() == 3) {
                    callback.onSingleCallback((ArrayList<Bitmap>) thumbnailList.clone(), (int) interval);
                    thumbnailList.clear();
                }
            }
            if (thumbnailList.size() > 0) {
                callback.onSingleCallback((ArrayList<Bitmap>) thumbnailList.clone(), (int) interval);
                thumbnailList.clear();
            }
            mediaMetadataRetriever.release();
        } catch (final Throwable e) {
            e.printStackTrace();
            //Thread.getDefaultUncaughtExceptionHandler().uncaughtException(Thread.currentThread(), e);
        }
    }

    private void backgroundShootVideoThumb(final Context context, final Uri videoUri, final SingleCallback<ArrayList<Bitmap>, Integer> callback) {
        BackgroundExecutor.execute(new BackgroundExecutor.Task("", 0L, "") {
            @Override
            public void execute() {
                boolean useSystemReriever = false;
                if (useSystemReriever) {
                    systemRetriever(context, videoUri, callback);
                } else {
                    FFmpegRetriever(context, videoUri, callback);
                }
            }
        });
    }
}
