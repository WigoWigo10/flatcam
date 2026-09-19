package org.flatcam.fx;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * Thin wrapper over {@link Preferences} (JDK-native, no extra dependency)
 * for the shell state users asked to have remembered across sessions:
 * selected theme, window size, split-pane divider positions, and the last
 * folder used to open a Gerber file. Not a general settings/preferences
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
    private static final String KEY_SPLIT_HORIZONTAL = "splitHorizontal";
    private static final String KEY_SPLIT_VERTICAL = "splitVertical";
    private static final String KEY_LAST_GERBER_DIR = "lastGerberDirectory";

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

    static double loadSplitHorizontal(double fallback) {
        return PREFS.getDouble(KEY_SPLIT_HORIZONTAL, fallback);
    }

    static double loadSplitVertical(double fallback) {
        return PREFS.getDouble(KEY_SPLIT_VERTICAL, fallback);
    }

    static void saveSplitPositions(double horizontal, double vertical) {
        PREFS.putDouble(KEY_SPLIT_HORIZONTAL, horizontal);
        PREFS.putDouble(KEY_SPLIT_VERTICAL, vertical);
        flush();
    }

    static String loadLastGerberDirectory(String fallback) {
        return PREFS.get(KEY_LAST_GERBER_DIR, fallback);
    }

    static void saveLastGerberDirectory(String path) {
        PREFS.put(KEY_LAST_GERBER_DIR, path);
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
