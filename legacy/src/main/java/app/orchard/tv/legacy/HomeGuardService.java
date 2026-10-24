package app.orchard.tv.legacy;

import android.accessibilityservice.AccessibilityService;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import java.util.HashSet;
import java.util.Set;

/** Opt-in only: observes exact HOME activities, never filters remote keys or changes system settings. */
public final class HomeGuardService extends AccessibilityService {
    private final Set<ComponentName> homes = new HashSet<>();
    private long lastLaunch;
    private static boolean visitingStock;

    static void visitStock() { visitingStock = true; }
    static void returnedHome() { visitingStock = false; }

    @Override protected void onServiceConnected() {
        refreshHomes();
    }

    private void refreshHomes() {
        homes.clear();
        for (ResolveInfo info : AppCatalog.homes(this)) {
            if (info.priority >= 0 && !getPackageName().equals(info.activityInfo.packageName)) {
                homes.add(new ComponentName(info.activityInfo.packageName, info.activityInfo.name));
            }
        }
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (visitingStock || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                || event.getPackageName() == null || event.getClassName() == null) return;
        if (homes.isEmpty()) refreshHomes();
        ComponentName window = new ComponentName(event.getPackageName().toString(), event.getClassName().toString());
        PowerManager power = (PowerManager) getSystemService(POWER_SERVICE);
        if (power == null || !power.isScreenOn() || !homes.contains(window)) return;
        long now = SystemClock.uptimeMillis();
        if (now - lastLaunch < 1500) return;
        lastLaunch = now;
        startActivity(new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
    }

    @Override public void onInterrupt() { }
}
