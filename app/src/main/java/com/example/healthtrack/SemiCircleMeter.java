package com.example.healthtrack;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

public class SemiCircleMeter extends View {
    private Paint backgroundPaint;
    private Paint progressPaint;
    private RectF rectF;
    private float progress = 45; // Progress value (0 to 100)

    public SemiCircleMeter(Context context) {
        super(context);
        init();
    }

    public SemiCircleMeter(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SemiCircleMeter(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        backgroundPaint = new Paint();
        backgroundPaint.setColor(Color.LTGRAY);
        backgroundPaint.setStyle(Paint.Style.STROKE);
        backgroundPaint.setStrokeWidth(40);
        backgroundPaint.setAntiAlias(true);

        progressPaint = new Paint();
        progressPaint.setColor(Color.RED);
        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeWidth(40);
        progressPaint.setAntiAlias(true);

        rectF = new RectF();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();

        // Define the rectangle for the semi-circle
        rectF.set(20, 20, width - 20, height * 2 - 20);

        // Draw the background semi-circle
        canvas.drawArc(rectF, 180, 180, false, backgroundPaint);

        // Draw the progress semi-circle
        float sweepAngle = (progress / 100) * 180;
        canvas.drawArc(rectF, 180, sweepAngle, false, progressPaint);
    }

    public void setProgress(float progress) {
        this.progress = progress;
        invalidate(); // Redraw the view
    }
}