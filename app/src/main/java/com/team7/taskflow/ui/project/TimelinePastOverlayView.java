package com.team7.taskflow.ui.project;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

public class TimelinePastOverlayView extends View {

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stripePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public TimelinePastOverlayView(Context context) {
        super(context);
        init();
    }

    public TimelinePastOverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public TimelinePastOverlayView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        fillPaint.setColor(0x14_334155); // subtle slate tint
        fillPaint.setStyle(Paint.Style.FILL);

        stripePaint.setColor(0x33_94A3B8);
        stripePaint.setStrokeWidth(dp(1f));
        stripePaint.setStyle(Paint.Style.STROKE);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;

        canvas.drawRect(0, 0, w, h, fillPaint);

        float step = dp(12f);
        for (float x = -h; x < w + h; x += step) {
            canvas.drawLine(x, h, x + h, 0, stripePaint);
        }
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
