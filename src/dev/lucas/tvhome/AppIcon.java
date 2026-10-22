package dev.lucas.tvhome;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;

/**
 * Turns any app icon into a dock icon: full colour, in the continuous-corner
 * "squircle" shape, the way an iPad dock looks.
 *
 * Android TV icons come in every shape, so each one is sorted first:
 *   adaptive icon        background + foreground layers, cropped to the shape
 *   full-bleed square    already fills its canvas: just cropped to the shape
 *   rounded square       its own corners are cut off by zooming slightly, the
 *                        gap is filled with its edge colour
 *   badge (circle etc.)  sits on a plate in its own outer colour, so the circle
 *                        merges into a square
 *   bare logo            sits on a white or near-black plate, whichever it reads on
 */
final class AppIcon {

    private AppIcon() { }

    /** Superellipse, close to Apple's continuous corners. */
    static Path squircle(float l, float t, float size) {
        Path p = new Path();
        float r = size / 2f, cx = l + r, cy = t + r;
        double n = 5.0;
        int steps = 96;
        for (int i = 0; i <= steps; i++) {
            double a = 2 * Math.PI * i / steps;
            double c = Math.cos(a), s = Math.sin(a);
            float x = cx + (float) (r * Math.signum(c) * Math.pow(Math.abs(c), 2.0 / n));
            float y = cy + (float) (r * Math.signum(s) * Math.pow(Math.abs(s), 2.0 / n));
            if (i == 0) p.moveTo(x, y); else p.lineTo(x, y);
        }
        p.close();
        return p;
    }

    /** Full-colour dock icon at size x size pixels. */
    static Bitmap colour(Drawable icon, int size) {
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Path shape = squircle(0, 0, size);
        c.save();
        c.clipPath(shape);
        try {
            if (icon == null) {
                c.drawColor(0xFF3A3A3C);
            } else if (Build.VERSION.SDK_INT >= 26 && icon instanceof AdaptiveIconDrawable) {
                AdaptiveIconDrawable a = (AdaptiveIconDrawable) icon;
                int inset = Math.round(size * 0.25f);     // layers are 108dp, the visible part 72dp
                Drawable bg = a.getBackground(), fg = a.getForeground();
                if (bg != null) { bg.setBounds(-inset, -inset, size + inset, size + inset); bg.draw(c); }
                else c.drawColor(0xFFFFFFFF);
                if (fg != null) { fg.setBounds(-inset, -inset, size + inset, size + inset); fg.draw(c); }
            } else {
                drawLegacy(c, icon, size);
            }
        } catch (Throwable t) {
            c.drawColor(0xFF3A3A3C);
        }
        // A whisper of light from the top and a hairline rim, like a real icon under glass.
        Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
        sheen.setShader(new LinearGradient(0, 0, 0, size * 0.55f, 0x1FFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, size, size, sheen);
        c.restore();
        Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(Math.max(1f, size / 128f));
        rim.setColor(0x26FFFFFF);
        c.drawPath(shape, rim);
        return out;
    }

    /** White-logo dock icon: frosted plate with the app's logo in white. */
    static Bitmap white(Bitmap glyph, Drawable fallback, int size) {
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Path shape = squircle(0, 0, size);
        Paint plate = new Paint(Paint.ANTI_ALIAS_FLAG);
        plate.setColor(0x38FFFFFF);
        c.drawPath(shape, plate);
        int inner = Math.round(size * 0.62f), off = (size - inner) / 2;
        if (glyph != null) {
            c.drawBitmap(glyph, null, new Rect(off, off, off + inner, off + inner), new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG));
        } else if (fallback != null) {
            fallback.setBounds(off, off, off + inner, off + inner);
            fallback.draw(c);
        }
        Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(Math.max(1f, size / 128f));
        rim.setColor(0x40FFFFFF);
        c.drawPath(shape, rim);
        return out;
    }

    /** A system glyph (inputs, Live TV, settings) on a coloured plate. */
    static Bitmap system(Drawable glyph, int plateTop, int plateBottom, int size) {
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Path shape = squircle(0, 0, size);
        Paint plate = new Paint(Paint.ANTI_ALIAS_FLAG);
        plate.setShader(new LinearGradient(0, 0, 0, size, plateTop, plateBottom, Shader.TileMode.CLAMP));
        c.drawPath(shape, plate);
        int inner = Math.round(size * 0.52f), off = (size - inner) / 2;
        glyph.setBounds(off, off, off + inner, off + inner);
        glyph.draw(c);
        Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(Math.max(1f, size / 128f));
        rim.setColor(0x26FFFFFF);
        c.drawPath(shape, rim);
        return out;
    }

