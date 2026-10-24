import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.util.AffineTransformation;
import org.locationtech.jts.io.WKTReader;
import org.locationtech.jts.io.WKTWriter;
import org.flatcam.cam.gcode.GCodeGenerator;
import org.flatcam.cam.gcode.GeometryGCodeParameters;
import org.flatcam.cam.geometry.ToolGeometry;
import org.flatcam.cam.ncc.*;

/** Independent public point-touch fixture, exported for compare_cam_python.py's original GUI oracle. */
public class NccRestSeamProbe {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Usage: NccRestSeamProbe.java NEW_OUTPUT_DIRECTORY");
        Path output = Path.of(args[0]);
        if (Files.exists(output)) throw new IllegalArgumentException("Choose a new output directory");
        Files.createDirectories(output);
        var writer = new WKTWriter();
        Geometry source = new WKTReader().read("POLYGON ((5 20,2 10,5 0,8 10,5 20))");
        Geometry reference = new GeometryFactory().toGeometry(new Envelope(0,30,0,20));
        for (String units : List.of("MM", "IN")) {
            double u = units.equals("IN") ? 1 / 25.4 : 1;
            Geometry copper = AffineTransformation.scaleInstance(u,u).transform(source);
            Geometry boundary = AffineTransformation.scaleInstance(u,u).transform(reference);
            var cases = new JSONArray();
            for (boolean rest : List.of(false,true)) for (boolean connect : List.of(false,true)) {
                var params = new NccParameters(List.of(u),.4,0,NccMethod.STANDARD,connect,true,0,
                        rest,NccOrder.NONE,new NccBoundary.ReferenceGeometry(boundary));
                var result = NccGenerator.generate(units,copper,params);
                var cnc = new GeometryGCodeParameters(3*u,.1*u,false,0,300*u,0,false);
                var toolResults = new JSONArray();
                var cncTools = new ArrayList<ToolGeometry>();
                for (var tool : result.toolResults()) {
                    cncTools.add(new ToolGeometry(tool.toolDiameter(),tool.geometry()));
                    toolResults.put(new JSONObject().put("diameter",tool.toolDiameter())
                            .put("fxWkt",writer.write(tool.geometry()))
                            .put("fxFailedPolygons",tool.failedPolygonCount())
                            .put("gcode",tool.isEmpty() ? "" : GCodeGenerator.generateGeometryCncJob(
                                    units,tool.geometry(),cnc,tool.toolDiameter()).gcode()));
                }
                var settings = new JSONObject().put("diameter",u).put("overlap",.4).put("method","STANDARD")
                        .put("connect",connect).put("contour",true).put("copperOffset",0);
                cases.put(new JSONObject().put("id","ncc-"+(rest ? "rest-" : "")+"reference-geometry"+(connect ? "-connect" : ""))
                        .put("operation","ncc").put("units",units).put("diameter",u)
                        .put("fxWkt",writer.write(result.geometry()))
                        .put("fxDetailedPreviewAvailable",false).put("fxPreviewWarning","Numerical CAM probe; no UI preview")
                        .put("gcode",GCodeGenerator.generateGeometryCncJob(units,cncTools,cnc).gcode())
                        .put("parameters",new JSONObject().put("boundary","reference-geometry")
                                .put("referenceWkt",writer.write(boundary)).put("margin",0).put("overlap",.4)
                                .put("method","STANDARD").put("connect",connect).put("contour",true)
                                .put("copperOffset",0).put("order","NONE").put("restMachining",rest)
                                .put("tools",new JSONArray().put(settings)).put("fxToolResults",toolResults)
                                .put("fxFailedPolygons",result.totalFailedPolygonCount())
                                .put("fxClearingAreaWkt",writer.write(result.clearingArea()))));
            }
            Files.writeString(output.resolve(units+".json"),new JSONObject().put("schema",1)
                    .put("sourceName","public-point-touch-diamond").put("units",units)
                    .put("sourceWkt",writer.write(copper)).put("cases",cases).toString());
        }
        System.out.println("Public NCC seams exported: Rest on/off x Connect on/off x MM/IN");
    }
}
