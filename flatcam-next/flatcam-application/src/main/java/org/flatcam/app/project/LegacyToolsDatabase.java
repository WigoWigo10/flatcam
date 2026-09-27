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
import org.json.JSONObject;

/** Read-only adapter for Python's JSON tools_db.FlatDB entries targeted at NCC. */
public final class LegacyToolsDatabase {

    public record NccTool(String name, double diameter, NccOperation operation,
                          NccToolSettings settings, ToolProfile toolProfile) {
        @Override
        public String toString() {
            return name + " - Ø " + diameter + " (" + operation + ")";
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
}
