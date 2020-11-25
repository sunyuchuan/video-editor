package tv.danmaku.ijk.media.example.widget.circlerview;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import java.lang.ref.WeakReference;

import tv.danmaku.ijk.media.example.R;

/**
 * Created by Yasin on 2016/4/7.
 */
public class CirclerView extends View {
    private int width;
    private int height;
    private Paint mPaintLine;
    private Paint mPaintCircle;
    private Paint mPaintSec;
    private Paint mPaintText,mPaintText2;
    public static final int NEED_INVALIDATE = 0X23;
    //圆心
    private float yxx,yxy;
    private String mName;
    private int mMin;
    private int mMax;
    private float mInterval;
    private int mCurrentValue;
    private int mRadius;
    private OnValueChangeListener mListener = null;
    private Handler mHandler = new CirclerView.MyHandler(this);
    private final static String TAG = "CirclerView";

    //每隔一秒，在handler中调用一次重新绘制方法
    private static class MyHandler extends Handler {
        //持有弱引用CirclerView, GC回收时会被回收掉.
        private final WeakReference<CirclerView> mView;

        public MyHandler(CirclerView view) {
            mView = new WeakReference<>(view);
        }

        @Override
        public void handleMessage(Message msg) {
            CirclerView view = mView.get();
            switch (msg.what){
                case NEED_INVALIDATE:
                    view.invalidate();//告诉UI主线程重新绘制
                    view.mHandler.sendEmptyMessageDelayed(NEED_INVALIDATE,1000);
                    break;
                default:
                    break;
            }
        }
    }

    public CirclerView(Context context) {
        super(context);
        init(context, null);
    }

    public CirclerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public CirclerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        if (attrs != null) {
            TypedArray ta = getContext().obtainStyledAttributes(attrs, R.styleable.circlerView);
            mName = ta.getString(R.styleable.circlerView_name);
            ta.recycle();
        }
        /*
        * 定义多个画不同东西的画笔
        * */
        mPaintLine = new Paint();
        mPaintLine.setColor(Color.WHITE);
        mPaintLine.setStrokeWidth(3);

        mPaintCircle = new Paint();
        mPaintCircle.setColor(context.getResources().getColor(R.color.green));//设置颜色
        mPaintCircle.setStrokeWidth(5);//设置线宽
        mPaintCircle.setAntiAlias(true);//设置是否抗锯齿
        mPaintCircle.setStyle(Paint.Style.STROKE);//设置绘制风格

        mPaintText = new Paint();
        mPaintText.setColor(Color.WHITE);
        mPaintText.setStrokeWidth(10);
        mPaintText.setTextAlign(Paint.Align.CENTER);
        mPaintText.setTextSize(40);

        mPaintText2 = new Paint();
        mPaintText2.setColor(Color.WHITE);
        mPaintText2.setStrokeWidth(10);
        mPaintText2.setTextAlign(Paint.Align.CENTER);
        mPaintText2.setTextSize(60);

