package com.kartik.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.Matrix;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

// The clock: a saffron ring that fills with the seconds, the date inside it, and the time beside it
// with a softly blinking colon. Drawn by hand once a second, so it costs almost nothing.
final class ClockView extends View {
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG), arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG), glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint day = new Paint(Paint.ANTI_ALIAS_FLAG), month = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint time = new Paint(Paint.ANTI_ALIAS_FLAG), ampm = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint date = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF ring = new RectF();
    private final Matrix spin = new Matrix();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE, d MMMM", Locale.ENGLISH);
    private final Runnable tick = () -> { invalidate(); schedule(); };

    ClockView(Context c) {
        super(c);
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeWidth(px(3.5f));
        track.setColor(0x33FFFFFF);
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeWidth(px(4.5f));
        arc.setStrokeCap(Paint.Cap.ROUND);
        dot.setColor(0x66FFFFFF);
        glow.setColor(0xFFFFD27A);
        day.setTypeface(Ui.BOLD);
        day.setColor(Ui.WHITE);
        day.setTextSize(px(27));
        day.setTextAlign(Paint.Align.CENTER);
        month.setTypeface(Ui.MEDIUM);
        month.setColor(Ui.ACCENT);
        month.setTextSize(px(10.5f));
        month.setLetterSpacing(0.2f);
        month.setTextAlign(Paint.Align.CENTER);
        time.setTypeface(Ui.MEDIUM);
        time.setTextSize(px(60));
        time.setLetterSpacing(-0.02f);
        ampm.setTypeface(Ui.MEDIUM);
        ampm.setColor(Ui.ACCENT);
        ampm.setTextSize(px(17));
        ampm.setLetterSpacing(0.1f);
        date.setTypeface(Ui.REGULAR);
        date.setColor(0xE6FFFFFF);
        date.setTextSize(px(16));
    }

    private float px(float v) { return v * getResources().getDisplayMetrics().widthPixels / 960f; }

    private void schedule() {
        removeCallbacks(tick);
        postDelayed(tick, 1000 - System.currentTimeMillis() % 1000);
    }

    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); schedule(); }
    @Override protected void onDetachedFromWindow() { removeCallbacks(tick); super.onDetachedFromWindow(); }

    @Override protected void onWindowVisibilityChanged(int v) {
        super.onWindowVisibilityChanged(v);
        if (v == VISIBLE) schedule(); else removeCallbacks(tick);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        float r = px(42), cx = px(48), cy = h / 2f;
        ring.set(cx - r, cy - r, cx + r, cy + r);
        arc.setShader(new SweepGradient(cx, cy, new int[] {0xFFE0651A, 0xFFFFA62B, 0xFFFFD27A}, new float[] {0, 0.6f, 1}));
        time.setShader(new LinearGradient(0, cy - px(42), 0, cy + px(14), 0xFFFFFFFF, 0xFFFFD9A6, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas c) {
        Calendar now = Calendar.getInstance();
        float cx = ring.centerX(), cy = ring.centerY(), r = ring.width() / 2;

        // The ring: twelve hour marks, the seconds so far as a saffron arc, a bright dot at its tip.
        c.drawOval(ring, track);
        for (int i = 0; i < 12; i++) {
            double a = Math.toRadians(i * 30 - 90);
            c.drawCircle(cx + (float) Math.cos(a) * (r - px(8)), cy + (float) Math.sin(a) * (r - px(8)), px(1.3f), dot);
        }
        float sweep = Math.max(6, now.get(Calendar.SECOND) / 60f * 360f);
        spin.setRotate(-90, cx, cy);
        arc.getShader().setLocalMatrix(spin);
        c.drawArc(ring, -90, sweep, false, arc);
        double tip = Math.toRadians(sweep - 90);
        float tx = cx + (float) Math.cos(tip) * r, ty = cy + (float) Math.sin(tip) * r;
        glow.setAlpha(0x55);
        c.drawCircle(tx, ty, px(7), glow);
        glow.setAlpha(0xFF);
        c.drawCircle(tx, ty, px(3.5f), glow);

        // Inside the ring: the day and month.
        c.drawText(String.valueOf(now.get(Calendar.DAY_OF_MONTH)), cx, cy + px(5), day);
        c.drawText(new SimpleDateFormat("MMM", Locale.ENGLISH).format(now.getTime()).toUpperCase(Locale.ENGLISH),
                cx, cy + px(21), month);

        // Beside it: the time, with the colon breathing once a second, then AM/PM and the date.
        int h = now.get(Calendar.HOUR);
        String hh = String.format(Locale.ROOT, "%02d", h == 0 ? 12 : h);
        String mm = String.format(Locale.ROOT, "%02d", now.get(Calendar.MINUTE));
        float x = px(112), base = cy + px(10);
        c.drawText(hh, x, base, time);
        x += time.measureText(hh);
        time.setAlpha(now.get(Calendar.SECOND) % 2 == 0 ? 0xFF : 0x66);
        c.drawText(":", x, base - px(4), time);
        time.setAlpha(0xFF);
        x += time.measureText(":");
        c.drawText(mm, x, base, time);
        x += time.measureText(mm) + px(8);
        c.drawText(now.get(Calendar.AM_PM) == Calendar.AM ? "AM" : "PM", x, cy - px(19), ampm);
        c.drawText(dateFormat.format(now.getTime()), px(114), cy + px(38), date);
    }
}
