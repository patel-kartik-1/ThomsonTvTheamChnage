package com.kartik.tvhome;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

// Shared look. Everything is laid out on a 960 x 540 grid and scaled to the TV's real size.
final class Ui {
    static final int ACCENT = 0xFFFFA62B;      // saffron, from the wallpaper's robe
    static final int GREEN = 0xFF7BE495, RED = 0xFFFF8A80;
    static final int WHITE = 0xFFFFFFFF, DIM = 0xB8FFFFFF, FAINT = 0x80FFFFFF;
    static final int CARD = 0x9E101012, CARD_FOCUS = 0xD9262628, LINE = 0x2EFFFFFF;
    static final Typeface REGULAR = Typeface.create("sans-serif", Typeface.NORMAL);
    static final Typeface MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    static final Typeface BOLD = Typeface.create("sans-serif", Typeface.BOLD);

    private Ui() {}

    static int px(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().widthPixels / 960f);
    }

    static TextView text(Context c, float size, int color, Typeface tf) {
        TextView t = new TextView(c);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(c, size));
        t.setTextColor(color);
        t.setTypeface(tf);
        t.setIncludeFontPadding(false);
        t.setHyphenationFrequency(android.text.Layout.HYPHENATION_FREQUENCY_NONE); // never "Swami-narayan"
        return t;
    }

    static TextView line(Context c, float size, int color, Typeface tf) {
        TextView t = text(c, size, color, tf);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    static GradientDrawable round(Context c, int fill, int stroke, float strokeW, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(px(c, radius));
        if (strokeW > 0) g.setStroke(px(c, strokeW), stroke);
        return g;
    }

    static GradientDrawable card(Context c, float radius) { return round(c, CARD, LINE, 1, radius); }

    static GradientDrawable cardFocus(Context c, float radius) { return round(c, CARD_FOCUS, ACCENT, 2, radius); }

    // Makes a view a remote target: OK clicks it, and focus lifts it with a saffron edge.
    static void focusable(View v, Drawable normal, Drawable focused, float lift, View.OnClickListener click) {
        v.setFocusable(true);
        v.setClickable(true);
        v.setBackground(normal);
        v.setOnClickListener(click);
        v.setOnFocusChangeListener((view, has) -> {
            view.setBackground(has ? focused : normal);
            view.setTranslationZ(has ? 8 : 0);
            view.animate().scaleX(has ? lift : 1f).scaleY(has ? lift : 1f).setDuration(140).start();
        });
    }

    // BAPS colours: maroon into saffron with a thin gold edge, for the YouTube channel buttons.
    static GradientDrawable baps(Context c, boolean focus, float radius) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[] {0xFF7E1616, 0xFFB8380F, 0xFFE07A1C});
        g.setCornerRadius(px(c, radius));
        g.setStroke(px(c, focus ? 2 : 1), focus ? WHITE : 0xFFE8B04A);
        return g;
    }

    // The YouTube logo: a red rounded box with a white play triangle.
    static Drawable youtube() {
        return new Drawable() {
            private final android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            private final android.graphics.Path tri = new android.graphics.Path();
            @Override public void draw(android.graphics.Canvas cv) {
                android.graphics.Rect b = getBounds();
                float w = b.width(), h = w * 0.7f, top = b.top + (b.height() - h) / 2;
                p.setColor(0xFFFF0033);
                cv.drawRoundRect(new android.graphics.RectF(b.left, top, b.left + w, top + h), h * 0.28f, h * 0.28f, p);
                tri.reset();
                tri.moveTo(b.left + w * 0.40f, top + h * 0.28f);
                tri.lineTo(b.left + w * 0.40f, top + h * 0.72f);
                tri.lineTo(b.left + w * 0.66f, top + h * 0.50f);
                tri.close();
                p.setColor(WHITE);
                cv.drawPath(tri, p);
            }
            @Override public void setAlpha(int a) { p.setAlpha(a); }
            @Override public void setColorFilter(android.graphics.ColorFilter f) { p.setColorFilter(f); }
            @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
        };
    }

    static FrameLayout.LayoutParams at(Context c, float x, float y, float w, float h) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w < 0 ? (int) w : px(c, w), h < 0 ? (int) h : px(c, h));
        lp.leftMargin = px(c, x);
        lp.topMargin = px(c, y);
        return lp;
    }
}
