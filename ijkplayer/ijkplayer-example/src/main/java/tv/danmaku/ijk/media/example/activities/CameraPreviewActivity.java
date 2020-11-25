package tv.danmaku.ijk.media.example.activities;


import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;

import com.xmly.media.camera.view.CameraView;
import com.xmly.media.gles.utils.XMFilterType;

import java.text.SimpleDateFormat;
import java.util.Date;

import tv.danmaku.ijk.media.example.R;

public class CameraPreviewActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = "CameraPreviewActivity";
    private static final int FPS = 15;
    private static final int PREVIEW_W = 960;
    private static final int PREVIEW_H = 540;

    private String mOutputPath;
    private CameraView mCameraView;
    private ImageView mRecord;
    private ImageView mBeautySwitch;
    private ImageView mCameraSwitch;
    private boolean mBeautyOn;
    private boolean mRecording;

    public static Intent newIntent(Context context) {
        Intent intent = new Intent(context, CameraPreviewActivity.class);
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
        setContentView(R.layout.activity_camera_preview);
        initControl();
    }

    private void initControl() {
        mRecord = (ImageView) findViewById(R.id.camera_recorder);
        mRecord.setOnClickListener(this);

        mBeautySwitch = (ImageView) findViewById(R.id.beauty_switch);
        mBeautySwitch.setOnClickListener(this);

        mCameraSwitch = (ImageView) findViewById(R.id.camera_switch);
        mCameraSwitch.setOnClickListener(this);
        mBeautyOn = true;
    }

    private void configCamera() {
        if (mCameraView == null) {
            mCameraView = (CameraView) findViewById(R.id.camera_view);
        }
        mCameraView.testAPISetSurfaceView();
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        mCameraView.setWindowRotation(rotation);
        mCameraView.setExpectedFps(FPS);
        mCameraView.setExpectedResolution(PREVIEW_W, PREVIEW_H);
        mCameraView.setListener(mOnCameraRecorderListener);
        mCameraView.setFilter(XMFilterType.FILTER_BEAUTY);
        mCameraView.startCameraPreview();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (getRequestedOrientation() != ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
        // avoid background
        configCamera();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onStop() {
        mCameraView.release();
        mCameraView = null;
        super.onStop();
    }

    @Override
    public void onClick(final View v) {
        switch (v.getId()) {
            case R.id.beauty_switch:
                if (mBeautyOn) {
                    mCameraView.setFilter(XMFilterType.NONE);
                    mBeautySwitch.setBackgroundResource(R.drawable.ugc_record_beautiful_girl);
                    mBeautyOn = false;
                } else {
                    mCameraView.setFilter(XMFilterType.FILTER_BEAUTY);
                    mBeautySwitch.setBackgroundResource(R.drawable.ugc_record_beautiful_girl_hover);
                    mBeautyOn = true;
                }
                break;
            case R.id.camera_recorder:
                if (mRecording) {
                    Log.i(TAG, "stop recorder ......");
                    mCameraView.stopRecorder();
                } else {
                    Date date = new Date();
                    SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
                    mOutputPath = "/sdcard/video_capture_" + format.format(date) + ".mp4";
                    Log.i(TAG, "start recorder ......." + mOutputPath);
                    mCameraView.startRecorder(mOutputPath);
                }
                break;
            case R.id.camera_switch:
                mCameraView.switchCamera();
                break;
            default:
                break;
        }
    }

    private CameraView.ICameraViewListener mOnCameraRecorderListener = new CameraView.ICameraViewListener() {
        @Override
        public void onRecorderStarted() {
            mRecording = true;
            mRecord.setBackgroundResource(R.drawable.stop_record);
            Log.i(TAG, "onRecorderStarted");
        }

        @Override
        public void onRecorderStopped() {
            mRecording = false;
            mRecord.setBackgroundResource(R.drawable.start_record);
            Log.i(TAG, "onRecorderStopped");
        }

        @Override
        public void onRecorderError() {
            mRecording = false;
            mRecord.setBackgroundResource(R.drawable.start_record);
            Log.i(TAG, "onRecorderError");
        }

        @Override
        public void onPreviewStarted() {
            Log.i(TAG, "onPreviewStarted");
        }

        @Override
        public void onPreviewStopped() {
            Log.i(TAG, "onPreviewStopped");
        }

        @Override
        public void onPreviewError() {
            Log.i(TAG, "onPreviewError");
        }
    };
}
