package app.orchard.tv.legacy;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import java.util.Locale;

/** Baked wide tiles; no icon processing or shader construction during focus animation. */
final class TileArt {
    static Bitmap render(String pkg, Bitmap icon, boolean home) {
        String name = pkg.toLowerCase(Locale.ROOT);
        String glyph = home ? "home" : name.contains("calculator") ? "calculator"
                : name.contains("browser") ? "browser" : name.contains("settings") ? "settings"
                : name.contains("calendar") ? "calendar" : name.contains("search") ? "search"
                : name.contains("download") ? "download" : name.contains("clock") ? "clock"
                : name.contains("account") ? "account" : "";
        int color = glyph.equals("browser") ? 0xff1677d6 : glyph.equals("calculator") ? 0xffcc8028
                : glyph.equals("calendar") ? 0xffdb6476 : glyph.equals("settings") ? 0xff677992
                : glyph.equals("search") ? 0xff6453bc : glyph.equals("clock") ? 0xff253044
                : glyph.equals("download") ? 0xff27a489 : glyph.equals("account") ? 0xff269ea9
                : home ? 0xff526ebe : dominant(icon);
        Bitmap bitmap = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        RectF rect = new RectF(0, 0, 320, 180);
        paint.setShader(new LinearGradient(0, 0, 290, 210,
                shade(color, 1.23f), shade(color, .68f), Shader.TileMode.CLAMP));
        canvas.drawRoundRect(rect, 24, 24, paint);
        paint.setShader(null);
        if (!glyph.isEmpty()) {
            canvas.save();
            canvas.translate(112, 42);
            canvas.scale(1.5f, 1.5f);
            drawGlyph(canvas, paint, glyph);
            canvas.restore();
        } else if (icon != null) {
            // Lift flat icon backgrounds into the wide brand-colored tile where possible.
            Bitmap foreground = removeFlatBackground(icon);
            canvas.drawBitmap(foreground, null, new RectF(104, 34, 216, 146), paint);
            if (foreground != icon) foreground.recycle();
        }
        paint.setShader(new LinearGradient(0, 0, 100, 180, 0x25ffffff, 0x00ffffff, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(rect, 24, 24, paint);
        paint.setShader(null);
        paint.setColor(0x33ffffff);
        paint.setStrokeWidth(1);
        paint.setStyle(Paint.Style.STROKE);
        rect.inset(.7f, .7f);
        canvas.drawRoundRect(rect, 24, 24, paint);
        return bitmap;
    }

    private static int shade(int color, float factor) {
        return Color.rgb(Math.min(255, Math.round(Color.red(color) * factor)),
                Math.min(255, Math.round(Color.green(color) * factor)), Math.min(255, Math.round(Color.blue(color) * factor)));
    }

    private static int dominant(Bitmap icon) {
        if (icon == null) return 0xff425875;
        int[] histogram = new int[512];
        int best = 0, maximum = 0;
        for (int y = 0; y < icon.getHeight(); y += 4) for (int x = 0; x < icon.getWidth(); x += 4) {
            int c = icon.getPixel(x, y), r = Color.red(c), g = Color.green(c), b = Color.blue(c);
            if (Color.alpha(c) < 180 || Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b)) < 25) continue;
            int bin = (r >> 5) * 64 + (g >> 5) * 8 + (b >> 5);
            if (++histogram[bin] > maximum) { maximum = histogram[bin]; best = bin; }
        }
        return maximum == 0 ? 0xff425875 : Color.rgb((best / 64) * 32 + 16, (best / 8 % 8) * 32 + 16, (best % 8) * 32 + 16);
    }

