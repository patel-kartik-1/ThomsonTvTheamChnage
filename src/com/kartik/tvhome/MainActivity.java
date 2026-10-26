package com.kartik.tvhome;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.StrictMode;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// The home screen: Jay Swaminarayan, the ring clock, Wi-Fi / Bluetooth / TV settings / Settings buttons, three cards (Sandhya Aarti,
// TV Health, Wi-Fi), the apps row, Recommended Videos and a menu (Home, Aarti, BAPS Kirtan and BAPS
// Channel on YouTube, Inputs, Apps). Laid out on a 960 x 540 grid. Everything above the menu stays left of
// x = 570, so the face and hands in the wallpaper photo are never covered.
public class MainActivity extends Activity {
    // The apps row. Change it with:  am start -n com.kartik.tvhome/.MainActivity --es apps "pkg1,pkg2"
    private static final String[] HOME_APPS = {"com.google.android.youtube.tv", "in.startv.hotstar",
            "org.videolan.vlc", "com.mobisystems.fileman", "com.android.vending"};
    private static final String HOME = "Home", LIVE = "Inputs", APPS = "Apps", SETTINGS = "Settings";
    private static final String KIRTAN = "BAPS Kirtan", BAPS = "BAPS Channel";
    // Pages this screen can show. Settings opens from the gear at the top right.
    private static final List<String> PAGES = java.util.Arrays.asList(HOME, "Aarti", LIVE, APPS, SETTINGS);
    // The menu bar. BAPS Kirtan and BAPS Channel open those YouTube channels instead of a page.
    private static final String[] MENU = {HOME, "Aarti", KIRTAN, BAPS, LIVE, APPS};
    private static final int[][] MENU_ICONS = {LineIcon.HOME, LineIcon.FLAME, null, null, LineIcon.TV, LineIcon.GRID};
    private static final float X = 40;           // left margin
    private static final float PANEL_W = 498;    // width of everything left of the hands in the photo (ends at x = 538)
    private static final int MAX_RECOMMENDED = 5;

    private FrameLayout homeContent, tabPanel;
    private LinearLayout appsRow, recRow, tabGrid;
    private TextView tabTitle, tabHint;
    private ScrollView tabScroll;
    private final List<LinearLayout> menu = new ArrayList<>();
    private String tab = HOME;
    private TextView aartiStatus, aartiWhen, wifiName, wifiState;
    private ImageView wifiCardIcon, wifiTopIcon;
    private String videoSig = "";
    private String shownWallpaper = "";

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<String, Bitmap> thumbs = new HashMap<>();
    private final Map<String, String> lengths = new HashMap<>();

