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
    private final float radius;
    private final float borderWidth;
    private int factor = 1;
    private int screenLeft, screenTop;
    private boolean hasBlur;

    Glass(Bitmap blurSmall, int factor, int screenLeft, int screenTop, float radius, int tintColor, float borderWidth) {
        this.radius = radius;
        this.borderWidth = borderWidth;
        this.screenLeft = screenLeft;
        this.screenTop = screenTop;
        tint.setColor(tintColor);
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(borderWidth);
        border.setColor(0x38FFFFFF);
        fill.setColor(0xCC141A24);
        setBlur(blurSmall, factor);
    }

    void setBlur(Bitmap blurSmall, int factor) {
        this.factor = factor;
        hasBlur = blurSmall != null;
        if (hasBlur) fill.setShader(new BitmapShader(blurSmall, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        else fill.setShader(null);
        apply();
        invalidateSelf();
    }

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
    }

    @Override protected void onBoundsChange(android.graphics.Rect b) {
        super.onBoundsChange(b);
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
