package tv.danmaku.ijk.media.example.activities;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.Intent;
import android.hardware.Camera;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;

import com.xmly.media.camera.preview.CameraPreview;
import com.xmly.media.gles.utils.XMFilterType;
import com.xmly.media.video.view.Subtitle;
import com.xmly.media.video.view.XMCameraView;
import com.xmly.media.video.view.XMImageView;
import com.xmly.media.video.view.XMPIPView;
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
    private XMCameraView mXMCameraView;
    private XMPIPView mXMPIPView;
    private XMPlayerView mXMPlayerView;
    private SurfaceView mSurfaceView;
    private CameraPreview mCameraPreview;
    private Button mCameraButton;
    private boolean mOpened;
    private Button mSwitchButton;
    private boolean has_btn_paster;
    private Button mMixButton;
    private boolean mMixing;
    private Button mPlayerButton;
    private boolean mPlaying;
    private Button mImageButton;
    private boolean mImagePreviewing;

    private String mMtcnnModelPath = "/sdcard/mtcnn/";
    private String mVideoPath = "/sdcard/y_bg.mp4";
    private String mOutputPath = "/sdcard/player_recorder_out.mp4";
    private int mOutputWidth = 1280;
    private int mOutputHeight = 720;
    private static final int PREVIEW_W = 960;
    private static final int PREVIEW_H = 540;
    private static final int FPS = 15;
    private ArrayList<Subtitle> mSubList = new ArrayList<Subtitle>();
    private long mRefreshTime = 0l;
    private volatile boolean abort = false;

    private LinearLayout mPasterLayout;
    private RecyclerView mPasterListView;
    private FilterAdapter mFilterAdapter;

    private final XMFilterType[] pasterTypes = new XMFilterType[] {
            XMFilterType.NONE,
            XMFilterType.FILTER_BEAUTY,
            XMFilterType.FILTER_FACE_STICKER,
            XMFilterType.FILTER_SKETCH,
            XMFilterType.FILTER_SEPIA,
            XMFilterType.FILTER_INVERT,
            XMFilterType.FILTER_VIGNETTE,
            XMFilterType.FILTER_LAPLACIAN,
            XMFilterType.FILTER_GLASS_SPHERE,
            XMFilterType.FILTER_CRAYON,
            XMFilterType.FILTER_MIRROR,
            XMFilterType.FILTER_BEAUTY_OPTIMIZATION,
            XMFilterType.FILTER_FISSION,
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

        mCameraButton = (Button) findViewById(R.id.btn_camera);
        mCameraButton.setOnClickListener(this);
        mSwitchButton = (Button) findViewById(R.id.btn_switch);
        mSwitchButton.setOnClickListener(this);
        findViewById(R.id.btn_choose_filter).setOnClickListener(this);
        mMixButton = (Button) findViewById(R.id.btn_decode);
        mMixButton.setOnClickListener(this);
        mPlayerButton = (Button) findViewById(R.id.btn_player);
        mPlayerButton.setOnClickListener(this);
        mImageButton = (Button) findViewById(R.id.btn_image);
        mImageButton.setOnClickListener(this);

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
        if (mXMPIPView == null) {
            hideView((GLSurfaceView) findViewById(R.id.pip_view));
            mXMPIPView = new XMPIPView(getApplicationContext());
            mXMPIPView.setGLSurfaceView((GLSurfaceView) findViewById(R.id.pip_view));
        }
        if (mSurfaceView == null) {
            mSurfaceView = (SurfaceView) findViewById(R.id.camera_view);
            hideView(mSurfaceView);
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            mCameraPreview = new CameraPreview.Builder()
                    .setCameraId(Camera.CameraInfo.CAMERA_FACING_FRONT)
                    .setContext(getApplicationContext())
                    .setExpectedFps(FPS)
                    .setExpectedResolution(PREVIEW_W, PREVIEW_H)
                    .setSurfaceView(mSurfaceView)
                    .setWindowRotation(rotation)
                    .build();
        }
        if (mXMPlayerView == null) {
            hideView((GLSurfaceView) findViewById(R.id.player_view));
            mXMPlayerView = new XMPlayerView(getApplicationContext());
            mXMPlayerView.setGLSurfaceView((GLSurfaceView) findViewById(R.id.player_view));
        }
        if (mXMImageView == null) {
            hideView((GLSurfaceView) findViewById(R.id.image_view));
            mXMImageView = new XMImageView(getApplicationContext());
            mXMImageView.setGLSurfaceView((GLSurfaceView) findViewById(R.id.image_view));
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onStop() {
        mXMPlayerView.release();
        mXMPlayerView = null;
        mXMPIPView.release();
        mXMPIPView = null;
        mCameraPreview.release();
        mCameraPreview = null;
        mSurfaceView = null;
        mXMImageView.release();
        mXMImageView = null;
        super.onStop();
    }

    @Override
    public void onClick(final View v) {
        switch (v.getId()) {
            case R.id.btn_camera:
                if (mOpened) {
                    hideView(mSurfaceView);
                    mCameraPreview.stopRecord();
                    mCameraPreview.closeCamera();
                    mOpened = false;
                    mCameraButton.setBackgroundResource(R.color.gray);
                } else {
                    showView(mSurfaceView);
                    mCameraPreview.setFilter(XMFilterType.FILTER_BEAUTY);
                    mCameraPreview.openCamera();
                    mCameraPreview.startRecord(mOutputPath);
                    mOpened = true;
                    mCameraButton.setBackgroundResource(R.color.green);
                }
                break;
            case R.id.btn_switch:
                mCameraPreview.switchCamera();
                break;
            case R.id.btn_choose_filter:
                if(!has_btn_paster) {
                    has_btn_paster = true;
                    showFilters(mPasterLayout);
                } else {
                    has_btn_paster = false;
                    hideFilters(mPasterLayout);
                }
                break;
            case R.id.btn_decode:
                if (mMixing) {
                    hideView((GLSurfaceView) findViewById(R.id.pip_view));
                    mXMPIPView.stop();
                    mMixing = false;
                    mMixButton.setBackgroundResource(R.color.gray);
                } else {
                    showView((GLSurfaceView) findViewById(R.id.pip_view));
                    mXMPIPView.setPipRectCoordinate(new XMPlayerView.Rect(0.1f, 0.1f, 0.45f, 0.45f));
                    mXMPIPView.start(mVideoPath, mVideoPath, mOutputPath);
                    mMixing = true;
                    mMixButton.setBackgroundResource(R.color.green);
                }
                break;
            case R.id.btn_player:
                if (mPlaying) {
                    hideView((GLSurfaceView) findViewById(R.id.player_view));
                    mXMPlayerView.stopCameraPreview();
                    mSubList.clear();
                    mXMPlayerView.stop();
                    mPlaying = false;
                    mPlayerButton.setBackgroundResource(R.color.gray);
                } else {
                    showView((GLSurfaceView) findViewById(R.id.player_view));
                    int rotation = getWindowManager().getDefaultDisplay().getRotation();
                    mXMPlayerView.setWindowRotation(rotation);
                    mXMPlayerView.setExpectedFps(FPS);
                    mXMPlayerView.setExpectedResolution(PREVIEW_W, PREVIEW_H);
                    mXMPlayerView.setPipRectCoordinate(new XMPlayerView.Rect(0.1f, 0.37f, 0.4f, 0.9f));
                    mXMPlayerView.setFilter(XMFilterType.FILTER_BEAUTY);
                    mXMPlayerView.startCameraPreview();
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
                    mXMPlayerView.start(mVideoPath, mOutputPath, new Subtitle.TextCanvasParam(), mSubList);
                    mPlaying = true;
                    mPlayerButton.setBackgroundResource(R.color.green);
                }
                break;
            case R.id.btn_image:
                if (mImagePreviewing) {
                    hideView((GLSurfaceView) findViewById(R.id.image_view));
                    mXMImageView.stopRecorder();
                    mXMImageView.stopPreview();
                    mImagePreviewing = false;
                    mImageButton.setBackgroundResource(R.color.gray);
                    abort = true;
                } else {
                    showView((GLSurfaceView) findViewById(R.id.image_view));
                    mXMImageView.setLogo("/sdcard/watermark.png", new XMImageView.Rect(0.55f, 0.55f, 0.95f, 0.95f));
                    mXMImageView.startPreview("/sdcard/out1.png", mOutputWidth, mOutputHeight, FPS);
                    mXMImageView.startRecorder("/sdcard/audio_album.mp4", mOutputWidth, mOutputHeight);
                    mImagePreviewing = true;
                    abort = false;
                    new RefreshThread().start();
                    mImageButton.setBackgroundResource(R.color.green);
                }
                break;
            default:
                break;
        }
    }

    private void showView(View view) {
        view.clearAnimation();
        view.setVisibility(View.VISIBLE);
    }

    private void hideView(View view) {
        view.setVisibility(View.GONE);
        view.clearAnimation();
    }

    private FilterAdapter.onFilterChangeListener onFilterChangeListener = new FilterAdapter.onFilterChangeListener() {
        @Override
        public void onFilterChanged(XMFilterType filterType, boolean show, boolean switch_filter) {
            Log.i(TAG, "onFilterChanged setFilter filterType "+filterType.getValue());
            if (show) {
                mCameraPreview.setFilter(filterType);
                mXMPIPView.setFilter(filterType);
                mXMImageView.setFilter(filterType);
            } else if(!switch_filter) {
                mCameraPreview.setFilter(XMFilterType.NONE);
                mXMPIPView.setFilter(XMFilterType.NONE);
                mXMImageView.setFilter(XMFilterType.NONE);
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
                    Thread.sleep((int) (1000 / FPS), 0);
                    mRefreshTime ++;
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                if (mRefreshTime % (FPS*DURATION) == 0) {
                    if (mXMImageView != null) {
                        mXMImageView.setImage("/sdcard/out" + (1 + mRefreshTime / (FPS * DURATION)) + ".png");
                    }
                    if (mRefreshTime / (FPS * DURATION) >= IMAGE_NUM) {
                        mRefreshTime = 0l;
                    }
                }
            }
            Log.i(TAG, "RefreshThread exit");
        }
    }
}
