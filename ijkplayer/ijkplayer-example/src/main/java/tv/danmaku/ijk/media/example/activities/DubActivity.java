package tv.danmaku.ijk.media.example.activities;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

import tv.danmaku.ijk.media.example.R;
import tv.danmaku.ijk.media.example.dub.IDubUi;
import tv.danmaku.ijk.media.example.dub.XmDub;
import tv.danmaku.ijk.media.example.dub.state.IDubState;
import tv.danmaku.ijk.media.example.record.AudioRecorder;
import tv.danmaku.ijk.media.example.widget.media.IjkVideoView;
import tv.danmaku.ijk.media.example.widget.srtview.SrtUtil;
import tv.danmaku.ijk.media.example.widget.srtview.SrtView;
import tv.danmaku.ijk.media.example.widget.waveform.AudioWaveView;
import tv.danmaku.ijk.media.example.widget.waveform.OnValueChangeListener;
import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;

public class DubActivity extends AppCompatActivity implements IDubUi
        , XmDub.IDubStateChangeListener, View.OnClickListener {
    private static final String TAG = "DubActivity";

    private IjkVideoView mVideoView;
    private IjkVideoView mBackgroundAudio, mRecordPlayIjkView;
    private IjkMediaPlayer mIjkMediaPlayer;
    private Bitmap mAlbumBmp;
    private ImageView mAlbumImageView;
    private ImageView mCancelImageView;
    private ImageView mCompleImageView;
    private ImageView mRewriteImageView;
    private ImageView mPreviewDubImageView;
    private Button mDubButton;
    private SrtView mSrtView;
    private AudioWaveView mAudioWaveView;
    private AudioRecorder mRecorder;
    private TextView mProgressView;

    private XmDub mXmDub;

    //for test
    private static final String mLocalVideoPath = "/sdcard/dubres/video.mp4";
    private static final String mLocalBackgroundAudioPath = "/sdcard/dubres/bg.m4a";
    private static final String mLocalAlbumPath = "/sdcard/dubres/album.jpg";
    private static final String mLocalSrtPath = "/sdcard/dubres/subtitle.srt";
    private static final String mLocalWav = "/sdcard/dubres/dub.wav";

    public static Intent newIntent(Context context) {
        Intent intent = new Intent(context, DubActivity.class);
        return intent;
    }

    public static void intentTo(Context context) {
        context.startActivity(newIntent(context));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dub);
        init();
        initData();
    }

    private void init() {  // init player
        IjkMediaPlayer.loadLibrariesOnce(null);
        IjkMediaPlayer.native_profileBegin("libijkplayer.so");

        mAlbumBmp = BitmapFactory.decodeFile(mLocalAlbumPath);
        mAlbumImageView = (ImageView) findViewById(R.id.album);
        mAlbumImageView.setImageBitmap(mAlbumBmp);

        mProgressView = (TextView)findViewById(R.id.mux_progress);

        mCancelImageView = (ImageView) findViewById(R.id.cancel);
        mCancelImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });
        mCompleImageView = (ImageView) findViewById(R.id.finish_dub);
        mCompleImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //goto audio effect activity
