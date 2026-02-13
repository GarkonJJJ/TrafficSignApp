package com.example.trafficsign.ui;


import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.camera.view.PreviewView;

import com.example.trafficsign.model.DetectionBox;

public class OverlayView extends View {

    private final Paint boxPaint = new Paint();
    private final Paint textPaint = new Paint();
    private final Paint hudPaint = new Paint();

    private DetectionBox[] detections = new DetectionBox[0];
    private int srcW = 1, srcH = 1;
    private PreviewView previewView;

    private boolean drawEnabled = true;

    private float lastFps = 0f;
    private double lastInferMs = 0;

    private int insetTopPx = 0;

    public OverlayView(Context c, @Nullable AttributeSet a) {
        super(c, a);

        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(6f);
        boxPaint.setAntiAlias(true);

        textPaint.setTextSize(36f);
        textPaint.setAntiAlias(true);

        hudPaint.setTextSize(40f);
        hudPaint.setAntiAlias(true);
    }

    public void toggleDraw() { drawEnabled = !drawEnabled; invalidate(); }

    public void setDetections(DetectionBox[] boxes, int srcW, int srcH, PreviewView previewView) {
        this.detections = boxes != null ? boxes : new DetectionBox[0];
        this.srcW = Math.max(1, srcW);
        this.srcH = Math.max(1, srcH);
        this.previewView = previewView;
        invalidate();
    }

    public void setTiming(float fps, double inferMs) {
        if (fps > 0) this.lastFps = fps;
        this.lastInferMs = inferMs;
        invalidate();
    }

    public float getLastFps() { return lastFps; }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!drawEnabled) return;

        // 将检测框从“输入图像坐标”映射到 Overlay 坐标
        float viewW = getWidth();
        float viewH = getHeight();

        // 简化：按 center-crop/fit 的差异你可进一步精确，这里先按“等比缩放到视图”处理
        float scaleX = viewW / srcW;
        float scaleY = viewH / srcH;

        // HUD
//        canvas.drawText(String.format("FPS: %.1f", lastFps), 20, 50, hudPaint);
//        canvas.drawText(String.format("Infer: %.2f ms", lastInferMs), 20, 100, hudPaint);

        // HUD2.0
        int baseY = insetTopPx + dp(8);
        canvas.drawText(String.format("FPS: %.1f", lastFps), 20, baseY + dp(16), hudPaint);
        canvas.drawText(String.format("Infer: %.2f ms", lastInferMs), 20, baseY + dp(44), hudPaint);

        for (DetectionBox b : detections) {
            RectF r = new RectF(
                    b.left * scaleX,
                    b.top * scaleY,
                    b.right * scaleX,
                    b.bottom * scaleY
            );
            canvas.drawRect(r, boxPaint);
            canvas.drawText(String.format("%s %.2f", b.label, b.score),
                    r.left, Math.max(40, r.top - 10), textPaint);
        }
    }

    public void setInsetTop(int px) {
        insetTopPx = Math.max(px, 0);
        invalidate();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
