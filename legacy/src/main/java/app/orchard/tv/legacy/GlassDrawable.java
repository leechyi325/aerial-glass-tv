package app.orchard.tv.legacy;

import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;

/** Samples the actual wallpaper at the surface position; no screen capture or per-frame blur. */
final class GlassDrawable extends Drawable {
    private final View owner;
    private final float scale, radius;
    private final Paint texture = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix matrix = new Matrix();
    private final RectF shape = new RectF();
    private final int[] location = new int[2];
    private BitmapShader shader;
    private boolean focused;

    GlassDrawable(View owner, Backdrop.Scene scene, float corner, boolean dark) {
        this.owner = owner;
        scale = owner.getResources().getDisplayMetrics().widthPixels / 960f;
        radius = corner * scale;
        shader = new BitmapShader(dark ? scene.darkGlass : scene.glass, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        texture.setShader(shader);
        rim.setStyle(Paint.Style.STROKE);
        glow.setColor(0x28ffffff);
    }

    @Override protected void onBoundsChange(Rect bounds) {
        shape.set(bounds);
        shape.inset(scale, scale);
        sheen.setShader(new LinearGradient(0, shape.top, shape.width() * .35f, shape.bottom,
                new int[]{0x30ffffff, 0x08ffffff, 0x03000000, 0x12ffffff},
                new float[]{0, .3f, .72f, 1}, Shader.TileMode.CLAMP));
        rim.setShader(new LinearGradient(shape.left, shape.top, shape.right, shape.bottom,
                new int[]{0xb3ffffff, 0x35ffffff, 0x12ffffff, 0x65ffffff},
                new float[]{0, .28f, .67f, 1}, Shader.TileMode.CLAMP));
    }

    @Override public void draw(Canvas canvas) {
        owner.getLocationOnScreen(location);
        float width = owner.getResources().getDisplayMetrics().widthPixels;
        float height = owner.getResources().getDisplayMetrics().heightPixels;
        matrix.setScale(width / 320f * 1.025f, height / 180f * 1.025f);
        matrix.postTranslate(-location[0] - width * .0125f, -location[1] - height * .0125f);
        shader.setLocalMatrix(matrix);
        canvas.drawRoundRect(shape, radius, radius, texture);
        canvas.drawRoundRect(shape, radius, radius, sheen);
        rim.setStrokeWidth((focused ? 1.6f : .8f) * scale);
        canvas.drawRoundRect(shape, radius, radius, rim);
        if (focused) {
            canvas.drawRoundRect(shape, radius, radius, glow);
        }
    }

    @Override public boolean isStateful() { return true; }

    @Override protected boolean onStateChange(int[] state) {
        boolean next = false;
        for (int item : state) if (item == android.R.attr.state_focused || item == android.R.attr.state_pressed) next = true;
        if (next == focused) return false;
        focused = next;
        invalidateSelf();
        return true;
    }

    @Override public void setAlpha(int alpha) { texture.setAlpha(alpha); }
    @Override public void setColorFilter(ColorFilter filter) { texture.setColorFilter(filter); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
