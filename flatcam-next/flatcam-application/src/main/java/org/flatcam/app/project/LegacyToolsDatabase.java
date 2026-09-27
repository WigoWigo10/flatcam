package org.flatcam.app.project;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.flatcam.cam.ncc.NccMethod;
import org.flatcam.cam.ncc.NccOperation;
import org.flatcam.cam.ncc.NccToolSettings;
import org.flatcam.cam.geometry.ToolProfile;
import org.flatcam.cam.gcode.DrillGCodeParameters;
import org.flatcam.cam.isolation.IsolationParameters;
import org.flatcam.cam.isolation.IsolationType;
import org.json.JSONObject;

/** Read-only adapter for Python's JSON tools_db.FlatDB entries. */
public final class LegacyToolsDatabase {

    public record NccTool(String name, double diameter, NccOperation operation,
                          NccToolSettings settings, ToolProfile toolProfile) {
        @Override
        public String toString() {
            return name + " - Ø " + diameter + " (" + operation + ")";
        }
    }

    public record IsolationTool(String name, IsolationParameters parameters, ToolProfile toolProfile) {
        @Override public String toString() {
            return name + " - dia " + parameters.toolDiameter() + " (" + toolProfile + ")";
        }
    }

    public record DrillTool(String name, double diameter, double toleranceMin,
                            double toleranceMax, DrillGCodeParameters parameters) {
        public boolean matchesDiameter(double sourceDiameter) {
            return Math.abs(sourceDiameter - diameter) <= 1e-6
                    || (toleranceMax > toleranceMin && sourceDiameter >= toleranceMin
                    && sourceDiameter <= toleranceMax);
        }
    }

    /** Exact diameter wins over tolerance; ambiguous matches must not silently pick a cutter. */
    public static Optional<DrillTool> matchDrillTool(List<DrillTool> database, double sourceDiameter) {
        List<DrillTool> candidates = database.stream()
                .filter(tool -> tool.matchesDiameter(sourceDiameter)).toList();
        List<DrillTool> exact = candidates.stream()
                .filter(tool -> Math.abs(tool.diameter() - sourceDiameter) <= 1e-6).toList();
        if (!exact.isEmpty()) candidates = exact;
        if (candidates.size() > 1)
            throw new IllegalArgumentException("Multiple database tools match diameter " + sourceDiameter);
        return candidates.stream().findFirst();
    }

    private LegacyToolsDatabase() {
    }

    public static List<NccTool> loadNccTools(Path path) throws IOException {
        if (Files.size(path) > 10_000_000) {
            throw new IOException("Tools Database exceeds 10 MB");
        }
        JSONObject root;
        try {
            root = new JSONObject(Files.readString(path, StandardCharsets.UTF_8));
        } catch (RuntimeException error) {
            throw new IOException("Invalid Python Tools Database JSON", error);
        }
        List<NccTool> tools = new ArrayList<>();
        for (String id : root.keySet()) {
            try {
                JSONObject entry = root.getJSONObject(id);
                JSONObject data = entry.getJSONObject("data");
                Object target = data.opt("tool_target");
                if (!(target instanceof Number number && (number.intValue() == 0 || number.intValue() == 5))
                        && !"NCC".equalsIgnoreCase(String.valueOf(target))
                        && !"General".equalsIgnoreCase(String.valueOf(target))) {
                    continue;
                }
                double diameter = entry.getDouble("tooldia");
                if (!Double.isFinite(diameter) || diameter <= 0) {
                    throw new IllegalArgumentException("tool diameter must be positive");
                }
                double overlap = data.optDouble("tools_ncc_overlap", 40) / 100.0;
                int method = data.optInt("tools_ncc_method", 0);
                if (method < 0 || method >= NccMethod.values().length) {
                    throw new IllegalArgumentException("unsupported NCC method " + method);
                }
                boolean offsetEnabled = data.optBoolean("tools_ncc_offset_choice", false);
                NccToolSettings settings = new NccToolSettings(overlap, NccMethod.values()[method],
                        data.optBoolean("tools_ncc_connect", true),
                        data.optBoolean("tools_ncc_contour", true),
                        offsetEnabled ? data.optDouble("tools_ncc_offset_value", 0) : 0);
                NccOperation operation = "iso".equalsIgnoreCase(data.optString("tools_ncc_operation", "clear"))
                        ? NccOperation.ISO : NccOperation.CLEAR;
                ToolProfile profile = ToolProfile.fromLegacy(entry.optString("tool_type", "C1"));
                if (profile == ToolProfile.V) operation = NccOperation.ISO;
                tools.add(new NccTool(entry.optString("name", "Tool " + id), diameter, operation,
                        settings, profile));
            } catch (RuntimeException error) {
                throw new IOException("Invalid NCC tool " + id + " in Tools Database", error);
            }
        }
        return List.copyOf(tools);
    }

