package app.orchard.tv.legacy;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.widget.Button;

final class ControlButton extends Button {
    private final float scale;
    private final Paint icon = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF track = new RectF();

    ControlButton(Context context) {
        super(context);
        scale = getResources().getDisplayMetrics().widthPixels / 960f;
        setPadding(0, 0, 0, 0);
        setMinWidth(0); setMinimumWidth(0);
        setContentDescription(getResources().getString(R.string.settings));
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.save();
        canvas.translate(getWidth() / 2f - 11 * scale, getHeight() / 2f - 8 * scale);
        canvas.scale(scale, scale);
        icon.setStyle(Paint.Style.STROKE);
        icon.setStrokeWidth(1.5f);
        icon.setColor(0xeeffffff);
        track.set(0, 0, 22, 6); canvas.drawRoundRect(track, 3, 3, icon);
        track.offset(0, 10); canvas.drawRoundRect(track, 3, 3, icon);
        icon.setStyle(Paint.Style.FILL);
        canvas.drawCircle(16, 3, 1.7f, icon); canvas.drawCircle(6, 13, 1.7f, icon);
        canvas.restore();
    }
}
