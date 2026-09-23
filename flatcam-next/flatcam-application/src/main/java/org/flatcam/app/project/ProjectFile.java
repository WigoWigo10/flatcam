package org.flatcam.app.project;

import java.util.List;
import org.flatcam.cam.excellon.ExcellonImage;
import org.flatcam.cam.gerber.GerberImage;

/**
 * What a FlatCAM Next project remembers. Gerber/Excellon objects now embed
 * their own fully-resolved geometry (WKT-wrapped, same convention as the
 * legacy app's own .FlatPrj format - see org.flatcam.app.project.flatprj)
 * rather than a path to re-parse: this is what lets a Transformation (or any
 * other in-memory edit) survive a save/reload, and what lets the object
 * still load if its original source file is later moved or deleted.
 *
 * <p>Geometry and CNC Job objects aren't part of this yet - CNC Job stays on
 * the older "remember the output path, re-read the G-code text, don't
 * restore toolpath geometry for plotting" behavior, and Geometry objects
 * (e.g. an NCC result) still aren't persisted at all. Both are next in line
 * for the same embedded-geometry treatment (see CONTEXTO_E_PROGRESSO.md
 * section 9.3) - each needs its own real model change first (Geometry needs
 * a persistent per-tool CAM-options dict; CNC Job needs to retain a
 * per-segment kind-tagged path list during generation), not just a
 * serialization-layer change like Gerber/Excellon got.
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

    public record CncJobRecord(String sourceName, String outputPath) {
    }
}
