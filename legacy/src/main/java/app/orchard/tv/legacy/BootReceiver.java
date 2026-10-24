package app.orchard.tv.legacy;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        android.content.SharedPreferences diagnostics = context.getSharedPreferences("boot_diagnostics", Context.MODE_PRIVATE);
        diagnostics.edit().putString("action", intent.getAction())
                .putLong("received_elapsed", android.os.SystemClock.elapsedRealtime())
                .putString("result", "disabled").putString("error", "none").apply();
        if (BootSetup.enabled(context)) {
            try {
                context.startActivity(new Intent(context, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
                diagnostics.edit().putString("result", "activity-requested").apply();
            } catch (RuntimeException failure) {
                diagnostics.edit().putString("error", failure.getClass().getSimpleName()).apply();
            }
        }
    }
}