//                Intent intent = new Intent(DubActivity.this, AudioProcessActivity.class);
//                intent.putExtra("video_path", mLocalVideoPath);
//                intent.putExtra("bg_path", mLocalBackgroundAudioPath);
//                intent.putExtra("human_path", mLocalWav);
//                intent.putExtra("img_path", mLocalAlbumPath);
//                startActivity(intent);
                mIjkMediaPlayer = new IjkMediaPlayer();
                try {
                    File file = new File("/sdcard/dubres/output.mp4");
                    if (file.exists()) {
                        file.delete();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                mIjkMediaPlayer.setOnInfoListener(new IMediaPlayer.OnInfoListener() {
                    @Override
                    public boolean onInfo(IMediaPlayer mp, int what, int extra) {
                        if (what == IMediaPlayer.MEDIA_INFO_MUX_SUCCESS) {
                            Toast.makeText(DubActivity.this
                                    , "文件合成成功！", Toast.LENGTH_LONG).show();
                            mProgressView.setVisibility(View.GONE);
                            mIjkMediaPlayer.setOnInfoListener(null);
                            mIjkMediaPlayer.release();
                        } else if (what == IMediaPlayer.MEDIA_INFO_MUX_PROGRESS) {
                            mProgressView.setVisibility(View.VISIBLE);
                            mProgressView.setTextSize(60);
                            mProgressView.setText("进度 " +extra + "%");
                        } else {
                            mIjkMediaPlayer.setOnInfoListener(null);
                            Toast.makeText(DubActivity.this
                                    ,"——————"+extra,Toast.LENGTH_LONG).show();
                        }
                        return false;
                    }
                });
                String[] inputPaths = new String[]{mLocalBackgroundAudioPath,mLocalWav,mLocalVideoPath};
                mIjkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec", 1);
                mIjkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "human-volume", "0.7f");
                mIjkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "background-volume", "0.3f");
                try {
                    mIjkMediaPlayer.muxStream(inputPaths, "/sdcard/dubres/output.mp4");
                } catch (Exception e) {
                    mIjkMediaPlayer.release();
                    e.printStackTrace();
                }
            }
        });
        mDubButton = (Button) findViewById(R.id.dub_button);
        mDubButton.setOnClickListener(this);
        //video
        mVideoView = (IjkVideoView) findViewById(R.id.video_view);

        mRecordPlayIjkView = (IjkVideoView) findViewById(R.id.ijk_record_play);
        mRecordPlayIjkView.setIsAudio(true);

        //background
        mBackgroundAudio = (IjkVideoView) findViewById(R.id.audio_view);

        mSrtView = (SrtView) findViewById(R.id.srtview);
        try {
            String srt = SrtUtil.readFile(mLocalSrtPath);
            mSrtView.setText(srt);
        } catch (Exception e) {
            e.printStackTrace();
        }

        mRewriteImageView = (ImageView) findViewById(R.id.rewrite);
        mRewriteImageView.setOnClickListener(this);
        mPreviewDubImageView = (ImageView) findViewById(R.id.shiting);
        mPreviewDubImageView.setOnClickListener(this);

        mAudioWaveView = (AudioWaveView) findViewById(R.id.wave);
        mAudioWaveView.setOnValueChangeListener(new OnValueChangeListener() {
            @Override
            public void onValueChanged(float value) {
                //Log.d(TAG, "seekTo " + value + " second");
                long seekPosition = mRecorder.setOffsetByPercent(value);
                if ((mVideoView.canSeekForward()
                        || mVideoView.canSeekBackward()) && mVideoView.getDuration() != 0) {
                    mVideoView.seekTo((int) seekPosition);
                }
            }
        });
    }

    private void initData() {
        mVideoView.setVideoPath(mLocalVideoPath);
        mBackgroundAudio.setVideoPath(mLocalBackgroundAudioPath);
        mBackgroundAudio.setIsAudio(true);
        mRecordPlayIjkView.setVideoPath(mLocalWav);
        mRecordPlayIjkView.setIsAudio(true);
        mRecorder = new AudioRecorder();
        mRecorder.createDefaultAudio(mLocalWav);
        try {
            //delete the old dub file
            File f = new File(mLocalWav);
            if (f.exists()) {
                f.delete();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        mXmDub = new XmDub(this);
        mXmDub.setDubStateChangeListener(this);
        mXmDub.init();
        mSrtView.setPlayer(mVideoView.getMediaPlayer());
    }

    @Override
    public IjkVideoView getVideoView() {
        return mVideoView;
    }

    @Override
    public IjkVideoView getBackgroundAudio() {
        return mBackgroundAudio;
    }

    @Override
    public IjkVideoView getRecordPlayIjkView() {
        return mRecordPlayIjkView;
    }

    @Override
    public AudioWaveView getAudioWaveView() {
        return mAudioWaveView;
    }

    @Override
    public AudioRecorder getAudioRecorder() {
        return mRecorder;
    }

    @Override
    public void onDubStateChange(IDubState dubState) {
        int state = dubState.getState();
        switch (state) {
            case IDubState.INIT_DUB_STATE:
                setUiInitState();
                break;
            case IDubState.DUB_PAUSE_STATE:
                setUiDubPauseState();
                break;
            case IDubState.DUBBING_STATE:
                setUiDubbingState();
                break;
            case IDubState.PREVIEW_DUB_STATE:
                setUiPreviewDubState();
                break;
            case IDubState.DUB_COMPLETE_STATE:
                setUiDubCompleteState();
                break;
        }
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.rewrite) {
        } else if (id == R.id.shiting) {
            mXmDub.startOrPausePreview();
        } else if (id == R.id.dub_button) {
            mXmDub.dubOrPause();
        } else if (id == R.id.finish_dub){

        }
    }

    private void setUiInitState(){
        mPreviewDubImageView.setVisibility(View.INVISIBLE);
        mRewriteImageView.setVisibility(View.INVISIBLE);
        mAudioWaveView.setVisibility(View.GONE);
        mDubButton.setBackgroundResource(R.drawable.dubbing_button);
        mDubButton.setEnabled(true);
        mAlbumImageView.setVisibility(View.VISIBLE);
    }

    private void setUiDubbingState(){
        mAlbumImageView.setVisibility(View.GONE);
        mPreviewDubImageView.setVisibility(View.INVISIBLE);
        mRewriteImageView.setVisibility(View.INVISIBLE);
        mDubButton.setBackgroundResource(R.drawable.dubbing_button_stop);
        mDubButton.setEnabled(true);
        mSrtView.setPlayer(mVideoView.getMediaPlayer());
    }

    private void setUiDubPauseState(){
        mPreviewDubImageView.setVisibility(View.VISIBLE);
        mRewriteImageView.setVisibility(View.VISIBLE);
        mDubButton.setBackgroundResource(R.drawable.dubbing_button);
        mDubButton.setEnabled(true);
    }

    private void setUiPreviewDubState(){
        mPreviewDubImageView.setVisibility(View.VISIBLE);
        mPreviewDubImageView.setEnabled(true);
        mRewriteImageView.setVisibility(View.VISIBLE);
        mDubButton.setBackgroundResource(R.drawable.dubbing_button_stop);
        mDubButton.setEnabled(false);
    }

    private void setUiDubCompleteState() {
        mPreviewDubImageView.setVisibility(View.VISIBLE);
        mPreviewDubImageView.setEnabled(true);
        mRewriteImageView.setVisibility(View.VISIBLE);
        mRewriteImageView.setEnabled(true);
        mAudioWaveView.setVisibility(View.VISIBLE);
        mAudioWaveView.setWavePath(null);
        mDubButton.setBackgroundResource(R.drawable.dubbing_button);
        mDubButton.setEnabled(false);
    }
    @Override
    protected void onStop() {
        super.onStop();

        //stop video
        mVideoView.stopPlayback();
        mVideoView.release(true);
        mVideoView.stopBackgroundPlay();

        //stop background
        mBackgroundAudio.stopBackgroundPlay();
        mBackgroundAudio.release(true);
        mBackgroundAudio.stopBackgroundPlay();

        mRecorder.stopRecord();
        IjkMediaPlayer.native_profileEnd();
    }
}
