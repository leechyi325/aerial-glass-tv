package app.orchard.tv.legacy;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

/** Keep the opt-in preference and our own receiver's package-manager state consistent. */
final class BootSetup {
    static boolean enabled(Context context) {
        return Preferences.get(context).getBoolean("boot", false);
    }

    static int receiverState(Context context) {
        return context.getPackageManager().getComponentEnabledSetting(new ComponentName(context, BootReceiver.class));
    }

    static boolean setEnabled(Context context, boolean enabled) {
        if (!Preferences.get(context).edit().putBoolean("boot", enabled).commit()) return false;
        return sync(context);
    }

    static boolean sync(Context context) {
        int wanted = enabled(context) ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        if (receiverState(context) == wanted) return true;
        try {
            // This changes only Aerial's own component. No secure settings or system app changes.
            context.getPackageManager().setComponentEnabledSetting(new ComponentName(context, BootReceiver.class),
                    wanted, PackageManager.DONT_KILL_APP);
            return receiverState(context) == wanted;
        } catch (SecurityException failure) {
            context.getSharedPreferences("boot_diagnostics", Context.MODE_PRIVATE).edit()
                    .putString("error", "receiver: " + failure.getClass().getSimpleName()).apply();
            return false;
        }
    }
}
