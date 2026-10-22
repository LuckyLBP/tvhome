package dev.lucas.tvhome;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;

/**
 * Turns an app icon into a white logo, the way a monochrome home screen wants it.
 *
 * No artwork is bundled. The icon is drawn small, its background is found (either
 * transparent, or the colour along the border grown inwards), and everything that
 * is not background becomes white. Enclosed areas in the background colour stay
 * cut out, so a play button keeps its triangle and a circle logo keeps its waves.
 * Adaptive icons use only their foreground layer, which is usually the logo itself.
 *
 * Returns null when the result is nonsense (almost everything or almost nothing
 * would be white), and the caller falls back to the colour icon.
 */
final class Mono {

    private static final int S = 96;            // working size
    private static final int ALPHA_BG = 40;     // below this: transparent background
    private static final int LOCAL = 60;        // neighbour-to-neighbour colour step still background
    private static final int GLOBAL = 210;      // max distance from the border colour for background
    private static final int HOLE = 48;         // this close to the border colour anywhere: background

    private Mono() { }

    static Bitmap glyph(Drawable icon, int outSize) {
        if (icon == null) return null;
        try {
            Bitmap b = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            Drawable d = icon;
            boolean adaptive = false;
            if (Build.VERSION.SDK_INT >= 26 && icon instanceof AdaptiveIconDrawable) {
                Drawable fg = ((AdaptiveIconDrawable) icon).getForeground();
                if (fg != null) { d = fg; adaptive = true; }
            }
            if (adaptive) {
                int inset = -(int) (S * 0.25f);     // the foreground layer is drawn on a 108/72 canvas
                d.setBounds(inset, inset, S - inset, S - inset);
            } else {
                d.setBounds(0, 0, S, S);
            }
            d.draw(c);

            int[] p = new int[S * S];
            b.getPixels(p, 0, S, 0, 0, S, S);
            b.recycle();

            byte[] bg = new byte[S * S];   // 1 = background
            int transparentBorder = 0, borderCount = 0;
            long br = 0, bgc = 0, bb = 0; int opaqueBorder = 0;
            for (int i = 0; i < S; i++) {
                int[] idx = { i, (S - 1) * S + i, i * S, i * S + S - 1 };
                for (int k : idx) {
                    borderCount++;
                    int v = p[k];
                    if (((v >>> 24) & 255) < ALPHA_BG) { transparentBorder++; continue; }
                    br += (v >> 16) & 255; bgc += (v >> 8) & 255; bb += v & 255; opaqueBorder++;
                }
            }
            boolean transparent = transparentBorder > borderCount / 2;
            int bgColor = opaqueBorder == 0 ? 0 : (int) ((br / opaqueBorder) << 16 | (bgc / opaqueBorder) << 8 | (bb / opaqueBorder));

            int[] queue = new int[S * S];
            int head = 0, tail = 0;
            for (int i = 0; i < S; i++) {
                int[] idx = { i, (S - 1) * S + i, i * S, i * S + S - 1 };
                for (int k : idx) {
                    if (bg[k] == 1) continue;
                    int a = (p[k] >>> 24) & 255;
                    if (a < ALPHA_BG || (!transparent && dist(p[k], bgColor) < GLOBAL)) { bg[k] = 1; queue[tail++] = k; }
                }
            }
            while (head < tail) {
                int k = queue[head++];
                int x = k % S, y = k / S;
                int[] ns = { k - 1, k + 1, k - S, k + S };
                for (int j = 0; j < 4; j++) {
                    int n = ns[j];
                    if (j == 0 && x == 0) continue;
                    if (j == 1 && x == S - 1) continue;
                    if (j == 2 && y == 0) continue;
                    if (j == 3 && y == S - 1) continue;
                    if (bg[n] == 1) continue;
                    int a = (p[n] >>> 24) & 255;
                    boolean isBg;
                    if (a < ALPHA_BG) isBg = true;
                    else if (transparent) isBg = false;
                    else isBg = dist(p[n], p[k]) < LOCAL && dist(p[n], bgColor) < GLOBAL;
                    if (isBg) { bg[n] = 1; queue[tail++] = n; }
                }
            }

            // Enclosed areas in the background colour: holes in the logo.
            if (!transparent) {
                for (int k = 0; k < p.length; k++) {
                    if (bg[k] == 0 && ((p[k] >>> 24) & 255) >= ALPHA_BG && dist(p[k], bgColor) < HOLE) bg[k] = 1;
                }
            }
            {
                // Near-white or near-black details inside a coloured shape are cut out too
                // (a play arrow inside a disc, sound waves), so the shape reads as a logo
                // rather than a blob. Logos that are themselves white or black are unaffected,
                // since the detail must differ strongly from the shape's own colour.
                // The shape's own colour is the most common one among its pixels (not the
                // average, which white text would pull towards white).
                int[] hist = new int[4096];
                long[] hr = new long[4096], hg = new long[4096], hb = new long[4096];
                int n = 0;
                for (int k = 0; k < p.length; k++) {
                    if (bg[k] == 1) continue;
                    int r = (p[k] >> 16) & 255, g = (p[k] >> 8) & 255, b2 = p[k] & 255;
                    int q = (r >> 4) << 8 | (g >> 4) << 4 | (b2 >> 4);
                    hist[q]++; hr[q] += r; hg[q] += g; hb[q] += b2; n++;
                }
                if (n > 0) {
                    int best = 0;
                    for (int q = 1; q < 4096; q++) if (hist[q] > hist[best]) best = q;
                    int dom = (int) ((hr[best] / hist[best]) << 16 | (hg[best] / hist[best]) << 8 | (hb[best] / hist[best]));
                    int domLum = lum(dom);
                    for (int k = 0; k < p.length; k++) {
                        if (bg[k] == 1) continue;
                        int l = lum(p[k]);
                        boolean extreme = l > 232 || l < 36;
                        if (extreme && Math.abs(l - domLum) > 110 && dist(p[k], dom) > 180) bg[k] = 1;
                    }
                }
            }

            int fg = 0;
            for (byte v : bg) if (v == 0) fg++;
            float frac = fg / (float) (S * S);
            if (frac < 0.02f || frac > 0.90f) return null;

            // Soft edges: a one-pixel box blur of the mask.
            int[] mask = new int[S * S];
            for (int k = 0; k < mask.length; k++) mask[k] = bg[k] == 0 ? 255 : 0;
            int[] soft = new int[S * S];
            for (int y = 0; y < S; y++) {
                for (int x = 0; x < S; x++) {
                    int sum = 0, cnt = 0;
                    for (int dy = -1; dy <= 1; dy++) {
                        int yy = y + dy; if (yy < 0 || yy >= S) continue;
                        for (int dx = -1; dx <= 1; dx++) {
                            int xx = x + dx; if (xx < 0 || xx >= S) continue;
                            sum += mask[yy * S + xx]; cnt++;
                        }
                    }
                    soft[y * S + x] = sum / cnt;
                }
            }
            int[] out = new int[S * S];
            for (int k = 0; k < out.length; k++) out[k] = (soft[k] << 24) | 0xFFFFFF;
            Bitmap big = Bitmap.createBitmap(out, S, S, Bitmap.Config.ARGB_8888);

            // Trim to the logo itself and fit it into the output square, so a wordmark and
            // a disc end up a similar visual size.
            int x0 = S, y0 = S, x1 = -1, y1 = -1;
            for (int y = 0; y < S; y++) for (int x = 0; x < S; x++) {
                if (soft[y * S + x] > 24) { if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y; }
            }
            if (x1 < x0 || y1 < y0) { big.recycle(); return null; }
            int cw = x1 - x0 + 1, ch = y1 - y0 + 1;
            Bitmap crop = Bitmap.createBitmap(big, x0, y0, cw, ch);
            big.recycle();
            Bitmap fitted = Bitmap.createBitmap(outSize, outSize, Bitmap.Config.ARGB_8888);
            Canvas fc = new Canvas(fitted);
            float box = outSize * 0.86f;                       // a little air around the logo
            float sc = Math.min(box / cw, box / ch);
            // Wide wordmarks may fill the width; tall or square marks are capped a bit smaller.
            if (cw < ch * 1.6f) sc = Math.min(sc, outSize * 0.78f / Math.max(cw, ch));
            float dw = cw * sc, dh = ch * sc;
            android.graphics.RectF dst = new android.graphics.RectF((outSize - dw) / 2f, (outSize - dh) / 2f, (outSize + dw) / 2f, (outSize + dh) / 2f);
            android.graphics.Paint fp = new android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG | android.graphics.Paint.ANTI_ALIAS_FLAG);
            fc.drawBitmap(crop, null, dst, fp);
            crop.recycle();
            return fitted;
        } catch (Throwable t) {
            return null;
        }
    }

    private static int dist(int a, int b) {
        return Math.abs(((a >> 16) & 255) - ((b >> 16) & 255))
                + Math.abs(((a >> 8) & 255) - ((b >> 8) & 255))
                + Math.abs((a & 255) - (b & 255));
    }

    private static int lum(int c) {
        return (((c >> 16) & 255) * 299 + ((c >> 8) & 255) * 587 + (c & 255) * 114) / 1000;
    }
}
