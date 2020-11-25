package tv.danmaku.ijk.media.example.widget.srtview;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import java.lang.ref.WeakReference;
import java.util.List;

import tv.danmaku.ijk.media.example.R;
import tv.danmaku.ijk.media.example.widget.waveform.AudioWaveView;
import tv.danmaku.ijk.media.player.IMediaPlayer;

public class SrtView extends View {
    private List<SrtBean> list;
    private Paint mPaint;
    private int width = 0, height = 0;
    private int currentPosition = 0;
    private IMediaPlayer player;
    private int lastPosition = 0;
    private int mHighLineColor;
    private int mTextColor;
    private int mHighLineTextHeight;
    private int mHighLineTextSize = 60;
    private int mTextSize = 36;
    private int mTextLineSize = 50;
    private MyHandler mMyHandler = new MyHandler(this);

    private static class MyHandler extends Handler {
        //持有弱引用AudioWaveView, GC回收时会被回收掉.
        private final WeakReference<SrtView> mView;

        public MyHandler(SrtView view) {
            mView = new WeakReference<>(view);
        }

        @Override
        public void handleMessage(Message msg) {
            SrtView view = mView.get();
            super.handleMessage(msg);
            if (view != null) {
                //执行业务逻辑
                view.invalidate();
            }
        }
    }

    private final static String TAG = SrtView.class.getName();

    public void setHighLineColor(int highLineColor) {
        this.mHighLineColor = highLineColor;
    }

    public void setTextColor(int textColor) {
        this.mTextColor = textColor;
    }

    public void setPlayer(IMediaPlayer player) {
        this.player = player;
        invalidate();
    }

    /**
     * 标准歌词字符串
     *
     * @param Text
     */
    public void setText(String Text) {
        list = SrtUtil.parseStr2List(Text);
        invalidate();
    }

    public SrtView(Context context) {
        this(context, null);
    }

    public SrtView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SrtView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        if (attrs != null) {
            TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.srtView);
            mHighLineColor = ta.getColor(R.styleable.srtView_hignLineColor, getResources().getColor(R.color.green));
            mTextColor = ta.getColor(R.styleable.srtView_srtColor, getResources().getColor(android.R.color.darker_gray));
            ta.recycle();
        }
        mPaint = new Paint();
        mPaint.setAntiAlias(true);
        mPaint.setTextAlign(Paint.Align.CENTER);
    }

    @Override
    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    protected void onDraw(Canvas canvas) {
        if (width == 0 || height == 0) {
            width = getMeasuredWidth();
            height = getMeasuredHeight();
            Log.d(TAG, "view height " + height + " view width " + width);
        }
        if (list == null || list.size() == 0) {
            mPaint.setColor(mHighLineColor);
            mPaint.setTextSize(mHighLineTextSize);
            canvas.drawText("暂无字幕", width / 2, height / 2, mPaint);
            return;
        }

        getCurrentPosition();
        long currentMillis = ((player == null) ? 0 : player.getCurrentPosition());
        drawText2(canvas, currentMillis);
        long start = list.get(currentPosition).getStart();

        float v = (currentMillis - start) > 500 ? currentPosition * mHighLineTextHeight : lastPosition * mHighLineTextHeight + (currentPosition - lastPosition) * mHighLineTextHeight * ((currentMillis - start) / 500f);
        setScrollY((int) v);
        if (getScrollY() == currentPosition * mHighLineTextHeight) {
            lastPosition = currentPosition;
        }
//        postInvalidateDelayed(20);
        mMyHandler.sendEmptyMessageDelayed(0,50);
    }

    private void drawText2(Canvas canvas, long currentMillis) {
        mPaint.setTextSize(60);
        String highLineLrc = list.get(currentPosition).getText();
        Rect textrect = new Rect();
        mPaint.getTextBounds(highLineLrc, 0, highLineLrc.length(), textrect);
        int highLineTextWidth = (int)Math.ceil(mPaint.measureText(highLineLrc));
        int highLineTextHeight = textrect.height();
        mHighLineTextHeight = highLineTextHeight;
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        //Log.d(TAG, "highline w " + highLineTextWidth + " h " + highLineTextHeight);
        for (int i = 0; i < list.size(); i++) {
            if (i < currentPosition) {
                continue;
            } else if (i == currentPosition) {
                mPaint.setTextSize(mHighLineTextSize);

                mPaint.setColor(mTextColor);
                canvas.drawText(highLineLrc, width / 2, highLineTextHeight * i + mTextLineSize, mPaint);

                mPaint.setColor(mHighLineColor);
                SrtBean srtBean = list.get(currentPosition);
                long start = srtBean.getStart();
                long end = srtBean.getEnd();
                int highlineWidth = (int) ((currentMillis - start) * 1.0f / (end - start) * highLineTextWidth);

                canvas.save();
                canvas.clipRect((width - highLineTextWidth) / 2, highLineTextHeight * i - highLineTextHeight + mTextLineSize, ((width - highLineTextWidth) / 2 + highlineWidth),  highLineTextHeight * i + mTextLineSize + highLineTextHeight);
                canvas.drawText(highLineLrc, width / 2, highLineTextHeight * i + mTextLineSize, mPaint);
                canvas.restore();
            } else {
                mPaint.setTextSize(mTextSize);
                mPaint.setColor(mTextColor);
                canvas.drawText(list.get(i).getText(), width / 2, highLineTextHeight * i + mTextLineSize, mPaint);
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    public void init() {
        currentPosition = 0;
        lastPosition = 0;
        setScrollY(0);
        invalidate();
    }

    private void getCurrentPosition() {
        try {
            if (player == null) {
                currentPosition = 0;
                return;
            }

            long currentMillis = player.getCurrentPosition();
            if (currentMillis < list.get(0).getStart()) {
                currentPosition = 0;
                return;
            }
            if (currentMillis > list.get(list.size() - 1).getStart()) {
                currentPosition = list.size() - 1;
                return;
            }
            for (int i = 0; i < list.size(); i++) {
                if (currentMillis >= list.get(i).getStart() && currentMillis < list.get(i).getEnd()) {
                    currentPosition = i;
                    return;
                }
            }
        } catch (Exception e) {
            postInvalidateDelayed(100);
        }
    }
}
