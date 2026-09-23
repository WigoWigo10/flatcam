package org.flatcam.app.project.flatprj;

import java.util.LinkedHashMap;
import java.util.Map;
import org.flatcam.cam.gerber.Aperture;
import org.flatcam.cam.gerber.ApertureKind;
import org.flatcam.cam.gerber.GerberImage;
import org.json.JSONArray;
import org.json.JSONObject;
import org.locationtech.jts.geom.Geometry;

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
 * <p>Two deliberate simplifications, both because the source data isn't
 * retained by {@code GerberParser} today:
 * <ul>
 *   <li>{@code apertures[code]['geometry']} is written as a ONE-entry list
 *       holding the aggregate (already-unioned) geometry for that aperture
 *       code, not Python's one-entry-PER-FLASH breakdown. Fine for display/
 *       CAM (same union of the same shapes), but an eventual editor couldn't
 *       select one flash out of it - a non-issue today since no editor exists.
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

        JSONObject apertures = new JSONObject();
        for (Map.Entry<String, Aperture> entry : image.apertures().entrySet()) {
            String code = entry.getKey();
            Aperture aperture = entry.getValue();
            JSONObject apertureJson = new JSONObject();
            apertureJson.put("type", aperture.kind.name());
            apertureJson.put("width", aperture.width);
            apertureJson.put("height", aperture.height);
            apertureJson.put("size", aperture.width);
            apertureJson.put("polygon_vertices", aperture.polygonVertices());
            apertureJson.put("polygon_rotation", aperture.polygonRotation());

            JSONArray geometryList = new JSONArray();
            Geometry apertureGeometry = image.apertureGeometry().get(code);
            if (apertureGeometry != null && !apertureGeometry.isEmpty()) {
                JSONObject flash = new JSONObject();
                flash.put("solid", WktJson.wrap(apertureGeometry));
                geometryList.put(flash);
            }
            apertureJson.put("geometry", geometryList);
            apertures.put(code, apertureJson);
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
        JSONObject aperturesJson = json.optJSONObject("apertures");
        if (aperturesJson != null) {
            for (String code : aperturesJson.keySet()) {
                JSONObject apertureJson = aperturesJson.getJSONObject(code);
                apertures.put(code, decodeAperture(apertureJson));
                JSONArray geometryList = apertureJson.optJSONArray("geometry");
                if (geometryList != null && geometryList.length() > 0) {
                    Geometry flashGeometry = WktJson.unwrap(geometryList.getJSONObject(0).opt("solid"));
                    if (flashGeometry != null) {
                        apertureGeometry.put(code, flashGeometry);
                    }
                }
            }
        }

        JSONObject options = json.optJSONObject("options");
        String name = options != null ? options.optString("name", "gerber") : "gerber";
        JSONObject javaExtra = json.optJSONObject("_java");
        boolean visible = javaExtra == null || javaExtra.optBoolean("visible", true);
        boolean filled = javaExtra == null || javaExtra.optBoolean("filled", true);
        boolean multicolor = javaExtra != null && javaExtra.optBoolean("multicolor", false);
        boolean followMode = javaExtra != null && javaExtra.optBoolean("follow", false);
        String fillColorWeb = json.optString("fill_color", null);
        String strokeColorWeb = json.optString("outline_color", null);

        GerberImage image = GerberImage.of(units, apertures, solidGeometry, followGeometry, apertureGeometry);
        return new Decoded(name, image, fillColorWeb, strokeColorWeb, visible, filled, multicolor, followMode);
    }

    private static Aperture decodeAperture(JSONObject json) {
        ApertureKind kind;
        try {
            kind = ApertureKind.valueOf(json.optString("type", "CIRCLE"));
        } catch (IllegalArgumentException e) {
            kind = ApertureKind.CIRCLE;
        }
        double width = json.optDouble("width", 0.1);
        double height = json.optDouble("height", width);
        return switch (kind) {
            case CIRCLE, MACRO -> Aperture.circle(width > 0 ? width : 0.1);
            case RECTANGLE -> Aperture.rectangle(width, height);
            case OBROUND -> Aperture.obround(width, height);
            case POLYGON -> Aperture.polygon(width, Math.max(3, json.optInt("polygon_vertices", 3)),
                    json.optDouble("polygon_rotation", 0));
        };
    }
}
