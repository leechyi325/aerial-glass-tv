package app.orchard.tv.legacy;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.Log;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class AppCatalog {
    static final class Entry {
        final ComponentName component;
        final String label;
        final Bitmap icon;
        final boolean stockHome;

        Entry(ComponentName component, String label, Bitmap icon, boolean stockHome) {
            this.component = component;
            this.label = label;
            this.icon = icon;
            this.stockHome = stockHome;
        }
    }

    static List<ResolveInfo> homes(Context context) {
        return context.getPackageManager().queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0);
    }

    static List<Entry> load(Context context) {
        PackageManager pm = context.getPackageManager();
        Map<String, ResolveInfo> apps = new LinkedHashMap<>();
        for (String category : new String[]{"android.intent.category.LEANBACK_LAUNCHER", Intent.CATEGORY_LAUNCHER}) {
            for (ResolveInfo info : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category), 0)) {
                if (info.activityInfo.exported && !apps.containsKey(info.activityInfo.packageName)) {
                    apps.put(info.activityInfo.packageName, info);
                }
            }
        }
        Map<String, ComponentName> homes = new LinkedHashMap<>();
        for (ResolveInfo info : homes(context)) {
            if (info.priority < 0 || !info.activityInfo.exported) continue;
            homes.put(info.activityInfo.packageName, new ComponentName(info.activityInfo.packageName, info.activityInfo.name));
            if (!apps.containsKey(info.activityInfo.packageName)) apps.put(info.activityInfo.packageName, info);
        }
        apps.remove(context.getPackageName());
        List<Entry> result = new ArrayList<>();
        for (ResolveInfo info : apps.values()) {
            String pkg = info.activityInfo.packageName;
            if (Preferences.hidden(context).contains(pkg)) continue;
            Bitmap icon = null;
            String label = pkg;
            try {
                label = info.loadLabel(pm).toString();
                Drawable drawable = info.loadIcon(pm);
                if (drawable == null) drawable = pm.getDefaultActivityIcon();
                icon = Bitmap.createBitmap(112, 112, Bitmap.Config.ARGB_8888);
                drawable.setBounds(0, 0, 112, 112);
                drawable.draw(new Canvas(icon));
            } catch (RuntimeException failure) {
                // A broken vendor icon must not remove an otherwise launchable app.
                Log.w("AerialLegacy", "Icon unavailable: " + pkg, failure);
            }
            boolean stock = homes.containsKey(pkg);
            ComponentName component = stock ? homes.get(pkg) : new ComponentName(pkg, info.activityInfo.name);
            result.add(new Entry(component, label, icon, stock));
        }
        final List<String> order = Preferences.order(context);
        final Collator collator = Collator.getInstance();
        Collections.sort(result, (a, b) -> {
            int left = order.indexOf(a.component.getPackageName());
            int right = order.indexOf(b.component.getPackageName());
            if (left != right) {
                if (left < 0) return 1;
                if (right < 0) return -1;
                return left - right;
            }
            return collator.compare(a.label, b.label);
        });
        return result;
    }
}
