import java.nio.file.*;
import java.util.*;
import org.json.*;
import org.flatcam.cam.*;
import org.flatcam.cam.ncc.*;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.geom.util.AffineTransformation;
import org.locationtech.jts.io.WKTWriter;

/** Public synthetic round, rounded-rectangle and capsule pads. Exports actual NCC/Paint outputs. */
public class RoundedPadLinesProbe {
    private static final GeometryFactory F = new GeometryFactory();
    private static final WKTWriter WKT = new WKTWriter();
    private static Geometry box(double x1, double x2, double y1, double y2) {
        return F.toGeometry(new Envelope(x1, x2, y1, y2));
    }
    private static Geometry trace(double x1, double y1, double x2, double y2, double radius) {
        return F.createLineString(new Coordinate[]{new Coordinate(x1,y1),new Coordinate(x2,y2)}).buffer(radius,64);
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Usage: RoundedPadLinesProbe.java OUTPUT_JSON");
        Geometry board = box(0,36,0,24);
        Geometry copper = F.createPoint(new Coordinate(10,17)).buffer(2.8,64)
                .union(F.createPoint(new Coordinate(10,7)).buffer(2.8,64))
                .union(trace(3,12,9,17,.65)).union(trace(3,3,9,7,.65))
                .union(box(23,29,14,18).buffer(1.2,64)).union(trace(29,16,33,16,.6))
                .union(trace(24,7,29,7,1.8)).union(trace(29,7,33,4,.5));
        JSONArray cases = new JSONArray();
        for (String units : List.of("MM","IN")) {
            double scale = units.equals("MM") ? 1 : 1/25.4;
            var transform = AffineTransformation.scaleInstance(scale,scale);
            Geometry b = transform.transform(board), c = transform.transform(copper), area = b.difference(c);
            String before = WKT.write(c); double diameter = .8*scale;
            for (boolean connect : List.of(false,true)) for (boolean contour : List.of(false,true)) {
                for (String operation : List.of("ncc","paint")) {
                    NccResult output = operation.equals("ncc")
                            ? NccGenerator.generate(units,c,new NccParameters(List.of(diameter),.4,0,NccMethod.LINES,
                                    connect,contour,0,false,NccOrder.NONE,new NccBoundary.Area(b)))
                            : NccGenerator.paint(units,area,new PaintParameters(List.of(diameter),.4,0,NccMethod.LINES,
                                    connect,contour,NccOrder.NONE,false),CancellationToken.NONE,ProgressCallback.none());
                    if (!before.equals(WKT.write(c))) throw new AssertionError("Source changed");
                    var parameters = new JSONObject().put("overlap",.4).put("method","LINES")
                            .put("connect",connect).put("contour",contour).put("margin",0).put("copperOffset",0)
                            .put("restMachining",false).put("order","NONE").put("boundary","area")
                            .put("referenceWkt",WKT.write(b)).put("fxClearingAreaWkt",WKT.write(output.clearingArea()));
                    cases.put(new JSONObject().put("id",operation+"-"+units.toLowerCase()+"-c"+(connect?1:0)+"-e"+(contour?1:0))
                            .put("operation",operation).put("units",units).put("diameter",diameter)
                            .put("inputWkt",WKT.write(operation.equals("ncc")?c:area)).put("copperWkt",WKT.write(c))
                            .put("boardWkt",WKT.write(b)).put("fxWkt",WKT.write(output.geometry()))
                            .put("fxFailedPolygons",output.totalFailedPolygonCount()).put("parameters",parameters));
                }
            }
        }
        var output = Path.of(args[0]); Files.createDirectories(output.toAbsolutePath().getParent());
        Files.writeString(output,new JSONObject().put("schema",1).put("fixture","public-rounded-pads-v1")
                .put("cases",cases).toString(2));
        System.out.println("Exported "+cases.length()+" public rounded-pad controls to "+output);
    }
}
