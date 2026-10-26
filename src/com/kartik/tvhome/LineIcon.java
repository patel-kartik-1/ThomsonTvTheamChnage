package com.kartik.tvhome;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

// Tiny outline icons on a 24x24 grid, so the app needs no image resources (from TV Detox, MIT).
// Each icon is a list of ops: 0=moveTo x y, 1=lineTo x y, 2=close, 3=circle cx cy r,
// 4=arc cx cy r startDegrees sweepDegrees (0 degrees is 3 o'clock, going clockwise).
final class LineIcon extends Drawable {
    static final int[] PLAY = {0, 75, 50, 1, 75, 190, 1, 190, 120, 2};
    static final int[] WIFI = {4, 120, 195, 135, 225, 90, 4, 120, 195, 92, 225, 90, 4, 120, 195, 50, 225, 90,
            3, 120, 195, 4};
    static final int[] WIFI_2 = {4, 120, 195, 92, 225, 90, 4, 120, 195, 50, 225, 90, 3, 120, 195, 4};
    static final int[] WIFI_1 = {4, 120, 195, 50, 225, 90, 3, 120, 195, 4};
    static final int[] SLASH = {0, 50, 45, 1, 190, 205};
    static final int[] BLUETOOTH = {0, 70, 80, 1, 170, 160, 1, 120, 205, 1, 120, 35, 1, 170, 80, 1, 70, 160};
    static final int[] ETHERNET = {0, 40, 60, 1, 200, 60, 1, 200, 165, 1, 160, 165, 1, 160, 195, 1, 80, 195,
            1, 80, 165, 1, 40, 165, 2, 0, 85, 60, 1, 85, 100, 0, 120, 60, 1, 120, 100, 0, 155, 60, 1, 155, 100};
    static final int[] SETTINGS = {0, 40, 70, 1, 140, 70, 0, 180, 70, 1, 200, 70, 0, 40, 170, 1, 60, 170,
            0, 100, 170, 1, 200, 170, 3, 160, 70, 20, 3, 80, 170, 20};
    static final int[] HOME = {0, 35, 115, 1, 120, 42, 1, 205, 115, 0, 62, 92, 1, 62, 200, 1, 178, 200, 1, 178, 92,
            0, 102, 200, 1, 102, 148, 1, 138, 148, 1, 138, 200};
    // A diya flame over its lamp.
    static final int[] FLAME = {0, 120, 28, 1, 151, 112, 4, 120, 140, 42, -42, 264, 0, 120, 28, 1, 89, 112,
            0, 120, 125, 1, 120, 150, 0, 60, 205, 1, 180, 205, 0, 80, 205, 1, 95, 222, 1, 145, 222, 1, 160, 205};
    static final int[] NOTE = {0, 150, 45, 1, 150, 168, 0, 150, 45, 1, 198, 66, 1, 198, 98, 1, 150, 78,
            3, 122, 170, 28};
    static final int[] BOOK = {0, 120, 68, 1, 120, 198, 0, 120, 68, 1, 45, 52, 1, 45, 182, 1, 120, 198,
            0, 120, 68, 1, 195, 52, 1, 195, 182, 1, 120, 198};
    static final int[] TV = {0, 32, 72, 1, 208, 72, 1, 208, 180, 1, 32, 180, 2, 0, 85, 30, 1, 120, 70, 1, 155, 30,
            0, 88, 208, 1, 152, 208};
    static final int[] GRID = {0, 45, 45, 1, 105, 45, 1, 105, 105, 1, 45, 105, 2, 0, 135, 45, 1, 195, 45, 1, 195, 105,
            1, 135, 105, 2, 0, 45, 135, 1, 105, 135, 1, 105, 195, 1, 45, 195, 2, 0, 135, 135, 1, 195, 135, 1, 195, 195,
            1, 135, 195, 2};
    // A heartbeat line, for TV Health.
    static final int[] PULSE = {0, 20, 130, 1, 72, 130, 1, 96, 66, 1, 130, 190, 1, 158, 104, 1, 176, 130, 1, 220, 130};
    static final int[] YOUTUBE = {0, 28, 62, 1, 212, 62, 1, 212, 178, 1, 28, 178, 2, 0, 100, 92, 1, 100, 148,
            1, 150, 120, 2};

    private final int[] ops;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF arc = new RectF();

    LineIcon(int[] ops) { this(ops, 0xEBFFFFFF); }

    LineIcon(int[] ops, int color) {
        this.ops = ops;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(color);
    }

    @Override
    public void draw(Canvas c) {
        if (ops == null) return;
        float s = getBounds().width() / 240f; // ops are in tenths of the 24-unit grid
        paint.setStrokeWidth(16 * s);
        path.reset();
        for (int i = 0; i < ops.length; ) {
            switch (ops[i]) {
                case 0: path.moveTo(ops[i + 1] * s, ops[i + 2] * s); i += 3; break;
                case 1: path.lineTo(ops[i + 1] * s, ops[i + 2] * s); i += 3; break;
                case 2: path.close(); i += 1; break;
                case 3: path.addCircle(ops[i + 1] * s, ops[i + 2] * s, ops[i + 3] * s, Path.Direction.CW); i += 4; break;
                default:
                    float cx = ops[i + 1] * s, cy = ops[i + 2] * s, r = ops[i + 3] * s;
                    arc.set(cx - r, cy - r, cx + r, cy + r);
                    path.addArc(arc, ops[i + 4], ops[i + 5]);
                    i += 6;
            }
        }
        c.save();
        c.translate(getBounds().left, getBounds().top);
        c.drawPath(path, paint);
        c.restore();
    }

    @Override public void setAlpha(int a) { paint.setAlpha(a); }
    @Override public void setColorFilter(ColorFilter cf) { paint.setColorFilter(cf); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    @Override public int getIntrinsicWidth() { return 44; }
    @Override public int getIntrinsicHeight() { return 44; }
}
