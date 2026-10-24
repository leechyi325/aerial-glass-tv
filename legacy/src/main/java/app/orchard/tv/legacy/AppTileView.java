package app.orchard.tv.legacy;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.View;
import android.widget.GridView;

final class AppTileView extends View {
    private final float scale;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final android.text.TextPaint label = new android.text.TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF card = new RectF();
    private final RectF outline = new RectF();
    private AppCatalog.Entry entry;
    private String displayName = "";
    private float focus, velocity, target;
    private long previousFrame;
    private boolean moving;
    private final boolean reducedMotion;

    AppTileView(Context context) {
        super(context);
        scale = context.getResources().getDisplayMetrics().widthPixels / 960f;
        label.setTextSize(13 * scale);
        label.setTextAlign(Paint.Align.CENTER);
        label.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        label.setShadowLayer(2 * scale, 0, scale, 0x70000000);
        reducedMotion = Settings.Global.getFloat(context.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1) == 0;
    }

    void bind(AppCatalog.Entry entry) {
        this.entry = entry;
        setContentDescription(entry.label);
        setTag(entry.component.getPackageName());
        updateLabel();
        refreshFocus();
        invalidate();
    }

    void refreshFocus() {
        float next = isSelected() && hasWindowFocus() && getParent() instanceof GridView
                && ((GridView) getParent()).hasFocus() ? 1 : 0;
        if (next == target) return;
        target = next;
        if (reducedMotion) { focus = target; velocity = 0; invalidate(); return; }
        if (!moving) { moving = true; previousFrame = SystemClock.uptimeMillis(); postOnAnimation(frame); }
    }

    @Override public void setSelected(boolean selected) { super.setSelected(selected); refreshFocus(); }

    // Critical damping, matching the original Compose focus spring. Preserve velocity on reversal.
    private final Runnable frame = new Runnable() {
        @Override public void run() {
            long now = SystemClock.uptimeMillis();
            float dt = Math.min(.032f, Math.max(.001f, (now - previousFrame) / 1000f));
            previousFrame = now;
            velocity += (300 * (target - focus) - 34.64f * velocity) * dt;
            focus += velocity * dt;
            if (Math.abs(target - focus) < .001f && Math.abs(velocity) < .01f) {
                focus = target; velocity = 0; moving = false;
            } else postOnAnimation(this);
            invalidate();
        }
    };

    @Override protected void onDetachedFromWindow() {
        removeCallbacks(frame); moving = false; focus = target; velocity = 0;
        super.onDetachedFromWindow();
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        card.set(0, 10 * scale, w, 10 * scale + w * 9f / 16);
        updateLabel();
    }

    @Override protected void onDraw(Canvas canvas) {
        if (entry == null) return;
        canvas.save();
        float zoom = 1 + .075f * focus;
        canvas.scale(zoom, zoom, card.centerX(), card.centerY());
        // Cheap soft contact shadow, only over the tile area, with no software layer.
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        for (int i = 3; i >= 1; i--) {
            outline.set(card); outline.offset(0, (2 + focus * 2) * scale);
            paint.setStrokeWidth((i * 3) * scale);
            paint.setColor((int) ((7 + 5 * focus) * (4 - i)) << 24);
            canvas.drawRoundRect(outline, 12 * scale, 12 * scale, paint);
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        canvas.drawBitmap(entry.tile, null, card, paint);
        if (focus > .001f) {
            paint.setColor(((int) (focus * 30) << 24) | 0xffffff);
            canvas.drawRoundRect(card, 12 * scale, 12 * scale, paint);
            outline.set(card); outline.inset(-2.7f * scale, -2.7f * scale);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.5f * scale);
            paint.setColor(((int) (focus * 225) << 24) | 0xffffff);
            canvas.drawRoundRect(outline, 14.5f * scale, 14.5f * scale, paint);
            paint.setStyle(Paint.Style.FILL);
        }
        canvas.restore();
        label.setColor(focus > .5f ? Color.WHITE : 0xccffffff);
        canvas.drawText(displayName, getWidth() / 2f, card.bottom + 21 * scale, label);
    }

    private void updateLabel() {
        if (entry != null && getWidth() > 0) displayName = android.text.TextUtils.ellipsize(entry.label,
                label, getWidth() - 4 * scale, android.text.TextUtils.TruncateAt.END).toString();
    }
}