    private final BroadcastReceiver packagesChanged = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            buildAppsRow();
            if (APPS.equals(tab)) buildTab(false);
        }
    };
    private final BroadcastReceiver minuteTick = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { refreshAarti(); refreshHealth(); }
    };
    private final ConnectivityManager.NetworkCallback networkChanged = new ConnectivityManager.NetworkCallback() {
        @Override public void onAvailable(Network n) { main.post(MainActivity.this::refreshNetwork); }
        @Override public void onLost(Network n) { main.post(MainActivity.this::refreshNetwork); }
        @Override public void onCapabilitiesChanged(Network n, NetworkCapabilities c) {
            main.post(MainActivity.this::refreshNetwork);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        // Videos are handed to VLC as file:// paths, which Android 7+ blocks unless this is relaxed.
        StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder().build());
        saveApps(getIntent());
        applyWallpaper();

        FrameLayout root = new FrameLayout(this);
        root.setClipChildren(false);
        addScrims(root);
        addTopBar(root);

        homeContent = layer();
        addCards(homeContent);
        appsRow = row();
        homeContent.addView(appsRow, Ui.at(this, X, 242, -2, -2));
        TextView rec = Ui.line(this, 15, Ui.WHITE, Ui.MEDIUM);
        rec.setText("Recommended Videos");
        homeContent.addView(rec, Ui.at(this, X, 322, -2, -2));
        recRow = row();
        homeContent.addView(recRow, Ui.at(this, X, 344, -2, -2));
        root.addView(homeContent, new FrameLayout.LayoutParams(-1, -1));

        tabPanel = layer();
        tabTitle = Ui.line(this, 22, Ui.WHITE, Ui.MEDIUM);
        tabPanel.addView(tabTitle, Ui.at(this, X, 150, PANEL_W, -2));
        tabHint = Ui.line(this, 12, Ui.DIM, Ui.REGULAR);
        tabPanel.addView(tabHint, Ui.at(this, X, 180, PANEL_W, -2));
        tabScroll = new ScrollView(this);
        tabScroll.setVerticalScrollBarEnabled(false);
        tabScroll.setOverScrollMode(View.OVER_SCROLL_NEVER); // clips, so long pages never run into the menu
        tabGrid = new LinearLayout(this);
        tabGrid.setOrientation(LinearLayout.VERTICAL);
        tabGrid.setClipChildren(false);
        tabGrid.setClipToPadding(false); // a focused tile grows into the padding
        int pad = Ui.px(this, 9); // room for a focused tile's lift and edge
        tabGrid.setPadding(pad, pad, pad, pad);
        tabScroll.addView(tabGrid);
        tabPanel.addView(tabScroll, Ui.at(this, X - 9, 193, PANEL_W + 18, 274));
        tabPanel.setVisibility(View.GONE);
        root.addView(tabPanel, new FrameLayout.LayoutParams(-1, -1));

        addMenu(root);
        setContentView(root);

        buildAppsRow();
        refreshVideos(true);
        Aarti.schedule(this);

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        if (android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(packagesChanged, f, Context.RECEIVER_EXPORTED);
        else registerReceiver(packagesChanged, f);
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) try { cm.registerDefaultNetworkCallback(networkChanged); } catch (Exception ignored) {}

        String start = getIntent().getStringExtra("tab");
        showTab(start != null ? start : HOME, true);
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(packagesChanged);
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) try { cm.unregisterNetworkCallback(networkChanged); } catch (Exception ignored) {}
        worker.shutdownNow();
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter f = new IntentFilter(Intent.ACTION_TIME_TICK);
        f.addAction(Intent.ACTION_TIME_CHANGED);
        f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(minuteTick, f);
        refreshAarti();
        refreshNetwork();
        refreshVideos(false);
        if (!wallpaperKey().equals(shownWallpaper)) applyWallpaper(); // "Change daily" moves on overnight
        refreshHealth();
        if (getCurrentFocus() == null) focusFirst();
    }

    @Override
    protected void onPause() {
        unregisterReceiver(minuteTick);
        super.onPause();
    }

    // Home goes back to the Home page (or the page named with --es tab).
    // The remote's All Apps button can't come here: Android TV 9 sends it only to the TV's built-in home screen.
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (saveApps(intent)) buildAppsRow();
        String start = intent.getStringExtra("tab");
        showTab(start != null ? start : HOME, true);
    }

    @Override
    public void onBackPressed() {
        if (!HOME.equals(tab)) showTab(HOME, true);
    }
    // --- Layout pieces ---

    private FrameLayout layer() {
        FrameLayout l = new FrameLayout(this);
        l.setClipChildren(false);
        return l;
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setClipChildren(false);
        return r;
    }

    // Darkens the left side and the bottom a little, so white text stays easy to read on the photo.
    private void addScrims(FrameLayout root) {
        View left = new View(this);
        left.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[] {0x99000000, 0x00000000}));
        root.addView(left, Ui.at(this, 0, 0, 640, 540));
        View bottom = new View(this);
        bottom.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0x00000000, 0xB0000000, 0xE6000000}));
        root.addView(bottom, Ui.at(this, 0, 300, 960, 240));
    }

    private void addTopBar(FrameLayout root) {
        TextView jay = Ui.line(this, 16, Ui.ACCENT, Ui.MEDIUM);
        jay.setText("Jay Swaminarayan");
        jay.setLetterSpacing(0.06f);
        root.addView(jay, Ui.at(this, X, 20, -2, -2));
        root.addView(new ClockView(this), Ui.at(this, X - 6, 42, 360, 100));

        // Wi-Fi, Bluetooth and Settings, round, at the top right.
        wifiTopIcon = new ImageView(this);
        root.addView(circleButton(wifiTopIcon, v -> openWifi()), Ui.at(this, 720, 24, 44, 44));
        ImageView bt = new ImageView(this);
        bt.setImageDrawable(new LineIcon(LineIcon.BLUETOOTH));
        root.addView(circleButton(bt, v -> openBluetooth()), Ui.at(this, 772, 24, 44, 44));
        // The TV's own settings, then this home screen's settings (aarti time, wallpaper).
        ImageView tv = new ImageView(this);
        tv.setImageDrawable(new LineIcon(LineIcon.TV));
        root.addView(circleButton(tv, v -> openTvSettings()), Ui.at(this, 824, 24, 44, 44));
        ImageView gear = new ImageView(this);
        gear.setImageDrawable(new LineIcon(LineIcon.SETTINGS));
        root.addView(circleButton(gear, v -> showTab(SETTINGS, true)), Ui.at(this, 876, 24, 44, 44));
    }

    private View circleButton(ImageView icon, View.OnClickListener click) {
        FrameLayout b = new FrameLayout(this);
        int p = Ui.px(this, 11);
        icon.setPadding(p, p, p, p);
        b.addView(icon, new FrameLayout.LayoutParams(-1, -1));
        Ui.focusable(b, Ui.round(this, 0x99101012, Ui.LINE, 1, 22), Ui.round(this, 0xE6262628, Ui.ACCENT, 2, 22), 1.1f, click);
        return b;
    }

    // The three cards: Sandhya Aarti, Jay Swaminarayan, Wi-Fi.
    private void addCards(FrameLayout into) {
        ImageView flame = new ImageView(this);
        flame.setImageDrawable(new LineIcon(LineIcon.FLAME, Ui.ACCENT));
        LinearLayout aarti = card(flame);
        TextView title = Ui.line(this, 14, Ui.WHITE, Ui.MEDIUM);
        title.setText("Sandhya Aarti");
        aartiStatus = Ui.line(this, 13, Ui.ACCENT, Ui.MEDIUM);
        aartiWhen = Ui.line(this, 11, Ui.DIM, Ui.REGULAR);
        column(aarti).addView(title);
        column(aarti).addView(spaced(aartiStatus, 5));
        column(aarti).addView(spaced(aartiWhen, 4));
        Ui.focusable(aarti, Ui.card(this, 14), Ui.cardFocus(this, 14), 1.04f, v -> {
            if (!Media.play(this, Media.sandhya())) showTab("Aarti", true);
        });
        into.addView(aarti, Ui.at(this, X, 150, 160, 74));

        into.addView(healthCard(), Ui.at(this, X + 169, 150, 160, 74));

        wifiCardIcon = new ImageView(this);
        LinearLayout wifi = card(wifiCardIcon);
        // Network names run long (a phone hotspot name, say), so they get two lines.
        wifiName = Ui.text(this, 13, Ui.WHITE, Ui.MEDIUM);
        wifiName.setMaxLines(2);
        wifiName.setEllipsize(TextUtils.TruncateAt.END);
        wifiName.setLineSpacing(Ui.px(this, 2), 1f);
        wifiState = Ui.line(this, 12, Ui.GREEN, Ui.MEDIUM);
        column(wifi).addView(wifiName);
        column(wifi).addView(spaced(wifiState, 5));
        Ui.focusable(wifi, Ui.card(this, 14), Ui.cardFocus(this, 14), 1.04f, v -> openWifi());
        into.addView(wifi, Ui.at(this, X + 338, 150, 160, 74));
    }

    // TV health: free memory, free storage and whether the Bluetooth remote is connected. OK clears
    // apps waiting in the background (only apps people installed, never casting or AirPlay), to free memory.
    private static final List<String> NEVER_CLEAN = java.util.Arrays.asList("com.ionitech.airscreen",
            "com.google.android.apps.mediashell", "com.google.android.tv.remote.service");
    private TextView healthMem, healthMore;
    private ImageView healthIcon;
    private boolean cleaning;

    private View healthCard() {
        healthIcon = new ImageView(this);
        LinearLayout c = card(healthIcon);
        TextView title = Ui.line(this, 14, Ui.WHITE, Ui.MEDIUM);
        title.setText("TV Health");
        healthMem = Ui.line(this, 12.5f, Ui.GREEN, Ui.MEDIUM);
        healthMore = Ui.line(this, 10, Ui.DIM, Ui.REGULAR);
        column(c).addView(title);
        column(c).addView(spaced(healthMem, 5));
        column(c).addView(spaced(healthMore, 4));
        Ui.focusable(c, Ui.card(this, 14), Ui.cardFocus(this, 14), 1.04f, v -> cleanUp());
        return c;
    }

    private long freeMemoryMb() {
        android.app.ActivityManager am = (android.app.ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        android.app.ActivityManager.MemoryInfo mi = new android.app.ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        return mi.availMem / (1024 * 1024);
    }

    // The remote shows up as an input device only while its Bluetooth link is up.
    private boolean remoteConnected() {
        android.hardware.input.InputManager im = (android.hardware.input.InputManager) getSystemService(Context.INPUT_SERVICE);
        for (int id : im.getInputDeviceIds()) {
            android.view.InputDevice d = im.getInputDevice(id);
            if (d != null && !d.isVirtual() && d.getName() != null
                    && d.getName().toLowerCase(Locale.ROOT).contains("remote")) return true;
        }
        return false;
    }

    private void refreshHealth() {
        if (healthMem == null || cleaning) return;
        long mb = freeMemoryMb();
        int color = mb >= 250 ? Ui.GREEN : mb >= 150 ? Ui.ACCENT : Ui.RED;
        healthMem.setText(mb + " MB free");
        healthMem.setTextColor(color);
        healthIcon.setImageDrawable(new LineIcon(LineIcon.PULSE, color));
        android.os.StatFs st = new android.os.StatFs(android.os.Environment.getExternalStorageDirectory().getPath());
        healthMore.setText(String.format(Locale.ROOT, "%.1f GB \u00B7 Remote %s",
                st.getAvailableBytes() / 1073741824f, remoteConnected() ? "\u2713" : "off"));
    }

    private void cleanUp() {
        if (cleaning) return;
        cleaning = true;
        final long before = freeMemoryMb();
        healthMem.setText("Cleaning\u2026");
        healthMem.setTextColor(Ui.ACCENT);
        android.app.ActivityManager am = (android.app.ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (android.content.pm.ApplicationInfo ai : getPackageManager().getInstalledApplications(0)) {
            boolean installed = (ai.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0
                    || "com.google.android.youtube.tv".equals(ai.packageName);
            if (installed && !NEVER_CLEAN.contains(ai.packageName) && !ai.packageName.equals(getPackageName()))
                am.killBackgroundProcesses(ai.packageName);
        }
        main.postDelayed(() -> {
            long freed = freeMemoryMb() - before;
            healthMem.setText(freed > 5 ? "Freed " + freed + " MB \u2713" : "Already clean \u2713");
            healthMem.setTextColor(Ui.GREEN);
            main.postDelayed(() -> { cleaning = false; refreshHealth(); }, 4000);
        }, 1500);
    }

    private LinearLayout card(View icon) {
        LinearLayout c = new LinearLayout(this);
        c.setGravity(Gravity.CENTER_VERTICAL);
        int p = Ui.px(this, 12);
        c.setPadding(p, 0, p, 0);
        c.addView(icon, new LinearLayout.LayoutParams(Ui.px(this, 30), Ui.px(this, 30)));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        lp.leftMargin = Ui.px(this, 10);
        c.addView(col, lp);
        return c;
    }

    private static LinearLayout column(LinearLayout card) { return (LinearLayout) card.getChildAt(1); }

    private TextView spaced(TextView t, float top) {
        t.setPadding(0, Ui.px(this, top), 0, 0);
        return t;
    }

    private void addMenu(FrameLayout root) {
        LinearLayout bar = row();
        float w = (880 - 5 * 8) / 6f;
        for (int i = 0; i < MENU.length; i++) {
            final String name = MENU[i];
            final boolean link = MENU_ICONS[i] == null;
            LinearLayout b = new LinearLayout(this);
            b.setGravity(Gravity.CENTER);
            ImageView icon = new ImageView(this);
            icon.setImageDrawable(link ? Ui.youtube() : new LineIcon(MENU_ICONS[i]));
            b.addView(icon, new LinearLayout.LayoutParams(Ui.px(this, link ? 22 : 18), Ui.px(this, 18)));
            TextView label = Ui.line(this, 13, Ui.WHITE, Ui.MEDIUM);
            label.setText(name);
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-2, -2);
            tp.leftMargin = Ui.px(this, 7);
            b.addView(label, tp);
            b.setFocusable(true);
            b.setClickable(true);
            b.setOnClickListener(v -> {
                if (KIRTAN.equals(name)) Media.openYouTube(this, Media.KIRTAN_CHANNEL[1]);
                else if (BAPS.equals(name)) Media.openYouTube(this, Media.BAPS_CHANNEL[1]);
                else if ("Aarti".equals(name) && Media.play(this, Media.sandhya())) { /* plays the aarti video */ }
                else showTab(name, true);
            });
            b.setOnFocusChangeListener((v, has) -> {
                styleMenu((LinearLayout) v, name);
                v.setTranslationZ(has ? 8 : 0);
                v.animate().scaleX(has ? 1.06f : 1f).scaleY(has ? 1.06f : 1f).setDuration(140).start();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.px(this, w), Ui.px(this, 40));
            if (i > 0) lp.leftMargin = Ui.px(this, 8);
            bar.addView(b, lp);
            menu.add(b);
        }
        root.addView(bar, Ui.at(this, X, 474, -2, -2));
    }

    private void styleMenu(LinearLayout b, String name) {
        boolean on = name.equals(tab), focus = b.hasFocus();
        if (KIRTAN.equals(name) || BAPS.equals(name)) {
            b.setBackground(Ui.baps(this, focus, 12));
            return;
        }
        int fill = on ? 0x59FFA62B : focus ? Ui.CARD_FOCUS : Ui.CARD;
        b.setBackground(Ui.round(this, fill, focus ? Ui.WHITE : on ? Ui.ACCENT : Ui.LINE, focus ? 2 : 1, 12));
    }

    // --- Pages ---

    private void showTab(String name, boolean focus) {
        if ("Live TV".equals(name)) name = LIVE; // the old name, still accepted with --es tab
        if (!PAGES.contains(name)) name = HOME;
        tab = name;
        boolean home = HOME.equals(name);
        homeContent.setVisibility(home ? View.VISIBLE : View.GONE);
        tabPanel.setVisibility(home ? View.GONE : View.VISIBLE);
        if (!home) buildTab(true);
        for (int i = 0; i < menu.size(); i++) styleMenu(menu.get(i), MENU[i]);
        if (focus) focusFirst();
    }

    private void focusFirst() {
        View first = HOME.equals(tab) ? firstFocusable(appsRow) : firstFocusable(tabGrid);
        if (first == null) first = menu.get(Math.max(0, java.util.Arrays.asList(MENU).indexOf(tab)));
        first.requestFocus();
    }

    private static View firstFocusable(View v) {
        if (v.getVisibility() != View.VISIBLE) return null;
        if (v.isFocusable()) return v;
        if (v instanceof LinearLayout || v instanceof FrameLayout) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                View f = firstFocusable(g.getChildAt(i));
                if (f != null) return f;
            }
        }
        return null;
    }

    private void buildTab(boolean scrollTop) {
        tabGrid.removeAllViews();
        List<View> items = new ArrayList<>();
        int cols = 3;
        switch (tab) {
            case "Aarti": {
                List<File> videos = Media.videos(tab);
                for (File f : videos) items.add(videoTile(f, 158, 89));
                String[] channel = Media.KIRTAN_CHANNEL;
                items.add(channelTile(channel));
                tabTitle.setText(tab);
                tabHint.setText(videos.isEmpty()
                        ? "No videos yet: copy them into the BAPS/" + tab + " folder (USB stick → File Commander)"
                        : videos.size() + (videos.size() == 1 ? " video" : " videos") + " on this TV  ·  "
                        + channel[0] + " on YouTube");
                break;
            }
            case LIVE: {
                for (TvInputInfo in : inputs()) items.add(inputTile(in));
                tabTitle.setText("Inputs");
                tabHint.setText("HDMI, AV and TV channels");
                break;
            }
            case APPS: {
                cols = 5;
                PackageManager pm = getPackageManager();
                for (ResolveInfo ri : allApps(pm)) {
                    final String pkg = ri.activityInfo.packageName;
                    items.add(appColumn(ri.loadLabel(pm).toString(), banner(pm, pkg), 90, 51, v -> launch(pkg), pkg));
                }
                tabTitle.setText("All Apps");
                tabHint.setText(items.size() + " apps  ·  press Menu on an app for App info");
                break;
            }
            case SETTINGS: {
                cols = 1;
                addSettings(items);
                tabTitle.setText("Settings");
                tabHint.setText("This home screen's settings, and the TV's");
                break;
            }
        }
        LinearLayout line = null;
        for (int i = 0; i < items.size(); i++) {
            if (i % cols == 0) {
                line = row();
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
                if (i > 0) lp.topMargin = Ui.px(this, cols == 1 ? 7 : 12);
                tabGrid.addView(line, lp);
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            if (i % cols > 0) lp.leftMargin = Ui.px(this, cols == 5 ? 8 : 12);
            line.addView(items.get(i), lp);
        }
        if (scrollTop) tabScroll.scrollTo(0, 0);
    }

    // --- Apps ---

    // Also shows the aarti popup on demand, to try it:  am start -n com.kartik.tvhome/.MainActivity --ez test_popup true
    private boolean saveApps(Intent intent) {
        if (intent != null && intent.getBooleanExtra("test_popup", false)) {
            intent.removeExtra("test_popup");
            main.postDelayed(() -> startActivity(new Intent(this, Aarti.Popup.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)), 500);
        }
        String apps = intent == null ? null : intent.getStringExtra("apps");
        if (apps == null) return false;
        SharedPreferences.Editor e = getSharedPreferences(Aarti.PREFS, 0).edit();
        if (apps.trim().isEmpty() || apps.trim().equalsIgnoreCase("default")) e.remove("apps");
        else e.putString("apps", apps.trim());
        e.apply();
        return true;
    }

    private void buildAppsRow() {
        appsRow.removeAllViews();
        PackageManager pm = getPackageManager();
        String saved = getSharedPreferences(Aarti.PREFS, 0).getString("apps", null);
        String[] wanted = saved != null ? saved.split("[,\\s]+") : HOME_APPS;
        List<String> shown = new ArrayList<>();
        for (String p : wanted) if (!shown.contains(p) && launchIntent(pm, p) != null) shown.add(p);
        for (final String p : shown.subList(0, Math.min(shown.size(), 5))) {
            String label;
            try { label = pm.getApplicationLabel(pm.getApplicationInfo(p, 0)).toString(); }
            catch (Exception e) { label = p; }
            if ("com.android.vending".equals(p)) label = "Play Store";
            appsRow.addView(appColumn(label, banner(pm, p), 76, 43, v -> launch(p), p), gap(appsRow, 8));
        }
        appsRow.addView(appColumn("All Apps", null, 76, 43, v -> showTab(APPS, true), null), gap(appsRow, 8));
    }

    private LinearLayout.LayoutParams gap(LinearLayout r, float left) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        if (r.getChildCount() > 0) lp.leftMargin = Ui.px(this, left);
        return lp;
    }

    // A tile with the app's TV banner (or its icon, or the All Apps grid) and its name underneath.
    private View appColumn(String label, Drawable banner, float w, float h, View.OnClickListener click, final String pkg) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setClipChildren(false);
        FrameLayout tile = new FrameLayout(this);
        int pad = Ui.px(this, 2);
        tile.setPadding(pad, pad, pad, pad);
        ImageView img = new ImageView(this);
        int iw = Ui.px(this, w) - 2 * pad, ih = Ui.px(this, h) - 2 * pad;
        if (banner != null) {
            img.setImageBitmap(rounded(render(banner, iw, ih), iw, ih, Ui.px(this, 8)));
        } else if (pkg != null) {
            try {
                Drawable icon = getPackageManager().getApplicationIcon(pkg);
                img.setImageBitmap(render(icon, ih * 3 / 4, ih * 3 / 4));
                img.setScaleType(ImageView.ScaleType.CENTER);
            } catch (Exception ignored) {}
            img.setBackground(Ui.round(this, 0xCC1C1C1F, 0, 0, 8));
        } else {
            img.setImageDrawable(new LineIcon(LineIcon.GRID));
            int ip = (ih - Ui.px(this, 24)) / 2;
            img.setPadding((iw - Ui.px(this, 24)) / 2, ip, (iw - Ui.px(this, 24)) / 2, ip);
            img.setBackground(Ui.round(this, 0xCC1C1C1F, 0, 0, 8));
        }
        tile.addView(img, new FrameLayout.LayoutParams(iw, ih));
        Ui.focusable(tile, Ui.round(this, 0x00000000, 0, 0, 10), Ui.round(this, 0x00000000, Ui.ACCENT, 2, 10), 1.08f, click);
        if (pkg != null) tile.setOnKeyListener((v, code, ev) -> {
            if (code != KeyEvent.KEYCODE_MENU || ev.getAction() != KeyEvent.ACTION_UP) return code == KeyEvent.KEYCODE_MENU;
            try {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception ignored) {}
            return true;
        });
        col.addView(tile, new LinearLayout.LayoutParams(Ui.px(this, w), Ui.px(this, h)));
        TextView name = Ui.line(this, 11, Ui.DIM, Ui.REGULAR);
        name.setText(label);
        name.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.px(this, w), -2);
        lp.topMargin = Ui.px(this, 6);
        col.addView(name, lp);
        return col;
    }

    private static Drawable banner(PackageManager pm, String pkg) {
        Drawable d = null;
        try {
            Intent li = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (li != null) d = pm.getActivityBanner(li);
            if (d == null) d = pm.getApplicationBanner(pkg);
        } catch (Exception ignored) {}
        return d;
    }

    private List<ResolveInfo> allApps(PackageManager pm) {
        Map<String, ResolveInfo> byPkg = new LinkedHashMap<>();
        for (String cat : new String[] {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER})
            for (ResolveInfo ri : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(cat), 0)) {
                String p = ri.activityInfo.packageName;
                if (!p.equals(getPackageName()) && !byPkg.containsKey(p)) byPkg.put(p, ri);
            }
        List<ResolveInfo> out = new ArrayList<>(byPkg.values());
        Map<ResolveInfo, String> labels = new HashMap<>();
        for (ResolveInfo ri : out) labels.put(ri, ri.loadLabel(pm).toString().toLowerCase(Locale.ROOT));
        Collections.sort(out, (a, b) -> labels.get(a).compareTo(labels.get(b)));
        return out;
    }

    private static Intent launchIntent(PackageManager pm, String pkg) {
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        return i != null ? i : pm.getLaunchIntentForPackage(pkg);
    }

    private void launch(String pkg) {
        Intent i = launchIntent(getPackageManager(), pkg);
        if (i != null) try { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Exception ignored) {}
    }

    // --- Videos ---

    // Rebuilds the video rows only when files in the BAPS folders were added, removed or changed.
    private void refreshVideos(boolean force) {
        List<File> all = Media.allVideos();
        StringBuilder sig = new StringBuilder();
        for (File f : all) sig.append(f.getPath()).append(f.lastModified()).append('|');
        if (!force && sig.toString().equals(videoSig)) return;
        videoSig = sig.toString();
        recRow.removeAllViews();
        for (File f : all.subList(0, Math.min(all.size(), MAX_RECOMMENDED)))
            recRow.addView(videoTile(f, 164, 92), gap(recRow, 10));
        if (all.isEmpty()) {
            TextView none = Ui.text(this, 12, Ui.DIM, Ui.REGULAR);
            none.setText("Copy videos into the BAPS/Aarti, Kirtan or Katha folder on the TV");
            recRow.addView(none);
        }
        if (Media.FOLDERS[0].equals(tab) || Media.FOLDERS[1].equals(tab) || Media.FOLDERS[2].equals(tab)) buildTab(false);
    }

    private View videoTile(final File f, float w, float h) {
        FrameLayout t = new FrameLayout(this);
        int pad = Ui.px(this, 2);
        t.setPadding(pad, pad, pad, pad);
        t.setClipChildren(false);
        final int iw = Ui.px(this, w) - 2 * pad, ih = Ui.px(this, h) - 2 * pad;
        final ImageView img = new ImageView(this);
        img.setBackground(Ui.round(this, 0xFF1C1C1F, 0, 0, 8));
        t.addView(img, new FrameLayout.LayoutParams(iw, ih));
        View shade = new View(this);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] {0x00000000, 0xD9000000});
        float r = Ui.px(this, 8);
        g.setCornerRadii(new float[] {0, 0, 0, 0, r, r, r, r});
        shade.setBackground(g);
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(iw, ih / 2, Gravity.BOTTOM);
        t.addView(shade, sp);
        ImageView play = new ImageView(this);
        play.setImageDrawable(new LineIcon(LineIcon.PLAY));
        int pp = Ui.px(this, 8);
        play.setPadding(pp + Ui.px(this, 1), pp, pp - Ui.px(this, 1), pp);
        play.setBackground(Ui.round(this, 0x8C000000, 0x99FFFFFF, 1, 15));
        t.addView(play, new FrameLayout.LayoutParams(Ui.px(this, 30), Ui.px(this, 30), Gravity.CENTER));
        TextView title = Ui.line(this, 11.5f, Ui.WHITE, Ui.MEDIUM);
        title.setText(Media.title(f));
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(iw - Ui.px(this, 52), -2, Gravity.BOTTOM | Gravity.START);
        tp.leftMargin = tp.bottomMargin = Ui.px(this, 7);
        t.addView(title, tp);
        final TextView len = Ui.line(this, 10.5f, Ui.DIM, Ui.REGULAR);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END);
        lp.rightMargin = lp.bottomMargin = Ui.px(this, 7);
        t.addView(len, lp);
        Ui.focusable(t, Ui.round(this, 0x00000000, Ui.LINE, 1, 10), Ui.round(this, 0x00000000, Ui.ACCENT, 2, 10), 1.07f,
                v -> Media.play(this, f));

        final String key = f.getPath() + "@" + f.lastModified() + "@" + iw;
        Bitmap cached = thumbs.get(key);
        if (cached != null) {
            img.setImageBitmap(cached);
            len.setText(lengths.get(key));
        } else {
            worker.execute(() -> {
                Bitmap raw = Media.thumbnail(f, iw, ih);
                final Bitmap b = raw == null ? null : rounded(raw, iw, ih, Ui.px(this, 8));
                final String d = Media.duration(f);
                main.post(() -> {
                    if (b != null) { thumbs.put(key, b); img.setImageBitmap(b); }
                    lengths.put(key, d);
                    len.setText(d);
                });
            });
        }
        return t;
    }

    private View channelTile(final String[] channel) {
        FrameLayout t = new FrameLayout(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_VERTICAL);
        int p = Ui.px(this, 12);
        col.setPadding(p, 0, p, 0);
        ImageView yt = new ImageView(this);
        yt.setImageDrawable(Ui.youtube());
        col.addView(yt, new LinearLayout.LayoutParams(Ui.px(this, 30), Ui.px(this, 22)));
        TextView name = Ui.line(this, 13, Ui.WHITE, Ui.MEDIUM);
        name.setText(channel[0]);
        col.addView(spaced(name, 7));
        TextView sub = Ui.line(this, 11, 0xE6FFE2B0, Ui.REGULAR);
        sub.setText("Open on YouTube");
        col.addView(spaced(sub, 4));
        t.addView(col, new FrameLayout.LayoutParams(-1, -1));
        Ui.focusable(t, Ui.baps(this, false, 10), Ui.baps(this, true, 10), 1.07f,
                v -> Media.openYouTube(this, channel[1]));
        return wrap(t, 158, 89);
    }

    // A tile in a grid row needs its own size; wrapping keeps it when the row adds it.
    private View wrap(View v, float w, float h) {
        FrameLayout box = new FrameLayout(this);
        box.setClipChildren(false);
        box.addView(v, new FrameLayout.LayoutParams(Ui.px(this, w), Ui.px(this, h)));
        return box;
    }

    // --- Live TV ---

    // The TV's own inputs (HDMI, AV and the antenna tuner), not app channels like VLC's previews.
    private List<TvInputInfo> inputs() {
        List<TvInputInfo> out = new ArrayList<>();
        TvInputManager tim = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
        if (tim == null) return out;
        for (TvInputInfo in : tim.getTvInputList()) {
            if (in.getParentId() != null || in.isHidden(this)) continue;
            if (in.isPassthroughInput() || in.getId().contains("/HW")) out.add(in);
        }
        Collections.sort(out, (a, b) -> {
            int ta = a.getType() == TvInputInfo.TYPE_HDMI ? 0 : a.isPassthroughInput() ? 1 : 2;
            int tb = b.getType() == TvInputInfo.TYPE_HDMI ? 0 : b.isPassthroughInput() ? 1 : 2;
            return ta != tb ? ta - tb : a.getId().compareTo(b.getId());
        });
        return out;
    }

    private View inputTile(final TvInputInfo in) {
        FrameLayout t = new FrameLayout(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        ImageView icon = new ImageView(this);
        icon.setImageDrawable(new LineIcon(LineIcon.TV));
        col.addView(icon, new LinearLayout.LayoutParams(Ui.px(this, 30), Ui.px(this, 30)));
        TextView name = Ui.line(this, 14, Ui.WHITE, Ui.MEDIUM);
        CharSequence custom = in.loadCustomLabel(this), label = in.loadLabel(this);
        String text = custom != null && custom.length() > 0 ? custom.toString()
                : label != null && label.length() > 0 ? label.toString() : "TV";
        if (!in.isPassthroughInput()) text = "TV Channels"; // the antenna tuner; its own name is the chip maker's
        name.setText(text);
        name.setGravity(Gravity.CENTER);
        col.addView(spaced(name, 8), new LinearLayout.LayoutParams(-1, -2));
        t.addView(col, new FrameLayout.LayoutParams(-1, -1));
        Ui.focusable(t, Ui.card(this, 10), Ui.cardFocus(this, 10), 1.07f, v -> openInput(in));
        return wrap(t, 158, 89);
    }

    // HDMI and AV open straight into the Live TV app; the antenna opens Live TV on its last channel.
    private void openInput(TvInputInfo in) {
        List<Intent> tries = new ArrayList<>();
        if (in.isPassthroughInput()) {
            Intent view = new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(in.getId()));
            tries.add(new Intent(view).setClassName("com.android.tv", "com.android.tv.MainActivity"));
            tries.add(view);
        }
        Intent live = launchIntent(getPackageManager(), "com.android.tv");
        if (live != null) tries.add(live);
        for (Intent t : tries) {
            try {
                startActivity(t.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return;
            } catch (Exception ignored) {}
        }
    }

    // --- Settings ---

    private void addSettings(List<View> items) {
        final TextView[] time = new TextView[1];
        View t = settingRow("Sandhya Aarti time", Aarti.clock(Aarti.minutes(this)) + "   ◀ ▶", time, null);
        // Left and Right change the time by 5 minutes, on the row itself (the one the remote focuses).
        ((FrameLayout) t).getChildAt(0).setOnKeyListener((v, code, ev) -> {
            if (code != KeyEvent.KEYCODE_DPAD_LEFT && code != KeyEvent.KEYCODE_DPAD_RIGHT) return false;
            if (ev.getAction() == KeyEvent.ACTION_DOWN) {
                Aarti.setMinutes(this, Aarti.minutes(this) + (code == KeyEvent.KEYCODE_DPAD_RIGHT ? 5 : -5));
                time[0].setText(Aarti.clock(Aarti.minutes(this)) + "   ◀ ▶");
                refreshAarti();
            }
            return true;
        });
        items.add(t);
        final TextView[] popup = new TextView[1];
        items.add(settingRow("Aarti reminder popup", Aarti.popupOn(this) ? "On" : "Off", popup, v -> {
            Aarti.setPopupOn(this, !Aarti.popupOn(this));
            popup[0].setText(Aarti.popupOn(this) ? "On" : "Off");
        }));
        final TextView[] wall = new TextView[1];
        // OK steps through: the built-in photo, each photo in BAPS/Wallpapers, Change daily, Dark.
        items.add(settingRow("Wallpaper", wallpaperLabel(), wall, v -> {
            List<String> choices = new ArrayList<>();
            choices.add("photo");
            for (File f : Media.wallpapers()) choices.add(f.getName());
            if (choices.size() > 1) choices.add("daily");
            choices.add("dark");
            int i = choices.indexOf(getSharedPreferences(Aarti.PREFS, 0).getString("wallpaper", "photo"));
            getSharedPreferences(Aarti.PREFS, 0).edit().putString("wallpaper", choices.get((i + 1) % choices.size())).apply();
            applyWallpaper();
            wall[0].setText(wallpaperLabel());
        }));
        items.add(settingRow("Wi-Fi", "›", null, v -> openWifi()));
        items.add(settingRow("Bluetooth & remote", "›", null, v -> openBluetooth()));
        items.add(settingRow("TV settings", "›", null, v -> openTvSettings()));
    }

    private View settingRow(String label, String value, TextView[] valueOut, View.OnClickListener click) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        int p = Ui.px(this, 16);
        r.setPadding(p, 0, p, 0);
        TextView l = Ui.line(this, 15, Ui.WHITE, Ui.REGULAR);
        l.setText(label);
        r.addView(l, new LinearLayout.LayoutParams(0, -2, 1));
        TextView v = Ui.line(this, 14, Ui.ACCENT, Ui.MEDIUM);
        v.setText(value);
        r.addView(v);
        if (valueOut != null) valueOut[0] = v;
        Ui.focusable(r, Ui.card(this, 10), Ui.cardFocus(this, 10), 1.02f, click);
        if (click == null) r.setClickable(false);
        return wrap(r, PANEL_W - 4, 36);
    }

    // --- Wallpaper, status, system screens ---

    // The saved choice: "photo" (built in), a file name in BAPS/Wallpapers, "daily" or "dark".
    private String wallpaperChoice() {
        return getSharedPreferences(Aarti.PREFS, 0).getString("wallpaper", "photo");
    }

    private String wallpaperLabel() {
        String w = wallpaperChoice();
        return "photo".equals(w) ? "Darshan photo" : "daily".equals(w) ? "Change daily" : "dark".equals(w) ? "Dark"
                : Media.title(new File(w));
    }

    // What is on screen now; for "daily", today's pick from the built-in photo and the folder's photos.
    private String wallpaperKey() {
        String w = wallpaperChoice();
        if (!"daily".equals(w)) return w;
        List<File> files = Media.wallpapers();
        int day = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR);
        int i = day % (files.size() + 1);
        return i == 0 ? "photo" : files.get(i - 1).getName();
    }

    private void applyWallpaper() {
        String key = wallpaperKey();
        shownWallpaper = key;
        Bitmap b = null;
        if ("photo".equals(key)) {
            try (InputStream in = getAssets().open("darshan.jpg")) {
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inPreferredConfig = Bitmap.Config.RGB_565; // half the memory, no visible difference
                b = BitmapFactory.decodeStream(in, null, o);
            } catch (Exception ignored) {}
        } else if (!"dark".equals(key)) {
            b = Media.screenImage(new File(Media.WALLPAPERS, key), getResources().getDisplayMetrics().widthPixels,
                    getResources().getDisplayMetrics().heightPixels);
        }
        if (b != null) getWindow().setBackgroundDrawable(new BitmapDrawable(getResources(), b));
        else getWindow().setBackgroundDrawable(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0xFF1A1012, 0xFF0C0809, 0xFF050405}));
    }

    private void refreshAarti() {
        if (aartiStatus == null) return;
        String s = Aarti.status(this);
        aartiStatus.setText(s);
        aartiWhen.setText(("Tomorrow".equals(s) ? "at " : "Today · ") + Aarti.clock(Aarti.minutes(this)));
    }

    // The Wi-Fi card and button follow the connection: network name, Connected, signal bars.
    private void refreshNetwork() {
        if (wifiName == null) return;
        int bars = -1; // -1 offline, 0 Ethernet, 1 to 3 Wi-Fi
        String name = "Not connected";
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkCapabilities c = cm == null ? null : cm.getNetworkCapabilities(cm.getActiveNetwork());
            if (c != null && c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                WifiInfo wi = wm == null ? null : wm.getConnectionInfo();
                name = "Wi-Fi";
                bars = 3;
                if (wi != null) {
                    String ssid = wi.getSSID();
                    if (ssid != null && ssid.startsWith("\"") && ssid.endsWith("\"") && ssid.length() > 1)
                        ssid = ssid.substring(1, ssid.length() - 1);
                    if (ssid != null && !ssid.isEmpty() && !"<unknown ssid>".equals(ssid)) name = ssid;
                    bars = 1 + WifiManager.calculateSignalLevel(wi.getRssi(), 3);
                }
            } else if (c != null) {
                name = "Ethernet";
                bars = 0;
            }
        } catch (Exception ignored) {}
        wifiName.setText(name);
        wifiState.setText(bars >= 0 ? "Connected" : "Offline");
        wifiState.setTextColor(bars >= 0 ? Ui.GREEN : Ui.RED);
        Drawable icon;
        if (bars == 0) icon = new LineIcon(LineIcon.ETHERNET);
        else {
            LineIcon fan = new LineIcon(LineIcon.WIFI);
            fan.setAlpha(0x47);
            int[] top = bars < 0 ? LineIcon.SLASH : bars == 1 ? LineIcon.WIFI_1 : bars == 2 ? LineIcon.WIFI_2 : LineIcon.WIFI;
            icon = new android.graphics.drawable.LayerDrawable(new Drawable[] {fan, new LineIcon(top)});
        }
        wifiTopIcon.setImageDrawable(icon);
        wifiCardIcon.setImageDrawable(bars > 0 ? new LineIcon(LineIcon.WIFI, Ui.GREEN)
                : bars == 0 ? new LineIcon(LineIcon.ETHERNET, Ui.GREEN) : new LineIcon(LineIcon.SLASH, Ui.RED));
    }

    private void openWifi() {
        open(new Intent(Settings.ACTION_WIFI_SETTINGS), new Intent(Settings.ACTION_WIRELESS_SETTINGS),
                new Intent(Settings.ACTION_SETTINGS));
    }

    // Android TV's Bluetooth page, else its remote and accessory pairing screen, else Settings.
    private void openBluetooth() {
        open(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS), new Intent("com.google.android.intent.action.CONNECT_INPUT"),
                new Intent(Settings.ACTION_SETTINGS));
    }

    private void openTvSettings() { open(new Intent(Settings.ACTION_SETTINGS)); }

    private void open(Intent... tries) {
        for (Intent i : tries) {
            if (i.resolveActivity(getPackageManager()) == null) continue;
            try {
                startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return;
            } catch (Exception ignored) {}
        }
    }

    // --- Bitmaps ---

    // Draws a banner or icon once into a bitmap this size (center-cropped). Some apps ship huge
    // banners that Android 9 refuses to draw; this also saves memory.
    private static Bitmap render(Drawable d, int w, int h) {
        Bitmap b = Bitmap.createBitmap(Math.max(1, w), Math.max(1, h), Bitmap.Config.ARGB_8888);
        int iw = d.getIntrinsicWidth(), ih = d.getIntrinsicHeight();
        if (iw <= 0 || ih <= 0) { iw = w; ih = h; }
        float s = Math.max((float) w / iw, (float) h / ih);
        int dw = Math.round(iw * s), dh = Math.round(ih * s);
        d.setBounds((w - dw) / 2, (h - dh) / 2, (w - dw) / 2 + dw, (h - dh) / 2 + dh);
        d.draw(new Canvas(b));
        return b;
    }

    // Center-crops a bitmap to w x h with rounded corners.
    private static Bitmap rounded(Bitmap src, int w, int h, float r) {
        Bitmap out = Bitmap.createBitmap(Math.max(1, w), Math.max(1, h), Bitmap.Config.ARGB_8888);
        BitmapShader shader = new BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        float s = Math.max((float) w / src.getWidth(), (float) h / src.getHeight());
        Matrix m = new Matrix();
        m.setScale(s, s);
        m.postTranslate((w - src.getWidth() * s) / 2f, (h - src.getHeight() * s) / 2f);
        shader.setLocalMatrix(m);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        p.setShader(shader);
        new Canvas(out).drawRoundRect(new RectF(0, 0, w, h), r, r, p);
        return out;
    }
}
