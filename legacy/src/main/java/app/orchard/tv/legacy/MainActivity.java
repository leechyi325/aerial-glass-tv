package app.orchard.tv.legacy;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int PHOTO = 7;
    private final Handler handler = new Handler();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final List<AppCatalog.Entry> apps = new ArrayList<>();
    private FrameLayout root;
    private HomeGrid grid;
    private TextView clock, date, network, empty;
    private TextView heading, hint;
    private View dock;
    private Backdrop.Scene scene;
    private boolean expanded;
    private Button settings;
    private BaseAdapter adapter;
    private float scale;
    private boolean foreground;
    private boolean loading;
    private boolean reloadPending;
    private boolean destroyed;
    private boolean firstLoad = true;
    private AlertDialog dialog;
    private final Runnable idle = () -> {
        if (foreground && (dialog == null || !dialog.isShowing())) previewScreensaver();
    };
    private final BroadcastReceiver timeReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { updateClock(); }
    };
    private final BroadcastReceiver appsReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { reloadApps(); }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setBackgroundDrawable(null);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        scale = getResources().getDisplayMetrics().widthPixels / 960f;
        scene = Backdrop.scene(this);
        root = new FrameLayout(this);
        root.setClipChildren(false);
        root.setBackground(new BitmapDrawable(getResources(), scene.wallpaper));
        setContentView(root);
        clock = text(25);
        clock.setTypeface(android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL));
        place(clock, 56, 31, 190, 36);
        date = text(13);
        date.setTextColor(0xd9ffffff);
        place(date, 56, 70, 460, 24);
        network = text(12);
        network.setTextColor(0xddffffff);
        network.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        place(network, 704, 31, 132, 36);
        settings = new ControlButton(this);
        settings.setId(View.generateViewId());
        settings.setBackground(new GlassDrawable(settings, scene, 18, false));
        settings.setOnClickListener(v -> showSettings());
        place(settings, 855, 30, 49, 36);
        heading = text(20);
        heading.setText(R.string.apps);
        heading.setVisibility(View.INVISIBLE);
        place(heading, 56, 102, 700, 30);
        dock = new View(this);
        dock.setBackground(new GlassDrawable(dock, scene, 30, false));
        place(dock, 30, 350, 900, 153);
        hint = text(10);
        hint.setTextColor(0xaaffffff);
        hint.setGravity(Gravity.CENTER);
        hint.setText(R.string.more_apps);
        place(hint, 56, 515, 848, 20);
        grid = new HomeGrid(this);
        grid.setId(View.generateViewId());
        grid.setNumColumns(5);
        grid.setColumnWidth(px(152));
        grid.setHorizontalSpacing(px(22));
        grid.setVerticalSpacing(px(20));
        grid.setPadding(px(8), px(4), px(8), px(10));
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        grid.setVerticalScrollBarEnabled(false);
        grid.setSelector(android.R.color.transparent);
        grid.setDrawSelectorOnTop(true);
        grid.setNextFocusUpId(settings.getId());
        settings.setNextFocusDownId(grid.getId());
        place(grid, 48, 361, 864, 146);
        empty = text(18);
        empty.setText(R.string.loading_apps);
        empty.setGravity(Gravity.CENTER);
        place(empty, 56, 365, 848, 120);
        grid.setEmptyView(empty);
        adapter = new BaseAdapter() {
            @Override public int getCount() { return apps.size(); }
            @Override public Object getItem(int position) { return apps.get(position); }
            @Override public long getItemId(int position) { return position; }
            @Override public View getView(int position, View recycled, ViewGroup parent) {
                AppTileView tile;
                if (recycled == null) {
                    tile = new AppTileView(MainActivity.this);
                    tile.setLayoutParams(new android.widget.AbsListView.LayoutParams(-1, px(124)));
                } else tile = (AppTileView) recycled;
                AppCatalog.Entry entry = apps.get(position);
                tile.bind(entry);
                return tile;
            }
        };
        grid.setAdapter(adapter);
        grid.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                setExpanded(position >= 5);
                grid.refreshTiles();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { grid.refreshTiles(); }
        });
        grid.setOnItemClickListener((parent, view, position, id) -> launch(apps.get(position)));
        grid.setOnItemLongClickListener((parent, view, position, id) -> {
            showAppMenu(apps.get(position));
            return true;
        });
        grid.requestFocus();
        updateClock();
    }

    private int px(float value) { return Math.round(value * scale); }

    private TextView text(int size) {
        TextView view = new TextView(this);
        view.setTextColor(Color.WHITE);
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(size));
        view.setIncludeFontPadding(false);
        view.setShadowLayer(px(2), 0, px(1), 0x66000000);
        return view;
    }

    private void place(View view, int x, int y, int width, int height) {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(px(width), px(height));
        params.leftMargin = px(x);
        params.topMargin = px(y);
        root.addView(view, params);
    }

    private StateListDrawable focusBackground() {
        StateListDrawable states = new StateListDrawable();
        for (int state : new int[]{android.R.attr.state_pressed, android.R.attr.state_selected, android.R.attr.state_focused, 0}) {
            GradientDrawable shape = new GradientDrawable();
            shape.setColor(state == 0 ? 0x00ffffff : 0x25ffffff);
            shape.setCornerRadius(px(12));
            if (state != 0) shape.setStroke(px(1), 0x99ffffff);
            states.addState(state == 0 ? new int[0] : new int[]{state}, shape);
        }
        return states;
    }

    private void setExpanded(boolean next) {
        if (next == expanded) return;
        expanded = next;
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) grid.getLayoutParams();
        params.topMargin = px(expanded ? 138 : 361);
        params.height = px(expanded ? 365 : 146);
        grid.setLayoutParams(params);
        dock.setVisibility(expanded ? View.INVISIBLE : View.VISIBLE);
        heading.setVisibility(expanded ? View.VISIBLE : View.INVISIBLE);
        hint.setText(expanded ? R.string.home_hint : R.string.more_apps);
        root.setBackground(new BitmapDrawable(getResources(), expanded ? scene.grid : scene.wallpaper));
    }

    private void updateBackdrop() {
        scene = Backdrop.scene(this);
        root.setBackground(new BitmapDrawable(getResources(), expanded ? scene.grid : scene.wallpaper));
        dock.setBackground(new GlassDrawable(dock, scene, 30, false));
        settings.setBackground(new GlassDrawable(settings, scene, 18, false));
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (grid != null) grid.refreshTiles();
    }

    @Override protected void onStart() {
        super.onStart();
        IntentFilter time = new IntentFilter(Intent.ACTION_TIME_TICK);
        time.addAction(Intent.ACTION_TIME_CHANGED);
        time.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        time.addAction(Intent.ACTION_SCREEN_ON);
        time.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(timeReceiver, time);
        IntentFilter packages = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
        packages.addAction(Intent.ACTION_PACKAGE_REMOVED);
        packages.addAction(Intent.ACTION_PACKAGE_CHANGED);
        packages.addDataScheme("package");
        registerReceiver(appsReceiver, packages);
        reloadApps();
    }

    @Override protected void onResume() {
        super.onResume();
        foreground = true;
        BootSetup.sync(this);
        HomeGuardService.returnedHome();
        updateClock();
        resetIdle();
    }

    @Override protected void onPause() {
        foreground = false;
        handler.removeCallbacks(idle);
        super.onPause();
    }

    @Override protected void onStop() {
        unregisterReceiver(timeReceiver);
        unregisterReceiver(appsReceiver);
        super.onStop();
    }

    @Override protected void onDestroy() {
        destroyed = true;
        handler.removeCallbacksAndMessages(null);
        if (dialog != null) dialog.dismiss();
        worker.shutdownNow();
        super.onDestroy();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (dialog != null) dialog.dismiss();
        grid.requestFocus();
        grid.setSelection(0);
        updateClock();
    }

    private void updateClock() {
        Date now = new Date();
        clock.setText(DateFormat.getTimeFormat(this).format(now));
        date.setText(DateFormat.getLongDateFormat(this).format(now));
        ConnectivityManager manager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo active = manager == null ? null : manager.getActiveNetworkInfo();
        network.setText(active != null && active.isConnected() ? R.string.connected : R.string.offline);
    }

    private void reloadApps() {
        if (destroyed) return;
        if (loading) { reloadPending = true; return; }
        loading = true;
        worker.execute(() -> {
            List<AppCatalog.Entry> result;
            try { result = AppCatalog.load(getApplicationContext()); }
            catch (RuntimeException failure) {
                Log.e("AerialLegacy", "App scan failed", failure);
                result = null;
            }
            final List<AppCatalog.Entry> loaded = result;
            handler.post(() -> {
                loading = false;
                if (destroyed) return;
                if (loaded != null) {
                    int selected = grid.getSelectedItemPosition();
                    apps.clear();
                    apps.addAll(loaded);
                    adapter.notifyDataSetChanged();
                    grid.setSelection(Math.max(0, Math.min(selected, apps.size() - 1)));
                    empty.setText(R.string.no_apps);
                    if (firstLoad && !apps.isEmpty()) {
                        firstLoad = false;
                        grid.requestFocus();
                        grid.setSelection(0);
                    }
                    Log.i("AerialLegacy", "Loaded " + apps.size() + " apps");
                }
                if (reloadPending) { reloadPending = false; reloadApps(); }
            });
        });
    }

    private void launch(AppCatalog.Entry app) {
        if (app.stockHome) HomeGuardService.visitStock();
        open(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(app.component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
    }

    private void open(Intent intent) {
        try { startActivity(intent); }
        catch (android.content.ActivityNotFoundException | SecurityException failure) {
            HomeGuardService.returnedHome();
            Toast.makeText(this, R.string.unavailable, Toast.LENGTH_LONG).show();
        }
    }

    private void showAppMenu(AppCatalog.Entry app) {
        dialog = new AlertDialog.Builder(this).setTitle(app.label)
                .setItems(new CharSequence[]{getString(R.string.pin_first), getString(R.string.hide), getString(R.string.app_info)}, (d, which) -> {
                    if (which == 0) Preferences.pinFirst(this, app.component.getPackageName());
                    if (which == 1) Preferences.hide(this, app.component.getPackageName());
                    if (which == 2) open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:" + app.component.getPackageName())));
                    else reloadApps();
                }).create();
        showDialog();
    }

    private void showSettings() {
        boolean boot = BootSetup.enabled(this);
        dialog = new AlertDialog.Builder(this).setTitle(R.string.settings)
                .setItems(new CharSequence[]{getString(R.string.wallpaper), getString(R.string.screensaver),
                        getString(R.string.idle_time), getString(R.string.preview), getString(R.string.network_settings),
                        getString(R.string.system_settings), getString(R.string.guard_name),
                        getString(R.string.boot, getString(boot ? R.string.on : R.string.off)),
                        getString(R.string.restore_hidden), getString(R.string.about)}, (d, which) -> {
                    switch (which) {
                        case 0: chooseWallpaper(); break;
                        case 1: chooseScreensaver(); break;
                        case 2: chooseIdle(); break;
                        case 3: previewScreensaver(); break;
                        case 4: open(new Intent(Settings.ACTION_WIFI_SETTINGS)); break;
                        case 5: open(new Intent(Settings.ACTION_SETTINGS)); break;
                        case 6:
                            dialog = new AlertDialog.Builder(this).setTitle(R.string.guard_name)
                                    .setMessage(R.string.guard_description).setPositiveButton(R.string.open_settings,
                                            (a, b) -> open(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)))
                                    .setNegativeButton(android.R.string.cancel, null).create();
                            showDialog(); break;
                        case 7:
                            if (!BootSetup.setEnabled(this, !boot)) Toast.makeText(this, R.string.boot_blocked, Toast.LENGTH_LONG).show();
                            break;
                        case 8: Preferences.get(this).edit().remove("hidden").apply(); reloadApps(); break;
                        case 9:
                            dialog = new AlertDialog.Builder(this).setTitle(R.string.app_name).setMessage(R.string.about_body)
                                    .setPositiveButton(android.R.string.ok, null).create();
                            showDialog(); break;
                        default: break;
                    }
                }).create();
        showDialog();
    }

    private void chooseWallpaper() {
        dialog = new AlertDialog.Builder(this).setTitle(R.string.wallpaper)
                .setItems(R.array.wallpapers, (d, which) -> {
                    if (which == 3) open(new Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE), PHOTO);
                    else {
                        Preferences.get(this).edit().putInt("wallpaper", which).apply();
                        updateBackdrop();
                    }
                }).create();
        showDialog();
    }

    private void open(Intent intent, int requestCode) {
        try { startActivityForResult(intent, requestCode); }
        catch (android.content.ActivityNotFoundException | SecurityException failure) {
            Toast.makeText(this, R.string.unavailable, Toast.LENGTH_LONG).show();
        }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != PHOTO || result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        worker.execute(() -> {
            boolean success;
            try { Backdrop.importPhoto(this, uri); success = true; }
            catch (java.io.IOException | RuntimeException failure) {
                Log.w("AerialLegacy", "Photo import failed", failure);
                success = false;
            }
            final boolean imported = success;
            handler.post(() -> {
                if (destroyed) return;
                if (imported) updateBackdrop();
                else Toast.makeText(this, R.string.image_failed, Toast.LENGTH_LONG).show();
            });
        });
    }

    private void chooseScreensaver() {
        dialog = new AlertDialog.Builder(this).setTitle(R.string.screensaver)
                .setSingleChoiceItems(R.array.screensavers, Preferences.get(this).getInt("screensaver", 0), (d, which) -> {
                    Preferences.get(this).edit().putInt("screensaver", which).apply();
                    d.dismiss();
                }).create();
        showDialog();
    }

    private void chooseIdle() {
        final int[] minutes = {0, 2, 5, 10, 30};
        int selected = 2;
        for (int i = 0; i < minutes.length; i++) if (minutes[i] == Preferences.get(this).getInt("idle", 5)) selected = i;
        dialog = new AlertDialog.Builder(this).setTitle(R.string.idle_time)
                .setSingleChoiceItems(R.array.idle_times, selected, (d, which) -> {
                    Preferences.get(this).edit().putInt("idle", minutes[which]).apply();
                    d.dismiss();
                }).create();
        showDialog();
    }

    private void showDialog() {
        handler.removeCallbacks(idle);
        dialog.setOnDismissListener(d -> resetIdle());
        dialog.show();
        android.view.Window window = dialog.getWindow();
        if (window != null) {
            View decor = window.getDecorView();
            clearPanelBackgrounds(decor);
            decor.setPadding(px(18), px(16), px(18), px(16));
            window.setBackgroundDrawable(new GlassDrawable(decor, scene, 28, true));
            android.widget.ListView list = dialog.getListView();
            int height = list != null && list.getCount() > 5 ? px(460) : ViewGroup.LayoutParams.WRAP_CONTENT;
            window.setLayout(px(360), height);
            window.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            WindowManager.LayoutParams attributes = window.getAttributes();
            attributes.x = px(36);
            window.setAttributes(attributes);
            window.setDimAmount(.12f);
        }
    }

    private void clearPanelBackgrounds(View view) {
        view.setBackground(null);
        if (view instanceof TextView) {
            ((TextView) view).setTextColor(0xf2ffffff);
            ((TextView) view).setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL));
        }
        if (view instanceof android.widget.ListView) {
            android.widget.ListView list = (android.widget.ListView) view;
            list.setCacheColorHint(Color.TRANSPARENT);
            list.setSelector(focusBackground());
            list.setDivider(null);
        }
        if (view instanceof Button) view.setBackground(focusBackground());
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) clearPanelBackgrounds(group.getChildAt(i));
        }
    }

    private void resetIdle() {
        handler.removeCallbacks(idle);
        int minutes = Preferences.get(this).getInt("idle", 5);
        if (foreground && minutes > 0) handler.postDelayed(idle, minutes * 60_000L);
    }

    private void previewScreensaver() { open(new Intent(this, ScreensaverActivity.class)); }

    @Override public void onUserInteraction() { super.onUserInteraction(); resetIdle(); }

    @Override public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            int selected = grid.getSelectedItemPosition();
            if (grid.hasFocus() && selected >= 0 && selected < apps.size()) showAppMenu(apps.get(selected));
            else showSettings();
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override public void onBackPressed() {
        if (grid.getSelectedItemPosition() >= 5 || !grid.hasFocus()) {
            grid.setSelection(0);
            grid.requestFocus();
        } else previewScreensaver();
    }

    @Override public void dump(String prefix, java.io.FileDescriptor fd, java.io.PrintWriter writer, String[] args) {
        super.dump(prefix, fd, writer, args);
        writer.println(prefix + "Aerial Legacy: API=" + android.os.Build.VERSION.SDK_INT
                + " apps=" + apps.size() + " selected=" + grid.getSelectedItemPosition()
                + " foreground=" + foreground + " clock=" + clock.getText());
        writer.println(prefix + "Screensaver: " + ScreensaverView.diagnostics());
        android.content.SharedPreferences bootLog = getSharedPreferences("boot_diagnostics", MODE_PRIVATE);
        writer.println(prefix + "Boot: enabled=" + BootSetup.enabled(this) + " receiverState=" + BootSetup.receiverState(this)
                + " action=" + bootLog.getString("action", "none")
                + " receivedElapsed=" + bootLog.getLong("received_elapsed", -1)
                + " result=" + bootLog.getString("result", "none") + " error=" + bootLog.getString("error", "none"));
    }
}