    public static List<IsolationTool> loadIsolationTools(Path path) throws IOException {
        if (Files.size(path) > 10_000_000) throw new IOException("Tools Database exceeds 10 MB");
        JSONObject root;
        try {
            root = new JSONObject(Files.readString(path, StandardCharsets.UTF_8));
        } catch (RuntimeException error) {
            throw new IOException("Invalid Python Tools Database JSON", error);
        }
        List<IsolationTool> tools = new ArrayList<>();
        for (String id : root.keySet()) {
            try {
                JSONObject entry = root.getJSONObject(id);
                JSONObject data = entry.getJSONObject("data");
                Object target = data.opt("tool_target");
                if (!(target instanceof Number number && (number.intValue() == 0 || number.intValue() == 3))
                        && !"Isolation".equalsIgnoreCase(String.valueOf(target))
                        && !"General".equalsIgnoreCase(String.valueOf(target))) continue;
                double diameter = entry.getDouble("tooldia");
                int passes = data.optInt("tools_iso_passes", 1);
                double overlap = data.optDouble("tools_iso_overlap", 10) / 100.0;
                String isoType = data.optString("tools_iso_isotype", "full");
                IsolationType type = switch (isoType.toLowerCase(java.util.Locale.ROOT)) {
                    case "full", "both" -> IsolationType.BOTH;
                    case "ext", "exterior" -> IsolationType.EXTERIOR;
                    case "int", "interior" -> IsolationType.INTERIOR;
                    default -> throw new IllegalArgumentException("unsupported isolation type " + isoType);
                };
                IsolationParameters parameters = new IsolationParameters(diameter, passes, overlap, type);
                ToolProfile profile = ToolProfile.fromLegacy(entry.optString("tool_type", "C1"));
                tools.add(new IsolationTool(entry.optString("name", "Tool " + id), parameters, profile));
            } catch (RuntimeException error) {
                throw new IOException("Invalid Isolation tool " + id + " in Tools Database", error);
            }
        }
        return List.copyOf(tools);
    }

    public static List<DrillTool> loadDrillTools(Path path) throws IOException {
        if (Files.size(path) > 10_000_000) throw new IOException("Tools Database exceeds 10 MB");
        JSONObject root;
        try {
            root = new JSONObject(Files.readString(path, StandardCharsets.UTF_8));
        } catch (RuntimeException error) {
            throw new IOException("Invalid Python Tools Database JSON", error);
        }
        List<DrillTool> tools = new ArrayList<>();
        for (String id : root.keySet()) {
            try {
                JSONObject entry = root.getJSONObject(id);
                JSONObject data = entry.getJSONObject("data");
                Object target = data.opt("tool_target");
                if (!(target instanceof Number number && (number.intValue() == 0 || number.intValue() == 2))
                        && !"Drilling".equalsIgnoreCase(String.valueOf(target))
                        && !"General".equalsIgnoreCase(String.valueOf(target))) continue;
                double diameter = entry.getDouble("tooldia");
                if (!Double.isFinite(diameter) || diameter <= 0)
                    throw new IllegalArgumentException("tool diameter must be positive");
                double toleranceMin = data.optDouble("tol_min", 0);
                double toleranceMax = data.optDouble("tol_max", 0);
                if (!Double.isFinite(toleranceMin) || !Double.isFinite(toleranceMax)
                        || toleranceMin < 0 || toleranceMax < toleranceMin)
                    throw new IllegalArgumentException("invalid tool diameter tolerance");
                double cutZ = data.optDouble("tools_drill_cutz", -1.7);
                if (!Double.isFinite(cutZ) || cutZ == 0)
                    throw new IllegalArgumentException("invalid drill Cut Z");
                DrillGCodeParameters parameters = new DrillGCodeParameters(
                        data.optDouble("tools_drill_travelz", 2.0), Math.abs(cutZ),
                        data.optDouble("tools_drill_feedrate_z", 300),
                        data.optInt("tools_drill_spindlespeed", 0), false,
                        data.optBoolean("tools_drill_multidepth", false),
                        data.optDouble("tools_drill_depthperpass", 0.7),
                        data.optBoolean("tools_drill_dwell", false),
                        data.optDouble("tools_drill_dwelltime", 1.0),
                        data.optDouble("tools_drill_offset", 0));
                tools.add(new DrillTool(entry.optString("name", "Tool " + id), diameter,
                        toleranceMin, toleranceMax, parameters));
            } catch (RuntimeException error) {
                throw new IOException("Invalid Drilling tool " + id + " in Tools Database", error);
            }
        }
        return List.copyOf(tools);
    }
}
