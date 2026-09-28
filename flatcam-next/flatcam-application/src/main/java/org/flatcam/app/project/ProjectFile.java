package org.flatcam.app.project;

import java.util.List;
import org.flatcam.cam.excellon.ExcellonImage;
import org.flatcam.cam.gcode.GeometryGCodeParameters;
import org.flatcam.cam.gerber.GerberImage;
import org.flatcam.cam.geometry.ToolGeometry;
import org.locationtech.jts.geom.Geometry;

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
 * objects, including their per-tool paths, are also embedded in the Java
 * extension. A CNC Job's original
 * tool-diameter metadata and full machine semantics are not serialized; the
 * supported G0/G1 XY preview is reconstructed on reload.
 */
public record ProjectFile(
        List<GerberEntry> gerbers,
        List<ExcellonEntry> excellons,
        List<GeometryEntry> geometries,
        List<CncJobRecord> cncJobs,
        List<String> importWarnings
) {
    public ProjectFile {
        importWarnings = List.copyOf(importWarnings);
    }

    public ProjectFile(List<GerberEntry> gerbers, List<ExcellonEntry> excellons,
                       List<GeometryEntry> geometries, List<CncJobRecord> cncJobs) {
        this(gerbers, excellons, geometries, cncJobs, List.of());
    }

    public ProjectFile(List<GerberEntry> gerbers, List<ExcellonEntry> excellons,
                       List<CncJobRecord> cncJobs) {
        this(gerbers, excellons, List.of(), cncJobs, List.of());
    }

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

    public record GeometryEntry(String name, String sourceName, String units, Geometry geometry,
                                boolean strokeOnly, List<ToolGeometry> tools,
                                String fillColorWeb, String strokeColorWeb, boolean visible,
                                GeometryGCodeParameters cncDefaults) {
        public GeometryEntry(String name, String sourceName, String units, Geometry geometry,
                             boolean strokeOnly, List<ToolGeometry> tools,
                             String fillColorWeb, String strokeColorWeb, boolean visible) {
            this(name, sourceName, units, geometry, strokeOnly, tools,
                    fillColorWeb, strokeColorWeb, visible, null);
        }
    }

    /** {@code name}/{@code gcode} are null for older path-only projects. */
    public record CncJobRecord(String name, String sourceName, String outputPath, String gcode,
                               boolean visible) {
        public CncJobRecord(String name, String sourceName, String outputPath, String gcode) {
            this(name, sourceName, outputPath, gcode, true);
        }

        public CncJobRecord(String sourceName, String outputPath) {
            this(null, sourceName, outputPath, null, true);
        }

        public CncJobRecord(String sourceName, String outputPath, String gcode) {
            this(null, sourceName, outputPath, gcode, true);
        }
    }
}
