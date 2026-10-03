import java.nio.file.*;
import java.util.*;
import org.json.*;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.io.*;
import org.locationtech.jts.operation.buffer.*;

/** Public synthetic diagnostic fixtures; no geometry from the user's board. */
public class CamKernelProbe {
    static final GeometryFactory factory = new GeometryFactory();
    static final WKTWriter writer = new WKTWriter();
    static Geometry rectangle(double min, double max) {
        return factory.toGeometry(new Envelope(min, max, min, max));
    }
    static Geometry inset(Geometry geometry, double distance) {
        var parameters = new BufferParameters(64);
        parameters.setSimplifyFactor(0);
        return BufferOp.bufferOp(geometry, -distance, parameters);
    }
    static JSONObject describe(Geometry geometry) {
        var result = new JSONObject().put("wkt", writer.write(geometry))
                .put("points", geometry.getNumPoints()).put("area", geometry.getArea());
        if (!geometry.isEmpty()) {
            var seed = geometry.getInteriorPoint().getCoordinate();
            result.put("seed", new JSONArray(List.of(seed.x, seed.y)));
            var reversedSeed = geometry.reverse().getInteriorPoint().getCoordinate();
            result.put("reversedSeed", new JSONArray(List.of(reversedSeed.x, reversedSeed.y)));
        }
        return result;
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Usage: CamKernelProbe.java OUTPUT_JSON");
        var output = new JSONArray();
        var standard = new JSONArray();
        for (int resolution : List.of(4, 16, 64)) {
            Geometry hole = factory.createPoint(new Coordinate(6, 6)).buffer(1, resolution);
            Geometry area = rectangle(0, 12).difference(hole);
            var passes = new JSONArray();
            Geometry current = inset(area, .5 / 1.999999);
            for (int pass = 0; pass < 8 && !current.isEmpty(); pass++) {
                passes.put(describe(current));
                current = inset(current, .3);
            }
            standard.put(new JSONObject().put("index", resolution).put("source", describe(area)).put("passes", passes));
        }
        output.put(new JSONObject().put("id", "synthetic-standard").put("diameter", .5).put("step", .3).put("parts", standard));
        var seeds = new JSONArray();
        int index = 0;
        for (double translate : List.of(0.0, 20.0, -20.0, 1e4)) {
            for (int resolution : List.of(4, 16, 64)) {
                for (double epsilon : List.of(0.0, 1e-14, -1e-14)) {
                    Geometry hole = factory.createPoint(new Coordinate(translate + 4 + epsilon, translate + 4))
                            .buffer(1, resolution);
                    Geometry area = rectangle(translate, translate + 8).difference(hole);
                    seeds.put(new JSONObject().put("index", index++).put("translation", translate)
                            .put("resolution", resolution).put("epsilon", epsilon)
                            .put("source", describe(area)).put("safeArea", describe(inset(area, .25))));
                }
            }
        }
        output.put(new JSONObject().put("id", "synthetic-seed").put("diameter", .5).put("step", .3).put("parts", seeds));
        // Isolate InteriorPoint's scan-line discontinuity, without any buffer/kernel difference.
        Geometry reference = factory.createPolygon(new Coordinate[]{new Coordinate(0, 0), new Coordinate(2, 2),
                new Coordinate(0, 4), new Coordinate(-2, 2), new Coordinate(0, 0)});
        Geometry perturbed = factory.createPolygon(new Coordinate[]{new Coordinate(0, 0), new Coordinate(2, 2 + 1e-12),
                new Coordinate(0, 4), new Coordinate(-2, 2 + 1e-12), new Coordinate(0, 0)});
        output.put(new JSONObject().put("id", "synthetic-scanline")
                .put("reference", describe(reference)).put("perturbed", describe(perturbed)));
        var report = new JSONObject().put("schema", 1).put("java", System.getProperty("java.version"))
                .put("jts", org.locationtech.jts.JTSVersion.CURRENT_VERSION.toString()).put("cases", output);
        Path destination = Path.of(args[0]);
        if (destination.getParent() != null) Files.createDirectories(destination.getParent());
        Files.writeString(destination, report.toString());
        System.out.println("Exported synthetic fixtures: " + standard.length() + " Standard, " + seeds.length() + " Seed, 1 scan-line pair");
    }
}
