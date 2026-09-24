package org.flatcam.app.project.flatprj;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.flatcam.cam.gerber.Aperture;
import org.flatcam.cam.gerber.ApertureKind;
import org.flatcam.cam.gerber.GerberImage;
import org.flatcam.cam.gerber.GerberShape;
import org.json.JSONArray;
import org.json.JSONObject;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.operation.union.UnaryUnionOp;

/**
 * Converts a {@link GerberImage} to/from the JSON shape Python's own Gerber/
 * GerberObject.ser_attrs produce ({@code appParsers/ParseGerber.py:231},
 * {@code appObjects/FlatCAMGerber.py:108}): {@code kind}/{@code units}/
 * {@code solid_geometry}/{@code follow_geometry}/{@code tools}/
 * {@code apertures}/{@code options}/{@code fill_color}/{@code outline_color}/
 * {@code alpha_level} - so a project this port saves can be opened by a real
 * FlatCAM Python install (confirmed against the Python source; not verified
 * against a live Python+Shapely install, none is available here).
 *
 * <p>The Java editor additionally writes {@code _java.shape_order} so it can
 * replay dark/clear shapes in their original file order after a round trip.
 * Python ignores this private field and reads the standard per-aperture
 * {@code geometry} lists. One deliberate simplification remains:
 * <ul>
 *   <li>A MACRO aperture's exact evaluated shape is not reconstructed on
 *       load (the raw macro text isn't kept) - it round-trips as a CIRCLE
 *       placeholder. This only affects the Apertures Table's display for
 *       that row; {@link GerberImage#apertureGeometry()}/{@link GerberImage#solidGeometry()}
 *       (what every real CAM operation reads) are stored independently and
 *       come back exactly as saved either way.
 * </ul>
 */
public final class GerberFlatPrjCodec {

    private GerberFlatPrjCodec() {
    }

    public static JSONObject toJson(String name, GerberImage image, String fillColorWeb, String strokeColorWeb,
                                    boolean visible, boolean filled, boolean multicolor, boolean followMode) {
        JSONObject json = new JSONObject();
        json.put("kind", "gerber");
        json.put("units", image.units());
        json.put("solid_geometry", WktJson.wrap(image.solidGeometry()));
        json.put("follow_geometry", WktJson.wrap(image.followGeometry()));
        json.put("tools", new JSONObject());

        Map<String, JSONArray> shapesByAperture = new LinkedHashMap<>();
        JSONArray shapeOrder = new JSONArray();
        for (GerberShape shape : image.shapes()) {
            JSONArray group = shapesByAperture.computeIfAbsent(shape.apertureCode(), ignored -> new JSONArray());
            JSONObject shapeJson = new JSONObject();
            shapeJson.put(shape.clear() ? "clear" : "solid", WktJson.wrap(shape.geometry()));
            if (shape.followGeometry() != null) {
                shapeJson.put("follow", WktJson.wrap(shape.followGeometry()));
            }
            shapeOrder.put(new JSONObject().put("code", shape.apertureCode()).put("index", group.length()));
            group.put(shapeJson);
        }

        JSONObject apertures = new JSONObject();
        for (Map.Entry<String, Aperture> entry : image.apertures().entrySet()) {
            String code = entry.getKey();
            Aperture aperture = entry.getValue();
            JSONObject apertureJson = new JSONObject();
            switch (aperture.kind) {
                case CIRCLE -> apertureJson.put("type", "C").put("size", aperture.width);
                case RECTANGLE, OBROUND -> apertureJson
                        .put("type", aperture.kind == ApertureKind.RECTANGLE ? "R" : "O")
                        .put("width", aperture.width).put("height", aperture.height)
                        .put("size", Math.hypot(aperture.width, aperture.height));
                case POLYGON -> apertureJson.put("type", "P")
                        .put("diam", aperture.width).put("nVertices", aperture.polygonVertices())
                        .put("rotation", aperture.polygonRotation()).put("size", aperture.width);
                // The parser does not retain the macro definition. The actual
                // shape geometry is serialized separately; a harmless circular
                // aperture keeps Python's editor from expecting a missing macro.
                case MACRO -> apertureJson.put("type", "C").put("size", 0.1);
            }

            JSONArray geometryList = shapesByAperture.get(code);
            if (geometryList == null) {
                geometryList = new JSONArray();
                if (image.shapes().isEmpty()) {
                    Geometry apertureGeometry = image.apertureGeometry().get(code);
                    if (apertureGeometry != null && !apertureGeometry.isEmpty()) {
                        geometryList.put(new JSONObject().put("solid", WktJson.wrap(apertureGeometry)));
                    }
                }
            }
            apertureJson.put("geometry", geometryList);
            apertures.put(code, apertureJson);
        }
        JSONArray regions = shapesByAperture.get(GerberShape.REGION_APERTURE);
        if (regions != null) {
            apertures.put(GerberShape.REGION_APERTURE,
                    new JSONObject().put("type", "REG").put("size", 0.0).put("geometry", regions));
        }
        json.put("apertures", apertures);

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
        javaExtra.put("follow", followMode);
        if (!image.shapes().isEmpty()) {
            javaExtra.put("shape_order", shapeOrder);
        }
        json.put("_java", javaExtra);
        return json;
    }

