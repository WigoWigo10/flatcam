package org.flatcam.fx;

import java.util.*;
import org.json.JSONObject;

/** Field names/types mirror ToolsDB2.form_fields in appDatabase.py. */
final class ToolsDatabaseFields {
    enum Group {
        DESCRIPTION("Tool Description", "search_db32.png"), MILLING("Milling Parameters", "milling_tool32.png"),
        DRILLING("Drilling Parameters", "drilling_tool32.png"), ISOLATION("Isolation Parameters", "iso_16.png"),
        PAINT("Paint Parameters", "paint20.png"), NCC("NCC Parameters", "eraser26.png"), CUTOUT("Cutout Parameters", "cut32_bis.png");
        final String label, icon;
        Group(String label, String icon) { this.label = label; this.icon = icon; }
        boolean visible(int target) {
            return this == DESCRIPTION || target == 0 || this == MILLING && target != 2 && target >= 1
                    || ordinal() == target && target >= 2;
        }
    }
    record Choice(String label, Object value) { @Override public String toString() { return label; } }
    record Field(Group group, String key, String label, boolean root, Object fallback,
                 double min, double max, boolean integer, List<Choice> choices) {
        Object parse(String text) {
            if (fallback instanceof String) return text;
            try {
                double value = Double.parseDouble(text.trim().replace(',', '.'));
                if (!Double.isFinite(value) || value < min || value > max || integer && value != Math.rint(value))
                    throw new NumberFormatException();
                if (integer) return (int) value;
                return value;
            } catch (NumberFormatException error) {
                throw new IllegalArgumentException(label + ": valor invalido (" + min + " a " + max + (integer ? ", inteiro" : "") + ").");
            }
        }
        Object read(JSONObject entry) {
            JSONObject container = root ? entry : entry.getJSONObject("data");
            return container.has(key) ? container.get(key) : fallback;
        }
        void write(JSONObject entry, Object value) { (root ? entry : entry.getJSONObject("data")).put(key, value); }
    }
    private static Field number(Group g, String key, String label, boolean root, double fallback, double min, double max) {
        return new Field(g, key, label, root, fallback, min, max, false, List.of());
    }
    private static Field integer(Group g, String key, String label, int fallback, int min) {
        return new Field(g, key, label, false, fallback, min, Integer.MAX_VALUE, true, List.of());
    }
    private static Field flag(Group g, String key, String label, boolean fallback) {
        return new Field(g, key, label, false, fallback, 0, 0, false, List.of());
    }
    private static Field choices(Group g, String key, String label, boolean root, Object fallback, String... values) {
        List<Choice> choices = new ArrayList<>();
        for (String value : values) choices.add(new Choice(value, value));
        return new Field(g, key, label, root, fallback, 0, 0, false, List.copyOf(choices));
    }
    private static Field indexed(Group g, String key, String label, int fallback, String... labels) {
        List<Choice> choices = new ArrayList<>();
        for (int i = 0; i < labels.length; i++) choices.add(new Choice(labels[i], i));
        return new Field(g, key, label, false, fallback, 0, 0, false, List.copyOf(choices));
    }
    private static Field labeled(Group g, String key, String label, String fallback, String... pairs) {
        List<Choice> choices = new ArrayList<>();
        for (int i = 0; i < pairs.length; i += 2) choices.add(new Choice(pairs[i + 1], pairs[i]));
        return new Field(g, key, label, false, fallback, 0, 0, false, List.copyOf(choices));
    }
    private static Field n(Group g, String key, String label, double fallback, double min) {
        return number(g, key, label, false, fallback, min, 1e9);
    }
    private static Field percent(Group g, String key, String label, double fallback) {
        return number(g, key, label, false, fallback, 0, 99.999999);
    }
    static final List<Field> ALL = List.of(
        new Field(Group.DESCRIPTION, "name", "Name", true, "new_tool", 0, 0, false, List.of()),
        number(Group.DESCRIPTION, "tooldia", "Diameter", true, 2.4, 0.000000001, 10000),
        n(Group.DESCRIPTION, "tol_min", "Tolerance Min", 0, 0), n(Group.DESCRIPTION, "tol_max", "Tolerance Max", 0, 0),
        indexed(Group.DESCRIPTION, "tool_target", "Operation", 0, "General", "Milling", "Drilling", "Isolation", "Paint", "NCC", "Cutout"),
        choices(Group.MILLING, "tool_type", "Shape", true, "C1", "C1", "C2", "C3", "C4", "B", "V"),
        n(Group.MILLING, "vtipdia", "V-Dia", 0.1, 0), number(Group.MILLING, "vtipangle", "V-Angle", false, 30, 0.000001, 179.999999),
        choices(Group.MILLING, "type", "Tool Type", true, "Rough", "Iso", "Rough", "Finish"),
        choices(Group.MILLING, "offset", "Tool Offset", true, "Path", "Path", "In", "Out", "Custom"),
        number(Group.MILLING, "offset_value", "Custom Offset", true, 0, -1e9, 1e9),
        n(Group.MILLING, "cutz", "Cut Z", -2.4, -1e9), flag(Group.MILLING, "multidepth", "Multi-Depth", false),
        n(Group.MILLING, "depthperpass", "Depth / Pass", 0.8, 0.000000001), n(Group.MILLING, "travelz", "Travel Z", 2, 0.000000001),
        n(Group.MILLING, "feedrate", "Feedrate X-Y", 120, 0.000000001), n(Group.MILLING, "feedrate_z", "Feedrate Z", 60, 0.000000001),
        n(Group.MILLING, "feedrate_rapid", "Feedrate Rapids", 1500, 0), integer(Group.MILLING, "spindlespeed", "Spindle RPM", 0, 0),
        flag(Group.MILLING, "dwell", "Dwell", false), n(Group.MILLING, "dwelltime", "Dwell Time", 1, 0),
        flag(Group.MILLING, "extracut", "Extra Cut", false), n(Group.MILLING, "extracut_length", "Extra Cut Length", 0.1, 0),
        n(Group.DRILLING, "tools_drill_cutz", "Cut Z", -1.7, -1e9), flag(Group.DRILLING, "tools_drill_multidepth", "Multi-Depth", false),
        n(Group.DRILLING, "tools_drill_depthperpass", "Depth / Pass", 0.7, 0.000000001),
        n(Group.DRILLING, "tools_drill_travelz", "Travel Z", 2, 0.000000001), n(Group.DRILLING, "tools_drill_feedrate_z", "Feedrate Z", 300, 0.000000001),
        n(Group.DRILLING, "tools_drill_feedrate_rapid", "Feedrate Rapids", 1500, 0), integer(Group.DRILLING, "tools_drill_spindlespeed", "Spindle RPM", 0, 0),
        flag(Group.DRILLING, "tools_drill_dwell", "Dwell", false), n(Group.DRILLING, "tools_drill_dwelltime", "Dwell Time", 1, 0),
        n(Group.DRILLING, "tools_drill_offset", "Offset Z", 0, -1e9), flag(Group.DRILLING, "tools_drill_drill_slots", "Drill Slots", false),
        percent(Group.DRILLING, "tools_drill_drill_overlap", "Overlap (%)", 0), flag(Group.DRILLING, "tools_drill_last_drill", "Last Drill", true),
        integer(Group.ISOLATION, "tools_iso_passes", "Passes", 1, 1), percent(Group.ISOLATION, "tools_iso_overlap", "Overlap (%)", 10),
        labeled(Group.ISOLATION, "tools_iso_milling_type", "Milling Type", "cl", "cl", "Climb", "cv", "Conventional"),
        flag(Group.ISOLATION, "tools_iso_follow", "Follow", false), labeled(Group.ISOLATION, "tools_iso_isotype", "Isolation Type", "full", "full", "Both", "ext", "Exterior", "int", "Interior"),
        percent(Group.PAINT, "tools_paint_overlap", "Overlap (%)", 20), n(Group.PAINT, "tools_paint_offset", "Offset", 0, -1e9),
        indexed(Group.PAINT, "tools_paint_method", "Method", 0, "Standard", "Seed", "Lines", "Laser_lines", "Combo"),
        flag(Group.PAINT, "tools_paint_connect", "Connect", true), flag(Group.PAINT, "tools_paint_contour", "Contour", true),
        labeled(Group.NCC, "tools_ncc_operation", "Operation", "clear", "clear", "Clear", "iso", "Isolation"),
        labeled(Group.NCC, "tools_ncc_milling_type", "Milling Type", "cl", "cl", "Climb", "cv", "Conventional"), percent(Group.NCC, "tools_ncc_overlap", "Overlap (%)", 40),
        n(Group.NCC, "tools_ncc_margin", "Margin", 1, -1e9), indexed(Group.NCC, "tools_ncc_method", "Method", 1, "Standard", "Seed", "Lines", "Combo"),
        flag(Group.NCC, "tools_ncc_connect", "Connect", true), flag(Group.NCC, "tools_ncc_contour", "Contour", true),
        flag(Group.NCC, "tools_ncc_offset_choice", "Offset", false), n(Group.NCC, "tools_ncc_offset_value", "Offset Value", 0, -1e9),
        n(Group.CUTOUT, "tools_cutout_margin", "Margin", 0.1, -1e9), n(Group.CUTOUT, "tools_cutout_gapsize", "Gap Size", 4, 0),
        choices(Group.CUTOUT, "tools_cutout_gaps_ff", "Gaps", false, "4", "None", "LR", "TB", "4", "2LR", "2TB", "8"),
        flag(Group.CUTOUT, "tools_cutout_convexshape", "Convex Shape", false),
        labeled(Group.CUTOUT, "tools_cutout_gap_type", "Gap Type", "b", "b", "Bridge", "bt", "Thin", "mb", "M-Bites"),
        n(Group.CUTOUT, "tools_cutout_gap_depth", "Thin Depth", -1, -1e9), n(Group.CUTOUT, "tools_cutout_mb_dia", "M-Bites Diameter", 0.6, 0.000000001),
        n(Group.CUTOUT, "tools_cutout_mb_spacing", "M-Bites Spacing", 0.3, 0)
    );
    static JSONObject newEntry(String name) {
        JSONObject entry = new JSONObject().put("data", new JSONObject().put("plot", true));
        ALL.forEach(field -> field.write(entry, field.fallback()));
        return entry.put("name", name);
    }
    private ToolsDatabaseFields() { }
}
