package org.flatcam.app.project;

import java.util.List;
import java.util.Map;
import org.flatcam.cam.excellon.ExcellonImage;
import org.flatcam.cam.gcode.DrillGCodeParameters;
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
        List<String> importWarnings,
        PythonLegacy pythonLegacy
) {
    public ProjectFile {
        importWarnings = List.copyOf(importWarnings);
    }

    /**
     * The FlatCAM Python project this one was opened from, so that saving it back as .FlatPrj keeps everything the FX
     * does not model (see {@link PythonProjectWriter}). Null for anything else. It also travels inside a native
     * .fcnproj save, so the way back to Python survives working in the FX's own format.
     *
     * @param original the bytes of the opened file, as they were (XZ or plain JSON); what changed since is found by
     *                 comparing with what the FX reads from them
     */
    public record PythonLegacy(byte[] original) {
        @Override
        public boolean equals(Object other) {
            return other instanceof PythonLegacy legacy && java.util.Arrays.equals(original, legacy.original);
        }

        @Override
        public int hashCode() {
            return java.util.Arrays.hashCode(original);
        }
    }

    public ProjectFile(List<GerberEntry> gerbers, List<ExcellonEntry> excellons,
                       List<GeometryEntry> geometries, List<CncJobRecord> cncJobs, List<String> importWarnings) {
        this(gerbers, excellons, geometries, cncJobs, importWarnings, null);
    }

    public ProjectFile withPythonLegacy(PythonLegacy legacy) {
        return new ProjectFile(gerbers, excellons, geometries, cncJobs, importWarnings, legacy);
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
                                boolean visible, boolean filled, boolean multicolor,
                                Map<Integer, DrillGCodeParameters> drillDefaults, DrillCncSettings cncSettings) {
        public ExcellonEntry {
            drillDefaults = Map.copyOf(drillDefaults);
        }

        public ExcellonEntry(String name, ExcellonImage image, String fillColorWeb, String strokeColorWeb,
                             boolean visible, boolean filled, boolean multicolor,
                             Map<Integer, DrillGCodeParameters> drillDefaults) {
            this(name, image, fillColorWeb, strokeColorWeb, visible, filled, multicolor, drillDefaults, null);
        }

        public ExcellonEntry(String name, ExcellonImage image, String fillColorWeb, String strokeColorWeb,
                             boolean visible, boolean filled, boolean multicolor) {
            this(name, image, fillColorWeb, strokeColorWeb, visible, filled, multicolor, Map.of());
        }
    }

    public record GeometryEntry(String name, String sourceName, String units, Geometry geometry,
                                boolean strokeOnly, List<ToolGeometry> tools,
                                String fillColorWeb, String strokeColorWeb, boolean visible,
                                GeometryGCodeParameters cncDefaults, GeometryCncSettings cncSettings) {
        public GeometryEntry {
            if (cncSettings != null && cncSettings.jobDefaultsByTool().keySet().stream()
                    .anyMatch(id -> id >= Math.max(1, tools.size())))
                throw new IllegalArgumentException("Configuracao comum CNC sem ferramenta correspondente.");
            if (cncSettings != null && cncSettings.parametersByTool().keySet().stream()
                    .anyMatch(id -> id >= Math.max(1, tools.size())))
                throw new IllegalArgumentException("Parametros CNC sem ferramenta correspondente.");
            if (cncSettings != null && cncDefaults == null)
                throw new IllegalArgumentException("Perfil CNC de Geometry sem parametros de geracao.");
            if (cncSettings != null && cncSettings.preprocessor().requiresProbe()
                    && (!cncDefaults.pauseForToolChange() || cncDefaults.probing() == null))
                throw new IllegalArgumentException("Perfil Mach3 de Geometry sem configuracao explicita de sondagem.");
        }

        public GeometryEntry(String name, String sourceName, String units, Geometry geometry,
                             boolean strokeOnly, List<ToolGeometry> tools,
                             String fillColorWeb, String strokeColorWeb, boolean visible,
                             GeometryGCodeParameters cncDefaults) {
            this(name, sourceName, units, geometry, strokeOnly, tools, fillColorWeb, strokeColorWeb,
                    visible, cncDefaults, null);
        }

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
