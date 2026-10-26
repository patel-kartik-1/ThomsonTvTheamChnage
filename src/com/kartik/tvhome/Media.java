package com.kartik.tvhome;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Environment;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

// Videos kept on the TV in BAPS/Aarti, BAPS/Kirtan and BAPS/Katha. To add one, copy it into the
// folder (with File Commander from a USB stick, or adb push). A .jpg with the same name is its
// thumbnail; without one, a frame from the video is used. The file name is the title.
final class Media {
    static final File ROOT = new File(Environment.getExternalStorageDirectory(), "BAPS");
    static final String[] FOLDERS = {"Aarti", "Kirtan", "Katha"};
    private static final String[] VIDEO = {".mp4", ".mkv", ".webm", ".m4v", ".mov", ".avi", ".3gp", ".ts"};
    static final String VLC = "org.videolan.vlc", YOUTUBE = "com.google.android.youtube.tv";

    // YouTube channels the Aarti, Kirtan and Katha pages link to: name, address.
    static final String[] KIRTAN_CHANNEL = {"BAPS Kirtan Channel", "https://www.youtube.com/channel/UCs7ARxpbAP25LhDAnnzDuvA"};
    static final String[] BAPS_CHANNEL = {"BAPS", "https://www.youtube.com/channel/UCuQNLnncEruW0pllaE0ZeKQ"};

    private Media() {}

    static boolean isVideo(File f) {
        String n = f.getName().toLowerCase(Locale.ROOT);
        for (String ext : VIDEO) if (n.endsWith(ext)) return f.isFile();
        return false;
    }

    // The videos in one folder, newest first.
    static List<File> videos(String folder) {
        List<File> out = new ArrayList<>();
        File[] files = new File(ROOT, folder).listFiles();
        if (files != null) for (File f : files) if (isVideo(f)) out.add(f);
        Collections.sort(out, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        return out;
    }

    // Every folder's videos together, newest first.
    static List<File> allVideos() {
        List<File> out = new ArrayList<>();
        for (String folder : FOLDERS) out.addAll(videos(folder));
        Collections.sort(out, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        return out;
    }

    // Wallpaper photos the family adds (BAPS/Wallpapers), by name.
    static final File WALLPAPERS = new File(ROOT, "Wallpapers");

    static List<File> wallpapers() {
        List<File> out = new ArrayList<>();
        File[] files = WALLPAPERS.listFiles();
        if (files != null) for (File f : files) {
            String n = f.getName().toLowerCase(Locale.ROOT);
            if (f.isFile() && (n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp")))
                out.add(f);
        }
        Collections.sort(out, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        return out;
    }

    // A photo scaled and center-cropped to fill the screen exactly, or null if it can't be read.
    static Bitmap screenImage(File f, int w, int h) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), o);
        if (o.outWidth <= 0) return null;
        o.inSampleSize = 1;
        while (o.outWidth / (o.inSampleSize * 2) >= w && o.outHeight / (o.inSampleSize * 2) >= h) o.inSampleSize *= 2;
        o.inJustDecodeBounds = false;
        Bitmap src = BitmapFactory.decodeFile(f.getPath(), o);
        if (src == null) return null;
        float s = Math.max((float) w / src.getWidth(), (float) h / src.getHeight());
        int sw = Math.round(w / s), sh = Math.round(h / s);
        Bitmap crop = Bitmap.createBitmap(src, (src.getWidth() - sw) / 2, (src.getHeight() - sh) / 2,
                Math.min(sw, src.getWidth()), Math.min(sh, src.getHeight()));
        Bitmap out = Bitmap.createScaledBitmap(crop, w, h, true);
        if (crop != src) src.recycle();
        if (out != crop) crop.recycle();
        return out;
    }

    // The evening aarti video: the first Aarti video with "sandhya" in its name, else the newest one.
    static File sandhya() {
        List<File> aarti = videos("Aarti");
        for (File f : aarti) if (f.getName().toLowerCase(Locale.ROOT).contains("sandhya")) return f;
        return aarti.isEmpty() ? null : aarti.get(0);
    }

    static String title(File f) {
        String n = f.getName();
        int dot = n.lastIndexOf('.');
        return (dot > 0 ? n.substring(0, dot) : n).replace('_', ' ').trim();
    }

    // The thumbnail at about w x h pixels: the .jpg (or .png) next to the video, else a frame from it.
    static Bitmap thumbnail(File video, int w, int h) {
        String base = video.getPath().substring(0, video.getPath().lastIndexOf('.'));
        for (String ext : new String[] {".jpg", ".jpeg", ".png"}) {
            File img = new File(base + ext);
            if (!img.isFile()) continue;
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(img.getPath(), o);
            o.inSampleSize = 1;
            while (o.outWidth / (o.inSampleSize * 2) >= w && o.outHeight / (o.inSampleSize * 2) >= h) o.inSampleSize *= 2;
            o.inJustDecodeBounds = false;
            Bitmap b = BitmapFactory.decodeFile(img.getPath(), o);
            if (b != null) return b;
        }
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
            r.setDataSource(video.getPath());
            Bitmap frame = r.getFrameAtTime(30_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            if (frame == null) return null;
            Bitmap small = Bitmap.createScaledBitmap(frame, w, h, true);
            if (small != frame) frame.recycle();
            return small;
        } catch (Exception e) {
            return null;
        } finally {
            try { r.release(); } catch (Exception ignored) {}
        }
    }

    // Length as "20:18" or "1:04:35", or "" if the file can't be read.
    static String duration(File video) {
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
            r.setDataSource(video.getPath());
            String ms = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            long s = ms == null ? 0 : Long.parseLong(ms) / 1000;
            if (s <= 0) return "";
            return s >= 3600 ? String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
                    : String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
        } catch (Exception e) {
            return "";
        } finally {
            try { r.release(); } catch (Exception ignored) {}
        }
    }

    // Plays a video in VLC from the start, else in whatever player the TV picks.
    static boolean play(Context c, File video) {
        if (video == null) return false;
        Intent i = new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.fromFile(video), "video/*")
                .putExtra("title", title(video)).putExtra("from_start", true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            c.startActivity(new Intent(i).setPackage(VLC));
            return true;
        } catch (Exception ignored) {}
        try {
            c.startActivity(i);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // Opens a YouTube address in the YouTube app, else YouTube's home page.
    static void openYouTube(Context c, String url) {
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            c.startActivity(new Intent(i).setPackage(YOUTUBE));
            return;
        } catch (Exception ignored) {}
        Intent app = c.getPackageManager().getLeanbackLaunchIntentForPackage(YOUTUBE);
        if (app != null) try { c.startActivity(app.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Exception ignored) {}
    }
}
