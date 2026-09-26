package org.flatcam.app.project;

import java.util.List;
import org.flatcam.cam.excellon.ExcellonImage;
import org.flatcam.cam.gerber.GerberImage;

/**
 * What a FlatCAM FX project remembers. Gerber/Excellon objects now embed
 * their own fully-resolved geometry (WKT-wrapped, same convention as the
 * legacy app's own .FlatPrj format - see org.flatcam.app.project.flatprj)
 * rather than a path to re-parse: this is what lets a Transformation (or any
 * other in-memory edit) survive a save/reload, and what lets the object
 * still load if its original source file is later moved or deleted.
 *
 * <p>CNC Jobs embed their edited G-code text in the Java extension so edits
 * survive a save/reload without overwriting the machine-code file. Geometry
 * objects (e.g. an NCC result) are not persisted yet. A CNC Job's original
 * tool-diameter metadata and full machine semantics are not serialized; the
 * supported G0/G1 XY preview is reconstructed on reload.
 */
public record ProjectFile(
        List<GerberEntry> gerbers,
        List<ExcellonEntry> excellons,
        List<CncJobRecord> cncJobs
) {
    /**
     * @param fillColorWeb   {@code Color.toString()} form (e.g. "0xrrggbbaa"), or null to use this app's default
     * @param strokeColorWeb same encoding as {@code fillColorWeb}
     */
    public record GerberEntry(String name, GerberImage image, String fillColorWeb, String strokeColorWeb,
                              boolean visible, boolean filled, boolean multicolor, boolean followMode) {
    }

    public record ExcellonEntry(String name, ExcellonImage image, String fillColorWeb, String strokeColorWeb,
                                boolean visible, boolean filled, boolean multicolor) {
    }

    /** {@code name}/{@code gcode} are null for older path-only projects. */
    public record CncJobRecord(String name, String sourceName, String outputPath, String gcode) {
        public CncJobRecord(String sourceName, String outputPath) {
            this(null, sourceName, outputPath, null);
        }

        public CncJobRecord(String sourceName, String outputPath, String gcode) {
            this(null, sourceName, outputPath, gcode);
        }
    }
}
