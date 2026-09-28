package org.flatcam.app.project.flatprj;

import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.locationtech.jts.io.WKTWriter;

/**
 * Wraps/unwraps a JTS Geometry exactly the way Python's own .FlatPrj project
 * format wraps a Shapely geometry ({@code camlib.py}'s {@code to_dict}/
 * {@code dict2obj}, used as {@code json.dumps(..., default=to_dict)}):
 * <pre>{"__class__": "Shply", "__inst__": "&lt;WKT text&gt;"}</pre>
 * WKT is a plain OGC text format understood by both JTS and Shapely, so a
 * project saved by this port embeds geometry a real FlatCAM Python install
 * can read back (and vice versa) without either side knowing about the
 * other's geometry library. This shape was confirmed by reading
 * {@code camlib.py}'s serialization code directly; it has not been verified
 * against a live Python + Shapely install, since none is available in this
 * project's development environment.
 */
public final class WktJson {

    private static final String CLASS_KEY = "__class__";
    private static final String INST_KEY = "__inst__";
    private static final String SHAPELY_CLASS = "Shply";

    private static final GeometryFactory FACTORY = new GeometryFactory();
    private static final WKTWriter WRITER = new WKTWriter();

    private WktJson() {
    }

    /** A wrapped WKT object, or {@link JSONObject#NULL} for a null/empty geometry - matches Python writing {@code None}. */
    public static Object wrap(Geometry geometry) {
        if (geometry == null || geometry.isEmpty()) {
            return JSONObject.NULL;
        }
        JSONObject wrapped = new JSONObject();
        wrapped.put(CLASS_KEY, SHAPELY_CLASS);
        wrapped.put(INST_KEY, WRITER.write(geometry));
        return wrapped;
    }

    /** Reads one wrapped geometry or Python's nested lists of wrapped geometries. */
    public static Geometry unwrap(Object value) {
        if (value instanceof JSONArray array) {
            List<Geometry> parts = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                Geometry part = unwrap(array.get(i));
                if (part != null && !part.isEmpty()) parts.add(part);
            }
            return parts.isEmpty() ? FACTORY.createGeometryCollection() : FACTORY.buildGeometry(parts);
        }
        if (!(value instanceof JSONObject wrapped) || !wrapped.has(INST_KEY)) {
            return null;
        }
        String wkt = wrapped.getString(INST_KEY);
        try {
            return new WKTReader(FACTORY).read(wkt);
        } catch (ParseException e) {
            throw new IllegalArgumentException("Invalid WKT in project file: " + wkt, e);
        }
    }
}
