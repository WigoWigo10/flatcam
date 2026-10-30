package org.flatcam.fx;

import java.util.function.*;
import javafx.scene.Node;
import org.json.JSONObject;

/** A tool owns its draft codec; MainWindow only associates it with the project/source. */
record CamToolState(Supplier<JSONObject> capture, Consumer<JSONObject> restore, Supplier<String> stamp) {
    private static final String KEY = CamToolState.class.getName();
    void attach(Node form) { form.getProperties().put(KEY, this); }
    static CamToolState of(Node form) { return (CamToolState) form.getProperties().get(KEY); }
    /** Semantic draft equality without serializing the source/reference geometry. */
    static String stamp(JSONObject json) {
        var copy = new JSONObject(json.toString());
        copy.remove("sourceSignature"); copy.remove("referenceSignature");
        return canonical(copy);
    }
    private static String canonical(Object value) {
        if (value instanceof JSONObject object) return object.keySet().stream().sorted()
                .map(key -> JSONObject.quote(key) + ":" + canonical(object.get(key)))
                .collect(java.util.stream.Collectors.joining(",", "{", "}"));
        if (value instanceof org.json.JSONArray array) return java.util.stream.IntStream.range(0, array.length())
                .mapToObj(index -> canonical(array.get(index))).collect(java.util.stream.Collectors.joining(",", "[", "]"));
        return JSONObject.valueToString(value);
    }
    static String signature(org.locationtech.jts.geom.Geometry geometry) {
        try {
            var bytes = new org.locationtech.jts.io.WKTWriter().write(geometry).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
}
