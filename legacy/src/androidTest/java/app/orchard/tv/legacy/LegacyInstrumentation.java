package app.orchard.tv.legacy;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.GridView;

/** Dependency-free device smoke tests, run against the same shrunk release used on the TV. */
public final class LegacyInstrumentation extends Instrumentation {
    private Activity home;
    private boolean aerial;

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        aerial = arguments != null && "true".equals(arguments.getString("aerial"));
        start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            Intent launch = getTargetContext().getPackageManager()
                    .getLaunchIntentForPackage(getTargetContext().getPackageName());
            check(launch != null, "Launcher entry missing");
            home = startActivitySync(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            waitForIdleSync();
            final GridView[] grid = new GridView[1];
            runOnMainSync(() -> grid[0] = find(home.getWindow().getDecorView(), GridView.class));
            check(grid[0] != null, "Application grid missing");
            long deadline = SystemClock.uptimeMillis() + 15_000;
            final int[] count = {0};
            while (SystemClock.uptimeMillis() < deadline && count[0] == 0) {
                runOnMainSync(() -> count[0] = grid[0].getCount());
                if (count[0] == 0) SystemClock.sleep(100);
            }
            check(count[0] > 1, "Installed apps were not loaded");
            waitForHomeFocus(true);
            runOnMainSync(() -> { grid[0].requestFocus(); grid[0].setSelection(0); });
            waitForIdleSync();
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT);
            waitForIdleSync();
            final int[] selected = {0};
            runOnMainSync(() -> selected[0] = grid[0].getSelectedItemPosition());
            check(selected[0] == 1, "D-pad right did not move to the next tile");
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_LEFT);
            waitForIdleSync();
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP);
            waitForIdleSync();
            final boolean[] toolbar = {false};
            runOnMainSync(() -> toolbar[0] = home.getCurrentFocus() instanceof Button);
            check(toolbar[0], "D-pad up cannot reach settings");
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);
            waitForIdleSync();
            runOnMainSync(() -> check(grid[0].hasFocus(), "D-pad down cannot return to apps"));
            runOnMainSync(() -> grid[0].setSelection(0));
            waitForIdleSync();
            for (int i = 0; i < 3; i++) {
                ActivityMonitor monitor = addMonitor("app.orchard.tv.legacy.ScreensaverActivity", null, false);
                sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
                Activity saver = waitForMonitorWithTimeout(monitor, 5000);
                removeMonitor(monitor);
                check(saver != null, "Screensaver did not open");
                waitForIdleSync();
                sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
                waitForIdleSync();
                // The waking OK must not launch the focused app.
                runOnMainSync(() -> check(home.hasWindowFocus(), "Wake key leaked through or home did not resume"));
            }
            // Use our own external probe app so vendor login screens cannot hijack the test.
            runOnMainSync(() -> {
                int probe = -1;
                for (int i = 0; i < grid[0].getCount(); i++) {
                    View tile = grid[0].getAdapter().getView(i, null, grid[0]);
                    if (getContext().getPackageName().equals(tile.getTag())) { probe = i; break; }
                }
                check(probe >= 0, "External probe app missing from catalogue");
                grid[0].setSelection(probe);
            });
            waitForIdleSync();
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
            waitForHomeFocus(false);
            waitForHomeFocus(true);
            waitForIdleSync();
            runOnMainSync(() -> grid[0].setSelection(0));
            waitForIdleSync();
            if (aerial) testAerial();
            result.putString("stream", "PASS: app enumeration, D-pad grid/toolbar navigation, app launch/return, 3 screensaver wake cycles on release APK"
                    + (aerial ? ", H.264 first frame" : "") + "\n");
            result.putInt("tests", 6);
            finish(Activity.RESULT_OK, result);
        } catch (Throwable failure) {
            result.putString("stream", "FAIL: " + android.util.Log.getStackTraceString(failure));
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private void waitForHomeFocus(boolean expected) {
        long deadline = SystemClock.uptimeMillis() + 5000;
        boolean[] focused = {!expected};
        while (SystemClock.uptimeMillis() < deadline) {
            runOnMainSync(() -> focused[0] = home.hasWindowFocus());
            if (focused[0] == expected) return;
            SystemClock.sleep(100);
        }
        throw new AssertionError("Home focus did not become " + expected + "; close system overlays and avoid concurrent remote input");
    }

    private void testAerial() {
        android.content.SharedPreferences prefs = getTargetContext().getSharedPreferences("legacy", 0);
        boolean existed = prefs.contains("screensaver");
        int previous = prefs.getInt("screensaver", 0);
        Activity saver = null;
        try {
            check(prefs.edit().putInt("screensaver", 1).commit(), "Could not select aerial for test");
            ActivityMonitor monitor = addMonitor("app.orchard.tv.legacy.ScreensaverActivity", null, false);
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            saver = waitForMonitorWithTimeout(monitor, 5000);
            removeMonitor(monitor);
            check(saver != null, "Aerial screensaver did not open");
            Activity active = saver;
            long deadline = SystemClock.uptimeMillis() + 75_000;
            String[] state = {""};
            while (SystemClock.uptimeMillis() < deadline) {
                runOnMainSync(() -> {
                    java.io.StringWriter buffer = new java.io.StringWriter();
                    active.dump("", null, new java.io.PrintWriter(buffer), new String[0]);
                    state[0] = buffer.toString();
                });
                if (state[0].contains("state=rendering")) return;
                if (state[0].contains("state=clock-fallback")) break;
                SystemClock.sleep(250);
            }
            throw new AssertionError("H.264 did not render: " + state[0]);
        } finally {
            if (existed) prefs.edit().putInt("screensaver", previous).commit();
            else prefs.edit().remove("screensaver").commit();
            if (saver != null) {
                Activity active = saver;
                runOnMainSync(active::finish);
                waitForIdleSync();
            }
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static <T extends View> T find(View root, Class<T> type) {
        if (type.isInstance(root)) return type.cast(root);
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                T found = find(group.getChildAt(i), type);
                if (found != null) return found;
            }
        }
        return null;
    }
}
