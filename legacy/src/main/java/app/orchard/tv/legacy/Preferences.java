package app.orchard.tv.legacy;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class Preferences {
    static SharedPreferences get(Context context) {
        return context.getSharedPreferences("legacy", Context.MODE_PRIVATE);
    }

    static List<String> order(Context context) {
        List<String> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(get(context).getString("order", "[]"));
            for (int i = 0; i < array.length(); i++) result.add(array.getString(i));
        } catch (JSONException ignored) { /* An invalid order falls back to alphabetical order. */ }
        return result;
    }

    static void pinFirst(Context context, String pkg) {
        List<String> order = order(context);
        order.remove(pkg);
        order.add(0, pkg);
        get(context).edit().putString("order", new JSONArray(order).toString()).apply();
    }

    static Set<String> hidden(Context context) {
        return new HashSet<>(get(context).getStringSet("hidden", new HashSet<String>()));
    }

    static void hide(Context context, String pkg) {
        Set<String> hidden = hidden(context);
        hidden.add(pkg);
        get(context).edit().putStringSet("hidden", hidden).apply();
    }
}
