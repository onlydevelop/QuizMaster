package com.testmaster.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

public class ScoreStarView extends View {

    private static final int COLOR_RED = Color.parseColor("#C62828");
    private static final int COLOR_GREEN = Color.parseColor("#2E7D32");

    private int score;
    private int totalQuestions = 10;

    private final Paint starPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public ScoreStarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);
    }

    public void setScore(int score, int totalQuestions) {
        this.score = score;
        this.totalQuestions = totalQuestions;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float w = getWidth();
        float h = getHeight();
        float cx = w / 2f;
        float cy = h / 2f;
        float outerRadius = Math.min(w, h) / 2f * 0.95f;
        float innerRadius = outerRadius * 0.5f;

        int clampedScore = Math.max(1, Math.min(totalQuestions, score));
        float fraction = totalQuestions > 1 ? (clampedScore - 1) / (float) (totalQuestions - 1) : 1f;
        int color = interpolateColor(COLOR_RED, COLOR_GREEN, fraction);

        starPaint.setStyle(Paint.Style.FILL);
        starPaint.setShader(new LinearGradient(0, 0, w, h, lighten(color), color, Shader.TileMode.CLAMP));

        Path path = buildStarPath(cx, cy, outerRadius, innerRadius);
        canvas.drawPath(path, starPaint);

        textPaint.setTextSize(outerRadius * 0.6f);
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float textY = cy - (fm.ascent + fm.descent) / 2f;
        canvas.drawText(String.valueOf(score), cx, textY, textPaint);
    }

    private Path buildStarPath(float cx, float cy, float outerRadius, float innerRadius) {
        Path path = new Path();
        int points = 5;
        double angleStep = Math.PI / points;
        double startAngle = -Math.PI / 2;
        for (int i = 0; i < points * 2; i++) {
            double radius = (i % 2 == 0) ? outerRadius : innerRadius;
            double angle = startAngle + i * angleStep;
            float x = (float) (cx + radius * Math.cos(angle));
            float y = (float) (cy + radius * Math.sin(angle));
            if (i == 0) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }
        }
        path.close();
        return path;
    }

    private int interpolateColor(int colorA, int colorB, float fraction) {
        int rA = Color.red(colorA), gA = Color.green(colorA), bA = Color.blue(colorA);
        int rB = Color.red(colorB), gB = Color.green(colorB), bB = Color.blue(colorB);
        int r = Math.round(rA + fraction * (rB - rA));
        int g = Math.round(gA + fraction * (gB - gA));
        int b = Math.round(bA + fraction * (bB - bA));
        return Color.rgb(r, g, b);
    }

    private int lighten(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[2] = Math.min(1f, hsv[2] * 1.3f);
        return Color.HSVToColor(hsv);
    }
}
