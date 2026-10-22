package dev.lucas.tvhome;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/**
 * Frosted glass: the blurred wallpaper behind the panel, a dark tint, a soft
 * highlight along the top and a hairline border. The blur is one small bitmap of
 * the whole screen; the panel's position on screen maps the right part of it.
 */
final class Glass extends Drawable {

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint tint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Matrix matrix = new Matrix();
    private float radius;
    private final float borderWidth;
    private int factor = 1;
    private int screenLeft, screenTop;
    private boolean hasBlur;
    private Bitmap blur;
    private int lightTint, darkTint;
    private boolean adaptive;

    Glass(Bitmap blurSmall, int factor, int screenLeft, int screenTop, float radius, int tintColor, float borderWidth) {
        this.radius = radius;
        this.borderWidth = borderWidth;
        this.screenLeft = screenLeft;
        this.screenTop = screenTop;
        tint.setColor(tintColor);
        lightTint = tintColor;
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(borderWidth);
        border.setColor(0x38FFFFFF);
        fill.setColor(0xCC141A24);
        setBlur(blurSmall, factor);
    }

    void setBlur(Bitmap blurSmall, int factor) {
        this.factor = factor;
        hasBlur = blurSmall != null;
        blur = blurSmall;
        if (hasBlur) fill.setShader(new BitmapShader(blurSmall, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        else fill.setShader(null);
        apply();
        invalidateSelf();
    }

    void setBorderColor(int c) { border.setColor(c); invalidateSelf(); }

    void setRadius(float r) { radius = r; invalidateSelf(); }

    /**
     * Adaptive material: over a bright part of the photo the glass darkens towards darkTint,
     * so white glyphs on it stay readable; over a dark part it stays light.
     */
    void setAdaptive(int darkTint) { this.darkTint = darkTint; adaptive = true; apply(); invalidateSelf(); }

    void setScreenPosition(int left, int top) {
        screenLeft = left;
        screenTop = top;
        apply();
        invalidateSelf();
    }

    private void apply() {
        if (!hasBlur || fill.getShader() == null) return;
        matrix.setScale(factor, factor);
        matrix.postTranslate(-screenLeft, -screenTop);
        fill.getShader().setLocalMatrix(matrix);
        if (adaptive) tint.setColor(mix(lightTint, darkTint, brightness()));
    }

    /** 0 for a dark area behind the panel, 1 for a bright one. */
    private float brightness() {
        android.graphics.Rect b = getBounds();
        if (blur == null || blur.isRecycled() || b.isEmpty()) return 0f;
        int x0 = Math.max(0, screenLeft / factor), y0 = Math.max(0, screenTop / factor);
        int x1 = Math.min(blur.getWidth(), (screenLeft + b.width()) / factor + 1);
        int y1 = Math.min(blur.getHeight(), (screenTop + b.height()) / factor + 1);
        if (x1 <= x0 || y1 <= y0) return 0f;
        long sum = 0; int n = 0;
        for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) {
            int v = blur.getPixel(x, y);
            sum += (((v >> 16) & 255) * 299 + ((v >> 8) & 255) * 587 + (v & 255) * 114) / 1000;
            n++;
        }
        float lum = sum / (float) n;
        return Math.max(0f, Math.min(1f, (lum - 105f) / 95f));
    }

    private static int mix(int a, int b, float t) {
        int aa = (a >>> 24), ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int ba = (b >>> 24), br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return Math.round(aa + (ba - aa) * t) << 24 | Math.round(ar + (br - ar) * t) << 16
                | Math.round(ag + (bg - ag) * t) << 8 | Math.round(ab + (bb - ab) * t);
    }

    @Override protected void onBoundsChange(android.graphics.Rect b) {
        super.onBoundsChange(b);
        apply();
        shine.setShader(new LinearGradient(0, b.top, 0, b.top + b.height() * 0.45f,
                0x2EFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
    }

    @Override public void draw(Canvas c) {
        rect.set(getBounds());
        c.drawRoundRect(rect, radius, radius, fill);
        c.drawRoundRect(rect, radius, radius, tint);
        c.drawRoundRect(rect, radius, radius, shine);
        if (borderWidth > 0) {
            float in = borderWidth / 2f;
            rect.inset(in, in);
            c.drawRoundRect(rect, radius - in, radius - in, border);
        }
    }

    /** Rounded outline, so a view with clipToOutline clips to the glass shape, corners included. */
    @Override public void getOutline(android.graphics.Outline outline) {
        outline.setRoundRect(getBounds(), radius);
    }

    @Override public void setAlpha(int a) { fill.setAlpha(a); tint.setAlpha(a); shine.setAlpha(a); border.setAlpha(a); }
    @Override public void setColorFilter(ColorFilter f) { fill.setColorFilter(f); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
