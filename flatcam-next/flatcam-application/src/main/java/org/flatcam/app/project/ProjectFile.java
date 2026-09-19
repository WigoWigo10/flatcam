package org.flatcam.app.project;

import java.util.List;

/**
 * What a FlatCAM Next project remembers: which fabrication files were open,
 * and which G-code jobs were generated from them. On load, source files are
 * re-parsed from disk rather than restored from a snapshot.
 *
 * <p>This is deliberately lighter than the legacy app's .FlatPrj format,
 * which serializes every object's full processed geometry (XZ-compressed
 * JSON - a real project file was measured at ~20 MB decompressed for one
 * two-layer board) so that in-editor modifications survive a save/reload.
 * This app has no editors yet, so a Gerber/Excellon object is always
 * exactly what its source file parses to - remembering the path is
 * equivalent and far cheaper. This stops being true the day an editor
 * exists: at that point, project files need to carry the edited geometry
 * too, not just a path to re-parse.
 */
public record ProjectFile(
        List<String> gerberPaths,
        List<String> excellonPaths,
        List<CncJobRecord> cncJobs
) {
    public record CncJobRecord(String sourceName, String outputPath) {
    }
}
