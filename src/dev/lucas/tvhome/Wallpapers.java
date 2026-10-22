package dev.lucas.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Where photos come from and how they are decoded.
 *
 *   "file:<name>"   pushed to the TV by scripts/wallpapers.sh
 *   "asset:<name>"  built into the APK from wallpapers/ by build.sh
 *
 * Photos are decoded no larger than the screen and centre-cropped to it. Thumbnails
 * for the settings panel are cached as small JPEGs in the app's private storage.
 */
final class Wallpapers {

    static final int THUMB_W = 320, THUMB_H = 180;

    private final Context ctx;
    private final int screenW, screenH;

    Wallpapers(Context ctx, int screenW, int screenH) {
        this.ctx = ctx;
        this.screenW = screenW;
        this.screenH = screenH;
    }

    File dir() {
        File d = ctx.getExternalFilesDir("wallpapers");
        if (d == null) d = new File(ctx.getFilesDir(), "wallpapers");
        return d;
    }

    List<String> refs() {
        List<String> out = new ArrayList<>();
        File[] files = dir().listFiles();
        if (files != null) {
            Arrays.sort(files);
            for (File f : files) if (isImage(f.getName())) out.add("file:" + f.getName());
        }
        try {
            String[] assets = ctx.getAssets().list("wallpapers");
            if (assets != null) {
                Arrays.sort(assets);
                for (String a : assets) if (isImage(a)) out.add("asset:" + a);
            }
        } catch (Throwable ignored) { }
        return out;
    }

    static boolean isImage(String n) {
        String l = n.toLowerCase(Locale.ROOT);
        return l.endsWith(".jpg") || l.endsWith(".jpeg") || l.endsWith(".png") || l.endsWith(".webp");
    }

    static String name(String ref) {
        String n = ref.substring(ref.indexOf(':') + 1);
        int dot = n.lastIndexOf('.');
        return dot > 0 ? n.substring(0, dot) : n;
    }

    private InputStream open(String ref) throws Exception {
        String n = ref.substring(ref.indexOf(':') + 1);
        if (ref.startsWith("file:")) return new FileInputStream(new File(dir(), n));
        return ctx.getAssets().open("wallpapers/" + n);
    }

    /** The photo at screen size, centre-cropped, or null if it can't be read. */
    Bitmap decodeFitted(String ref) {
        return decodeCropped(ref, screenW, screenH);
    }

    private Bitmap decodeCropped(String ref, int w, int h) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            InputStream in = open(ref);
            try { BitmapFactory.decodeStream(in, null, o); } finally { in.close(); }
            if (o.outWidth <= 0 || o.outHeight <= 0) return null;
            int sample = 1;
            while (o.outWidth / (sample * 2) >= w && o.outHeight / (sample * 2) >= h) sample *= 2;
            BitmapFactory.Options d = new BitmapFactory.Options();
            d.inSampleSize = sample;
            d.inPreferredConfig = Bitmap.Config.ARGB_8888;
            d.inDither = true;
            in = open(ref);
            Bitmap src;
            try { src = BitmapFactory.decodeStream(in, null, d); } finally { in.close(); }
            if (src == null) return null;
            Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(out);
            float s = Math.max(w / (float) src.getWidth(), h / (float) src.getHeight());
            Matrix m = new Matrix();
            m.setScale(s, s);
            m.postTranslate((w - src.getWidth() * s) / 2f, (h - src.getHeight() * s) / 2f);
            c.drawBitmap(src, m, new Paint(Paint.FILTER_BITMAP_FLAG));
            src.recycle();
            return out;
        } catch (Throwable t) {
            return null;
        }
    }

    /** A small thumbnail, cached on disk after the first time. */
    Bitmap thumb(String ref) {
        File cache = new File(ctx.getFilesDir(), "thumbs");
        cache.mkdirs();
        File f = new File(cache, name(ref) + ".jpg");
        if (f.exists()) {
            Bitmap b = BitmapFactory.decodeFile(f.getAbsolutePath());
            if (b != null) return b;
        }
        Bitmap b = decodeCropped(ref, THUMB_W, THUMB_H);
        if (b == null) return null;
        try {
            FileOutputStream out = new FileOutputStream(f);
            try { b.compress(Bitmap.CompressFormat.JPEG, 82, out); } finally { out.close(); }
        } catch (Throwable ignored) { }
        return b;
    }

    /** With no photos: a soft dark gradient with a couple of glows. */
    Bitmap placeholder() {
        Bitmap b = Bitmap.createBitmap(screenW, screenH, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, 0, screenW, screenH, 0xFF0C1626, 0xFF03060B, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, screenW, screenH, p);
        p.setShader(new RadialGradient(screenW * 0.72f, screenH * 0.28f, screenW * 0.45f,
                0x66206A7A, 0x00000000, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, screenW, screenH, p);
        p.setShader(new RadialGradient(screenW * 0.18f, screenH * 0.85f, screenW * 0.4f,
                0x4D3A2C6E, 0x00000000, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, screenW, screenH, p);
        return b;
    }

    /** A heavily blurred copy of the screen at 1/factor size, for the glass panels. */
    static Bitmap blurDown(Bitmap screen, int factor, int radius, int passes) {
        int w = Math.max(8, screen.getWidth() / factor), h = Math.max(8, screen.getHeight() / factor);
        Bitmap small = Bitmap.createScaledBitmap(screen, w, h, true);
        if (small == screen || small.getConfig() != Bitmap.Config.ARGB_8888 || !small.isMutable()) {
            Bitmap copy = small.copy(Bitmap.Config.ARGB_8888, true);
            if (small != screen) small.recycle();
            small = copy;
        }
        for (int i = 0; i < passes; i++) boxBlur(small, radius);
        return small;
    }

    static void boxBlur(Bitmap b, int r) {
        int w = b.getWidth(), h = b.getHeight();
        int[] px = new int[w * h], tmp = new int[w * h];
        b.getPixels(px, 0, w, 0, 0, w, h);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rs = 0, gs = 0, bs = 0, n = 0;
                for (int k = -r; k <= r; k++) {
                    int xx = Math.min(w - 1, Math.max(0, x + k));
                    int c = px[y * w + xx];
                    rs += (c >> 16) & 255; gs += (c >> 8) & 255; bs += c & 255; n++;
                }
                tmp[y * w + x] = 0xFF000000 | (rs / n << 16) | (gs / n << 8) | (bs / n);
            }
        }
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                int rs = 0, gs = 0, bs = 0, n = 0;
                for (int k = -r; k <= r; k++) {
                    int yy = Math.min(h - 1, Math.max(0, y + k));
                    int c = tmp[yy * w + x];
                    rs += (c >> 16) & 255; gs += (c >> 8) & 255; bs += c & 255; n++;
                }
                px[y * w + x] = 0xFF000000 | (rs / n << 16) | (gs / n << 8) | (bs / n);
            }
        }
        b.setPixels(px, 0, w, 0, 0, w, h);
    }
}
