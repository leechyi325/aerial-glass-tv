package app.orchard.tv.legacy;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
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

    static BitmapDrawable load(Context context) {
        Bitmap result = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.RGB_565);
        Canvas canvas = new Canvas(result);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        int style = Preferences.get(context).getInt("wallpaper", 0);
        int[] colors = style == 1 ? new int[]{0xff402957, 0xff102c47}
                : style == 2 ? new int[]{0xff805239, 0xff241b3b} : new int[]{0xff16495b, 0xff111a35};
        paint.setShader(new LinearGradient(0, 0, WIDTH, HEIGHT, colors, null, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, WIDTH, HEIGHT, paint);
        paint.setShader(null);
        if (style == 3) {
            Bitmap photo = BitmapFactory.decodeFile(new File(context.getFilesDir(), "wallpaper.jpg").getPath());
            if (photo != null) {
                float scale = Math.max(WIDTH / (float) photo.getWidth(), HEIGHT / (float) photo.getHeight());
                int w = Math.round(photo.getWidth() * scale), h = Math.round(photo.getHeight() * scale);
                canvas.drawBitmap(photo, null, new Rect((WIDTH - w) / 2, (HEIGHT - h) / 2,
                        (WIDTH + w) / 2, (HEIGHT + h) / 2), paint);
                photo.recycle();
                canvas.drawColor(0x55000000);
            }
        }
        // One baked bitmap per frame; never blur or shade a full-screen surface while idle.
        return new BitmapDrawable(context.getResources(), result);
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
