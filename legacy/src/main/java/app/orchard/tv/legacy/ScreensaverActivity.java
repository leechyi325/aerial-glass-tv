package app.orchard.tv.legacy;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

public final class ScreensaverActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setBackgroundDrawable(null);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    @Override protected void onResume() {
        super.onResume();
        setContentView(new ScreensaverView(this));
    }

    @Override protected void onPause() {
        // Removing the view releases the decoder even when the process stays alive in standby.
        setContentView(new View(this));
        super.onPause();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        // Consume the entire waking press so it cannot also launch a tile on the home screen.
        if (event.getAction() == KeyEvent.ACTION_UP) finish();
        return true;
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP) finish();
        return true;
    }

    @Override public void dump(String prefix, java.io.FileDescriptor fd, java.io.PrintWriter writer, String[] args) {
        super.dump(prefix, fd, writer, args);
        writer.println(prefix + "Aerial Legacy screensaver: " + ScreensaverView.diagnostics());
    }
}
