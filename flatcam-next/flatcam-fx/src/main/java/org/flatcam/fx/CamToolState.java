package org.flatcam.fx;

import java.util.function.*;
import javafx.scene.Node;
import org.json.JSONObject;

/** A tool owns its draft codec; MainWindow only associates it with the project/source. */
record CamToolState(Supplier<JSONObject> capture, Consumer<JSONObject> restore) {
    private static final String KEY = CamToolState.class.getName();
    void attach(Node form) { form.getProperties().put(KEY, this); }
    static CamToolState of(Node form) { return (CamToolState) form.getProperties().get(KEY); }
    static String signature(org.locationtech.jts.geom.Geometry geometry) {
        try {
            var bytes = new org.locationtech.jts.io.WKTWriter().write(geometry).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
}