        //滑针
        mPaintSec = new Paint();
        mPaintSec.setStrokeWidth(5);
        mPaintSec.setColor(Color.YELLOW);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        width = getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec);
        height = getDefaultSize(getSuggestedMinimumHeight(), heightMeasureSpec);
        Log.d(TAG, " width " + width + " height " + height);
        yxx = width / 2;
        yxy = height / 2;
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        //大圆半径
        mRadius = (width / 2) > (height / 2) ? (height / 2) : (width / 2);
        //50是外围刻度和文字宽度
        mRadius -= 50;

        //画出大圆
        canvas.drawCircle(width / 2, height / 2, mRadius, mPaintCircle);

        //依次旋转画布，画出每个刻度和对应数字
        //当前刻度值
        mPaintText2.setColor(Color.WHITE);
        mPaintText2.setTextSize(140);
        String text = mCurrentValue + "";
        Rect textrect = new Rect();
        mPaintText2.getTextBounds(text, 0, text.length(), textrect);
        int texth = textrect.height();
        canvas.drawText(text,width / 2,height / 2 + texth / 2, mPaintText2);

        //当前效果器名字
        mPaintText2.setTextSize(40);
        canvas.drawText(mName, width / 2, height / 2 + 150, mPaintText2);

        //一共有72个（刻度+档位文字）
        mPaintLine.setStrokeWidth(5);
        for (int i = 0; i <= 49; i++) {
            canvas.save();//保存当前画布
            canvas.rotate(235 + 360 / 72 * i, width / 2, height / 2);
            if (i < (mCurrentValue - mMin) / mInterval) {
                /*如果使用的档位调节，则该档位内的都有黄色；
                  如果滑动的，一个刻度是5°,s所以个当前度数除以5*/
                mPaintLine.setColor(Color.YELLOW);
            } else {
                mPaintLine.setColor(Color.WHITE);
            }
            canvas.drawLine(width / 2, height / 2 - mRadius - 10, width / 2, height / 2 - mRadius - 30, mPaintLine);
            canvas.restore();
        }
    }

    public void setName(String name) {
        this.mName = name;
    }

    public String getName() {
        return mName;
    }

    public void setRange(int min, int max) {
        if (min >= max) {
            Log.e(TAG, "pls input right min and max");
        } else {
            mMin = min;
            mMax = max;
            mCurrentValue = min;
            mInterval = (float)(max - min) / 50;
            Log.d(TAG, "min " + min + " max " + max + " current " + mCurrentValue + " interval " + mInterval);
        }
    }

    public void setValueChangeListener(OnValueChangeListener listener) {
        mListener = listener;
    }

    private double getDistance(float ax,float ay,float bx,float by,int type) {
        if (type == 1) {
            return Math.sqrt(((ax-bx)*(ax-bx)+(ay-by)*(ay-by)));
        } else {
            return (((ax-bx)*(ax-bx)+(ay-by)*(ay-by)));
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean ret = true;
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                break;
            case MotionEvent.ACTION_MOVE:
                float x = event.getX();
                float y = event.getY();
                // 判断当前手指是否在圆环上,半径+-60
                if ((mRadius + 60) < getDistance(yxx, yxy, x, y, 1)
                        || getDistance(yxx, yxy, x, y, 1) < (mRadius - 60)) {
                    //Log.e("cmos---->", "终止滑动");
                    return false;
                }
                // 判断当前手指距离圆心的距离 如果大于圆心x坐标代表在圆心的右侧
                double cosValue;
                if (x > yxx) {
                    //圆心右边
                    cosValue = (x - yxx) / getDistance(yxx, yxy, x, y, 1);
                } else {
                    //圆心左边
                    cosValue = (yxx - x) / getDistance(yxx, yxy, x, y, 1);
                }

                // 根据cosValue求角度值
                double acos = Math.acos(cosValue); //弧度值
                acos = Math.toDegrees(acos); //角度值

                //计算和x轴的角度
                if (x > yxx && y < yxy) {
                    acos = 215 - acos;// 第一象限
                    //Log.e("象限---->", "第一象限");
                } else if (x > yxx && y > yxy) {
                    acos = 215 + acos;// 第二象限
                    if (acos >= 250) {
                        mCurrentValue = mMax;
                        ret = false;
                    }
                    //Log.e("象限---->", "第二象限");
                } else if (x < yxx && y > yxy) {
                    if (acos > 35) {
                        //Log.d(TAG, "min");
                        mCurrentValue = mMin;
                        ret = false;
                    }
                    //Log.e("象限---->", "第三象限");
                } else {
                    acos = 35 + acos;// 第四象限
                    //Log.e("象限---->", "第四象限");
                }

                if (ret) {
                    mCurrentValue = (int)((acos / 5) * mInterval) + mMin;
                }
                if (mListener != null) {
                    mListener.onValueChanged(mCurrentValue);
                }
                if (ret) {
                    postInvalidate();
                }
                //Log.e("旋转的角度---->", acos + " " + " value " + mCurrentValue);
                return ret;
            default:
                break;
        }
        return true;
    }
}