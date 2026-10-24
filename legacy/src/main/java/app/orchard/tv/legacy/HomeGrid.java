package app.orchard.tv.legacy;

import android.content.Context;
import android.graphics.Rect;
import android.widget.GridView;

final class HomeGrid extends GridView {
    HomeGrid(Context context) { super(context); }

    @Override protected void onFocusChanged(boolean gain, int direction, Rect previous) {
        super.onFocusChanged(gain, direction, previous);
        refreshTiles();
    }

    void refreshTiles() {
        for (int i = 0; i < getChildCount(); i++) {
            if (getChildAt(i) instanceof AppTileView) ((AppTileView) getChildAt(i)).refreshFocus();
        }
    }
}
