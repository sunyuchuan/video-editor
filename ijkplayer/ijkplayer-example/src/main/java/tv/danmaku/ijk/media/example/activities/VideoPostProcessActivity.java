package tv.danmaku.ijk.media.example.activities;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;

import com.xmly.media.camera.view.utils.XMFilterType;
import com.xmly.media.video.view.Subtitle;
import com.xmly.media.video.view.XMDecoderView;
import com.xmly.media.video.view.XMImageView;
import com.xmly.media.video.view.XMPlayerView;
import java.util.ArrayList;
import tv.danmaku.ijk.media.example.R;
import tv.danmaku.ijk.media.example.widget.filteradapter.FilterAdapter;

/**
 * Created by sunyc on 19-3-4.
 */

public class VideoPostProcessActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String TAG = "VideoPostProcess";
    private static final int DURATION = 4;
    private static final int IMAGE_NUM = 5;
    private XMImageView mXMImageView;
    private XMDecoderView mXMDecoderView;
    private Button mCameraButton;
    private boolean mOpened;
    private boolean has_btn_paster;
    private Button mPlayerButton;
    private boolean mPlaying;
    private Button mDecodeButton;
    private boolean mDecoding;

    private String mVideoPath = "/sdcard/y_bg.mp4";
    private String mOutputPath = "/sdcard/player_recorder_out.mp4";
    private int mOutputWidth = 1279;
    private int mOutputHeight = 719;
    private static final int PREVIEW_W = 960;
    private static final int PREVIEW_H = 540;
    private static final int FPS = 15;
    private ArrayList<Subtitle> mSubList = new ArrayList<Subtitle>();
    private long mRefreshTime = 0l;
    private volatile boolean abort;

    private LinearLayout mPasterLayout;
    private RecyclerView mPasterListView;
    private FilterAdapter mFilterAdapter;

    private final XMFilterType[] pasterTypes = new XMFilterType[] {
            XMFilterType.NONE,
            XMFilterType.FILTER_BEAUTY,
            XMFilterType.FILTER_SKETCH,
            XMFilterType.FILTER_SEPIA,
            XMFilterType.FILTER_INVERT,
            XMFilterType.FILTER_VIGNETTE,
            XMFilterType.FILTER_LAPLACIAN,
            XMFilterType.FILTER_GLASS_SPHERE,
            XMFilterType.FILTER_CRAYON,
            XMFilterType.FILTER_MIRROR,
            XMFilterType.FILTER_BEAUTY_OPTIMIZATION,
            XMFilterType.FILTER_IMAGE_SWITCH,
            XMFilterType.FILTER_BLINDS,
            XMFilterType.FILTER_FADE_IN_OUT
    };

    public static Intent newIntent(Context context) {
        Intent intent = new Intent(context, VideoPostProcessActivity.class);
        return intent;
    }

    public static void intentTo(Context context) {
        context.startActivity(newIntent(context));
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_video_post_process);

        mCameraButton = (Button) findViewById(R.id.camera);
        mCameraButton.setOnClickListener(this);
        findViewById(R.id.button_choose_filter).setOnClickListener(this);
        mPlayerButton = (Button) findViewById(R.id.button_play);
        mPlayerButton.setOnClickListener(this);
        mDecodeButton = (Button) findViewById(R.id.button_decode);
        mDecodeButton.setOnClickListener(this);

        //paster
        mPasterLayout = (LinearLayout)findViewById(R.id.filter_list);
        mPasterListView = (RecyclerView) findViewById(R.id.base_filter_listView);
        LinearLayoutManager pasterLinearLayoutManager = new LinearLayoutManager(this);
        pasterLinearLayoutManager.setOrientation(LinearLayoutManager.HORIZONTAL);
        mPasterListView.setLayoutManager(pasterLinearLayoutManager);
        mFilterAdapter = new FilterAdapter(this, pasterTypes);
        mPasterListView.setAdapter(mFilterAdapter);
        mFilterAdapter.setOnFilterChangeListener(onFilterChangeListener);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mXMImageView == null) {
            mXMImageView = (XMImageView) findViewById(R.id.image_view);
            mXMImageView.setSurfaceView();
        }
        if (mXMDecoderView == null) {
            mXMDecoderView = (XMDecoderView) findViewById(R.id.decoder_view);
            mXMDecoderView.setSurfaceView();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onStop() {
        mXMImageView.release();
        mXMImageView = null;
        mXMDecoderView.release();
        mXMDecoderView = null;
        super.onStop();
    }

    @Override
    public void onClick(final View v) {
        switch (v.getId()) {
            case R.id.camera:
                if (mOpened) {
                    mXMImageView.stopPreview();
                    mOpened = false;
                    mCameraButton.setBackgroundResource(R.color.gray);
                    abort = true;
                } else {
                    //mXMPlayerView.setFilter(XMFilterType.FILTER_BEAUTY);
                    mXMImageView.setLogo("/sdcard/watermark.png", new XMImageView.Rect(0.55f, 0.55f, 0.95f, 0.95f));
                    mXMImageView.startPreview("/sdcard/out1.png", mOutputWidth, mOutputHeight, FPS);
                    mOpened = true;
                    abort = false;
                    new RefreshThread().start();
                    mCameraButton.setBackgroundResource(R.color.green);
                }
                break;
            case R.id.button_choose_filter:
                if(!has_btn_paster) {
                    has_btn_paster = true;
                    showFilters(mPasterLayout);
                } else {
                    has_btn_paster = false;
                    hideFilters(mPasterLayout);
                }
                break;
            case R.id.button_decode:
                if (mDecoding) {
                    mXMDecoderView.stop();
                    mDecoding = false;
                    mDecodeButton.setBackgroundResource(R.color.gray);
                } else {
                    mXMDecoderView.start(mVideoPath, mOutputPath);
                    mDecoding = true;
                    mDecodeButton.setBackgroundResource(R.color.green);
                }
                break;
            case R.id.button_play:
                if (mPlaying) {
                    mSubList.clear();
                    mXMImageView.stopRecorder();
                    mPlaying = false;
                    mPlayerButton.setBackgroundResource(R.color.gray);
                } else {
                    int start = 0;
                    int end = 0;
                    String str = "测试字幕 syc 测试字幕";
                    String path = null;
                    for (int i = 0; i < 10; i++) {
                        start += 1000;
                        end = start + 1000;
                        str = str + i;
                        if (i%3 == 0) {
                            path = "/sdcard/dog.png";
                        } else if (i%3 == 1) {
                            path = "/sdcard/sub/header.jpg";
                        } else {
                            path = null;
                        }

                        mSubList.add(new Subtitle(start, end, str, path));
                    }
                    mXMImageView.startRecorder("/sdcard/audio_album.mp4", mOutputWidth, mOutputHeight);
                    mPlaying = true;
                    mPlayerButton.setBackgroundResource(R.color.green);
                }
                break;
            default:
                break;
        }
    }

    private FilterAdapter.onFilterChangeListener onFilterChangeListener = new FilterAdapter.onFilterChangeListener() {
        @Override
        public void onFilterChanged(XMFilterType filterType, boolean show, boolean switch_filter) {
            Log.i(TAG, "onFilterChanged setFilter filterType "+filterType.getValue());
            if (show) {
                mXMImageView.setFilter(filterType);
                mXMDecoderView.setFilter(filterType);
            } else if(!switch_filter) {
                mXMImageView.setFilter(XMFilterType.NONE);
                mXMDecoderView.setFilter(XMFilterType.NONE);
            }
        }
    };

    private void showFilters(final LinearLayout layout) {
        ObjectAnimator animator = ObjectAnimator.ofFloat(layout, "translationY", layout.getHeight(), 0);
        animator.setDuration(200);
        animator.addListener(new Animator.AnimatorListener() {

            @Override
            public void onAnimationStart(Animator animation) {
                layout.setVisibility(View.VISIBLE);
            }

            @Override
            public void onAnimationRepeat(Animator animation) {

            }

            @Override
            public void onAnimationEnd(Animator animation) {

            }

            @Override
            public void onAnimationCancel(Animator animation) {

            }
        });
        animator.start();
    }

    private void hideFilters(final LinearLayout layout) {
        ObjectAnimator animator = ObjectAnimator.ofFloat(layout, "translationY", 0 ,  layout.getHeight());
        animator.setDuration(200);
        animator.addListener(new Animator.AnimatorListener() {

            @Override
            public void onAnimationStart(Animator animation) {
                // TODO Auto-generated method stub
            }

            @Override
            public void onAnimationRepeat(Animator animation) {
                // TODO Auto-generated method stub

            }

            @Override
            public void onAnimationEnd(Animator animation) {
                // TODO Auto-generated method stub
                layout.setVisibility(View.INVISIBLE);
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                // TODO Auto-generated method stub
                layout.setVisibility(View.INVISIBLE);
            }
        });
        animator.start();
    }

    class RefreshThread extends Thread {
        @Override
        public void run() {
            while (!abort) {
                try {
                    Thread.sleep((int) (1000 / 15), 0);
                    mRefreshTime ++;
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                if (mRefreshTime % (15*DURATION) == 0) {
                    if (mXMImageView != null) {
                        mXMImageView.setImage("/sdcard/out" + (1 + mRefreshTime / (15 * DURATION)) + ".png");
                    }
                    if (mRefreshTime / (15 * DURATION) >= IMAGE_NUM) {
                        mRefreshTime = 0l;
                    }
                }
            }
            Log.i(TAG, "RefreshThread exit");
        }
    }
}
