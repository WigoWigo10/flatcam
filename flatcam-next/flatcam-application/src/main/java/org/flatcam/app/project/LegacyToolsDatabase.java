package org.flatcam.app.project;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.flatcam.cam.ncc.NccMethod;
import org.flatcam.cam.ncc.NccOperation;
import org.flatcam.cam.ncc.NccToolSettings;
import org.flatcam.cam.geometry.ToolProfile;
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
}
