import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.WKTReader;
import org.locationtech.jts.io.WKTWriter;
import org.locationtech.jts.operation.buffer.BufferOp;
import org.locationtech.jts.operation.buffer.BufferParameters;
import org.locationtech.jts.operation.overlayng.OverlayNG;
import org.locationtech.jts.operation.overlayng.OverlayNGRobust;
import org.locationtech.jts.operation.union.UnaryUnionOp;
import org.flatcam.cam.ncc.geosbuffer.GeosBufferOp;
import org.flatcam.cam.ncc.*;
import org.flatcam.cam.gcode.GCodeGenerator;
import org.flatcam.cam.gcode.GeometryGCodeParameters;
import org.flatcam.cam.geometry.ToolGeometry;

/** Diagnostic stages only. Candidate buffers do not change production NCC. */
public class NccPreparationProbe {
    public static void main(String[] args) throws Exception {
        if (args.length != 2 && args.length != 3) throw new IllegalArgumentException(
                "Usage: FX_EXPORT_JSON NEW_OUTPUT_JSON [NEW_DIAGNOSTIC_CAM_EXPORT]");
        Path output = Path.of(args[1]);
        if (Files.exists(output)) throw new IllegalArgumentException("Choose a new output file");
        if (args.length == 3 && Files.exists(Path.of(args[2]))) throw new IllegalArgumentException("Choose a new control file");
        var export = args[0].equals("--synthetic") ? syntheticExport() : new JSONObject(Files.readString(Path.of(args[0])));
        if (export.getInt("schema") != 1) throw new IllegalArgumentException("Unsupported export schema");
        var reader = new WKTReader();
        var writer = new WKTWriter();
        Geometry source = reader.read(export.getString("sourceWkt"));
        var cases = export.getJSONArray("cases");
        JSONObject baseline = null;
        for (int i = 0; i < cases.length(); i++) {
            if (cases.getJSONObject(i).getString("id").equals("ncc-multi-none")) baseline = cases.getJSONObject(i);
        }
        if (baseline == null) throw new IllegalArgumentException("Export must include ncc-multi-none");
        double margin = baseline.getJSONObject("parameters").getDouble("margin");
        Geometry clean = source.buffer(0);
        Geometry rawHull = source.convexHull();
        Geometry cleanHull = clean.convexHull();
        var params = new BufferParameters(64,BufferParameters.CAP_ROUND,BufferParameters.JOIN_MITRE,5);
        Geometry jtsBoundary = BufferOp.bufferOp(cleanHull,margin,params);
        Geometry geosBoundary = GeosBufferOp.bufferOp(cleanHull,margin,params);
        Geometry rawJtsBoundary = BufferOp.bufferOp(rawHull,margin,params);
        Geometry rawGeosBoundary = GeosBufferOp.bufferOp(rawHull,margin,params);
        var stages = new JSONObject();
        stages.put("source",writer.write(source));
        stages.put("cleanCopper",writer.write(clean));
        stages.put("rawHull",writer.write(rawHull));
        stages.put("cleanHull",writer.write(cleanHull));
        stages.put("jtsBoundary",writer.write(jtsBoundary));
        stages.put("geosBoundary",writer.write(geosBoundary));
        stages.put("rawJtsBoundary",writer.write(rawJtsBoundary));
        stages.put("rawGeosBoundary",writer.write(rawGeosBoundary));
        stages.put("exportedClearingArea",baseline.getJSONObject("parameters").getString("fxClearingAreaWkt"));
        for (String name : new String[]{"jts","geos"}) {
            Geometry boundary = name.equals("jts") ? jtsBoundary : geosBoundary;
            stages.put(name + "ClearingArea",writer.write(
                    OverlayNGRobust.overlay(boundary,clean,OverlayNG.DIFFERENCE).buffer(0)));
        }
        stages.put("geosClearingWithoutFinalRepair",writer.write(
                OverlayNGRobust.overlay(geosBoundary,clean,OverlayNG.DIFFERENCE)));
        Geometry rawPolygonal = source;
        while (rawPolygonal.getGeometryType().equals("GeometryCollection") && rawPolygonal.getNumGeometries() == 1) {
            rawPolygonal = rawPolygonal.getGeometryN(0);
        }
        if (!rawPolygonal.getGeometryType().equals("GeometryCollection")) {
            stages.put("geosClearingRawTarget",writer.write(
                    OverlayNGRobust.overlay(geosBoundary,rawPolygonal,OverlayNG.DIFFERENCE)));
        }
        stages.put("classicUnionCopper",writer.write(UnaryUnionOp.union(source)));
        stages.put("modernUnionCopper",writer.write(OverlayNGRobust.union(source)));
        var report = new JSONObject().put("schema",1).put("sourceName",export.getString("sourceName"))
                .put("units",export.getString("units")).put("margin",margin).put("stages",stages);
        Files.writeString(output,report.toString(2));
        System.out.println("NCC preparation stages saved; candidate buffers are diagnostic only.");
        if (args.length == 3) {
            var selected = Set.of("ncc-standard","ncc-connect","ncc-multi-none","ncc-multi-forward",
                    "ncc-multi-reverse","ncc-rest","ncc-rest-area","ncc-rest-reference-geometry","ncc-rest-connect");
            var controls = new JSONArray();
            for (int i = 0; i < cases.length(); i++) {
                var entry = cases.getJSONObject(i);
                if (!selected.contains(entry.getString("id"))) continue;
                var p = entry.getJSONObject("parameters");
                if (Set.of("itself","connect","no-contour").contains(p.optString("boundary","itself"))) {
                    if (p.getDouble("margin") != margin) throw new IllegalArgumentException("Control margins differ");
                    var diameters = new ArrayList<Double>();
                    var settings = new HashMap<Double,NccToolSettings>();
                    boolean multi = p.has("tools");
                    if (multi) {
                        var inputs = p.getJSONArray("tools");
                        for (int t = 0; t < inputs.length(); t++) {
                            var input = inputs.getJSONObject(t);
                            double diameter = input.getDouble("diameter");
                            diameters.add(diameter);
                            settings.put(diameter,new NccToolSettings(input.getDouble("overlap"),
                                    NccMethod.valueOf(input.getString("method")),input.getBoolean("connect"),
                                    input.getBoolean("contour"),input.getDouble("copperOffset")));
                        }
                    } else diameters.add(entry.getDouble("diameter"));
                    // Pre-expanded candidate boundary + zero margin isolates only
                    // the margin stage, without modifying production Itself code.
                    var nccParams = new NccParameters(diameters,p.getDouble("overlap"),0,
                            NccMethod.valueOf(p.getString("method")),p.getBoolean("connect"),
                            p.getBoolean("contour"),p.optDouble("copperOffset",0),p.optBoolean("restMachining",false),
                            NccOrder.valueOf(p.optString("order","NONE")),new NccBoundary.Area(geosBoundary),List.of(),settings);
                    String units = export.getString("units");
                    var result = NccGenerator.generate(units,source,nccParams);
                    entry.put("units",units).put("fxWkt",writer.write(result.geometry()));
                    p.put("fxClearingAreaWkt",writer.write(result.clearingArea()))
                            .put("fxFailedPolygons",result.totalFailedPolygonCount());
                    var toolOutputs = new JSONArray();
                    var cncTools = new ArrayList<ToolGeometry>();
                    for (var tool : result.toolResults()) {
                        cncTools.add(new ToolGeometry(tool.toolDiameter(),tool.geometry()));
                        toolOutputs.put(new JSONObject().put("diameter",tool.toolDiameter())
                                .put("fxWkt",writer.write(tool.geometry())).put("fxFailedPolygons",tool.failedPolygonCount())
                                .put("gcode",tool.isEmpty() ? "" : GCodeGenerator.generateGeometryCncJob(units,
                                        tool.geometry(),cncParameters(units,false),tool.toolDiameter()).gcode()));
                    }
                    if (multi) p.put("fxToolResults",toolOutputs);
                    entry.put("gcode",GCodeGenerator.generateGeometryCncJob(units,cncTools,cncParameters(units,multi)).gcode());
                    // Preview metadata belongs to the old export, not this control.
                    entry.put("fxDetailedPreviewAvailable",false).put("fxPreviewWarning","Diagnostic control; preview not checked");
                    System.out.println("Diagnostic margin control generated: " + entry.getString("id"));
                }
                controls.put(entry);
            }
            if (controls.length() != selected.size()) throw new IllegalArgumentException("Missing control cases");
            export.put("cases",controls).put("diagnosticControl","Candidate GEOS margin only; production NCC is unchanged");
            Files.writeString(Path.of(args[2]),export.toString(2));
        }
    }

    private static GeometryGCodeParameters cncParameters(String units,boolean multi) {
        double mm = units.equals("IN") || units.equals("INCH") ? 1 / 25.4 : 1;
        return new GeometryGCodeParameters(3 * mm,.1 * mm,false,0,300 * mm,0,multi);
    }

    private static JSONObject syntheticExport() throws Exception {
        // Independently designed shallow convex corner, no private coordinates.
        Geometry copper = new WKTReader().read("POLYGON ((0 0,30 0,30 20,15 20.15,0 20,0 0))");
        var result = NccGenerator.generate("MM",copper,new NccParameters(.5,.4,1,NccMethod.STANDARD,false,true,0));
        var baseline = new JSONObject().put("id","ncc-multi-none").put("parameters",new JSONObject()
                .put("margin",1).put("fxClearingAreaWkt",new WKTWriter().write(result.clearingArea())));
        return new JSONObject().put("schema",1).put("sourceName","public-shallow-convex-corner")
                .put("units","MM").put("sourceWkt",new WKTWriter().write(copper))
                .put("cases",new JSONArray().put(baseline));
    }
}
