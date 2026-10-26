package com.kartik.tvhome;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Calendar;
import java.util.Locale;

// Sandhya Aarti: its time (set in Settings with the remote), the home screen card's text, and the
// daily "Play now?" popup. The popup is an exact alarm, set again after every popup, restart and
// clock change.
final class Aarti {
    static final String PREFS = "config";
    private static final String KEY_MINUTES = "aarti_minutes", KEY_POPUP = "aarti_popup";
    static final int DEFAULT_MINUTES = 18 * 60 + 30; // 6:30 PM at the family's mandir
    private static final int NOW_MINUTES = 30;       // counts as "now" for the half hour after it starts
    private static final String ACTION = "com.kartik.tvhome.AARTI";

    private Aarti() {}

    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences(PREFS, 0); }

    static int minutes(Context c) { return prefs(c).getInt(KEY_MINUTES, DEFAULT_MINUTES); }

    static void setMinutes(Context c, int m) {
        prefs(c).edit().putInt(KEY_MINUTES, ((m % 1440) + 1440) % 1440).apply();
        schedule(c);
    }

    static boolean popupOn(Context c) { return prefs(c).getBoolean(KEY_POPUP, true); }

    static void setPopupOn(Context c, boolean on) {
        prefs(c).edit().putBoolean(KEY_POPUP, on).apply();
        schedule(c);
    }

    static String clock(int m) {
        int h = m / 60 % 12;
        return String.format(Locale.ROOT, "%d:%02d %s", h == 0 ? 12 : h, m % 60, m < 720 ? "AM" : "PM");
    }

    // "in 25 min", "in 2 h 5 min", "Now 🙏" or "Tomorrow".
    static String status(Context c) {
        Calendar now = Calendar.getInstance();
        int diff = minutes(c) - (now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE));
        if (diff > 0) return diff >= 60 ? "in " + diff / 60 + " h" + (diff % 60 > 0 ? " " + diff % 60 + " min" : "")
                : "in " + diff + " min";
        return -diff < NOW_MINUTES ? "Now 🙏" : "Tomorrow";
    }

    static void schedule(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Intent i = new Intent(c, Alarm.class).setAction(ACTION);
        PendingIntent pi = PendingIntent.getBroadcast(c, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.cancel(pi);
        if (!popupOn(c)) return;
        Calendar at = Calendar.getInstance();
        at.set(Calendar.HOUR_OF_DAY, minutes(c) / 60);
        at.set(Calendar.MINUTE, minutes(c) % 60);
        at.set(Calendar.SECOND, 0);
        at.set(Calendar.MILLISECOND, 0);
        if (at.getTimeInMillis() <= System.currentTimeMillis() + 5000) at.add(Calendar.DAY_OF_MONTH, 1);
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.getTimeInMillis(), pi);
        } catch (SecurityException e) { // Android 12+ without exact alarms allowed: close enough
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.getTimeInMillis(), pi);
        }
    }

    // The daily alarm: show the popup, then set tomorrow's.
    public static class Alarm extends BroadcastReceiver {
        @Override public void onReceive(Context c, Intent i) {
            if (popupOn(c)) try {
                c.startActivity(new Intent(c, Popup.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception ignored) {}
            schedule(c);
        }
    }

    // Restarts, app updates and clock changes drop alarms: set it again.
    public static class Boot extends BroadcastReceiver {
        @Override public void onReceive(Context c, Intent i) { schedule(c); }
    }

    // A small card in the corner, over whatever is on screen: Play now, or Not now.
    public static class Popup extends Activity {
        private final Runnable close = this::finish;
        private FrameLayout root;

        @Override protected void onCreate(Bundle state) {
            super.onCreate(state);
            root = new FrameLayout(this);
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(Ui.round(this, 0xF0141416, Ui.ACCENT, 2, 16));
            int pad = Ui.px(this, 18);
            card.setPadding(pad, pad, pad, pad);

            LinearLayout head = new LinearLayout(this);
            head.setGravity(Gravity.CENTER_VERTICAL);
            ImageView flame = new ImageView(this);
            flame.setImageDrawable(new LineIcon(LineIcon.FLAME, Ui.ACCENT));
            head.addView(flame, new LinearLayout.LayoutParams(Ui.px(this, 34), Ui.px(this, 34)));
            LinearLayout words = new LinearLayout(this);
            words.setOrientation(LinearLayout.VERTICAL);
            words.setPadding(Ui.px(this, 12), 0, 0, 0);
            TextView title = Ui.line(this, 19, Ui.WHITE, Ui.MEDIUM);
            title.setText("Sandhya Aarti · " + clock(minutes(this)));
            TextView sub = Ui.line(this, 13, Ui.DIM, Ui.REGULAR);
            sub.setText("Jay Swaminarayan 🙏  It's aarti time");
            sub.setPadding(0, Ui.px(this, 6), 0, 0);
            words.addView(title);
            words.addView(sub);
            head.addView(words);
            card.addView(head);

            LinearLayout buttons = new LinearLayout(this);
            buttons.setPadding(0, Ui.px(this, 16), 0, 0);
            View play = button("▶  Play now", v -> {
                if (!Media.play(this, Media.sandhya()))
                    startActivity(new Intent(this, MainActivity.class).putExtra("tab", "Aarti")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                finish();
            });
            buttons.addView(play, new LinearLayout.LayoutParams(Ui.px(this, 150), Ui.px(this, 40)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.px(this, 120), Ui.px(this, 40));
            lp.leftMargin = Ui.px(this, 12);
            buttons.addView(button("Not now", v -> finish()), lp);
            card.addView(buttons);

            FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(Ui.px(this, 360), -2,
                    Gravity.BOTTOM | Gravity.END);
            cp.rightMargin = cp.bottomMargin = Ui.px(this, 36);
            root.addView(card, cp);
            setContentView(root);
            play.requestFocus();
            root.postDelayed(close, 3 * 60 * 1000); // goes away by itself after three minutes
        }

        private TextView button(String label, View.OnClickListener click) {
            TextView b = Ui.line(this, 14, Ui.WHITE, Ui.MEDIUM);
            b.setText(label);
            b.setGravity(Gravity.CENTER);
            Ui.focusable(b, Ui.round(this, 0x26FFFFFF, 0, 0, 20), Ui.round(this, 0xFFE08A12, Ui.WHITE, 2, 20), 1.05f, click);
            return b;
        }

        @Override protected void onDestroy() {
            root.removeCallbacks(close);
            super.onDestroy();
        }
    }
}
