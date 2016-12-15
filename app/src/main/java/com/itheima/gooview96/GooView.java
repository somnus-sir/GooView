package com.itheima.gooview96;

import android.animation.FloatEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.drawable.AnimationDrawable;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;

/**
 * Created by whn on 2016/12/15.
 */

public class GooView extends View {
    Paint paint = null;
    FloatEvaluator floatEval = new FloatEvaluator();

    public GooView(Context context) {
        this(context, null);
    }

    public GooView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public GooView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.RED);
    }

    PointF dragCenter = new PointF(250, 300);//drag圆的圆心坐标
    PointF stickyCenter = new PointF(250, 300);//sticky圆的圆心坐标
    float dragRadius = 20;//drag圆的半径
    float stickyRadius = 20;//sticky圆的半径

    PointF[] dragPoints = {new PointF(300, 280), new PointF(300, 320)};//drag圆上的那2个点
    PointF[] stickyPoints = {new PointF(400, 280), new PointF(400, 320)};//sticky圆的那2个点
    PointF controlPoint = new PointF(350, 300);
    double lineK;//斜率，或者说角的正切值

    @Override
    protected void onDraw(Canvas canvas) {
        //动态计算sticky圆半径
        stickyRadius = calculateStickyRadius();

        //a.计算斜率
        float dy = dragCenter.y - stickyCenter.y;
        float dx = dragCenter.x - stickyCenter.x;
        if (dx != 0) {
            lineK = dy / dx;
        }

        //b.计算4个贝塞尔曲线的点
        dragPoints = GeometryUtil.getIntersectionPoints(dragCenter, dragRadius, lineK);
        stickyPoints = GeometryUtil.getIntersectionPoints(stickyCenter, stickyRadius, lineK);

        //c.计算控制点
        controlPoint = GeometryUtil.getPointByPercent(dragCenter, stickyCenter, 0.618f);

        //1.绘制2个静态的圈圈
        canvas.drawCircle(dragCenter.x, dragCenter.y, dragRadius, paint);
        if (!isOutOfRange) {
            canvas.drawCircle(stickyCenter.x, stickyCenter.y, stickyRadius, paint);

            //2.使用贝塞尔曲线绘制2圆链接部分
            Path path = new Path();
            path.moveTo(stickyPoints[0].x, stickyPoints[0].y);//指定一个起点
            path.quadTo(controlPoint.x, controlPoint.y, dragPoints[0].x, dragPoints[0].y);
            //连线到第二个曲线的起点
            path.lineTo(dragPoints[1].x, dragPoints[1].y);
            path.quadTo(controlPoint.x, controlPoint.y, stickyPoints[1].x, stickyPoints[1].y);
            path.close();//可以不用，因为会自动闭合
            canvas.drawPath(path, paint);
        }


        //绘制sticky圆周围的保护圈
        paint.setStyle(Paint.Style.STROKE);//只画边
        canvas.drawCircle(stickyCenter.x, stickyCenter.y, maxDistance, paint);
        paint.setStyle(Paint.Style.FILL);//填充内容
    }

    float maxDistance = 180;//最大距离

    /**
     * 动态计算sticky圆的半径
     *
     * @return
     */
    private float calculateStickyRadius() {
        //1.获取2圆圆心距离
        float distance = GeometryUtil.getDistanceBetween2Points(dragCenter, stickyCenter);
        //2.计算半径，开始值是20，结束值是3
        float fraction = distance / maxDistance;
        return floatEval.evaluate(fraction, 20, 3);
    }

    boolean isOutOfRange;//是否超出最大范围

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                dragCenter.set(event.getX(), event.getY());

                //判断有没有超出范围
                float distance = GeometryUtil.getDistanceBetween2Points(dragCenter, stickyCenter);
                isOutOfRange = distance > maxDistance;

                break;
            case MotionEvent.ACTION_UP:
                //判断是是否是在圈内抬起
                if (isOutOfRange) {
                    //执行爆炸动画
                    executeBoomAnim(dragCenter);
                    //归位
                    dragCenter.set(stickyCenter);
//                    Toast.makeText(getContext(), "执行气泡动画!!!", Toast.LENGTH_SHORT).show();
                } else {
                    //弹回家
                    execBounceAnim();
                }
                break;
        }
        //重绘
        invalidate();

        return true;
    }

    FrameLayout parent;

    /**
     * 执行爆炸动画
     */
    private void executeBoomAnim(PointF p) {
        if (parent == null) {
            parent = (FrameLayout) getParent();
        }
        //1.创建动画的VIew载体
        final View animView = createAnimView();
        //2.添加到当前界面中，也就是FrameLayout
        parent.addView(animView);
        //计算要移动的位置
        //让View移动过去
        animView.measure(0, 0);
        animView.setTranslationX(p.x - animView.getMeasuredWidth() / 2);
        animView.setTranslationY(p.y - animView.getMeasuredHeight() / 2);

        AnimationDrawable drawable = (AnimationDrawable) animView.getBackground();
        drawable.start();//开始播放帧动画
        new Handler().postDelayed(
                new Runnable() {
                    @Override
                    public void run() {
                        parent.removeView(animView);
                    }
                }, 601);
    }

    private View createAnimView() {
        View view = new View(getContext());
        view.setLayoutParams(new FrameLayout.LayoutParams(68, 68));
        view.setBackgroundResource(R.drawable.anim);
        return view;
    }

    /**
     * 执行回弹动画
     */
    public void execBounceAnim() {
        final PointF startPoint = new PointF(dragCenter.x, dragCenter.y);
        ValueAnimator animator = ValueAnimator.ofFloat(0, 8);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                //获取动画执行的百分比进度
                float fraction = animation.getAnimatedFraction();
                //根据百分比去计算2个起始点中间的点
                PointF p = GeometryUtil.getPointByPercent(startPoint, stickyCenter, fraction);
                dragCenter.set(p.x, p.y);
                //重绘
                invalidate();
            }
        });
        animator.setInterpolator(new OvershootInterpolator(2));
        animator.setDuration(600);
        animator.start();
    }
}
