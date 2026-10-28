package org.flatcam.fx;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/** FX-only history; never reads or rewrites Python's recent.json files. */
final class RecentFiles {
    static final int LIMIT = 10;
    enum Kind {
        PROJECT(I18n.text("ui.4c7877d7558f"), "folder32.png"), GERBER("Gerber", "flatcam_icon32.png"),
        EXCELLON("Excellon", "drill32.png"), GCODE("G-Code", "cnc32.png"),
        SVG_GEOMETRY("SVG → Geometry", "svg32.png"), SVG_GERBER("SVG → Gerber", "svg32.png"),
        DXF_GEOMETRY("DXF → Geometry", "dxf16.png"), DXF_GERBER("DXF → Gerber", "dxf16.png"),
        HPGL("HPGL2", "import.png"), PDF("PDF", "pdf32.png");
        final String label, icon;
        Kind(String label, String icon) { this.label = label; this.icon = icon; }
    }
    record Entry(Kind kind, Path path) {
        Entry { java.util.Objects.requireNonNull(kind); path = path.toAbsolutePath().normalize(); }
    }
    private final Preferences preferences;
    RecentFiles(Preferences preferences) { this.preferences = preferences; }
    List<Entry> entries(boolean projects) {
        String prefix = projects ? "projects." : "files.";
        List<Entry> result = new ArrayList<>();
        for (int i = 0; i < LIMIT; i++) {
            try {
                String kind = preferences.get(prefix + i + ".kind", "");
                String path = preferences.get(prefix + i + ".path", "");
                if (kind.isBlank() || path.isBlank()) continue;
                Entry entry = new Entry(Kind.valueOf(kind), Path.of(path));
                if ((entry.kind() == Kind.PROJECT) == projects && !result.contains(entry)) result.add(entry);
            } catch (IllegalArgumentException invalid) { /* Ignore an obsolete/corrupt entry, not the entire list. */ }
        }
        return List.copyOf(result);
    }
    void remember(Kind kind, Path path) {
        Entry entry = new Entry(kind, path);
        if (entry.path().toString().length() > Preferences.MAX_VALUE_LENGTH) return;
        boolean projects = kind == Kind.PROJECT;
        List<Entry> updated = new ArrayList<>(entries(projects));
        updated.remove(entry); updated.addFirst(entry);
        store(projects, updated.stream().limit(LIMIT).toList());
    }
    void clear(boolean projects) { store(projects, List.of()); }
    private void store(boolean projects, List<Entry> entries) {
        String prefix = projects ? "projects." : "files.";
        for (int i = 0; i < LIMIT; i++) {
            if (i < entries.size()) {
                preferences.put(prefix + i + ".kind", entries.get(i).kind().name());
                preferences.put(prefix + i + ".path", entries.get(i).path().toString());
            } else {
                preferences.remove(prefix + i + ".kind"); preferences.remove(prefix + i + ".path");
            }
        }
        try { preferences.flush(); }
        catch (BackingStoreException failure) {
            System.getLogger(RecentFiles.class.getName()).log(System.Logger.Level.WARNING,
                    I18n.text("ui.389debfc85c1"), failure);
        }
    }
}
