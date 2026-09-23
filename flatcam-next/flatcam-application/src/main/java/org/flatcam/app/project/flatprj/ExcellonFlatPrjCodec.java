package org.flatcam.app.project.flatprj;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.flatcam.cam.excellon.ExcellonImage;
import org.json.JSONArray;
import org.json.JSONObject;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;

/**
 * Converts an {@link ExcellonImage} to/from the JSON shape Python's own
 * Excellon/ExcellonObject.ser_attrs produce ({@code appParsers/ParseExcellon.py:121-122},
 * {@code appObjects/FlatCAMExcellon.py:110}). Drills/slots are regrouped per
 * tool into {@code tools[id] = {tooldia, drills, slots, solid_geometry,
 * data}}, matching Python's own nested shape, rather than this port's flat
 * lists keyed by a {@code toolId} field.
 *
 * <p>Per-tool {@code solid_geometry} and {@code data} (per-tool CAM options)
 * are written empty: this port keeps only ONE combined
 * {@link ExcellonImage#solidGeometry()} for every tool, and has no
 * persistent per-tool CAM-options dict at all. Both are harmless gaps for a
 * plain "open, view, re-drill" round-trip, since the top-level combined
 * geometry is what actually renders and drives G-code generation - only a
 * Python-side workflow that specifically needs one tool's own isolated
 * solid_geometry would notice.
 */
public final class ExcellonFlatPrjCodec {

    private static final GeometryFactory FACTORY = new GeometryFactory();

    private ExcellonFlatPrjCodec() {
    }

    public static JSONObject toJson(String name, ExcellonImage image, String fillColorWeb, String strokeColorWeb,
                                    boolean visible, boolean filled, boolean multicolor) {
        JSONObject json = new JSONObject();
        json.put("kind", "excellon");
        json.put("units", image.units());
        json.put("excellon_units", image.units());
        json.put("solid_geometry", WktJson.wrap(image.solidGeometry()));
        json.put("follow_geometry", JSONObject.NULL);

        Map<Integer, List<ExcellonImage.Drill>> drillsByTool = new LinkedHashMap<>();
        for (ExcellonImage.Drill drill : image.drills()) {
            drillsByTool.computeIfAbsent(drill.toolId(), id -> new ArrayList<>()).add(drill);
        }
        Map<Integer, List<ExcellonImage.Slot>> slotsByTool = new LinkedHashMap<>();
        for (ExcellonImage.Slot slot : image.slots()) {
            slotsByTool.computeIfAbsent(slot.toolId(), id -> new ArrayList<>()).add(slot);
        }

        JSONObject tools = new JSONObject();
        for (Map.Entry<Integer, Double> toolEntry : image.toolDiameters().entrySet()) {
            int toolId = toolEntry.getKey();
            JSONObject toolJson = new JSONObject();
            toolJson.put("tooldia", toolEntry.getValue());

            JSONArray drillsJson = new JSONArray();
            for (ExcellonImage.Drill drill : drillsByTool.getOrDefault(toolId, List.of())) {
                drillsJson.put(WktJson.wrap(FACTORY.createPoint(new Coordinate(drill.x(), drill.y()))));
            }
            toolJson.put("drills", drillsJson);

            JSONArray slotsJson = new JSONArray();
            for (ExcellonImage.Slot slot : slotsByTool.getOrDefault(toolId, List.of())) {
                JSONObject slotJson = new JSONObject();
                slotJson.put("start", WktJson.wrap(FACTORY.createPoint(new Coordinate(slot.x1(), slot.y1()))));
                slotJson.put("stop", WktJson.wrap(FACTORY.createPoint(new Coordinate(slot.x2(), slot.y2()))));
                slotsJson.put(slotJson);
            }
            toolJson.put("slots", slotsJson);
            toolJson.put("solid_geometry", new JSONArray());
            toolJson.put("data", new JSONObject());
            tools.put(String.valueOf(toolId), toolJson);
        }
        json.put("tools", tools);

        JSONObject options = new JSONObject();
        options.put("name", name);
        json.put("options", options);
        json.put("fill_color", fillColorWeb == null ? JSONObject.NULL : fillColorWeb);
        json.put("outline_color", strokeColorWeb == null ? JSONObject.NULL : strokeColorWeb);
        json.put("alpha_level", 0.75);

        JSONObject javaExtra = new JSONObject();
        javaExtra.put("visible", visible);
        javaExtra.put("filled", filled);
        javaExtra.put("multicolor", multicolor);
        json.put("_java", javaExtra);
        return json;
    }

    public record Decoded(String name, ExcellonImage image, String fillColorWeb, String strokeColorWeb,
                          boolean visible, boolean filled, boolean multicolor) {
    }

    public static Decoded fromJson(JSONObject json) {
        String units = json.optString("excellon_units", json.optString("units", "MM"));
        Geometry solidGeometry = WktJson.unwrap(json.opt("solid_geometry"));

        Map<Integer, Double> toolDiameters = new LinkedHashMap<>();
        List<ExcellonImage.Drill> drills = new ArrayList<>();
        List<ExcellonImage.Slot> slots = new ArrayList<>();
        JSONObject tools = json.optJSONObject("tools");
        if (tools != null) {
            for (String key : tools.keySet()) {
                int toolId = Integer.parseInt(key);
                JSONObject toolJson = tools.getJSONObject(key);
                toolDiameters.put(toolId, toolJson.optDouble("tooldia", 0.1));

                JSONArray drillsJson = toolJson.optJSONArray("drills");
                if (drillsJson != null) {
                    for (int i = 0; i < drillsJson.length(); i++) {
                        Geometry point = WktJson.unwrap(drillsJson.get(i));
                        if (point != null) {
                            Coordinate c = point.getCoordinate();
                            drills.add(new ExcellonImage.Drill(toolId, c.x, c.y));
                        }
                    }
                }
                JSONArray slotsJson = toolJson.optJSONArray("slots");
                if (slotsJson != null) {
                    for (int i = 0; i < slotsJson.length(); i++) {
                        JSONObject slotJson = slotsJson.getJSONObject(i);
                        Geometry start = WktJson.unwrap(slotJson.opt("start"));
                        Geometry stop = WktJson.unwrap(slotJson.opt("stop"));
                        if (start != null && stop != null) {
                            Coordinate s = start.getCoordinate();
                            Coordinate e = stop.getCoordinate();
                            slots.add(new ExcellonImage.Slot(toolId, s.x, s.y, e.x, e.y));
                        }
                    }
                }
            }
        }

        JSONObject options = json.optJSONObject("options");
        String name = options != null ? options.optString("name", "excellon") : "excellon";
        JSONObject javaExtra = json.optJSONObject("_java");
        boolean visible = javaExtra == null || javaExtra.optBoolean("visible", true);
        boolean filled = javaExtra == null || javaExtra.optBoolean("filled", true);
        boolean multicolor = javaExtra != null && javaExtra.optBoolean("multicolor", false);
        String fillColorWeb = json.optString("fill_color", null);
        String strokeColorWeb = json.optString("outline_color", null);

        ExcellonImage image = ExcellonImage.of(units, toolDiameters, drills, slots, solidGeometry);
        return new Decoded(name, image, fillColorWeb, strokeColorWeb, visible, filled, multicolor);
    }
}
