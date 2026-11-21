package com.leaftv.launcher;

/** Ephemeral remote-navigation state: BACK toggles Home chrome on/off. */
public final class HomeImmersion {
    private boolean wallpaperOnly;

    public boolean toggle() {
        wallpaperOnly = !wallpaperOnly;
        return wallpaperOnly;
    }

    public void reset() { wallpaperOnly = false; }

    public boolean isWallpaperOnly() { return wallpaperOnly; }
}