    public record Decoded(String name, GerberImage image, String fillColorWeb, String strokeColorWeb,
                          boolean visible, boolean filled, boolean multicolor, boolean followMode) {
    }

    public static Decoded fromJson(JSONObject json) {
        String units = json.optString("units", "MM");
        Geometry solidGeometry = WktJson.unwrap(json.opt("solid_geometry"));
        Geometry followGeometry = WktJson.unwrap(json.opt("follow_geometry"));

        Map<String, Aperture> apertures = new LinkedHashMap<>();
        Map<String, Geometry> apertureGeometry = new LinkedHashMap<>();
        Map<String, List<GerberShape>> shapesByAperture = new LinkedHashMap<>();
        JSONObject aperturesJson = json.optJSONObject("apertures");
        if (aperturesJson != null) {
            for (String code : aperturesJson.keySet()) {
                JSONObject apertureJson = aperturesJson.getJSONObject(code);
                if (!GerberShape.REGION_APERTURE.equals(code)) {
                    apertures.put(code, decodeAperture(apertureJson));
                }
                JSONArray geometryList = apertureJson.optJSONArray("geometry");
                if (geometryList != null) {
                    List<GerberShape> shapes = new ArrayList<>();
                    List<Geometry> apertureParts = new ArrayList<>();
                    for (int i = 0; i < geometryList.length(); i++) {
                        JSONObject item = geometryList.optJSONObject(i);
                        if (item == null) {
                            continue;
                        }
                        boolean clear = item.has("clear") && !item.isNull("clear");
                        Geometry shapeGeometry = WktJson.unwrap(item.opt(clear ? "clear" : "solid"));
                        if (shapeGeometry == null) {
                            continue;
                        }
                        shapes.add(new GerberShape(code, shapeGeometry, clear, WktJson.unwrap(item.opt("follow"))));
                        apertureParts.add(shapeGeometry);
                    }
                    shapesByAperture.put(code, shapes);
                    if (!GerberShape.REGION_APERTURE.equals(code) && !apertureParts.isEmpty()) {
                        apertureGeometry.put(code, apertureParts.size() == 1
                                ? apertureParts.get(0) : UnaryUnionOp.union(apertureParts));
                    }
                }
            }
        }

        JSONObject options = json.optJSONObject("options");
        String name = options != null ? options.optString("name", "gerber") : "gerber";
        JSONObject javaExtra = json.optJSONObject("_java");
        List<GerberShape> orderedShapes = new ArrayList<>();
        JSONArray shapeOrder = javaExtra == null ? null : javaExtra.optJSONArray("shape_order");
        if (shapeOrder != null) {
            for (int i = 0; i < shapeOrder.length(); i++) {
                JSONObject item = shapeOrder.getJSONObject(i);
                List<GerberShape> group = shapesByAperture.get(item.getString("code"));
                int index = item.getInt("index");
                if (group == null || index < 0 || index >= group.size()) {
                    throw new IllegalArgumentException("Invalid Gerber shape order in project");
                }
                orderedShapes.add(group.get(index));
            }
        } else if (javaExtra == null) {
            // Python groups shapes by aperture and does not retain the original
            // cross-aperture polarity order. All-dark groups can still be edited
            // exactly because union is independent of order.
            boolean hasClear = shapesByAperture.values().stream()
                    .flatMap(List::stream).anyMatch(GerberShape::clear);
            if (!hasClear) {
                shapesByAperture.values().forEach(orderedShapes::addAll);
            }
        }
        boolean visible = javaExtra == null || javaExtra.optBoolean("visible", true);
        boolean filled = javaExtra == null || javaExtra.optBoolean("filled", true);
        boolean multicolor = javaExtra != null && javaExtra.optBoolean("multicolor", false);
        boolean followMode = javaExtra != null && javaExtra.optBoolean("follow", false);
        String fillColorWeb = json.optString("fill_color", null);
        String strokeColorWeb = json.optString("outline_color", null);

        GerberImage image = GerberImage.of(units, apertures, solidGeometry, followGeometry,
                apertureGeometry, orderedShapes);
        return new Decoded(name, image, fillColorWeb, strokeColorWeb, visible, filled, multicolor, followMode);
    }

    private static Aperture decodeAperture(JSONObject json) {
        String type = json.optString("type", "C");
        ApertureKind kind = switch (type) {
            case "R", "RECTANGLE" -> ApertureKind.RECTANGLE;
            case "O", "OBROUND" -> ApertureKind.OBROUND;
            case "P", "POLYGON" -> ApertureKind.POLYGON;
            case "AM", "MACRO" -> ApertureKind.MACRO;
            default -> ApertureKind.CIRCLE;
        };
        double width = json.optDouble("width", json.optDouble("diam", json.optDouble("size", 0.1)));
        double height = json.optDouble("height", width);
        return switch (kind) {
            case CIRCLE, MACRO -> Aperture.circle(width > 0 ? width : 0.1);
            case RECTANGLE -> Aperture.rectangle(width, height);
            case OBROUND -> Aperture.obround(width, height);
            case POLYGON -> Aperture.polygon(width, Math.max(3,
                    json.optInt("nVertices", json.optInt("polygon_vertices", 3))),
                    json.optDouble("rotation", json.optDouble("polygon_rotation", 0)));
        };
    }
}