    private static void drawLegacy(Canvas c, Drawable icon, int size) {
        int S = 192;
        Bitmap src = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888);
        Canvas sc = new Canvas(src);
        icon.setBounds(0, 0, S, S);
        icon.draw(sc);
        int[] p = new int[S * S];
        src.getPixels(p, 0, S, 0, 0, S, S);

        // Border ring: how much of the canvas edge is covered?
        int ring = 0, ringOpaque = 0;
        for (int i = 0; i < S; i++) {
            for (int k : new int[] { i, (S - 1) * S + i, i * S, i * S + S - 1 }) {
                ring++;
                if (alpha(p[k]) > 200) ringOpaque++;
            }
        }
        float ringCover = ringOpaque / (float) ring;

        // Mid-points of each side: a rounded square has these opaque but not its corners.
        int mids = 0;
        int[] midIdx = { (S / 2), (S - 1) * S + S / 2, (S / 2) * S, (S / 2) * S + S - 1 };
        for (int k : midIdx) if (alpha(p[k]) > 200) mids++;

        Paint fp = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        if (ringCover > 0.85f) {
            // Full-bleed: crop straight to the shape.
            c.drawBitmap(src, null, new RectF(0, 0, size, size), fp);
        } else if (mids >= 3) {
            // Rounded square: fill with its edge colour, zoom 12% so its own corners fall outside.
            c.drawColor(average(p, midIdx));
            float z = size * 0.06f;
            c.drawBitmap(src, null, new RectF(-z, -z, size + z, size + z), fp);
        } else {
            // A shape on transparency. Find it, and what colour its outline is.
            int x0 = S, y0 = S, x1 = -1, y1 = -1, count = 0;
            int[] hist = new int[4096];
            long[] hr = new long[4096], hg = new long[4096], hb = new long[4096];
            long lum = 0;
            for (int y = 0; y < S; y++) for (int x = 0; x < S; x++) {
                int v = p[y * S + x];
                if (alpha(v) <= 200) continue;
                count++;
                if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y;
                lum += luminance(v);
                boolean edge = (x == 0 || alpha(p[y * S + x - 1]) <= 200) || (x == S - 1 || alpha(p[y * S + x + 1]) <= 200)
                        || (y == 0 || alpha(p[(y - 1) * S + x]) <= 200) || (y == S - 1 || alpha(p[(y + 1) * S + x]) <= 200);
                if (edge) {
                    int q = (((v >> 16) & 255) >> 4) << 8 | (((v >> 8) & 255) >> 4) << 4 | ((v & 255) >> 4);
                    hist[q]++; hr[q] += (v >> 16) & 255; hg[q] += (v >> 8) & 255; hb[q] += v & 255;
                }
            }
            if (count == 0) { c.drawColor(0xFF3A3A3C); src.recycle(); return; }
            int bw = x1 - x0 + 1, bh = y1 - y0 + 1;
            float coverage = count / (float) (bw * bh);
            boolean badge = coverage > 0.62f && Math.abs(bw - bh) < Math.max(bw, bh) * 0.25f;
            if (badge) {
                int best = 0;
                for (int q = 1; q < 4096; q++) if (hist[q] > hist[best]) best = q;
                int edgeColour = 0xFF000000 | (int) ((hr[best] / Math.max(1, hist[best])) << 16
                        | (hg[best] / Math.max(1, hist[best])) << 8 | (hb[best] / Math.max(1, hist[best])));
                c.drawColor(edgeColour);
                // Scale the badge so it overfills a little: its round edge disappears into the plate.
                float target = size * 1.04f, sc2 = target / Math.max(bw, bh);
                float dw = S * sc2, dx = (size - bw * sc2) / 2f - x0 * sc2, dy = (size - bh * sc2) / 2f - y0 * sc2;
                c.drawBitmap(src, null, new RectF(dx, dy, dx + dw, dy + dw), fp);
            } else {
                // Bare logo: white plate for dark or colourful logos, near-black for light ones.
                long avg = lum / count;
                c.drawColor(avg > 190 ? 0xFF1C1C1E : 0xFFFFFFFF);
                float target = size * 0.68f, sc2 = target / Math.max(bw, bh);
                float dw = S * sc2, dx = (size - bw * sc2) / 2f - x0 * sc2, dy = (size - bh * sc2) / 2f - y0 * sc2;
                c.drawBitmap(src, null, new RectF(dx, dy, dx + dw, dy + dw), fp);
            }
        }
        src.recycle();
    }

    private static int alpha(int v) { return (v >>> 24) & 255; }

    private static int luminance(int v) {
        return (((v >> 16) & 255) * 299 + ((v >> 8) & 255) * 587 + (v & 255) * 114) / 1000;
    }

    private static int average(int[] p, int[] idx) {
        long r = 0, g = 0, b = 0; int n = 0;
        for (int k : idx) {
            int v = p[k];
            if (alpha(v) <= 200) continue;
            r += (v >> 16) & 255; g += (v >> 8) & 255; b += v & 255; n++;
        }
        if (n == 0) return 0xFF3A3A3C;
        return 0xFF000000 | (int) ((r / n) << 16 | (g / n) << 8 | (b / n));
    }

    /**
     * Preview stand-in for an app the emulator doesn't have: the app's brand colour with
     * its initial. Never used on the TV.
     */
    static final class Placeholder extends Drawable {
        private final String letter;
        private final int bg, fg;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        Placeholder(String label) {
            letter = label.isEmpty() ? "?" : label.substring(0, 1).toUpperCase(java.util.Locale.ROOT);
            int[] c = brand(label);
            bg = c[0]; fg = c[1];
        }

        private static int[] brand(String l) {
            String s = l.toLowerCase(java.util.Locale.ROOT);
            if (s.startsWith("netflix")) return new int[] { 0xFF000000, 0xFFE50914 };
            if (s.startsWith("youtube")) return new int[] { 0xFFFFFFFF, 0xFFFF0033 };
            if (s.startsWith("svt")) return new int[] { 0xFF1F1F1F, 0xFFFFFFFF };
            if (s.startsWith("tv4")) return new int[] { 0xFFE4002B, 0xFFFFFFFF };
            if (s.startsWith("viaplay")) return new int[] { 0xFF2B1747, 0xFFFF4F8B };
            if (s.startsWith("disney")) return new int[] { 0xFF0B1C4F, 0xFFFFFFFF };
            if (s.equals("max") || s.startsWith("hbo")) return new int[] { 0xFF0028FF, 0xFFFFFFFF };
            if (s.startsWith("skyshowtime")) return new int[] { 0xFF111111, 0xFFFFFFFF };
            if (s.startsWith("apple")) return new int[] { 0xFF000000, 0xFFFFFFFF };
            if (s.startsWith("prime")) return new int[] { 0xFF00A8E1, 0xFFFFFFFF };
            if (s.startsWith("plex")) return new int[] { 0xFF1F1F1F, 0xFFE5A00D };
            if (s.startsWith("spotify")) return new int[] { 0xFF121212, 0xFF1ED760 };
            if (s.startsWith("twitch")) return new int[] { 0xFF9146FF, 0xFFFFFFFF };
            return new int[] { 0xFF3A3A3C, 0xFFFFFFFF };
        }

        @Override public void draw(Canvas c) {
            Rect b = getBounds();
            paint.setShader(null);
            paint.setColor(bg);
            c.drawRect(b, paint);
            paint.setColor(fg);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            paint.setTextSize(b.height() * 0.52f);
            Paint.FontMetrics fm = paint.getFontMetrics();
            c.drawText(letter, b.exactCenterX(), b.exactCenterY() - (fm.ascent + fm.descent) / 2f, paint);
        }

        @Override public void setAlpha(int a) { paint.setAlpha(a); }
        @Override public void setColorFilter(ColorFilter f) { paint.setColorFilter(f); }
        @Override public int getOpacity() { return PixelFormat.OPAQUE; }
        @Override public int getIntrinsicWidth() { return 192; }
        @Override public int getIntrinsicHeight() { return 192; }
    }
}
