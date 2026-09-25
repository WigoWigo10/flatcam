package org.flatcam.fx;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * Thin wrapper over {@link Preferences} (JDK-native, no extra dependency)
 * for the shell state users asked to have remembered across sessions:
 * selected theme, window size, split-pane divider positions, and the last
 * folder used to open a Gerber/Excellon file. Not a general settings/preferences
 * system - the real one (per-object options, import/export, factory
 * defaults - see UI_INVENTORY.md section 4) is much bigger scope and
 * belongs to a later phase; this is only what Fase 1's shell itself needs
 * to remember about its own layout.
 *
 * <p>Every write calls {@link Preferences#flush()} - relying on the JVM
 * shutdown path to persist buffered writes turned out not to be safe
 * enough in practice (a split-divider drag saved only on the main window's
 * close handler was silently lost).
 */
final class AppPreferences {

    private static final Logger LOG = System.getLogger(AppPreferences.class.getName());
    private static final Preferences PREFS = Preferences.userNodeForPackage(AppPreferences.class);

    private static final String KEY_THEME = "theme";
    private static final String KEY_WINDOW_WIDTH = "windowWidth";
    private static final String KEY_WINDOW_HEIGHT = "windowHeight";
    private static final String KEY_WINDOW_SCREEN_ID = "windowScreenId";
    private static final String KEY_WINDOW_SCREEN_X = "windowScreenX";
    private static final String KEY_WINDOW_SCREEN_Y = "windowScreenY";
    private static final String KEY_WINDOW_SCREEN_WIDTH = "windowScreenWidth";
    private static final String KEY_WINDOW_SCREEN_HEIGHT = "windowScreenHeight";
    private static final String KEY_SPLIT_HORIZONTAL = "splitHorizontal";
    private static final String KEY_SPLIT_HORIZONTAL_SCREEN_PREFIX = "splitHorizontal_";
    private static final String KEY_SPLIT_VERTICAL = "splitVertical";
    private static final String KEY_CONSOLE_OPEN = "consoleOpen";
    private static final String KEY_LAST_CAM_DIR = "lastCamDirectory";
    private static final String KEY_LAST_PROJECT_DIR = "lastProjectDirectory";

    private AppPreferences() {
    }

    static ThemeOption loadTheme(ThemeOption fallback) {
        String name = PREFS.get(KEY_THEME, null);
        if (name == null) {
            return fallback;
        }
        try {
            return ThemeOption.valueOf(name);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    static void saveTheme(ThemeOption option) {
        PREFS.put(KEY_THEME, option.name());
        flush();
    }

    static double loadWindowWidth(double fallback) {
        return PREFS.getDouble(KEY_WINDOW_WIDTH, fallback);
    }

    static double loadWindowHeight(double fallback) {
        return PREFS.getDouble(KEY_WINDOW_HEIGHT, fallback);
    }

    static void saveWindowSize(double width, double height) {
        PREFS.putDouble(KEY_WINDOW_WIDTH, width);
        PREFS.putDouble(KEY_WINDOW_HEIGHT, height);
        flush();
    }

    record SavedWindowScreen(String deviceId, WindowScreenMatcher.Bounds bounds) {
    }

    static SavedWindowScreen loadWindowScreen() {
        String deviceId = PREFS.get(KEY_WINDOW_SCREEN_ID, null);
        if (deviceId == null || deviceId.isBlank()) {
            return null;
        }
        var bounds = new WindowScreenMatcher.Bounds(
                PREFS.getDouble(KEY_WINDOW_SCREEN_X, Double.NaN),
                PREFS.getDouble(KEY_WINDOW_SCREEN_Y, Double.NaN),
                PREFS.getDouble(KEY_WINDOW_SCREEN_WIDTH, Double.NaN),
                PREFS.getDouble(KEY_WINDOW_SCREEN_HEIGHT, Double.NaN));
        return bounds.valid() ? new SavedWindowScreen(deviceId, bounds) : null;
    }

    static void saveWindowScreen(String deviceId, WindowScreenMatcher.Bounds bounds) {
        if (deviceId == null || deviceId.isBlank() || bounds == null || !bounds.valid()) {
            return;
        }
        PREFS.put(KEY_WINDOW_SCREEN_ID, deviceId);
        PREFS.putDouble(KEY_WINDOW_SCREEN_X, bounds.x());
        PREFS.putDouble(KEY_WINDOW_SCREEN_Y, bounds.y());
        PREFS.putDouble(KEY_WINDOW_SCREEN_WIDTH, bounds.width());
        PREFS.putDouble(KEY_WINDOW_SCREEN_HEIGHT, bounds.height());
        flush();
    }

    static double loadSplitHorizontal(double fallback) {
        return PREFS.getDouble(KEY_SPLIT_HORIZONTAL, fallback);
    }

    /**
     * The sidebar divider width the user set the last time the window was on
     * THIS specific monitor - kept separate per screen (unlike the shared
     * {@link #loadSplitHorizontal(double)}) because this
     * app's two monitors can have very different resolutions/DPI, so one
     * fraction that looked right on one looked wrong-sized on the other.
     * Falls back to the shared (non-per-screen) value on a screen never seen
     * before, then to {@code fallback}.
     */
    static double loadSplitHorizontalForScreen(String screenId, double fallback) {
        return PREFS.getDouble(splitHorizontalScreenKey(screenId), loadSplitHorizontal(fallback));
    }

    static void saveSplitHorizontalForScreen(String screenId, double horizontal) {
        PREFS.putDouble(splitHorizontalScreenKey(screenId), horizontal);
        PREFS.putDouble(KEY_SPLIT_HORIZONTAL, horizontal);
        flush();
    }

    private static String splitHorizontalScreenKey(String screenId) {
        return KEY_SPLIT_HORIZONTAL_SCREEN_PREFIX + screenId.replaceAll("[^A-Za-z0-9]", "_");
    }

    static double loadSplitVertical(double fallback) {
        return PREFS.getDouble(KEY_SPLIT_VERTICAL, fallback);
    }

    static void saveSplitVertical(double vertical) {
        PREFS.putDouble(KEY_SPLIT_VERTICAL, vertical);
        flush();
    }

    static boolean loadConsoleOpen(boolean fallback) {
        return PREFS.getBoolean(KEY_CONSOLE_OPEN, fallback);
    }

    static void saveConsoleOpen(boolean open) {
        PREFS.putBoolean(KEY_CONSOLE_OPEN, open);
        flush();
    }

    /** Shared between Gerber and Excellon - they're almost always opened from the same fabrication folder. */
    static String loadLastCamDirectory(String fallback) {
        return PREFS.get(KEY_LAST_CAM_DIR, fallback);
    }

    static void saveLastCamDirectory(String path) {
        PREFS.put(KEY_LAST_CAM_DIR, path);
        flush();
    }

    static String loadLastProjectDirectory(String fallback) {
        return PREFS.get(KEY_LAST_PROJECT_DIR, fallback);
    }

    static void saveLastProjectDirectory(String path) {
        PREFS.put(KEY_LAST_PROJECT_DIR, path);
        flush();
    }

    private static void flush() {
        try {
            PREFS.flush();
        } catch (BackingStoreException e) {
            LOG.log(Level.WARNING, "Could not persist preferences", e);
        }
    }
}
