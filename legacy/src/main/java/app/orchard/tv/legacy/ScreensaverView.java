package app.orchard.tv.legacy;

import android.content.Context;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.os.Handler;
import android.text.format.DateFormat;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

/** A single platform decoder, with a visible clock throughout prepare/failure/rotation. */
final class ScreensaverView extends FrameLayout implements SurfaceHolder.Callback {
    private static String playbackState = "clock";
    private static int renderStarts;
    private static String lastError = "none";

    static String diagnostics() {
        return "state=" + playbackState + " renderStarts=" + renderStarts + " lastError=" + lastError;
    }
    private final Handler handler = new Handler();
    private final TextView clock, caption;
    private final SurfaceView surface;
    private MediaPlayer player;
    private JSONArray videos;
    private int index;
    private int failures;
    private boolean attached;
    private boolean surfaceReady;
    private boolean triedHttp;
    private String url;
    private final Runnable timeout = () -> failPlayback(false);
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!attached) return;
            clock.setText(DateFormat.getTimeFormat(getContext()).format(new Date()));
            // Move the clock once per minute to avoid keeping bright pixels in one position.
            long minute = System.currentTimeMillis() / 60_000;
            FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) clock.getLayoutParams();
            p.gravity = (minute % 2 == 0 ? Gravity.TOP : Gravity.BOTTOM)
                    | (minute % 4 < 2 ? Gravity.START : Gravity.END);
            clock.setLayoutParams(p);
            handler.postDelayed(this, 60_000 - System.currentTimeMillis() % 60_000);
        }
    };

    ScreensaverView(Context context) {
        super(context);
        setBackground(Backdrop.load(context));
        surface = new SurfaceView(context);
        surface.setVisibility(View.GONE);
        addView(surface, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        surface.getHolder().addCallback(this);
        float scale = getResources().getDisplayMetrics().widthPixels / 960f;
        clock = new TextView(context);
        clock.setTextColor(Color.WHITE);
        clock.setTextSize(TypedValue.COMPLEX_UNIT_PX, 64 * scale);
        clock.setShadowLayer(4 * scale, 0, 2 * scale, Color.BLACK);
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        cp.setMargins((int) (56 * scale), (int) (48 * scale), (int) (56 * scale), (int) (48 * scale));
        addView(clock, cp);
        caption = new TextView(context);
        caption.setTextColor(Color.WHITE);
        caption.setTextSize(TypedValue.COMPLEX_UNIT_PX, 17 * scale);
        caption.setShadowLayer(3 * scale, 0, scale, Color.BLACK);
        FrameLayout.LayoutParams label = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        label.setMargins((int) (56 * scale), 0, (int) (56 * scale), (int) (18 * scale));
        addView(caption, label);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        playbackState = "clock";
        tick.run();
        if (Preferences.get(getContext()).getInt("screensaver", 0) == 1) {
            try {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                try (InputStream in = getContext().getAssets().open("aerials.json")) {
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = in.read(buffer)) != -1) bytes.write(buffer, 0, count);
                }
                videos = new JSONObject(bytes.toString("UTF-8")).getJSONArray("videos");
                index = new Random().nextInt(videos.length());
                surface.setVisibility(View.VISIBLE);
            } catch (IOException | JSONException | IllegalArgumentException failure) {
                Log.w("AerialLegacy", "Aerial catalogue unavailable", failure);
                caption.setText(R.string.video_failed);
            }
        }
    }

    @Override protected void onDetachedFromWindow() {
        attached = false;
        handler.removeCallbacksAndMessages(null);
        releasePlayer();
        super.onDetachedFromWindow();
    }

    @Override public void surfaceCreated(SurfaceHolder holder) {
        surfaceReady = true;
        play(false);
    }

    @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) { }

    @Override public void surfaceDestroyed(SurfaceHolder holder) {
        surfaceReady = false;
        releasePlayer();
    }

    private void play(boolean http) {
        releasePlayer();
        if (!attached || !surfaceReady || videos == null || failures >= 3) return;
        try {
            JSONObject video = videos.getJSONObject(index);
            url = video.getString("url");
            // Only public Apple video files may use HTTP on old firmware with an obsolete CA store.
            // Certificate verification is never disabled, and user photos never leave the device.
            if (http && url.startsWith("https://sylvan.apple.com/")) url = "http://" + url.substring(8);
            caption.setText(R.string.video_loading);
            playbackState = http ? "preparing-http" : "preparing-https";
            player = new MediaPlayer();
            MediaPlayer current = player;
            current.setDisplay(surface.getHolder());
            current.setVolume(0, 0);
            current.setOnPreparedListener(mp -> {
                if (player != mp || !attached || !surfaceReady) return;
                // A separate first-frame timeout covers decoders that prepare but never render.
                handler.removeCallbacks(timeout);
                handler.postDelayed(timeout, 20_000);
                mp.start();
                playbackState = "started-awaiting-frame";
            });
            current.setOnInfoListener((mp, what, extra) -> {
                if (player == mp && what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                    handler.removeCallbacks(timeout);
                    playbackState = "rendering";
                    renderStarts++;
                    caption.setText(captionFor(video));
                    Log.i("AerialLegacy", "H.264 first frame rendered");
                }
                return false;
            });
            current.setOnVideoSizeChangedListener((mp, width, height) -> {
                if (width <= 0 || height <= 0 || getWidth() <= 0 || getHeight() <= 0) return;
                float scale = Math.min(getWidth() / (float) width, getHeight() / (float) height);
                surface.setLayoutParams(new FrameLayout.LayoutParams(Math.round(width * scale), Math.round(height * scale), Gravity.CENTER));
            });
            current.setOnCompletionListener(mp -> {
                failures = 0;
                index = (index + 1) % videos.length();
                triedHttp = false;
                play(false);
            });
            current.setOnErrorListener((mp, what, extra) -> {
                if (player == mp) {
                    Log.w("AerialLegacy", "Playback error " + what + "/" + extra);
                    lastError = what + "/" + extra;
                    // -1004 is I/O; decoding/container failures should try another H.264 file.
                    handler.post(() -> { if (player == mp) failPlayback(extra == -1004); });
                }
                return true;
            });
            current.setDataSource(url);
            current.prepareAsync();
            handler.postDelayed(timeout, 20_000);
        } catch (IOException | JSONException | RuntimeException failure) {
            Log.w("AerialLegacy", "Cannot prepare H.264 video", failure);
            lastError = failure.getClass().getSimpleName();
            handler.post(() -> failPlayback(true));
        }
    }

    private String captionFor(JSONObject video) {
        JSONArray captions = video.optJSONArray("captions");
        if (captions != null && captions.length() > 0) {
            JSONArray first = captions.optJSONArray(0);
            if (first != null) return first.optString("zh".equals(Locale.getDefault().getLanguage()) ? 1 : 2, video.optString("label"));
        }
        return video.optString("label");
    }

    private void failPlayback(boolean networkFailure) {
        if (!attached || !surfaceReady) return;
        boolean retryHttp = networkFailure && !triedHttp && url != null && url.startsWith("https://sylvan.apple.com/");
        triedHttp |= retryHttp;
        releasePlayer();
        if (retryHttp) { play(true); return; }
        failures++;
        if (failures < 3 && videos != null) {
            index = (index + 1) % videos.length();
            triedHttp = false;
            play(false);
        } else {
            surface.setVisibility(View.GONE);
            caption.setText(R.string.video_failed);
            playbackState = "clock-fallback";
        }
    }

    private void releasePlayer() {
        handler.removeCallbacks(timeout);
        if (player != null) {
            MediaPlayer old = player;
            player = null;
            old.setOnPreparedListener(null);
            old.setOnErrorListener(null);
            old.setOnCompletionListener(null);
            old.setOnInfoListener(null);
            old.setOnVideoSizeChangedListener(null);
            old.release();
        }
    }
}
