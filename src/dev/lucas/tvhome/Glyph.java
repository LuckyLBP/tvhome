package dev.lucas.tvhome;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/** Thin white line icons drawn in code, so the APK needs no image files. */
final class Glyph extends Drawable {

    static final int IMAGE = 0, GRID = 1, GEAR = 2, INPUT = 3, TV = 4, PLUS = 5, CHECK = 6, SHUFFLE = 7, STORE = 8, LETTER = 9;
    static final int WIFI = 10, ETHERNET = 11, OFFLINE = 12;
    static final int WX_SUN = 20, WX_MOON = 21, WX_SUN_CLOUD = 22, WX_MOON_CLOUD = 23, WX_CLOUD = 24,
            WX_FOG = 25, WX_RAIN = 26, WX_SNOW = 27, WX_THUNDER = 28;

    static final int SUN_YELLOW = 0xFFFFD60A, RAIN_BLUE = 0xFF64D2FF;

    /** Wi-Fi bars lit, 0..3. */
    int level = 3;

    final int kind;
    private String letter = "";

    /** A bold white initial, used for preview placeholders. */
    static Glyph letter(String label) {
        Glyph g = new Glyph(LETTER);
        g.letter = label.isEmpty() ? "?" : label.substring(0, 1).toUpperCase(java.util.Locale.ROOT);
        return g;
    }

    /** A fresh drawable for another view; drawables can't share bounds. */
    Glyph copy() {
        Glyph g = new Glyph(kind);
        g.letter = letter;
        g.level = level;
        return g;
    }

