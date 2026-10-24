package app.orchard.tv.legacy;

import android.service.dreams.DreamService;
import android.view.View;

public final class AerialDream extends DreamService {
    @Override public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        setScreenBright(false);
    }

    @Override public void onDreamingStarted() {
        super.onDreamingStarted();
        setContentView(new ScreensaverView(this));
    }

    @Override public void onDreamingStopped() {
        setContentView(new View(this));
        super.onDreamingStopped();
    }
}
