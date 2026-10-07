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
import org.flatcam.cam.gcode.GeometryGCodeParameters;
import org.flatcam.cam.gcode.VTipSettings;
import org.flatcam.cam.ncc.PaintParameters;
import org.flatcam.cam.ncc.NccOrder;
import org.flatcam.cam.cutout.*;
import org.flatcam.cam.isolation.IsolationParameters;
import org.flatcam.cam.isolation.IsolationType;
import org.json.JSONObject;

/** CAM projections of Python's JSON tools_db.FlatDB entries (disk or editor snapshot). */
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

    public record MillingTool(String name, double diameter, ToolProfile profile,
                              GeometryGCodeParameters parameters, VTipSettings tip) {
        @Override public String toString() { return name + " - " + diameter + " (" + profile + ")"; }
    }

    private static boolean accepts(JSONObject data, int index, String name) {
        Object target = data.opt("tool_target");
        return target instanceof Number n && (n.intValue() == 0 || n.intValue() == index)
                || "General".equalsIgnoreCase(String.valueOf(target))
                || name.equalsIgnoreCase(String.valueOf(target));
    }

    public static List<MillingTool> millingTools(JSONObject root) throws IOException {
        List<MillingTool> tools = new ArrayList<>();
        for (String id : root.keySet()) {
            try {
                JSONObject entry = root.getJSONObject(id), data = entry.getJSONObject("data");
                if (!accepts(data, 1, "Milling")) continue;
                double diameter = entry.getDouble("tooldia");
                if (!Double.isFinite(diameter) || diameter <= 0)
                    throw new IllegalArgumentException("tool diameter must be positive");
                double cutZ = data.optDouble("cutz", -0.1);
                if (!Double.isFinite(cutZ) || cutZ >= 0)
                    throw new IllegalArgumentException("Milling Cut Z must be below the surface");
                ToolProfile profile = ToolProfile.fromLegacy(entry.optString("tool_type", "C1"));
                VTipSettings tip = profile == ToolProfile.V
                        ? new VTipSettings(data.optDouble("vtipdia", 0.1), data.optDouble("vtipangle", 30)) : null;
                if (tip != null) tip.cutDepth(diameter);
                GeometryGCodeParameters parameters = new GeometryGCodeParameters(
                        data.optDouble("travelz", 2), Math.abs(cutZ), data.optBoolean("multidepth", false),
                        data.optDouble("depthperpass", 0.1), data.optDouble("feedrate", 120),
                        data.optInt("spindlespeed", 0), false, data.optDouble("feedrate_rapid", 0), null,
                        data.optDouble("feedrate_z", 60), data.optBoolean("dwell", false), data.optDouble("dwelltime", 1),
                        data.optBoolean("extracut", false), data.optDouble("extracut_length", 0.1))
                        .withCompensation(org.flatcam.cam.gcode.ToolPathOffset.fromLegacy(entry.optString("offset", "Path")),
                                entry.optDouble("offset_value", 0));
                tools.add(new MillingTool(entry.optString("name", "Tool " + id), diameter, profile, parameters, tip));
            } catch (RuntimeException invalid) {
                throw new IOException("Invalid Milling tool " + id + ": " + invalid.getMessage(), invalid);
            }
        }
        return List.copyOf(tools);
    }

    public record PaintTool(String name, PaintParameters parameters) {
        @Override public String toString() { return name + " - " + parameters.toolDiameters().getFirst(); }
    }

    public record CutoutTool(String name, CutoutParameters parameters, String gapType,
                             double biteDiameter, double biteSpacing, GeometryGCodeParameters machining,
                             double thinDepth, ToolProfile profile) {
        @Override public String toString() { return name + " - " + parameters.toolDiameter(); }
    }

    private static void requireNonV(JSONObject entry) {
        if (ToolProfile.fromLegacy(entry.optString("tool_type", "C1")) == ToolProfile.V)
            throw new IllegalArgumentException("Ponta V nao suportada neste fluxo; use Geometry/CNC com V-Tip explicito.");
    }

    public static List<PaintTool> paintTools(JSONObject root) throws IOException {
        List<PaintTool> tools = new ArrayList<>();
        for (String id : root.keySet()) {
            try {
                JSONObject entry = root.getJSONObject(id), data = entry.getJSONObject("data");
                if (!accepts(data, 4, "Paint")) continue;
                requireNonV(entry);
                int method = data.optInt("tools_paint_method", 0);
                NccMethod value = switch (method) {
                    case 0 -> NccMethod.STANDARD; case 1 -> NccMethod.SEED; case 2 -> NccMethod.LINES;
                    case 4 -> NccMethod.COMBO;
                    default -> throw new IllegalArgumentException("Paint method " + method + " nao suportado (Laser Lines).");
                };
                PaintParameters parameters = new PaintParameters(List.of(entry.getDouble("tooldia")),
                        data.optDouble("tools_paint_overlap", 20) / 100, data.optDouble("tools_paint_offset", 0),
                        value, data.optBoolean("tools_paint_connect", true), data.optBoolean("tools_paint_contour", true),
                        NccOrder.REVERSE, false);
                tools.add(new PaintTool(entry.optString("name", "Tool " + id), parameters));
            } catch (RuntimeException invalid) { throw new IOException("Invalid Paint tool " + id + ": " + invalid.getMessage(), invalid); }
        }
        return List.copyOf(tools);
    }

    public static List<CutoutTool> cutoutTools(JSONObject root) throws IOException {
        List<CutoutTool> tools = new ArrayList<>();
        for (String id : root.keySet()) {
            try {
                JSONObject entry = root.getJSONObject(id), data = entry.getJSONObject("data");
                if (!accepts(data, 6, "Cutout")) continue;
                requireNonV(entry);
                GapPattern gaps = switch (data.optString("tools_cutout_gaps_ff", "4")) {
                    case "None" -> GapPattern.NONE; case "LR" -> GapPattern.LR; case "TB" -> GapPattern.TB;
                    case "4" -> GapPattern.FOUR; case "2LR" -> GapPattern.TWO_LR;
                    case "2TB" -> GapPattern.TWO_TB; case "8" -> GapPattern.EIGHT;
                    default -> throw new IllegalArgumentException("Padrao de gaps desconhecido.");
                };
                String type = data.optString("tools_cutout_gap_type", "b");
                if (!List.of("b", "bt", "mb").contains(type)) throw new IllegalArgumentException("Tipo de gap desconhecido.");
                double dia = data.optDouble("tools_cutout_mb_dia", 0.6), spacing = data.optDouble("tools_cutout_mb_spacing", 0.3);
                if (!Double.isFinite(dia) || dia <= 0 || !Double.isFinite(spacing) || spacing < 0)
                    throw new IllegalArgumentException("M-Bites exige diametro positivo e espacamento nao negativo.");
                CutoutParameters parameters = new CutoutParameters(entry.getDouble("tooldia"),
                        data.optDouble("tools_cutout_margin", 0.1), data.optBoolean("tools_cutout_convexshape", false),
                        CutoutKind.SINGLE, CutoutShape.FREEFORM, data.optDouble("tools_cutout_gapsize", 4), gaps);
                if (!Double.isFinite(parameters.toolDiameter()) || !Double.isFinite(parameters.margin())
                        || !Double.isFinite(parameters.gapSize())) throw new IllegalArgumentException("Cutout exige valores finitos.");
                // Python's DB callback copies milling cutz/multidepth/depthperpass into Cutout.
                double cutZ = data.optDouble("cutz", data.optDouble("tools_cutout_z", -1.7));
                if (!Double.isFinite(cutZ) || cutZ >= 0) throw new IllegalArgumentException("Cutout Cut Z deve ser negativo.");
                var machining = new GeometryGCodeParameters(data.optDouble("travelz", 2), -cutZ,
                        data.optBoolean("multidepth", data.optBoolean("tools_cutout_mdepth", false)),
                        data.optDouble("depthperpass", data.optDouble("tools_cutout_depthperpass", 0.5)),
                        data.optDouble("feedrate", 120), data.optInt("spindlespeed", 0), false,
                        data.optDouble("feedrate_rapid", 0), null, data.optDouble("feedrate_z", 60),
                        data.optBoolean("dwell", false), data.optDouble("dwelltime", 1),
                        data.optBoolean("extracut", false), data.optDouble("extracut_length", 0.1));
                double thinZ = data.optDouble("tools_cutout_gap_depth", -0.5);
                if (type.equals("bt") && (!Double.isFinite(thinZ) || thinZ >= 0 || -thinZ >= machining.cutDepth()))
                    throw new IllegalArgumentException("Thin Depth deve ser negativo e mais raso que Cut Z.");
                tools.add(new CutoutTool(entry.optString("name", "Tool " + id), parameters, type, dia, spacing,
                        machining, Double.isFinite(thinZ) && thinZ < 0 ? -thinZ : 0.5,
                        ToolProfile.fromLegacy(entry.optString("tool_type", "C1"))));
            } catch (RuntimeException invalid) { throw new IOException("Invalid Cutout tool " + id + ": " + invalid.getMessage(), invalid); }
        }
        return List.copyOf(tools);
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
        return nccTools(root);
    }

    public static List<NccTool> nccTools(JSONObject root) throws IOException {
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
        return isolationTools(root);
    }

    public static List<IsolationTool> isolationTools(JSONObject root) throws IOException {
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
        return drillTools(root);
    }

    public static List<DrillTool> drillTools(JSONObject root) throws IOException {
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
                if (!Double.isFinite(cutZ) || cutZ >= 0)
                    throw new IllegalArgumentException("Drilling Cut Z must be below the surface");
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
