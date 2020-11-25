package tv.danmaku.ijk.media.example.widget.waveform;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;

import java.io.File;
import java.io.RandomAccessFile;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;

import tv.danmaku.ijk.media.example.R;


/**
 * 声音波形的view
 * Created by shuyu on 2016/11/15.
 */

public class AudioWaveView extends View {
    final protected Object mViewLock = new Object();
    final protected Object mPositionLock = new Object();
    final protected Object mDrawTimesLock = new Object();

    private volatile Bitmap mViewBitmap, mCacheBitmap;

    private Paint mLinePaint,mViewPaint,mTextPaint;

    private Canvas mViewCanvas = new Canvas();

    private Canvas mCacheCanVas = new Canvas();

    private ArrayList<Short> mRecDataList = new ArrayList<>();

    private DrawThread mInnerThread;

    private int mWidthSpecSize;
    private int mHeightSpecSize;
    private int mBaseLine;

    private int mOffset = 0;//波形之间线与线的间隔

    private boolean mIsDraw = true;

    private int mWaveCount = 2;

    private int mWaveColor = Color.RED;

    private int mLineWith = 2;//单位px
    //开始绘制的地方，没绘制一个条线往左边走mLineWith个px距离
    private int mDrawStartPosition;
    //第一条绘制声音特征线在数组里面的索引，和最后一条绘制的声音特征线在数组里面的索引
    private int mDrawHeardIndex, mDrawTailIndex;
    private int mMaxVolume = 1;
    private float mCurPercent;
    private Bitmap mCutBitmap;
    private Rect mSrcRect,mDestRect;
    private float lastX;
    private int mCutBitmapWidth = 80;
    private boolean mIsMoveCutBitmap = false;

    private OnValueChangeListener mOnValueChangeListener;

    private final static String TAG = "waveform";

    private final static int VALUE_CHANGE = 1;
    private final static int NEED_REDRAW = 2;
    private String mCurrentTime;
    private String mDuration;
    private int mSamplesPerSec;
    private int mDurationSecond;
    private int mNeedDrawTime;

    private volatile boolean mIsTouching = false;
    private boolean mIsPreview = false;

    //取样间隔
    private final static int mWaveSpeed = 2205;
    private Handler mHandler = new MyHandler(this);


    private static class MyHandler extends Handler {
        //持有弱引用AudioWaveView, GC回收时会被回收掉.
        private final WeakReference<AudioWaveView> mView;

        public MyHandler(AudioWaveView view) {
            mView = new WeakReference<>(view);
        }

        @Override
        public void handleMessage(Message msg) {
            AudioWaveView view = mView.get();
            super.handleMessage(msg);
            if (view != null) {
                //执行业务逻辑
                switch (msg.what) {
                    case VALUE_CHANGE:
                        if (null != view.mOnValueChangeListener) {
                            view.mOnValueChangeListener.onValueChanged(((Float)msg.obj).floatValue());
                        }
                        break;
                    case NEED_REDRAW:
                        view.invalidate();
                        //Log.d(TAG, "redraw wave form");
                        break;
                    default:
                        break;
                }
            }
        }
    }

    public AudioWaveView(Context context) {
        super(context);
        init(context, null);
    }