    static Glyph wifi(int level) { Glyph g = new Glyph(WIFI); g.level = level; return g; }
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);

    Glyph(int kind) {
        this.kind = kind;
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        stroke.setColor(0xFFFFFFFF);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(0xFFFFFFFF);
    }

    @Override public void draw(Canvas c) {
        Rect b = getBounds();
        float s = Math.min(b.width(), b.height());
        float ox = b.left + (b.width() - s) / 2f, oy = b.top + (b.height() - s) / 2f;
        stroke.setStrokeWidth(s * 0.075f);
        c.save();
        c.translate(ox, oy);
        switch (kind) {
            case IMAGE: image(c, s); break;
            case GRID: grid(c, s); break;
            case GEAR: gear(c, s); break;
            case INPUT: input(c, s); break;
            case PLUS: plus(c, s); break;
            case CHECK: check(c, s); break;
            case SHUFFLE: shuffle(c, s); break;
            case STORE: store(c, s); break;
            case WIFI: wifi(c, s); break;
            case ETHERNET: ethernet(c, s); break;
            case OFFLINE: offline(c, s); break;
            case WX_SUN: sun(c, s, s * 0.5f, s * 0.5f, s * 0.2f, true); break;
            case WX_MOON: moon(c, s, s * 0.5f, s * 0.5f, s * 0.3f); break;
            case WX_SUN_CLOUD: sun(c, s, s * 0.36f, s * 0.36f, s * 0.15f, true); cloud(c, s, s * 0.08f, 0xFFFFFFFF); break;
            case WX_MOON_CLOUD: moon(c, s, s * 0.38f, s * 0.36f, s * 0.2f); cloud(c, s, s * 0.08f, 0xFFFFFFFF); break;
            case WX_CLOUD: cloud(c, s, 0, 0xFFFFFFFF); break;
            case WX_FOG: fog(c, s); break;
            case WX_RAIN: cloud(c, s, -s * 0.1f, 0xFFFFFFFF); drops(c, s, RAIN_BLUE, false); break;
            case WX_SNOW: cloud(c, s, -s * 0.1f, 0xFFFFFFFF); drops(c, s, 0xFFFFFFFF, true); break;
            case WX_THUNDER: cloud(c, s, -s * 0.1f, 0xFFFFFFFF); bolt(c, s); break;
            case LETTER: {
                fill.setTextSize(s * 0.78f);
                fill.setTextAlign(Paint.Align.CENTER);
                fill.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD));
                Paint.FontMetrics fm = fill.getFontMetrics();
                c.drawText(letter, s / 2f, s / 2f - (fm.ascent + fm.descent) / 2f, fill);
                break;
            }
            default: tv(c, s);
        }
        c.restore();
    }

    private void image(Canvas c, float s) {
        c.drawRoundRect(new RectF(s * 0.10f, s * 0.14f, s * 0.90f, s * 0.86f), s * 0.12f, s * 0.12f, stroke);
        c.drawCircle(s * 0.36f, s * 0.38f, s * 0.07f, fill);
        Path p = new Path();
        p.moveTo(s * 0.16f, s * 0.78f);
        p.lineTo(s * 0.42f, s * 0.50f);
        p.lineTo(s * 0.57f, s * 0.66f);
        p.lineTo(s * 0.66f, s * 0.57f);
        p.lineTo(s * 0.86f, s * 0.78f);
        c.drawPath(p, stroke);
    }

    private void grid(Canvas c, float s) {
        float a = s * 0.12f, w = s * 0.30f, g = s * 0.16f, r = s * 0.07f;
        c.drawRoundRect(new RectF(a, a, a + w, a + w), r, r, stroke);
        c.drawRoundRect(new RectF(a + w + g, a, a + 2 * w + g, a + w), r, r, stroke);
        c.drawRoundRect(new RectF(a, a + w + g, a + w, a + 2 * w + g), r, r, stroke);
        c.drawRoundRect(new RectF(a + w + g, a + w + g, a + 2 * w + g, a + 2 * w + g), r, r, stroke);
    }

    private void gear(Canvas c, float s) {
        float cx = s / 2, cy = s / 2;
        c.drawCircle(cx, cy, s * 0.27f, stroke);
        c.drawCircle(cx, cy, s * 0.10f, stroke);
        for (int i = 0; i < 8; i++) {
            double a = Math.toRadians(i * 45);
            float x1 = cx + (float) Math.cos(a) * s * 0.30f, y1 = cy + (float) Math.sin(a) * s * 0.30f;
            float x2 = cx + (float) Math.cos(a) * s * 0.44f, y2 = cy + (float) Math.sin(a) * s * 0.44f;
            c.drawLine(x1, y1, x2, y2, stroke);
        }
    }

    private void input(Canvas c, float s) {
        // An HDMI plug seen head-on: a wide rounded body with a narrower lip.
        Path p = new Path();
        p.moveTo(s * 0.14f, s * 0.36f);
        p.lineTo(s * 0.86f, s * 0.36f);
        p.lineTo(s * 0.86f, s * 0.56f);
        p.lineTo(s * 0.74f, s * 0.70f);
        p.lineTo(s * 0.26f, s * 0.70f);
        p.lineTo(s * 0.14f, s * 0.56f);
        p.close();
        c.drawPath(p, stroke);
        c.drawLine(s * 0.32f, s * 0.53f, s * 0.68f, s * 0.53f, stroke);
    }

    private void plus(Canvas c, float s) {
        c.drawLine(s * 0.5f, s * 0.18f, s * 0.5f, s * 0.82f, stroke);
        c.drawLine(s * 0.18f, s * 0.5f, s * 0.82f, s * 0.5f, stroke);
    }

    private void check(Canvas c, float s) {
        Path p = new Path();
        p.moveTo(s * 0.20f, s * 0.52f);
        p.lineTo(s * 0.42f, s * 0.74f);
        p.lineTo(s * 0.82f, s * 0.30f);
        c.drawPath(p, stroke);
    }

    private void shuffle(Canvas c, float s) {
        // Two crossing paths with arrow heads, the usual shuffle sign.
        Path a = new Path();
        a.moveTo(s * 0.12f, s * 0.30f); a.lineTo(s * 0.32f, s * 0.30f); a.lineTo(s * 0.68f, s * 0.70f); a.lineTo(s * 0.84f, s * 0.70f);
        c.drawPath(a, stroke);
        Path b = new Path();
        b.moveTo(s * 0.12f, s * 0.70f); b.lineTo(s * 0.32f, s * 0.70f); b.lineTo(s * 0.68f, s * 0.30f); b.lineTo(s * 0.84f, s * 0.30f);
        c.drawPath(b, stroke);
        c.drawLine(s * 0.76f, s * 0.22f, s * 0.86f, s * 0.30f, stroke);
        c.drawLine(s * 0.76f, s * 0.38f, s * 0.86f, s * 0.30f, stroke);
        c.drawLine(s * 0.76f, s * 0.62f, s * 0.86f, s * 0.70f, stroke);
        c.drawLine(s * 0.76f, s * 0.78f, s * 0.86f, s * 0.70f, stroke);
    }

    private void store(Canvas c, float s) {
        // A simple shop bag.
        c.drawRoundRect(new RectF(s * 0.18f, s * 0.36f, s * 0.82f, s * 0.86f), s * 0.08f, s * 0.08f, stroke);
        Path p = new Path();
        p.moveTo(s * 0.34f, s * 0.36f);
        p.cubicTo(s * 0.34f, s * 0.10f, s * 0.66f, s * 0.10f, s * 0.66f, s * 0.36f);
        c.drawPath(p, stroke);
    }

    // --- status

    private void wifi(Canvas c, float s) {
        // Three filled arcs and a dot, like the status-bar symbol; unlit bars are dimmed.
        float cx = s / 2f, cy = s * 0.80f;
        Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeCap(Paint.Cap.ROUND);
        arc.setStrokeWidth(s * 0.11f);
        float[] radii = { s * 0.20f, s * 0.38f, s * 0.56f };
        for (int i = 0; i < 3; i++) {
            arc.setColor(i < level ? 0xFFFFFFFF : 0x59FFFFFF);
            float r = radii[i];
            c.drawArc(new RectF(cx - r, cy - r, cx + r, cy + r), 225, 90, false, arc);
        }
        fill.setColor(level > 0 ? 0xFFFFFFFF : 0x59FFFFFF);
        c.drawCircle(cx, cy, s * 0.075f, fill);
        fill.setColor(0xFFFFFFFF);
    }

    private void ethernet(Canvas c, float s) {
        // Three linked boxes: the "network" symbol.
        float w = s * 0.24f, h = s * 0.18f, r = s * 0.04f;
        c.drawRoundRect(new RectF(s * 0.38f, s * 0.14f, s * 0.38f + w, s * 0.14f + h), r, r, fill);
        c.drawRoundRect(new RectF(s * 0.10f, s * 0.66f, s * 0.10f + w, s * 0.66f + h), r, r, fill);
        c.drawRoundRect(new RectF(s * 0.66f, s * 0.66f, s * 0.66f + w, s * 0.66f + h), r, r, fill);
        c.drawLine(s * 0.5f, s * 0.32f, s * 0.5f, s * 0.5f, stroke);
        c.drawLine(s * 0.22f, s * 0.5f, s * 0.78f, s * 0.5f, stroke);
        c.drawLine(s * 0.22f, s * 0.5f, s * 0.22f, s * 0.66f, stroke);
        c.drawLine(s * 0.78f, s * 0.5f, s * 0.78f, s * 0.66f, stroke);
    }

    private void offline(Canvas c, float s) {
        int keep = level; level = 0; wifi(c, s); level = keep;
        stroke.setColor(0xFFFFFFFF);
        c.drawLine(s * 0.16f, s * 0.12f, s * 0.84f, s * 0.88f, stroke);
    }

    // --- weather, multicolour like the system weather symbols

    private void sun(Canvas c, float s, float cx, float cy, float r, boolean rays) {
        fill.setColor(SUN_YELLOW);
        c.drawCircle(cx, cy, r, fill);
        if (rays) {
            Paint ray = new Paint(Paint.ANTI_ALIAS_FLAG);
            ray.setStyle(Paint.Style.STROKE);
            ray.setStrokeCap(Paint.Cap.ROUND);
            ray.setStrokeWidth(s * 0.065f);
            ray.setColor(SUN_YELLOW);
            for (int i = 0; i < 8; i++) {
                double a = Math.toRadians(i * 45);
                float x1 = cx + (float) Math.cos(a) * r * 1.45f, y1 = cy + (float) Math.sin(a) * r * 1.45f;
                float x2 = cx + (float) Math.cos(a) * r * 1.95f, y2 = cy + (float) Math.sin(a) * r * 1.95f;
                c.drawLine(x1, y1, x2, y2, ray);
            }
        }
        fill.setColor(0xFFFFFFFF);
    }

    private void moon(Canvas c, float s, float cx, float cy, float r) {
        Path p = new Path();
        p.addCircle(cx, cy, r, Path.Direction.CW);
        Path bite = new Path();
        bite.addCircle(cx + r * 0.55f, cy - r * 0.35f, r * 0.85f, Path.Direction.CW);
        p.op(bite, Path.Op.DIFFERENCE);
        fill.setColor(0xFFFFFFFF);
        c.drawPath(p, fill);
    }

    private void cloud(Canvas c, float s, float dy, int colour) {
        Path p = new Path();
        p.addCircle(s * 0.40f, s * 0.56f + dy, s * 0.17f, Path.Direction.CW);
        p.addCircle(s * 0.60f, s * 0.50f + dy, s * 0.21f, Path.Direction.CW);
        p.addCircle(s * 0.26f, s * 0.66f + dy, s * 0.12f, Path.Direction.CW);
        p.addCircle(s * 0.78f, s * 0.64f + dy, s * 0.13f, Path.Direction.CW);
        p.addRoundRect(new RectF(s * 0.14f, s * 0.60f + dy, s * 0.91f, s * 0.78f + dy), s * 0.09f, s * 0.09f, Path.Direction.CW);
        fill.setColor(colour);
        c.drawPath(p, fill);
        fill.setColor(0xFFFFFFFF);
    }

    private void fog(Canvas c, float s) {
        cloud(c, s, -s * 0.14f, 0xFFFFFFFF);
        Paint l = new Paint(Paint.ANTI_ALIAS_FLAG);
        l.setStyle(Paint.Style.STROKE);
        l.setStrokeCap(Paint.Cap.ROUND);
        l.setStrokeWidth(s * 0.07f);
        l.setColor(0xFFC7C7CC);
        c.drawLine(s * 0.16f, s * 0.78f, s * 0.84f, s * 0.78f, l);
        c.drawLine(s * 0.26f, s * 0.92f, s * 0.74f, s * 0.92f, l);
    }

    private void drops(Canvas c, float s, int colour, boolean snow) {
        Paint d = new Paint(Paint.ANTI_ALIAS_FLAG);
        d.setColor(colour);
        d.setStyle(Paint.Style.STROKE);
        d.setStrokeCap(Paint.Cap.ROUND);
        d.setStrokeWidth(s * 0.07f);
        float[] xs = { 0.32f, 0.52f, 0.72f };
        for (float x : xs) {
            if (snow) {
                d.setStyle(Paint.Style.FILL);
                c.drawCircle(s * x, s * 0.84f, s * 0.05f, d);
            } else {
                c.drawLine(s * (x + 0.03f), s * 0.76f, s * (x - 0.03f), s * 0.92f, d);
            }
        }
    }

    private void bolt(Canvas c, float s) {
        Path p = new Path();
        p.moveTo(s * 0.54f, s * 0.66f);
        p.lineTo(s * 0.40f, s * 0.84f);
        p.lineTo(s * 0.50f, s * 0.84f);
        p.lineTo(s * 0.44f, s * 0.99f);
        p.lineTo(s * 0.62f, s * 0.78f);
        p.lineTo(s * 0.52f, s * 0.78f);
        p.close();
        fill.setColor(SUN_YELLOW);
        c.drawPath(p, fill);
        fill.setColor(0xFFFFFFFF);
    }

    private void tv(Canvas c, float s) {
        c.drawRoundRect(new RectF(s * 0.10f, s * 0.20f, s * 0.90f, s * 0.70f), s * 0.08f, s * 0.08f, stroke);
        c.drawLine(s * 0.34f, s * 0.86f, s * 0.66f, s * 0.86f, stroke);
    }

    @Override public void setAlpha(int a) { stroke.setAlpha(a); fill.setAlpha(a); }
    @Override public void setColorFilter(ColorFilter f) { stroke.setColorFilter(f); fill.setColorFilter(f); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    @Override public int getIntrinsicWidth() { return 64; }
    @Override public int getIntrinsicHeight() { return 64; }
}
