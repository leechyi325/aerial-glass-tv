package app.orchard.tv.legacy;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

final class Backdrop {
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static Scene cached;
    private static String cachedKey;

    static final class Scene {
        final Bitmap wallpaper;
        final Bitmap glass;
        final Bitmap darkGlass;
        final Bitmap grid;

        Scene(Bitmap wallpaper) {
            this.wallpaper = wallpaper;
            Bitmap small = Bitmap.createScaledBitmap(wallpaper, 320, 180, true);
            int[] pixels = new int[320 * 180];
            small.getPixels(pixels, 0, 320, 0, 0, 320, 180);
            int[] scratch = new int[pixels.length];
            for (int i = 0; i < 3; i++) {
                blur(pixels, scratch, 320, 180, 7, true);
                blur(scratch, pixels, 320, 180, 7, false);
            }
            glass = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888);
            darkGlass = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888);
            int[] dark = new int[pixels.length];
            for (int i = 0; i < pixels.length; i++) {
                int c = pixels[i], r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
                dark[i] = 0xff000000 | ((r * 42 / 100 + 5) << 16)
                        | ((g * 42 / 100 + 7) << 8) | (b * 42 / 100 + 12);
                // A restrained milky tint, baked once; the spatial wallpaper colors remain visible.
                pixels[i] = 0xff000000 | ((r * 77 / 100 + 37) << 16)
                        | ((g * 77 / 100 + 39) << 8) | (b * 77 / 100 + 44);
            }
            glass.setPixels(pixels, 0, 320, 0, 0, 320, 180);
            darkGlass.setPixels(dark, 0, 320, 0, 0, 320, 180);
            small.recycle();
            grid = Bitmap.createScaledBitmap(darkGlass, WIDTH, HEIGHT, true);
        }
    }

    static synchronized Scene scene(Context context) {
        int style = Preferences.get(context).getInt("wallpaper", 0);
        String key = style + ":" + new File(context.getFilesDir(), "wallpaper.jpg").lastModified();
        if (cached == null || !key.equals(cachedKey)) {
            cached = new Scene(render(context, style));
            cachedKey = key;
        }
        return cached;
    }

    static BitmapDrawable load(Context context) {
        return new BitmapDrawable(context.getResources(), scene(context).wallpaper);
    }

    private static Bitmap render(Context context, int style) {
        Bitmap result = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        // Native counterpart of the original project's layered hills and dunes, not Apple artwork.
        int[] sky = style == 1 ? new int[]{0xff26365b, 0xffb77d95, 0xffeab79c}
                : style == 2 ? new int[]{0xff557eaf, 0xffc1c7ce, 0xfff5d4ac}
                : new int[]{0xff4d6995, 0xffaaa9c2, 0xffedd0bc};
        paint.setShader(new LinearGradient(0, 0, 0, HEIGHT * .72f, sky, null, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, WIDTH, HEIGHT, paint);
        paint.setShader(new RadialGradient(WIDTH * .77f, HEIGHT * .40f, WIDTH * .46f,
                new int[]{0x60ffe2bf, 0x00ffe2bf}, null, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, WIDTH, HEIGHT, paint);
        int[][] layers = style == 1
                ? new int[][]{{0xff866781, 0xff685773}, {0xff695570, 0xff3c3c60}, {0xff41435f, 0xff242d4b}, {0xff29364f, 0xff101c34}}
                : style == 2
                ? new int[][]{{0xffe5bc91, 0xffcb966e}, {0xffd4a078, 0xffaa7352}, {0xffb47d5e, 0xff704a3c}, {0xff81573f, 0xff443529}}
                : new int[][]{{0xff9a96b8, 0xff6b7599}, {0xff747b9d, 0xff3e527a}, {0xff4c6389, 0xff233b64}, {0xff2d4c76, 0xff152c4f}};
        for (int layer = 0; layer < layers.length; layer++) {
            float base = HEIGHT * (.49f + .12f * layer);
            Path ridge = new Path();
            ridge.moveTo(0, HEIGHT);
            for (int step = 0; step <= 180; step++) {
                float x = step / 180f;
                float wave = (float) (Math.sin(x * 3.3 + layer * 1.3) * .64
                        + Math.sin(x * 5.8 + layer * .7) * .27 + Math.sin(x * 9.5 + 1) * .09);
                ridge.lineTo(x * WIDTH, base + wave * HEIGHT * (.075f - layer * .008f));
            }
            ridge.lineTo(WIDTH, HEIGHT);
            ridge.close();
            paint.setShader(new LinearGradient(0, base - HEIGHT * .12f, WIDTH * .7f, HEIGHT,
                    layers[layer], null, Shader.TileMode.CLAMP));
            canvas.drawPath(ridge, paint);
        }
        paint.setShader(null);
        if (style == 3) {
            Bitmap photo = BitmapFactory.decodeFile(new File(context.getFilesDir(), "wallpaper.jpg").getPath());
            if (photo != null) {
                float scale = Math.max(WIDTH / (float) photo.getWidth(), HEIGHT / (float) photo.getHeight());
                int w = Math.round(photo.getWidth() * scale), h = Math.round(photo.getHeight() * scale);
                canvas.drawBitmap(photo, null, new Rect((WIDTH - w) / 2, (HEIGHT - h) / 2,
                        (WIDTH + w) / 2, (HEIGHT + h) / 2), paint);
                photo.recycle();
                // Keep the photo prominent; a baked vignette protects the header and dock text.
            }
        }
        paint.setShader(new LinearGradient(0, 0, 0, HEIGHT,
                new int[]{0x28061120, 0x00061120, 0x55061120}, new float[]{0, .45f, 1}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, WIDTH, HEIGHT, paint);
        dither(result);
        // One baked bitmap per frame; never blur or shade a full-screen surface while idle.
        return result;
    }

    private static void blur(int[] source, int[] output, int width, int height, int radius, boolean horizontal) {
        int lines = horizontal ? height : width, length = horizontal ? width : height;
        int step = horizontal ? 1 : width, divisor = radius * 2 + 1;
        for (int line = 0; line < lines; line++) {
            int base = horizontal ? line * width : line;
            int r = 0, g = 0, b = 0;
            for (int i = -radius; i <= radius; i++) {
                int c = source[base + Math.max(0, Math.min(length - 1, i)) * step];
                r += (c >> 16) & 255; g += (c >> 8) & 255; b += c & 255;
            }
            for (int i = 0; i < length; i++) {
                output[base + i * step] = 0xff000000 | ((r / divisor) << 16) | ((g / divisor) << 8) | (b / divisor);
                int leaving = source[base + Math.max(0, i - radius) * step];
                int entering = source[base + Math.min(length - 1, i + radius + 1) * step];
                r += ((entering >> 16) & 255) - ((leaving >> 16) & 255);
                g += ((entering >> 8) & 255) - ((leaving >> 8) & 255);
                b += (entering & 255) - (leaving & 255);
            }
        }
    }

    private static void dither(Bitmap bitmap) {
        int[] row = new int[WIDTH];
        int seed = 37;
        for (int y = 0; y < HEIGHT; y++) {
            bitmap.getPixels(row, 0, WIDTH, 0, y, WIDTH, 1);
            for (int x = 0; x < WIDTH; x++) {
                seed = seed * 1664525 + 1013904223;
                int noise = (seed >>> 30) - 1, c = row[x];
                int r = Math.max(0, Math.min(255, ((c >> 16) & 255) + noise));
                int g = Math.max(0, Math.min(255, ((c >> 8) & 255) + noise));
                int b = Math.max(0, Math.min(255, (c & 255) + noise));
                row[x] = 0xff000000 | (r << 16) | (g << 8) | b;
            }
            bitmap.setPixels(row, 0, WIDTH, 0, y, WIDTH, 1);
        }
    }

    static void importPhoto(Context context, Uri uri) throws IOException {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IOException("No image stream");
            BitmapFactory.decodeStream(in, null, options);
        }
        if (options.outWidth <= 0 || options.outHeight <= 0) throw new IOException("Invalid image");
        options.inSampleSize = 1;
        while (options.outWidth / options.inSampleSize > WIDTH * 2
                || options.outHeight / options.inSampleSize > HEIGHT * 2) options.inSampleSize *= 2;
        options.inJustDecodeBounds = false;
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        Bitmap image;
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            image = BitmapFactory.decodeStream(in, null, options);
        }
        if (image == null) throw new IOException("Image decode failed");
        File temporary = new File(context.getFilesDir(), "wallpaper.tmp");
        try {
            try (FileOutputStream out = new FileOutputStream(temporary)) {
                if (!image.compress(Bitmap.CompressFormat.JPEG, 90, out)) throw new IOException("Image save failed");
            }
            if (!temporary.renameTo(new File(context.getFilesDir(), "wallpaper.jpg"))) throw new IOException("Image replace failed");
            Preferences.get(context).edit().putInt("wallpaper", 3).apply();
        } finally {
            image.recycle();
            if (temporary.exists() && !temporary.delete()) temporary.deleteOnExit();
        }
    }
}