    public AudioWaveView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public AudioWaveView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mIsDraw = false;
        stopView();
        if (mViewBitmap != null && !mViewBitmap.isRecycled()) {
            mViewBitmap.recycle();
        }
        if (mCacheBitmap != null && !mCacheBitmap.isRecycled()) {
            mCacheBitmap.recycle();
        }
    }

    private void init(Context context, AttributeSet attrs) {
        if (isInEditMode())
            return;

        if (attrs != null) {
            TypedArray ta = getContext().obtainStyledAttributes(attrs, R.styleable.audioWaveView);
//            mOffset = ta.getInt(R.styleable.audioWaveView_waveOffset, dip2px(context, -11));
            mWaveColor = ta.getColor(R.styleable.audioWaveView_waveColor, Color.RED);
            mWaveCount = ta.getInt(R.styleable.audioWaveView_waveCount, 2);
            ta.recycle();
        }

//        if (mOffset == dip2px(context, -11)) {
//            mOffset = dip2px(context, 1);
//        }

        if (mWaveCount < 1) {
            mWaveCount = 1;
        } else if (mWaveCount > 2) {
            mWaveCount = 2;
        }
        Log.d(TAG, "waveCount " + mWaveCount);
        //波形画笔
        mLinePaint = new Paint();
        mLinePaint.setColor(mWaveColor);
        mLinePaint.setStrokeWidth(2.0f);


        mViewPaint = new Paint();
        mViewPaint.setAntiAlias(true);//用于防止边缘的锯齿
        mViewPaint.setColor(Color.BLUE);//设置颜色
        mViewPaint.setStyle(Paint.Style.STROKE);//设置样式为空心矩形
        mViewPaint.setStrokeWidth(4.0f);//设置空心矩形边框的宽度
        mViewPaint.setTextSize(20);

        //文字画笔
        mTextPaint = new Paint();
        mTextPaint.setColor(Color.WHITE);
        mTextPaint.setStrokeWidth(3);
        mTextPaint.setTextSize(40);
        mTextPaint.setTextAlign(Paint.Align.LEFT);
//        mCutBitmap = BitmapFactory.decodeResource(this.getResources(),R.drawable.btn_cut_start);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility == VISIBLE && mCacheBitmap == null) {
            ViewTreeObserver vto = getViewTreeObserver();
            vto.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    if (getWidth() > 0 && getHeight() > 0) {
                        //控件整个宽高
                        mWidthSpecSize = getWidth();
                        mHeightSpecSize = getHeight();
                        mBaseLine = mHeightSpecSize / 2;
                        mDrawStartPosition = mWidthSpecSize / 2;
                        mOffset = 0;
                        mViewBitmap = Bitmap.createBitmap(mWidthSpecSize
                                , mHeightSpecSize, Bitmap.Config.ARGB_8888);
                        mCacheBitmap = Bitmap.createBitmap(mWidthSpecSize
                                , mHeightSpecSize, Bitmap.Config.ARGB_8888);
                        mViewCanvas.setBitmap(mViewBitmap);
                        mCacheCanVas.setBitmap(mCacheBitmap);
                        mSrcRect = new Rect(0,0,mCutBitmapWidth,mHeightSpecSize);
                        mDestRect = new Rect(mWidthSpecSize/2-mCutBitmapWidth/2,0
                                ,mWidthSpecSize/2+mCutBitmapWidth/2,mHeightSpecSize);
                        Bitmap bitmap = BitmapFactory
                                .decodeResource(AudioWaveView.this.getResources()
                                                        ,R.drawable.btn_cut_start);
                        mCutBitmap = AudioWaveView.extractBitmap(bitmap
                                                ,mCutBitmapWidth,mHeightSpecSize);
                        ViewTreeObserver vto = getViewTreeObserver();
                        vto.removeOnPreDrawListener(this);
                        Log.d(TAG, "mWidthSpecSize " + mWidthSpecSize + " mHeightSpecSize " + mHeightSpecSize);
                    }
                    return true;
                }
            });
        }
    }

    private void moveCutBitmap(int x){
        mDestRect.offsetTo(x - mCutBitmapWidth/2,0);
        this.invalidate();
    }

    private void drawOnceMore() {
        synchronized (mDrawTimesLock) {
            mNeedDrawTime++;
            mDrawTimesLock.notifyAll();
        }
    }

    public static Bitmap extractBitmap(Bitmap src, int width, int height) {
        if (src == null) {
            return src;
        }
        if (width == 0 && height == 0) {
            return src;
        }
        int oriWidth = src.getWidth();
        int oriHeight = src.getHeight();
        if (width == 0) {
            width = (int) (height * (oriWidth / (float) oriHeight));
        } else if (height == 0) {
            height = (int) (width * (oriHeight / (float) oriWidth));
        }
        float scaleX = width / (float) oriWidth;
        float scaleY = height / (float) oriHeight;
        Matrix matrix = new Matrix();
        matrix.postScale(scaleX, scaleY);
        return Bitmap.createBitmap(src, 0, 0, oriWidth, oriHeight, matrix, true);
    }

    //内部类的线程
    private class DrawThread extends Thread {
        @SuppressWarnings("unchecked")
        @Override
        public void run() {
            int drawStartPosition, drawHeardIndex, drawTailIndex;

            while (mIsDraw) {
                if (mRecDataList == null || mRecDataList.size() == 0) {
                    parseSoundFile(mLocalWav);
                    updateDrawLineIndex();
                }
                if (mCacheBitmap == null || mViewBitmap == null) {
                    continue;
                }
                synchronized (mDrawTimesLock){
                    if (mNeedDrawTime > 0){
                        mNeedDrawTime --;
                    } else {
                        try {
                            mDrawTimesLock.wait();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        if (!mIsDraw){
                            return;
                        }
                    }
                }
                if (mCacheCanVas != null) {
                    mCacheCanVas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                    mCacheCanVas.drawLine(0, mBaseLine, mWidthSpecSize, mBaseLine, mLinePaint);
                    mCacheCanVas.drawRect(0, 0, mWidthSpecSize, mHeightSpecSize, mViewPaint);
                    mCacheCanVas.drawLine(mWidthSpecSize/2,0,mWidthSpecSize/2,mHeightSpecSize,mViewPaint);
                    mCacheCanVas.drawText(mCurPercent +"%",mWidthSpecSize/2,mHeightSpecSize,mTextPaint);
                    synchronized (mPositionLock) {
                        drawHeardIndex = mDrawHeardIndex;
                        drawStartPosition = mDrawStartPosition;
                        drawTailIndex = mDrawTailIndex;
                    }
                    for (int i = drawHeardIndex; i >= drawTailIndex; i--) {
                        Short sh = mRecDataList.get(i);
                        drawNow(sh, i, drawHeardIndex, drawStartPosition);
                    }
                    synchronized (mViewLock) {
                        mViewCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                        mViewCanvas.drawBitmap(mCacheBitmap, 0, 0, mLinePaint);
                    }

                    Message msg = new Message();
                    msg.what = NEED_REDRAW;
                    mHandler.sendMessage(msg);
                }
            }
        }
    }

    private void drawNow(Short sh, int i, int drawHeardIndex, int drawStartPosition) {
        int offSet = drawHeardIndex - i;
        int xPosition = drawStartPosition - offSet * mLineWith;
        if (sh != null) {
            short max = (short) (mBaseLine - sh * mBaseLine / mMaxVolume);
            short min;
            if (mWaveCount == 2) {
                min = (short) (mBaseLine + sh * mBaseLine / mMaxVolume);
            } else {
                min = (short) (mBaseLine);
            }
            mCacheCanVas.drawLine(xPosition, mBaseLine, xPosition, max, mLinePaint);
            mCacheCanVas.drawLine(xPosition, min, xPosition, mBaseLine, mLinePaint);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mIsDraw && mViewBitmap != null) {
            synchronized (mViewLock) {
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                canvas.drawBitmap(mViewBitmap, 0, 0, mViewPaint);
                canvas.drawBitmap(mCutBitmap,mSrcRect,mDestRect,mLinePaint);
            }
        }
    }

    /**
     * 开始绘制
     */
    private void startView() {
        mRecDataList.clear();
        mOffset = 0;
        mDrawStartPosition = mWidthSpecSize/2;
        if (mInnerThread != null && mInnerThread.isAlive()) {
            synchronized (mDrawTimesLock){
                mIsDraw = false;
                mNeedDrawTime = -1;
                mDrawTimesLock.notifyAll();
            }
            try {
                mInnerThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            mCacheCanVas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            mViewCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        }
        mIsDraw = true;
        mInnerThread = new DrawThread();
        mInnerThread.setName("recorder_drawUi");
        mInnerThread.start();
        drawOnceMore();
        drawOnceMore();
        Log.d(TAG, "start view");
    }

    /**
     * 停止绘制
     */
    private void stopView() {
        mIsDraw = false;
        mRecDataList.clear();
        mNeedDrawTime = -1;
        synchronized (mDrawTimesLock){
            mDrawTimesLock.notifyAll();
        }
        try {
            if (mInnerThread != null) {
                mInnerThread.join();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        mCacheCanVas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        mViewCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        Log.d(TAG, "stop view");
    }

    //设置value变化监听
    public void setOnValueChangeListener(OnValueChangeListener onValueChangeListener) {
        this.mOnValueChangeListener = onValueChangeListener;
    }

    private static final String mLocalWav = "/sdcard/dubres/dub.wav";

    private void parseSoundFile(String path) {
        mCurrentTime = "00:00";
        int channels;
        int bitsPerSample;
        int dataLength;
        byte[] buf = new byte[44];
        int size;
        try {
            File file = new File(path);
            RandomAccessFile rfile = new RandomAccessFile(file, "r");
            size = rfile.read(buf, 0, buf.length);
            if (size != 44) {
                Log.d(TAG, "read wave header failed ");
                return;
            }
            channels = ((buf[23] & 0xff) << 8) | (buf[22] & 0xff);
            mSamplesPerSec = ((buf[27] & 0xff) << 24) | ((buf[26] & 0xff) << 16) | ((buf[25] & 0xff) << 8) | (buf[24] & 0xff);
            bitsPerSample = ((buf[35] & 0xff) << 8) | (buf[34] & 0xff);
            dataLength = ((buf[43] & 0xff) << 24) | ((buf[42] & 0xff) << 16) | ((buf[41] & 0xff) << 8) | (buf[40] & 0xff);
            mDurationSecond = dataLength / (channels * (bitsPerSample / 8) * mSamplesPerSec);
            Log.d(TAG, "channels " + channels + " samplesPerSec " + mSamplesPerSec + " bitsPerSample " + bitsPerSample + " dataLength " + dataLength + " duration " + mDurationSecond);
//            setRange(0, mDurationSecond);

            //read 1000 frames
            int frameSize = channels * (bitsPerSample / 8) * 1024;
            byte[] pcmData = new byte[frameSize * 1000];
            short[] pcmShortData = new short[(frameSize / 2) * 1000];

            int pos = 0;
            long startTime = System.currentTimeMillis();
            while (true) {
                rfile.seek(pos + 44);
                size = rfile.read(pcmData, 0, pcmData.length);
                if (size <= 0) {
                    break;
                }
                if (size < pcmData.length) {
                    Log.d(TAG, "this is last data size " + size);
                }
                //convert the byte[] to short[]
                ByteBuffer.wrap(pcmData).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(pcmShortData);

                if (mRecDataList != null) {
                    int length = size / (mWaveSpeed * 2);
                    //Log.d(TAG, "length " + length);
                    short resultMax = 0;
                    for (int i = 0, k = 0; i < length; i++, k += mWaveSpeed) {
                        short max = 0;
                        //find the max value int 300 shorts
                        for (int j = k; j < k + mWaveSpeed; j++) {
                            if (pcmShortData[j] > max) {
                                max = pcmShortData[j];
                                resultMax = max;
                            }
                        }
                        mRecDataList.add(resultMax);
                        if (resultMax >= mMaxVolume) {
                            mMaxVolume = resultMax;
                        }
                    }
                }
                pos += size;
            }
            long endTime = System.currentTimeMillis();
            Log.d(TAG, "get sample cost time " + (endTime - startTime) + " ms ");
            Log.d(TAG, "the list size " + mRecDataList.size());
            Log.d(TAG, "0 " + mRecDataList.get(mRecDataList.size() / 8) + " 1 " + mRecDataList.get(mRecDataList.size() / 4) + " 2 " + mRecDataList.get(mRecDataList.size() / 2));
        } catch (Exception e) {
            e.printStackTrace();
        }
        int min = mDurationSecond / 60;
        int sec = mDurationSecond % 60;
        mDuration = String.format("%02d:%02d", min, sec);
        Log.d(TAG, "duration " + mDuration);
    }

    //本地wav文件
    public void setWavePath(String path) {
        startView();
    }

    /**
     * dip转为PX
     */
    private int dip2px(Context context, float dipValue) {
        float fontScale = context.getResources().getDisplayMetrics().density;
        return (int) (dipValue * fontScale + 0.5f);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mIsTouching = true;
                lastX = x;
                if (mDestRect.contains((int)x,1)){
                    mIsMoveCutBitmap = true;
                } else {
                    mIsMoveCutBitmap = false;
                }
                break;
            case MotionEvent.ACTION_MOVE:
                int offsetX1= (int) (x - lastX);
                if (mIsMoveCutBitmap){
                    if (x <= mWidthSpecSize){
                        Log.e("cf_test","剪切图标位置：" + x);
                        moveCutBitmap((int)x);
                    }
                }else {
                    mOffset += offsetX1;
                    updateXPosition(true);
                }
                lastX = x;
                break;
            case MotionEvent.ACTION_UP:
                mIsTouching = false;
                break;
            case MotionEvent.ACTION_CANCEL:
                mIsTouching = false;
                Log.e("cf_test","__________MotionEvent.ACTION_CANCEL");
                break;
        }
        return true;
    }

    public void setCurPercentPosition(float percentPosition){
        if (mIsTouching){
            return;
        }
        if (mRecDataList == null || mRecDataList.size() == 0){
            return;
        }

        if (percentPosition >= 100.0f || percentPosition <=0.0f){
            percentPosition = 100.0f;
        }
        mOffset = (int) ((100.0f - percentPosition)/100.0f*mRecDataList.size()*mLineWith);
        updateXPosition(false);
    }

    private void updateXPosition(boolean isNeedNotify) {
        synchronized (mPositionLock) {
            if (mOffset < 0){
                mOffset = 0;
            } else if(mOffset > mRecDataList.size()*mLineWith){
                mOffset = mRecDataList.size()*mLineWith;
            }
            if (mWidthSpecSize/2 + mOffset > mWidthSpecSize) {
                mDrawStartPosition = mWidthSpecSize;
            } else {
                mDrawStartPosition = mWidthSpecSize/2 + mOffset;
            }
            updateDrawLineIndex();
            if (isNeedNotify){
                Message message = Message.obtain();
                message.what = VALUE_CHANGE;
                message.obj = mCurPercent;
                mHandler.sendMessage(message);
            }
        }
        drawOnceMore();
    }

    private void updateDrawLineIndex() {
        if (mRecDataList == null || mRecDataList.size() == 0) {
            return;
        }
        mCurPercent = 100 - 100.0f*mOffset/mLineWith/mRecDataList.size();
        int rightOutRange = mOffset - mWidthSpecSize / 2;
        if (rightOutRange > 0) {
            mDrawHeardIndex = mRecDataList.size() - 1 - rightOutRange / mLineWith;
        } else {
            mDrawHeardIndex = mRecDataList.size() - 1;
        }
        int leftOutSize = mDrawHeardIndex - mDrawStartPosition / mLineWith;
        if (leftOutSize > 0) {
            mDrawTailIndex = leftOutSize;
        } else {
            mDrawTailIndex = 0;
        }
//        Log.e("cf_test", "mOffset:" + mOffset
//                + "__mDrawHeardIndex:" + mDrawHeardIndex
//                + "__mDrawTailIndex:" + mDrawTailIndex
//                + "___mDrawStartPosition:" + mDrawStartPosition);
    }

}