    private static Bitmap removeFlatBackground(Bitmap icon) {
        int w = icon.getWidth(), h = icon.getHeight();
        int background = icon.getPixel(w / 2, Math.min(3, h - 1));
        if (Color.alpha(background) < 240) return icon;
        int matching = 0;
        for (int i = 0; i < 16; i++) {
            int x = i * (w - 1) / 15;
            if (distance(icon.getPixel(x, Math.min(3, h - 1)), background) < 35) matching++;
            if (distance(icon.getPixel(x, Math.max(0, h - 4)), background) < 35) matching++;
        }
        if (matching < 25) return icon;
        int[] pixels = new int[w * h];
        icon.getPixels(pixels, 0, w, 0, 0, w, h);
        for (int i = 0; i < pixels.length; i++) {
            int c = pixels[i], d = distance(c, background);
            int alpha = Color.alpha(c) * Math.max(0, Math.min(45, d - 18)) / 45;
            pixels[i] = (c & 0xffffff) | (alpha << 24);
        }
        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888);
    }

    private static int distance(int a, int b) {
        return Math.abs(Color.red(a) - Color.red(b)) + Math.abs(Color.green(a) - Color.green(b)) + Math.abs(Color.blue(a) - Color.blue(b));
    }

    private static void drawGlyph(Canvas c, Paint p, String glyph) {
        p.setColor(Color.WHITE);
        p.setStrokeWidth(3.2f);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setStyle(Paint.Style.STROKE);
        switch (glyph) {
            case "search": c.drawCircle(27, 26, 16, p); c.drawLine(40, 39, 54, 53, p); break;
            case "browser":
                c.drawCircle(32, 32, 25, p);
                Path needle = new Path(); needle.moveTo(43, 18); needle.lineTo(37, 37);
                needle.lineTo(21, 46); needle.lineTo(27, 27); needle.close(); c.drawPath(needle, p); break;
            case "calculator":
                c.drawRoundRect(new RectF(13, 5, 51, 59), 5, 5, p);
                c.drawLine(21, 18, 43, 18, p);
                for (int y = 31; y <= 47; y += 16) for (int x = 23; x <= 41; x += 18) {
                    c.drawLine(x - 3, y, x + 3, y, p); if (y == 31 && x == 23) c.drawLine(x, y - 3, x, y + 3, p);
                }
                break;
            case "calendar":
                c.drawRoundRect(new RectF(8, 13, 56, 57), 5, 5, p);
                c.drawLine(8, 25, 56, 25, p); c.drawLine(20, 7, 20, 18, p); c.drawLine(44, 7, 44, 18, p);
                p.setStyle(Paint.Style.FILL);
                for (int y = 35; y <= 45; y += 10) for (int x = 20; x <= 44; x += 12) c.drawCircle(x, y, 2, p);
                break;
            case "download":
                c.drawLine(32, 7, 32, 41, p); c.drawLine(20, 30, 32, 42, p); c.drawLine(44, 30, 32, 42, p);
                c.drawLine(10, 46, 10, 56, p); c.drawLine(10, 56, 54, 56, p); c.drawLine(54, 56, 54, 46, p); break;
            case "clock": c.drawCircle(32, 32, 25, p); c.drawLine(32, 15, 32, 33, p); c.drawLine(32, 33, 43, 40, p); break;
            case "settings":
                for (int i = 0; i < 8; i++) {
                    c.save(); c.rotate(i * 45, 32, 32); c.drawLine(32, 5, 32, 13, p); c.restore();
                }
                c.drawCircle(32, 32, 19, p); c.drawCircle(32, 32, 7, p); break;
            case "account":
                c.drawCircle(32, 19, 11, p); c.drawArc(new RectF(10, 34, 54, 72), 185, 170, false, p);
                c.drawLine(10, 50, 54, 50, p); break;
            default:
                Path house = new Path(); house.moveTo(7, 29); house.lineTo(32, 8); house.lineTo(57, 29);
                house.moveTo(14, 26); house.lineTo(14, 56); house.lineTo(27, 56); house.lineTo(27, 40);
                house.lineTo(38, 40); house.lineTo(38, 56); house.lineTo(50, 56); house.lineTo(50, 26);
                c.drawPath(house, p); break;
        }
        p.setStyle(Paint.Style.FILL);
    }
}
