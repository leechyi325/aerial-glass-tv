package app.orchard.tv.legacy;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.widget.TextView;

/** Test-only external application. It closes itself without changing any TV or user data. */
public final class ProbeActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        TextView label = new TextView(this);
        label.setText("Aerial compatibility test");
        setContentView(label);
        new Handler().postDelayed(this::finish, 1500);
    }
}
